package com.vantaflow.kairo.radio

import com.vantaflow.kairo.net.HttpGet
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

/** Une radio de l'annuaire radio-browser.info. */
data class Station(
    val id: String,
    val name: String,
    val url: String,
    val favicon: String = "",
    val country: String = "",
    val tags: String = "",
    /** Site de la radio : son icône sert de logo quand l'annuaire n'en a pas de bon. */
    val homepage: String = "",
)

/** Sites d'annuaires ou de réseaux sociaux : leur icône n'est pas celle de la radio. */
private val NotStationSites = listOf(
    "zeno.fm", "facebook.com", "instagram.com", "youtube.com", "twitter.com", "x.com", "linktr.ee",
    "radiolive.ma", "streema.com", "radio.net", "tunein.com", "onlineradiobox.com", "radio-browser.info",
    "mytuner-radio.com", "radio.garden", "live365.com", "shoutcast.com", "icecast.org",
)

/**
 * Logos à essayer dans l'ordre : l'image de l'annuaire (sauf un simple favicon.ico, souvent
 * minuscule ou celui de WordPress), puis l'icône du site de la radio, puis ce favicon.
 */
fun Station.logoCandidates(): List<String> {
    val ico = favicon.substringBefore('?').endsWith(".ico", ignoreCase = true)
    val host = runCatching { java.net.URI(homepage.trim()).host }.getOrNull()?.removePrefix("www.")?.lowercase()
    val site = host?.takeIf { h -> '.' in h && NotStationSites.none { h == it || h.endsWith(".$it") } }
        ?.let { "https://www.google.com/s2/favicons?domain=$it&sz=256" }
    return listOfNotNull(favicon.takeIf { it.isNotBlank() && !ico }, site, favicon.takeIf { it.isNotBlank() && ico })
}

/** Pays proposés (code ISO, libellé) ; « » = les plus écoutées du monde. */
val RadioCountries = listOf(
    "MA" to "Maroc", "FR" to "France", "DZ" to "Algérie", "TN" to "Tunisie",
    "BE" to "Belgique", "CH" to "Suisse", "CA" to "Canada", "" to "Monde",
)

fun parseStations(body: String): List<Station> = try {
    val a = JSONArray(body)
    (0 until a.length()).mapNotNull { i ->
        val o = a.getJSONObject(i)
        val url = o.optString("url_resolved").ifBlank { o.optString("url") }
        val name = o.optString("name").trim()
        if (url.isBlank() || name.isBlank()) null
        else Station(o.optString("stationuuid"), name, url, o.optString("favicon"), o.optString("country"), o.optString("tags"), o.optString("homepage"))
    // Même nom ou même flux : une seule tuile (les clés de la grille doivent être uniques).
    }.distinctBy { it.name.lowercase() }.distinctBy { it.url }
} catch (_: JSONException) {
    emptyList()
}

fun encodeStations(list: List<Station>): String = JSONArray(list.map {
    JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("favicon", it.favicon).put("country", it.country).put("homepage", it.homepage)
}).toString()

fun decodeStations(s: String?): List<Station> = try {
    if (s.isNullOrBlank()) emptyList() else JSONArray(s).let { a ->
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Station(o.getString("id"), o.getString("name"), o.getString("url"), o.optString("favicon"), o.optString("country"), homepage = o.optString("homepage"))
        }
    }
} catch (_: JSONException) {
    emptyList()
}

/** Annuaire libre radio-browser.info (sans clé) ; plusieurs serveurs, le premier qui répond. */
class RadioBrowser(private val http: HttpGet) {
    /** Les plus écoutées d'un pays ([country] vide = monde). */
    fun top(country: String, limit: Int = 60): List<Station> {
        val filter = if (country.isBlank()) "" else "countrycode=$country&"
        return query("stations/search?${filter}order=clickcount&reverse=true&hidebroken=true&limit=$limit")
    }

    fun search(name: String, limit: Int = 60): List<Station> =
        query("stations/search?name=${URLEncoder.encode(name.trim(), "UTF-8")}&order=clickcount&reverse=true&hidebroken=true&limit=$limit")

    /** Compte l'écoute (demandé par l'annuaire pour classer les radios). */
    fun click(id: String) {
        if (id.isNotBlank()) HOSTS.firstNotNullOfOrNull { http.get("https://$it/json/url/$id") }
    }

    private fun query(path: String): List<Station> =
        HOSTS.firstNotNullOfOrNull { host -> http.get("https://$host/json/$path") }?.let(::parseStations).orEmpty()

    private companion object {
        val HOSTS = listOf("all.api.radio-browser.info", "de1.api.radio-browser.info", "de2.api.radio-browser.info")
    }
}
