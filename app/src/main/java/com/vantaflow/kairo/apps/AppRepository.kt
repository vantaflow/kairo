package com.vantaflow.kairo.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import com.vantaflow.kairo.system.SafeHandler
import com.vantaflow.kairo.system.resilient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

/**
 * Inventaire des applis partagé par tous les écrans : un seul parcours du PackageManager au lancement,
 * puis un par installation, suppression ou mise à jour. Null tant que le premier n'est pas fini.
 * Démarré au premier lecteur, il vit ensuite autant que [scope].
 */
fun sharedApps(
    scope: CoroutineScope,
    load: () -> List<AppInfo>,
    changes: Flow<Unit>,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): StateFlow<List<AppInfo>?> =
    merge(flowOf(Unit), changes).map { load() }.flowOn(ioDispatcher)
        // Lecture en échec : nouvel essai (délai croissant), la dernière liste reste affichée en attendant.
        .resilient("liste des applis")
        .stateIn(scope, SharingStarted.Lazily, null)

/** Applis lanceables installées (leanback prioritaire sur launcher classique). */
class AppRepository(
    private val context: Context,
    /** Portée de l'inventaire partagé : celle du process. */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + SafeHandler),
) {
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

    /**
     * Inventaire partagé (accueil, écran Applis, catalogue, réglages). Le nettoyeur et la télécommande
     * sur téléphone gardent leur appel direct à [installedApps].
     */
    val apps: StateFlow<List<AppInfo>?> = sharedApps(scope, ::installedApps, changes)

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
}
