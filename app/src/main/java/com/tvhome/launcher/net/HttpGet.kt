package com.tvhome.launcher.net

import java.net.HttpURLConnection
import java.net.URL

/** GET HTTP renvoyant le corps, ou null en cas d'erreur réseau ou de statut non 2xx. */
fun interface HttpGet {
    fun get(url: String): String?
}

val UrlConnectionHttpGet = HttpGet { url ->
    runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "TVHome/1.0")
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
