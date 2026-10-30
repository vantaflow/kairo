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
        // Minuteur de mise en veille : bandeau d'avertissement la dernière minute.
        scope.launch { container.sleepTimer.warning.collect(::showSleepWarning) }
    }

    private var warningView: android.widget.TextView? = null

    private fun showSleepWarning(seconds: Int?) {
        val wm = getSystemService(android.view.WindowManager::class.java)
        if (seconds == null) {
            warningView?.let { runCatching { wm.removeView(it) } }
            warningView = null
            return
        }
        val text = "Mise en veille dans $seconds s · appuyez sur une touche pour continuer"
        warningView?.let { it.text = text; return }
        val density = resources.displayMetrics.density
        val view = android.widget.TextView(this).apply {
            this.text = text
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            val pad = (20 * density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xE6202329.toInt())
                cornerRadius = 18 * density
            }
        }
        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            y = (40 * density).toInt()
        }
        if (runCatching { wm.addView(view, params) }.isSuccess) warningView = view
    }

    /** Met la box en veille (la télé suit par HDMI-CEC si elle le gère). */
    fun sleep(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return consumed.remove(code)
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return code in consumed
        // Avertissement du minuteur affiché : n'importe quelle touche prolonge.
        if (warningView != null) {
            (application as TvHomeApp).container.sleepTimer.extend()
            consumed += code
            return true
        }
        if (KeyLearner.shared.offer(code)) {
            consumed += code
            return true
        }
        // Touche Réglages (roue dentée). La télécommande Google TV envoie KEYCODE_NOTIFICATION, que le
        // système passe au launcher Google (désactivé) : elle ne servait donc plus.
        // Partout, même dans une autre appli : réglages de Kairo (les paramètres Android y sont
        // accessibles par la section « Paramètres Android »).
        if (code == KeyEvent.KEYCODE_SETTINGS || code == KeyEvent.KEYCODE_NOTIFICATION) {
            val opened = openInKairo(null)
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

    /** Appli passée à l'écran : sert au nettoyage automatique (applis inutilisées). */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Seulement les applis (pas le clavier, les menus système ou les bulles).
        val app = launchable.getOrPut(pkg) {
            pkg == packageName || (application as TvHomeApp).container.appRepository.launchIntent(pkg) != null
        }
        if (app) {
            foreground = pkg
            (application as TvHomeApp).container.autoCleaner.onForeground(pkg)
        }
    }

    /** Dernière appli passée au premier plan (null tant qu'aucune n'a été vue). */
    @Volatile private var foreground: String? = null

    private val launchable = HashMap<String, Boolean>()
    override fun onInterrupt() = Unit

    /**
     * Kairo au premier plan ? D'après la fenêtre active, sinon la dernière appli vue à l'écran : la
     * fenêtre active est vide pour beaucoup de lecteurs vidéo (SmartTube, IPTV), qui passaient alors
     * pour Kairo et la touche Réglages ramenait à l'accueil.
     */
    private fun kairoInFront(): Boolean {
        val active = rootInActiveWindow?.packageName?.toString()
        return (active ?: foreground ?: packageName) == packageName
    }

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
