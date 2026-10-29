package com.vantaflow.kairo.system

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Mémoire basse : moins de 25 % de libre. */
fun memoryIsLow(available: Long, total: Long): Boolean = total > 0 && available * 4 < total

/** Applis quittées depuis plus de [idleMs] (hors appli à l'écran) ; [idleMs] ≤ 0 = jamais. */
fun idleApps(lastSeen: Map<String, Long>, now: Long, idleMs: Long, foreground: String?): List<String> =
    if (idleMs <= 0) emptyList() else lastSeen.filter { (pkg, t) -> pkg != foreground && now - t >= idleMs }.keys.toList()

/** Délais proposés pour fermer une appli inutilisée (minutes, 0 = jamais). */
val IdleCloseChoices = listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 120 to "2 h", 0 to "Jamais")

/**
 * Nettoyage automatique : ferme les applis laissées en arrière-plan depuis trop longtemps, et
 * toutes celles en arrière-plan (sauf la dernière quittée) quand la mémoire devient basse.
 * Android ne ferme jamais l'appli à l'écran ni celles qui jouent du son (service au premier plan).
 */
class AutoCleaner(
    private val context: Context,
    private val enabled: suspend () -> Boolean,
    private val idleMinutes: suspend () -> Int,
    private val installed: () -> List<String>,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val cleaner by lazy { MemoryCleaner(context) }
    /** Appli → moment où on l'a quittée. */
    private val lastSeen = ConcurrentHashMap<String, Long>()
    @Volatile private var foreground: String? = null

    /** Appli passée à l'écran (vue par le service d'accessibilité ou par l'accueil). */
    fun onForeground(pkg: String) {
        val previous = foreground
        if (pkg == previous) return
        if (previous != null && previous != context.packageName) lastSeen[previous] = now()
        lastSeen.remove(pkg)
        foreground = pkg
    }

    fun start(scope: CoroutineScope) = scope.launch {
        while (true) {
            delay(CHECK_MS)
            runCatching { check() }
        }
    }

    /** Vérifie tout de suite (ex. retour sur l'accueil). */
    suspend fun check() {
        if (!enabled()) return
        idleApps(lastSeen, now(), idleMinutes() * 60_000L, foreground).forEach { pkg ->
            cleaner.close(pkg)
            lastSeen.remove(pkg)
        }
        val mem = cleaner.state()
        if (memoryIsLow(mem.availableBytes, mem.totalBytes)) {
            // La dernière appli quittée reste prête : y revenir est immédiat.
            val keep = setOfNotNull(foreground, lastSeen.maxByOrNull { it.value }?.key)
            cleaner.clean(installed().filterNot { it in keep })
        }
    }

    private companion object {
        const val CHECK_MS = 5 * 60_000L
    }
}
