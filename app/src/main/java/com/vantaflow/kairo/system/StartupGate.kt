package com.vantaflow.kairo.system

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Durée après le démarrage de la box pendant laquelle Kairo ne ferme ni n'installe rien tout seul. */
const val BOOT_QUIET_MS = 600_000L

/**
 * Box allumée depuis moins de [minMs] ([elapsedRealtimeMs] : `SystemClock.elapsedRealtime()`) :
 * les autres applis démarrent encore, on ne les ferme pas et on n'installe rien.
 */
fun bootIsRecent(elapsedRealtimeMs: Long, minMs: Long = BOOT_QUIET_MS): Boolean = elapsedRealtimeMs < minMs

/**
 * Porte de démarrage : les fonctions de fond (télécommande, nettoyeur, bloqueur de pubs, lecture en
 * cours, mises à jour) attendent qu'elle s'ouvre, pour laisser la box et l'accueil démarrer d'abord.
 * Elle s'ouvre [delayAfterHomeMs] après la première image de l'accueil ([homeShown]), ou
 * [delayWithoutHomeMs] après sa création si l'accueil n'est pas apparu entre-temps (process lancé
 * par un service) ; elle ne se referme jamais. Sans dépendance Android.
 */
class StartupGate(
    private val scope: CoroutineScope,
    private val delayAfterHomeMs: Long = 30_000,
    private val delayWithoutHomeMs: Long = 120_000,
) {
    private val mutableOpen = MutableStateFlow(false)
    val open: StateFlow<Boolean> = mutableOpen.asStateFlow()

    private val home = CompletableDeferred<Unit>()
    private val lock = Any()
    /** Travaux en attente, dans l'ordre de leur demande ; null une fois la porte ouverte. */
    private var pending: MutableList<suspend () -> Unit>? = mutableListOf()

    init {
        scope.launch {
            // Accueil apparu à temps : on lui laisse encore le temps de s'installer.
            if (withTimeoutOrNull(delayWithoutHomeMs) { home.await() } != null) delay(delayAfterHomeMs)
            val blocks = synchronized(lock) { pending.orEmpty().also { pending = null } }
            mutableOpen.value = true
            blocks.forEach { run(it) }
        }
    }

    /** Première image de l'accueil dessinée ; les appels suivants sont sans effet. */
    fun homeShown() {
        home.complete(Unit)
    }

    /** Exécute [block] à l'ouverture de la porte (une seule fois), ou tout de suite si elle est ouverte. */
    fun whenOpen(block: suspend () -> Unit) {
        val queued = synchronized(lock) { pending?.add(block) ?: false }
        if (!queued) scope.launch { run(block) }
    }

    /** Un travail en échec n'empêche pas les suivants ; l'erreur va au filet de la portée. */
    private suspend fun run(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            scope.coroutineContext[CoroutineExceptionHandler]?.handleException(scope.coroutineContext, e)
        }
    }
}
