package com.tvhome.launcher.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.tvhome.launcher.ui.startSafely

/** Raccourci vers un écran des paramètres Android ; [actions] essayées dans l'ordre. */
data class SettingsShortcut(val title: String, val actions: List<String>)

private fun shortcut(title: String, vararg actions: String) =
    SettingsShortcut(title, actions.toList() + Settings.ACTION_SETTINGS)

/** Paramètres Android de la box accessibles depuis le launcher. */
val SettingsShortcuts: List<SettingsShortcut> = listOf(
    shortcut("Réseau et Internet", Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS),
    shortcut("Télécommandes et accessoires (Bluetooth)", Settings.ACTION_BLUETOOTH_SETTINGS),
    shortcut("Affichage et image", Settings.ACTION_DISPLAY_SETTINGS),
    shortcut("Son", Settings.ACTION_SOUND_SETTINGS),
    shortcut("HDMI-CEC (contrôle de la TV)", "android.settings.HDMI_CEC_SETTINGS", "com.android.tv.settings.HDMI_CEC_SETTINGS"),
    shortcut("Applications", Settings.ACTION_APPLICATION_SETTINGS, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
    shortcut("Stockage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    shortcut("Date et heure", Settings.ACTION_DATE_SETTINGS),
    shortcut("Langue", Settings.ACTION_LOCALE_SETTINGS),
    shortcut("Clavier", Settings.ACTION_INPUT_METHOD_SETTINGS),
    shortcut("Économiseur d'écran", Settings.ACTION_DREAM_SETTINGS),
    shortcut("Accessibilité", Settings.ACTION_ACCESSIBILITY_SETTINGS),
    shortcut("Localisation et confidentialité", Settings.ACTION_LOCATION_SOURCE_SETTINGS, Settings.ACTION_PRIVACY_SETTINGS),
    shortcut("Options pour les développeurs", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    shortcut("Mise à jour du système", "android.settings.SYSTEM_UPDATE_SETTINGS"),
    shortcut("À propos de la box", Settings.ACTION_DEVICE_INFO_SETTINGS),
    shortcut("Tous les paramètres"),
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
