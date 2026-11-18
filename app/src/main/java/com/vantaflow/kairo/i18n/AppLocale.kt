package com.vantaflow.kairo.i18n

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * Langue de l'appli, choisie dans Réglages › Langue : celle de la box (par défaut) ou l'une des
 * langues traduites. Fonctionne d'Android 11 à 14 sans dépendance : configuration localisée pour
 * les activités et l'écran de veille, ressources de l'appli mises à jour pour les services, et
 * [Locale.setDefault] pour les dates et nombres formatés dans le code.
 *
 * Le choix est lu au démarrage de chaque activité (avant toute ressource), donc gardé dans des
 * SharedPreferences, lisibles sans attendre, et non dans le DataStore.
 */
object AppLocale {
    /** Langue de la box. */
    const val SYSTEM = ""

    /** Langues traduites ; le français est la langue par défaut des ressources. */
    val SUPPORTED = listOf("fr", "en", "es", "ar")

    private const val PREFS = "kairo_locale"
    private const val KEY = "language"

    /** Langue choisie : [SYSTEM] ou un code de [SUPPORTED]. */
    fun saved(context: Context): String =
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) }
            .getOrNull()?.takeIf { it in SUPPORTED } ?: SYSTEM

    /** Langue de la box (ignore le choix fait dans Kairo). */
    fun systemLocale(): Locale = Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()

    /**
     * Locale à appliquer pour [language] : la langue choisie avec le pays de la box (dates et
     * formats régionaux), et chiffres occidentaux (0-9) en arabe.
     */
    fun localeFor(language: String, system: Locale = systemLocale()): Locale {
        val base = if (language == SYSTEM) system
        else Locale.Builder().setLanguage(language).setRegion(system.country.takeIf { it.length == 2 } ?: "").build()
        if (base.language != "ar") return base
        return runCatching { Locale.Builder().setLocale(base).setUnicodeLocaleKeyword("nu", "latn").build() }.getOrDefault(base)
    }

    /** Locale en vigueur dans Kairo. */
    fun current(context: Context): Locale = localeFor(saved(context))

    /** Configuration ne contenant que la langue et le sens d'écriture (à superposer). */
    private fun overrideFor(locale: Locale) = Configuration().apply {
        setLocales(LocaleList(locale))
        setLayoutDirection(locale)
    }

    /** Contexte localisé (services, écran de veille). */
    fun wrap(base: Context): Context {
        val locale = current(base)
        val config = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    /**
     * À appeler dans `attachBaseContext` d'une activité, après `super` : la langue reste
     * appliquée même quand l'activité gère elle-même un changement de configuration (HDMI…).
     */
    fun applyTo(activity: Activity, base: Context) {
        runCatching { activity.applyOverrideConfiguration(overrideFor(current(base))) }
    }

    /**
     * Ressources de l'appli (partagées par ses services) et locale par défaut de la JVM, au
     * démarrage, après un changement de langue et après un changement de configuration de la box.
     */
    fun applyToApp(app: Application) {
        val locale = current(app)
        Locale.setDefault(locale)
        val res = app.resources
        val config = Configuration(res.configuration).apply {
            setLocales(LocaleList(locale))
            setLayoutDirection(locale)
        }
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
    }

    /** Enregistre la langue et recrée l'activité pour l'appliquer aussitôt. */
    fun set(context: Context, language: String) {
        val value = language.takeIf { it in SUPPORTED } ?: SYSTEM
        if (value == saved(context)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).commit()
        (context.applicationContext as? Application)?.let(::applyToApp)
        context.findActivity()?.recreate()
    }

    /**
     * Langue des titres du catalogue (TMDB, « fr-FR », « ar-MA »…) : celle de l'appli, avec le
     * pays de la box s'il en a un, sinon le pays le plus courant pour cette langue.
     */
    fun catalogLanguage(locale: Locale = Locale.getDefault()): String {
        val language = locale.language.ifBlank { return "fr-FR" }
        val country = locale.country.takeIf { it.length == 2 } ?: DEFAULT_COUNTRY[language]
        return if (country == null) language else "$language-$country"
    }

    private val DEFAULT_COUNTRY = mapOf("fr" to "FR", "en" to "US", "es" to "ES", "ar" to "SA")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
