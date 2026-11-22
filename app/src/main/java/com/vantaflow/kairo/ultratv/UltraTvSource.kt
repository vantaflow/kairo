package com.vantaflow.kairo.ultratv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * fournisseur), [ownerOf] (paquet qui déclare une autorité), [uptimeMs] (temps depuis l'allumage
 * de la box, horloge monotone) et [warn] (journal des anomalies) sont fournis par le conteneur.
 */
class UltraTvSource(
    private val installed: Flow<Set<String>>,
    private val query: suspend (authority: String, path: String) -> List<Map<String, Any?>>?,
    private val changes: (authority: String) -> Flow<Unit>,
    private val ownerOf: (authority: String) -> String?,
    private val uptimeMs: () -> Long,
    private val scope: CoroutineScope,
    private val warn: (String) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(UtvState())
    val state: StateFlow<UtvState> = mutableState.asStateFlow()

    private val started = AtomicBoolean(false)

    /** Demandes de lecture du paquet retenu ; null tant qu'aucun paquet Ultra TV n'est suivi. */
    @Volatile private var requests: Channel<Unit>? = null

    /**
     * Requête vers Ultra TV qui n'a pas encore réellement rendu la main (même après le délai) ; tant
     * qu'elle tourne, aucune autre n'est lancée : une appli bloquée n'accumule pas de fils occupés.
     */
    @Volatile private var inFlight: Job? = null

    /** Mode enfants lu dans le dernier profil lisible du paquet suivi ; null tant qu'aucun ne l'a été. */
    @Volatile private var profileKids: Boolean? = null

    /** Moment de la dernière lecture réellement envoyée à Ultra TV, selon [uptimeMs] ; null avant la première. */
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
                    // Profil pas encore lu : mode enfants supposé (sens prudent).
                    profileKids = null
                    mutableState.value = UtvState(pkg = pkg, kids = true)
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
            // Observateur en échec ou terminé : réinscrit à la prochaine demande (retour sur l'accueil) ;
            // l'inscription ne démarre aucun process.
            val observerDown = AtomicBoolean(false)
            fun observe() = launch {
                try {
                    changes(authority).collect { pending.trySend(Unit) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Pas de notifications : la prochaine demande réessaiera l'inscription.
                }
                observerDown.set(true)
            }
            observe()
            for (request in pending) {
                if (observerDown.compareAndSet(true, false)) observe()
                // Box allumée il y a peu : une seule attente, jusqu'à la fin de la période calme.
                val quiet = ULTRA_TV_QUIET_AFTER_BOOT_MS - uptimeMs()
                if (quiet > 0) {
                    delay(quiet)
                    // Demandes reçues pendant l'attente : couvertes par la lecture qui suit.
                    while (pending.tryReceive().isSuccess) Unit
                }
                read(pkg, authority)
                delay(UTV_MIN_READ_INTERVAL_MS)
            }
        } finally {
            if (requests === pending) requests = null
        }
    }

    private suspend fun read(pkg: String, authority: String) {
        val owner = ownerOf(authority)
        // Fournisseur introuvable (souvent passager : Ultra TV en cours de mise à jour) : rien n'est lu,
        // l'état reste (pas de rangée qui clignote).
        if (owner == null) return
        // Autorité déclarée par une autre appli que le paquet retenu : rien n'est lu ni montré.
        if (owner != pkg) {
            mutableState.value = UtvState()
            return
        }
        // Requête précédente toujours bloquée : lecture refusée, sans compter comme une lecture.
        if (inFlight?.isCompleted == false) {
            warn("Ultra TV : lecture refusée, la requête précédente n'a pas rendu la main")
            return
        }
        lastReadAt = uptimeMs()
        // Échec ou délai dépassé : l'état précédent reste.
        val rows = timedQuery(authority, "now") ?: return
        val channels = rows.take(UTV_MAX_ROWS).mapNotNull(::utvChannelOf)
        // Ultra TV vient de répondre : le profil est lu même sans chaîne (les raccourcis en dépendent).
        // Profil illisible, vide ou sans la colonne : valeur précédente, sinon mode enfants (sens prudent).
        val profile = timedQuery(authority, "profile")?.firstOrNull()?.takeIf { it[COL_KIDS] != null }
        if (profile != null) profileKids = utvKidsOf(profile)
        mutableState.value = UtvState(pkg = pkg, channels = channels, kids = profileKids ?: true)
    }

    /**
     * Lecture bornée à [UTV_READ_TIMEOUT_MS] ; au-delà la requête est annulée et vaut un échec. Elle
     * tourne à part : une requête qui ignore l'annulation ne retient pas la boucle, mais tant qu'elle
     * n'a pas fini, toute nouvelle lecture échoue sans rien interroger.
     */
    private suspend fun timedQuery(authority: String, path: String): List<Map<String, Any?>>? {
        if (inFlight?.isCompleted == false) return null
        val reply = scope.async {
            try {
                query(authority, path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        inFlight = reply
        try {
            return withTimeoutOrNull(UTV_READ_TIMEOUT_MS) { reply.await() }
        } finally {
            reply.cancel()
        }
    }
}
