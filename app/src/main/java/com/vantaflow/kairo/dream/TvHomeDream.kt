package com.vantaflow.kairo.dream

import android.service.dreams.DreamService
import android.view.KeyEvent
import com.vantaflow.kairo.radio.Radio
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.vantaflow.kairo.ui.DesignScale


/** Écran de veille Kairo : photos du web, photos de la box ou dégradés animés (voir [DreamScreen]). */
class TvHomeDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Interactif : quand la radio joue, ← → changent de radio et OK l'arrête (voir dispatchKeyEvent).
        isInteractive = true
        isFullscreen = true
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TvHomeDream)
            setViewTreeSavedStateRegistryOwner(this@TvHomeDream)
            setContent { DesignScale { DreamScreen() } }
        }
        setContentView(view)
    }

    /** Radio en cours : ← → / ⏮ ⏭ changent de radio, OK / ⏯ l'arrête ; toute autre touche réveille. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = if (Radio.state.value.active) radioAction(event.keyCode) else null
        if (action == null) {
            if (event.action == KeyEvent.ACTION_UP) wakeUp()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (action) {
                -1, 1 -> Radio.skip(this, action)
                else -> Radio.stop(this)
            }
        }
        return true
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}

/** Touche → action radio dans l'écran de veille : -1 précédente, 1 suivante, 0 arrêt, null = réveil. */
fun radioAction(keyCode: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> -1
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> 1
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_PAUSE -> 0
    else -> null
}
