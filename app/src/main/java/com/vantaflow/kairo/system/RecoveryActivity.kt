package com.vantaflow.kairo.system

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.vantaflow.kairo.R

/**
 * « Kairo (réparation) » : seule entrée de Kairo une fois retiré de l'accueil (voir [HomeFallback]).
 * Vues Android classiques construites ici : ni Compose, ni DataStore, ni conteneur d'appli, rien qui
 * puisse replanter. Désactivée dans le manifeste, activée seulement par le retrait.
 */
class RecoveryActivity : Activity() {
    /** Consigne sous le texte ; remplacée par un message d'erreur si la remise en place échoue. */
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val pad = (48 * density).toInt()
        val text = TextView(this).apply {
            setText(R.string.recovery_text)
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        hint = TextView(this).apply {
            setText(R.string.recovery_hint)
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        val restore = Button(this).apply {
            setText(R.string.recovery_restore)
            isFocusable = true
            setOnClickListener { restoreHome() }
        }
        val keep = Button(this).apply {
            setText(R.string.recovery_keep)
            isFocusable = true
            setOnClickListener { finish() }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.BLACK)
            addView(text, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = pad / 4
            })
            addView(hint, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = pad / 2
            })
            addView(restore, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(keep, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = pad / 4
            })
        }
        setContentView(layout)
        restore.requestFocus()
    }

    /**
     * Accueil de Kairo réactivé et garde remise à zéro, puis choix de l'accueil dans les paramètres
     * d'Android. Le process est ensuite quitté : il a démarré sans conteneur (Kairo retiré), l'accueil
     * doit repartir dans un process neuf.
     */
    private fun restoreHome() {
        if (!HomeFallback(this).restore()) {
            hint.setText(R.string.recovery_failed)
            hint.setTextColor(Color.rgb(0xFF, 0x8A, 0x80))
            return
        }
        openHomeSettings()
        finishAndRemoveTask()
        Runtime.getRuntime().exit(0)
    }

    /**
     * Choix de l'écran d'accueil, à défaut les paramètres (souvent absent sur TV : d'où la consigne
     * affichée). Copie volontaire de celle des réglages, pour ne charger aucun écran de Kairo ici.
     */
    private fun openHomeSettings() {
        for (action in listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_SETTINGS)) {
            try {
                startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: RuntimeException) {
                // Introuvable (ActivityNotFoundException) ou refusé : intention suivante.
            }
        }
    }
}
