package com.vantaflow.kairo.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Applis lanceables installées (leanback prioritaire sur launcher classique). */
class AppRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun installedApps(): List<AppInfo> {
        val byPackage = LinkedHashMap<String, AppInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (info in query(category)) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName || pkg in byPackage) continue
                byPackage[pkg] = AppInfo(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    hasBanner = info.activityInfo.applicationInfo.banner != 0 || info.activityInfo.banner != 0,
                )
            }
        }
        return byPackage.values.sortedBy { it.label.lowercase() }
    }

    fun launchIntent(packageName: String): Intent? =
        pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)

    /** Bannière de l'activité de lancement TV, sinon celle de l'application. */
    fun banner(packageName: String): Drawable? = runCatching {
        pm.getLeanbackLaunchIntentForPackage(packageName)?.component?.let(pm::getActivityBanner)
            ?: pm.getApplicationBanner(packageName)
    }.getOrNull()

    fun icon(packageName: String): Drawable? =
        runCatching { pm.getApplicationIcon(packageName) }.getOrNull()

    /** Émet à chaque installation, suppression ou mise à jour d'appli. */
    val changes: Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
}
