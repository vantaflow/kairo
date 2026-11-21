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
        if (!HomeFallback(this).restore()) return
        val opened = runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess ||
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!opened) android.util.Log.w("Kairo", "paramètres d'accueil introuvables")
        finishAndRemoveTask()
        Runtime.getRuntime().exit(0)
    }
}
