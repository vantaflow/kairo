package com.vantaflow.kairo.dream

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vantaflow.kairo.ui.DesignScale

/** Aperçu de l'écran de veille depuis les réglages : n'importe quelle touche le ferme. */
class DreamPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DesignScale { DreamScreen() } }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) finish()
        return true
    }
}
