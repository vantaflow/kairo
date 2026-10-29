package com.vantaflow.kairo.tools

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Durées proposées, en minutes. */
val SleepDurations = listOf(15, 30, 45, 60, 90, 120)

fun formatMinutes(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${"%02d".format(min % 60)}"
}

/** Secondes d'avertissement avant la mise en veille. */
const val SLEEP_WARNING_SEC = 60

/** Minutes à ajouter quand on répond à l'avertissement. */
const val SLEEP_EXTEND_MIN = 15

/**
 * Minuteur de mise en veille : 1 min avant, [warning] donne les secondes restantes (le service
 * affiche un bandeau, une touche prolonge) ; à l'échéance, [onSleep] éteint la box.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val onSleep: () -> Unit,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _endsAt = MutableStateFlow<Long?>(null)
    /** Échéance (horloge [now]), null si le minuteur est arrêté. */
    val endsAt: StateFlow<Long?> = _endsAt
    private val _warning = MutableStateFlow<Int?>(null)
    val warning: StateFlow<Int?> = _warning
    private var job: Job? = null

    fun start(minutes: Int) = schedule(now() + minutes * 60_000L)

    fun extend(minutes: Int = SLEEP_EXTEND_MIN) {
        val base = maxOf(_endsAt.value ?: now(), now())
        schedule(base + minutes * 60_000L)
    }

    fun cancel() {
        job?.cancel()
        _endsAt.value = null
        _warning.value = null
    }

    /** Minutes restantes (arrondies au-dessus), null si arrêté. */
    fun remainingMinutes(): Int? = _endsAt.value?.let { ((it - now() + 59_999) / 60_000).toInt().coerceAtLeast(0) }

    private fun schedule(end: Long) {
        job?.cancel()
        _endsAt.value = end
        _warning.value = null
        job = scope.launch {
            val warnAt = end - SLEEP_WARNING_SEC * 1000L
            if (warnAt > now()) delay(warnAt - now())
            while (now() < end) {
                _warning.value = ((end - now() + 999) / 1000).toInt()
                delay(1000)
            }
            _warning.value = null
            _endsAt.value = null
            onSleep()
        }
    }
}
