package com.vantaflow.kairo.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mode « apprendre une touche » : capture la prochaine touche réaffectable.
 * Alimenté par le service d'accessibilité (qui voit aussi les touches d'applis globales)
 * et, en secours, par l'écran de réglages lui-même.
 */
class KeyLearner {
    private val _learning = MutableStateFlow(false)
    val learning: StateFlow<Boolean> = _learning.asStateFlow()

    private val _captured = MutableStateFlow<Int?>(null)
    val captured: StateFlow<Int?> = _captured.asStateFlow()

    fun start() {
        _captured.value = null
        _learning.value = true
    }

    fun cancel() {
        _learning.value = false
    }

    /** Propose une touche ; renvoie true si elle a été capturée (l'événement doit alors être consommé). */
    @Synchronized
    fun offer(keyCode: Int): Boolean {
        if (!_learning.value || !isRemappable(keyCode)) return false
        _captured.value = keyCode
        _learning.value = false
        return true
    }

    companion object {
        /** Instance partagée entre le service d'accessibilité et l'interface (même processus). */
        val shared = KeyLearner()
    }
}
