package com.vantaflow.kairo.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import com.vantaflow.kairo.R
import com.vantaflow.kairo.ui.startSafely

/** Raccourci vers un écran des paramètres Android ; [actions] essayées dans l'ordre ; [title] = 0 si sans libellé. */
data class SettingsShortcut(@StringRes val title: Int, val actions: List<String>)

private fun shortcut(@StringRes title: Int, vararg actions: String) =
    SettingsShortcut(title, actions.toList() + Settings.ACTION_SETTINGS)

/** Paramètres Android de la box accessibles depuis le launcher. */
val SettingsShortcuts: List<SettingsShortcut> = listOf(
    // En tête : la touche Réglages ouvre Kairo, Android est à un clic.
    shortcut(R.string.shortcut_all),
    shortcut(R.string.shortcut_network, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS),
    shortcut(R.string.shortcut_remotes, Settings.ACTION_BLUETOOTH_SETTINGS),
    shortcut(R.string.shortcut_display, Settings.ACTION_DISPLAY_SETTINGS),
    shortcut(R.string.shortcut_sound, Settings.ACTION_SOUND_SETTINGS),
    shortcut(R.string.shortcut_cec, "android.settings.HDMI_CEC_SETTINGS", "com.android.tv.settings.HDMI_CEC_SETTINGS"),
    shortcut(R.string.shortcut_apps, Settings.ACTION_APPLICATION_SETTINGS, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
    shortcut(R.string.shortcut_storage, Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    shortcut(R.string.shortcut_datetime, Settings.ACTION_DATE_SETTINGS),
    shortcut(R.string.shortcut_language, Settings.ACTION_LOCALE_SETTINGS),
    shortcut(R.string.shortcut_keyboard, Settings.ACTION_INPUT_METHOD_SETTINGS),
    shortcut(R.string.shortcut_screensaver, Settings.ACTION_DREAM_SETTINGS),
    shortcut(R.string.shortcut_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS),
    shortcut(R.string.shortcut_location, Settings.ACTION_LOCATION_SOURCE_SETTINGS, Settings.ACTION_PRIVACY_SETTINGS),
    shortcut(R.string.shortcut_developer, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    shortcut(R.string.shortcut_update, "android.settings.SYSTEM_UPDATE_SETTINGS"),
    shortcut(R.string.shortcut_about, Settings.ACTION_DEVICE_INFO_SETTINGS),
)

/** Ouvre le premier écran de paramètres disponible sur la box. */
fun Context.openSettings(shortcut: SettingsShortcut) {
    shortcut.actions.firstOrNull { startSafely(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private const val PLAY_STORE = "com.android.vending"

/**
 * Intents du Play Store, du plus précis au plus général : fiche de l'appli si [packageName],
 * sinon l'accueil du Play Store.
 */
fun playStoreIntents(packageName: String?): List<Intent> =
    if (packageName == null) {
        listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(PLAY_STORE),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=")).setPackage(PLAY_STORE),
        )
    } else {
        listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).setPackage(PLAY_STORE),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")),
        )
    }

fun Context.openPlayStore(packageName: String?) {
    val pmIntent = if (packageName == null) packageManager.getLeanbackLaunchIntentForPackage(PLAY_STORE) else null
    (listOfNotNull(pmIntent) + playStoreIntents(packageName))
        .firstOrNull { startSafely(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
