package com.vantaflow.kairo.remote

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import com.vantaflow.kairo.HomeActivity
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.ui.startSafely
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
        // Touche Réglages (roue dentée). La télécommande Google TV envoie KEYCODE_NOTIFICATION, que le
        // système passe au launcher Google (désactivé) : elle ne servait donc plus.
        // Dans Kairo : panneau des réglages de Kairo ; dans une autre appli : paramètres Android.
        if (code == KeyEvent.KEYCODE_SETTINGS || code == KeyEvent.KEYCODE_NOTIFICATION) {
            val opened = if (kairoInFront()) {
                openInKairo(null)
            } else {
                startSafely(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            if (opened) consumed += code
            return opened
        }
        // Touche Entrées : panneau « Entrées et HDMI » de Kairo (le launcher Google l'affichait).
        if (code == KeyEvent.KEYCODE_TV_INPUT) {
            val opened = openInKairo("Inputs")
            if (opened) consumed += code
            return opened
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

    private fun kairoInFront(): Boolean = rootInActiveWindow.let { it == null || it.packageName == packageName }

    /**
     * Réglages de Kairo (section [section] ou menu). Kairo déjà à l'écran : le panneau s'ouvre
     * directement, sans relancer l'activité (pas de transition de fenêtre).
     */
    private fun openInKairo(section: String?): Boolean {
        if (kairoInFront() && KairoCommands.openSettings.tryEmit(section.orEmpty())) return true
        val intent = Intent(this, HomeActivity::class.java)
            .putExtra("tvhome.open", "Settings")
            .putExtra("tvhome.section", section)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return startSafely(intent)
    }

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

/** Ordres du service (même processus) vers l'accueil déjà à l'écran. */
object KairoCommands {
    /** Ouvre les réglages ; valeur = nom de la section, vide pour le menu. */
    val openSettings = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
}
