package com.vantaflow.kairo.settings

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val FORMAT = "tvhome-backup-1"

/** Sérialise des préférences (valeurs typées) en JSON lisible. */
fun exportPrefs(values: Map<String, Any?>): String {
    val entries = JSONObject()
    values.forEach { (key, value) ->
        val typed = when (value) {
            is String -> JSONObject().put("t", "s").put("v", value)
            is Boolean -> JSONObject().put("t", "b").put("v", value)
            is Int -> JSONObject().put("t", "i").put("v", value)
            is Long -> JSONObject().put("t", "l").put("v", value)
            is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
            is Double -> JSONObject().put("t", "d").put("v", value)
            is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.map { it.toString() }))
            else -> null
        }
        typed?.let { entries.put(key, it) }
    }
    return JSONObject().put("format", FORMAT).put("prefs", entries).toString(2)
}

/** Relit une sauvegarde ; null si le fichier n'en est pas une. */
fun importPrefs(json: String): Map<String, Any>? = try {
    val root = JSONObject(json)
    if (root.optString("format") != FORMAT) null
    else {
        val entries = root.getJSONObject("prefs")
        entries.keys().asSequence().mapNotNull { key ->
            val o = entries.getJSONObject(key)
            val value: Any? = when (o.getString("t")) {
                "s" -> o.getString("v")
                "b" -> o.getBoolean("v")
                "i" -> o.getInt("v")
                "l" -> o.getLong("v")
                "f" -> o.getDouble("v").toFloat()
                "d" -> o.getDouble("v")
                "ss" -> o.getJSONArray("v").let { a -> (0 until a.length()).map(a::getString).toSet() }
                else -> null
            }
            value?.let { key to it }
        }.toMap()
    }
} catch (_: JSONException) {
    null
}
