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
 * Accueil → Applications → Accueil. [reset] (touche Home) ramène sur la 1re tuile, ou sur la
 * bannière du style Cinéma quand elle est affichée.
 */
class HomeFocus {
    var index by mutableIntStateOf(0)
    var resetToken by mutableIntStateOf(0)
        private set

    fun reset() {
        index = 0
        onBanner = true
        keyPressed = false
        resetToken++
    }

    /**
     * Le focus était sur la bannière (style Cinéma) plutôt que dans les rangées : [refocus] et le
     * retour sur l'accueil l'y ramènent si elle est affichée ; [reset] et le premier affichage aussi.
     */
    var onBanner = true

    /** Touche appuyée sur l'accueil depuis son affichage ou depuis [reset] : une bannière apparue après coup ne prend plus le focus. */
    var keyPressed = false

    /** Demande de focus sur les onglets (la carte focalisée a disparu, ex. retirée de Ma liste). */
    var tabsToken by mutableIntStateOf(0)

    /** Favori à focaliser dès qu'il apparaît (appli ajoutée aux favoris par l'appui long). */
    var followFavorite by mutableStateOf<String?>(null)

    /**
     * Redonne le focus à la même tuile (ex. à la fermeture du panneau de réglages). Sur la bannière,
     * c'est son bouton principal, même si le focus était sur « + Ma liste ».
     */
    fun refocus() {
        resetToken++
    }
}
