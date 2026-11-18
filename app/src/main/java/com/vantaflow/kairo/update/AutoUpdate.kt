package com.vantaflow.kairo.update

import android.content.Context

/** Une installation automatique : l'appli (identifiant de « nos applis ») et la version visée. */
data class AutoInstall(val appId: String, val version: String)

/**
 * Applis à mettre à jour automatiquement, dans l'ordre : [selfId] (Kairo) en dernier, car son
 * installation relance Kairo. Une version pour laquelle Android a exigé une confirmation
 * ([refused]) n'est pas retentée : elle reste proposée à la main.
 */
fun autoInstallPlan(available: Map<String, String>, refused: Map<String, String>, selfId: String): List<AutoInstall> =
    available
        .filter { (id, version) -> refused[id] != version }
        .map { (id, version) -> AutoInstall(id, version) }
        .sortedBy { it.appId == selfId }

/** Versions qu'Android a refusé d'installer sans confirmation (gardées sur la box). */
class AutoUpdateLog(context: Context) {
    private val store = context.getSharedPreferences("auto_update", Context.MODE_PRIVATE)

    fun refuse(appId: String, version: String) = store.edit().putString(appId, version).apply()

    fun refused(): Map<String, String> = store.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
}
