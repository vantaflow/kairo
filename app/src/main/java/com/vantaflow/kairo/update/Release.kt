package com.vantaflow.kairo.update

import org.json.JSONException
import org.json.JSONObject

/** Dernière version publiée sur GitHub : son tag et l'URL API de son APK. */
data class Release(val tag: String, val notes: String, val apkAssetUrl: String)

fun parseLatestRelease(json: String): Release? = try {
    val o = JSONObject(json)
    val assets = o.optJSONArray("assets")
    val apk = assets?.let { a -> (0 until a.length()).map(a::getJSONObject).firstOrNull { it.optString("name").endsWith(".apk") } }
    apk?.let { Release(o.getString("tag_name"), o.optString("body"), it.getString("url")) }
} catch (_: JSONException) {
    null
}

private fun parts(version: String): List<Int>? =
    version.removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }

/** Vrai si [tag] (« v0.2.0 ») est plus récent que [current] (« 0.1.9 »). */
fun isNewer(tag: String, current: String): Boolean {
    val a = parts(tag) ?: return false
    val b = parts(current) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
