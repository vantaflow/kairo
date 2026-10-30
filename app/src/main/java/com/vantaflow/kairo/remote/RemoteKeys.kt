package com.vantaflow.kairo.remote

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Actions déclenchées par une touche de la télécommande dans le launcher. */
enum class RemoteAction { OpenMenu }

fun remoteActionFor(keyCode: Int): RemoteAction? = when (keyCode) {
    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> RemoteAction.OpenMenu
    else -> null
}

/**
 * Tuile focalisée de l'accueil, tenue par l'activité : elle survit au passage
 * Accueil → Applications → Accueil. [reset] (touche Home) ramène sur la 1re tuile.
 */
class HomeFocus {
    var index by mutableIntStateOf(0)
    var resetToken by mutableIntStateOf(0)
        private set

    fun reset() {
        index = 0
        resetToken++
    }

    /** Favori à focaliser dès qu'il apparaît (appli ajoutée aux favoris par l'appui long). */
    var followFavorite by mutableStateOf<String?>(null)

    /** Redonne le focus à la même tuile (ex. à la fermeture du panneau de réglages). */
    fun refocus() {
        resetToken++
    }
}
