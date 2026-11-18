package com.vantaflow.kairo.player

import android.app.Activity
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * Langue de Kairo Player : celle de la box (pas de réglage Langue ici), avec les chiffres
 * occidentaux (0-9) en arabe, pour les textes comme pour les dates et nombres formatés.
 */
object BoxLocale {
    /** Langue de la box, chiffres 0-9 si elle est en arabe. */
    fun locale(system: Locale = Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()): Locale {
        if (system.language != "ar") return system
        return runCatching { Locale.Builder().setLocale(system).setUnicodeLocaleKeyword("nu", "latn").build() }.getOrDefault(system)
    }

    /** À appeler dans `attachBaseContext` de chaque activité, après `super`. */
    fun applyTo(activity: Activity) {
        val locale = locale()
        Locale.setDefault(locale)
        if (locale.language != "ar") return
        runCatching {
            activity.applyOverrideConfiguration(
                Configuration().apply {
                    setLocales(LocaleList(locale))
                    setLayoutDirection(locale)
                },
            )
        }
    }
}
