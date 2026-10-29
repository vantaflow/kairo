package com.tvhome.launcher.remote

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import com.tvhome.launcher.TvHomeApp
import com.tvhome.launcher.ui.startSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Réaffecte les touches d'applis de la télécommande (YouTube, Netflix…) vers l'appli choisie. */
class KeyRemapService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var bindings: Map<Int, String> = emptyMap()
    /** Touches dont l'appui a été consommé : on consomme aussi leur relâchement. */
    private val consumed = mutableSetOf<Int>()

    override fun onServiceConnected() {
        instance = this
        val container = (application as TvHomeApp).container
        scope.launch {
            combine(container.prefs.keyBindings, container.prefs.currentProfile, container.prefs.favorites) { b, profile, favs ->
                effectiveBindings(b, profile.kids, favs.toSet())
            }.collect { bindings = it }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return consumed.remove(code)
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return code in consumed
        if (KeyLearner.shared.offer(code)) {
            consumed += code
            return true
        }
        val pkg = bindingFor(code, bindings) ?: return false
        val container = (application as TvHomeApp).container
        val intent = container.appRepository.launchIntent(pkg) ?: return false
        startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        consumed += code
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    /** Écrit [text] dans le champ de saisie actif de l'appli au premier plan ; false s'il n'y en a pas. */
    fun typeText(text: String): Boolean {
        val field = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Service actif (null s'il n'est pas activé dans Accessibilité) : sert à la télécommande sur téléphone. */
        @Volatile var instance: KeyRemapService? = null
            private set

        fun component(context: Context) = ComponentName(context, KeyRemapService::class.java)

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val name = component(context)
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == name
            }
        }
    }
}
