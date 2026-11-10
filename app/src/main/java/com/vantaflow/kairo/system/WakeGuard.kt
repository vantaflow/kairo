package com.vantaflow.kairo.system

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Délai sans touche après l'allumage avant de rendormir la box. */
const val WAKE_GUARD_MS = 30_000L

/** Avertissement affiché avant de rendormir la box. */
const val WAKE_GUARD_WARNING_MS = 15_000L

/**
 * Allumage sans personne devant : certaines télés s'allument seules (la nuit) et réveillent la box
 * par HDMI-CEC. Si aucune touche n'est pressée dans les [timeoutMs] qui suivent l'allumage et que rien
 * ne joue ([busy]), la box se rendort ([onSleep]) et la télé s'éteint avec elle.
 * [warning] donne les secondes restantes pendant l'avertissement (null sinon).
 */
class WakeGuard(
    private val scope: CoroutineScope,
    private val busy: () -> Boolean,
    private val onSleep: () -> Unit,
    private val timeoutMs: Long = WAKE_GUARD_MS,
    private val warningMs: Long = WAKE_GUARD_WARNING_MS,
) {
    private val _warning = MutableStateFlow<Int?>(null)
    val warning: StateFlow<Int?> = _warning
    private var job: Job? = null

    /** Écran allumé : démarre la surveillance. */
    fun screenOn() {
        job?.cancel()
        job = scope.launch {
            delay(timeoutMs - warningMs)
            if (busy()) return@launch stop()
            var left = warningMs
            while (left > 0) {
                _warning.value = ((left + 999) / 1000).toInt()
                delay(1000)
                left -= 1000
            }
            _warning.value = null
            if (!busy()) onSleep()
        }
    }

    /** Touche pressée ou écran éteint : plus rien à surveiller. */
    fun stop() {
        job?.cancel()
        job = null
        _warning.value = null
    }

    val active: Boolean get() = job?.isActive == true
}
