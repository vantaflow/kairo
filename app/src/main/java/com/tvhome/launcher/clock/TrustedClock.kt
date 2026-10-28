package com.tvhome.launcher.clock

import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Heure affichée par le launcher. Après une synchro NTP, elle avance avec l'horloge monotone
 * (indépendante des corrections ultérieures de l'heure Android). Sans synchro réussie,
 * l'heure système s'affiche sans attendre le réseau.
 */
class TrustedClock(
    private val sntp: SntpClient,
    private val hosts: List<String> = DEFAULT_HOSTS,
    private val systemNow: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val elapsedNow: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Anchor(val ntpMs: Long, val elapsedMs: Long)

    @Volatile private var anchor: Anchor? = null

    /** Écart mesuré entre l'heure NTP et l'heure système au moment de la synchro (affichage). */
    private val _offsetMs = MutableStateFlow(0L)
    val offsetMs: StateFlow<Long> = _offsetMs.asStateFlow()

    private val _lastSyncMs = MutableStateFlow<Long?>(null)
    val lastSyncMs: StateFlow<Long?> = _lastSyncMs.asStateFlow()

    suspend fun sync() {
        val offset = withContext(ioDispatcher) {
            hosts.firstNotNullOfOrNull { sntp.queryOffsetMs(it, systemNow) }
        } ?: return
        val system = systemNow()
        anchor = Anchor(ntpMs = system + offset, elapsedMs = elapsedNow())
        _offsetMs.value = offset
        _lastSyncMs.value = system
    }

    fun now(): Long {
        val a = anchor ?: return systemNow()
        return a.ntpMs + (elapsedNow() - a.elapsedMs)
    }

    companion object {
        val DEFAULT_HOSTS = listOf("time.google.com", "pool.ntp.org")
        const val RESYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
