package com.vantaflow.kairo.ultratv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/** Lignes lues au plus dans une réponse du fournisseur. */
const val UTV_MAX_ROWS = 20

/** Intervalle minimal entre deux lectures dues aux notifications d'Ultra TV (elle est alors vivante). */
const val UTV_MIN_READ_INTERVAL_MS = 30_000L

/**
 * Retour sur l'accueil : pas de lecture si la précédente date de moins de cette durée. Ultra TV a
 * peut-être été tuée par le système : la relire la relance, on ne le fait pas à chaque retour.
 */
const val UTV_REFRESH_INTERVAL_MS = 300_000L

/** Au-delà, une lecture est considérée en échec (process d'Ultra TV trop long à démarrer). */
const val UTV_READ_TIMEOUT_MS = 10_000L

/** Box allumée depuis moins longtemps : aucune lecture (les autres applis démarrent encore). */
const val ULTRA_TV_QUIET_AFTER_BOOT_MS = 180_000L

/** Chaînes d'Ultra TV à l'accueil ; [pkg] null : Ultra TV n'est pas installée. */
data class UtvState(
    val pkg: String? = null,
    val channels: List<UtvChannel> = emptyList(),
    val kids: Boolean = false,
)

/**
 * Lit le fournisseur qu'Ultra TV réserve à Kairo. Interroger le fournisseur démarre le process
 * d'Ultra TV : rien n'est lu avant [start] (appelé à l'ouverture de la porte de démarrage) ni avant
 * [ULTRA_TV_QUIET_AFTER_BOOT_MS] de vie de la box, au plus une lecture par [UTV_MIN_READ_INTERVAL_MS],
 * jamais de nouvel essai automatique après un échec.
 * Sans dépendance Android : [installed] (paquets installés), [query] (lecture d'un chemin, null en
 * cas d'échec ; doit s'interrompre vite quand la coroutine est annulée), [changes] (notifications du
 * fournisseur), [ownerOf] (paquet qui déclare une autorité) et [uptimeMs] (temps depuis l'allumage
 * de la box, horloge monotone) sont fournis par le conteneur.
 */
class UltraTvSource(
    private val installed: Flow<Set<String>>,
    private val query: suspend (authority: String, path: String) -> List<Map<String, Any?>>?,
    private val changes: (authority: String) -> Flow<Unit>,
    private val ownerOf: (authority: String) -> String?,
    private val uptimeMs: () -> Long,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(UtvState())
    val state: StateFlow<UtvState> = mutableState.asStateFlow()

    private val started = AtomicBoolean(false)

    /** Demandes de lecture du paquet retenu ; null tant qu'aucun paquet Ultra TV n'est suivi. */
    @Volatile private var requests: Channel<Unit>? = null

    /** Moment de la dernière lecture selon [uptimeMs] ; null avant la première. */
    @Volatile private var lastReadAt: Long? = null

    /** Suit le paquet Ultra TV installé, l'observe et le lit ; les appels suivants sont sans effet. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            installed.map(::pickUltraTvPackage).distinctUntilChanged().collectLatest { pkg ->
                if (pkg == null) {
                    // Absente ou désinstallée : état vide, aucun observateur.
                    mutableState.value = UtvState()
                } else {
                    // Nouveau paquet : rien de l'ancien n'est gardé (ses liens ouvriraient la mauvaise appli).
                    mutableState.value = UtvState(pkg = pkg)
                    follow(pkg)
                }
            }
        }
    }

    /**
     * Accueil revenu au premier plan : relecture seulement si la précédente date d'au moins
     * [UTV_REFRESH_INTERVAL_MS] (sinon rien, pas même plus tard).
     */
    fun refresh() {
        val last = lastReadAt
        if (last != null && uptimeMs() - last < UTV_REFRESH_INTERVAL_MS) return
        requests?.trySend(Unit)
    }

    /** Observe et lit [pkg] jusqu'au prochain changement de paquet (annulation par collectLatest). */
    private suspend fun follow(pkg: String) = coroutineScope {
        val authority = ultraTvAuthority(pkg)
        // Demandes fusionnées : celles reçues pendant l'attente n'en font qu'une, lue à la fin.
        val pending = Channel<Unit>(Channel.CONFLATED)
        requests = pending
        try {
            pending.trySend(Unit)
            // Observateur en échec : plus de notifications, les retours sur l'accueil suffisent.
            launch { changes(authority).catch { }.collect { pending.trySend(Unit) } }
            for (request in pending) {
                // Box allumée il y a peu : une seule attente, jusqu'à la fin de la période calme.
                val quiet = ULTRA_TV_QUIET_AFTER_BOOT_MS - uptimeMs()
                if (quiet > 0) delay(quiet)
                read(pkg, authority)
                delay(UTV_MIN_READ_INTERVAL_MS)
            }
        } finally {
            if (requests === pending) requests = null
        }
    }

    private suspend fun read(pkg: String, authority: String) {
        // Autorité déclarée par une autre appli que le paquet retenu : rien n'est lu ni montré.
        if (ownerOf(authority) != pkg) {
            mutableState.value = UtvState()
            return
        }
        lastReadAt = uptimeMs()
        // Échec ou délai dépassé : l'état précédent reste.
        val rows = timedQuery(authority, "now") ?: return
        if (rows.isEmpty()) {
            mutableState.value = UtvState(pkg = pkg)
            return
        }
        val channels = rows.take(UTV_MAX_ROWS).mapNotNull(::utvChannelOf)
        // Profil illisible ou vide : pas de mode enfants.
        val kids = utvKidsOf(timedQuery(authority, "profile")?.firstOrNull())
        mutableState.value = UtvState(pkg = pkg, channels = channels, kids = kids)
    }

    /** Lecture bornée à [UTV_READ_TIMEOUT_MS] ; au-delà la requête est annulée et vaut un échec. */
    private suspend fun timedQuery(authority: String, path: String): List<Map<String, Any?>>? =
        withTimeoutOrNull(UTV_READ_TIMEOUT_MS) {
            try {
                query(authority, path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
}
