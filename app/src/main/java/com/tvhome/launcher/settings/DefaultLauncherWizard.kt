package com.tvhome.launcher.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings


/** Package du launcher Google TV à désactiver (à confirmer sur la KM7 Plus, session du soir n°1). */
const val GOOGLE_TV_LAUNCHER = "com.google.android.apps.tv.launcherx"

fun isDefaultHome(resolvedHomePackage: String?, ownPackage: String): Boolean =
    resolvedHomePackage != null && resolvedHomePackage == ownPackage

fun isDefaultHome(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return isDefaultHome(resolved?.activityInfo?.packageName, context.packageName)
}

/** Ouvre le choix de l'écran d'accueil (ou, à défaut, les paramètres). */
fun openHomeSettings(context: Context) {
    val intents = listOf(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}
