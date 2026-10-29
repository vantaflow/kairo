package com.vantaflow.kairo.weather

import com.vantaflow.kairo.net.HttpGet
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

data class Place(val name: String, val lat: Double, val lon: Double)

data class Weather(val temperature: Double, val code: Int, val place: String) {
    /** Texte de l'en-tête de l'accueil : « 22° · Ensoleillé ». */
    fun headerText(): String {
        val label = weatherLabel(code)
        val temp = "${temperature.roundToInt()}°"
        return if (label.isEmpty()) temp else "$temp · $label"
    }
}

/** Libellé français d'un code météo WMO (Open-Meteo). */
fun weatherLabel(code: Int): String = when (code) {
    0 -> "Ensoleillé"
    1 -> "Plutôt dégagé"
    2 -> "Partiellement nuageux"
    3 -> "Couvert"
    45, 48 -> "Brouillard"
    in 51..57 -> "Bruine"
    in 61..67 -> "Pluie"
    in 71..77 -> "Neige"
    in 80..82 -> "Averses"
    85, 86 -> "Averses de neige"
    in 95..99 -> "Orage"
    else -> ""
}

/** Météo Open-Meteo (sans clé) ; position par IP (ipapi.co) ou par nom de ville. */
class WeatherRepository(private val http: HttpGet) {
    /** Position approximative par l'adresse IP : geojs.io, sinon ipwho.is (sans clé, en HTTPS). */
    fun locateByIp(): Place? =
        IP_LOCATORS.firstNotNullOfOrNull { url -> json(url, ::placeOf) }

    /** Ville et coordonnées, en nombres ou en texte selon le service. */
    private fun placeOf(o: JSONObject): Place? {
        val city = o.optString("city").takeIf(String::isNotEmpty) ?: return null
        val lat = o.optString("latitude").toDoubleOrNull() ?: return null
        val lon = o.optString("longitude").toDoubleOrNull() ?: return null
        return Place(city, lat, lon)
    }

    fun geocode(city: String): Place? =
        json("https://geocoding-api.open-meteo.com/v1/search?name=${enc(city)}&count=1&language=fr&format=json") { o ->
            val first = o.optJSONArray("results")?.optJSONObject(0) ?: return@json null
            Place(first.getString("name"), first.getDouble("latitude"), first.getDouble("longitude"))
        }

    fun current(place: Place, fahrenheit: Boolean): Weather? {
        val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
            "&current=temperature_2m,weather_code&timezone=auto$unit"
        return json(url) { o ->
            val c = o.optJSONObject("current") ?: return@json null
            if (!c.has("temperature_2m")) return@json null
            Weather(c.getDouble("temperature_2m"), c.optInt("weather_code", -1), place.name)
        }
    }

    private fun <T> json(url: String, parse: (JSONObject) -> T?): T? {
        val body = http.get(url) ?: return null
        return try {
            parse(JSONObject(body))
        } catch (_: JSONException) {
            null
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        val IP_LOCATORS = listOf("https://get.geojs.io/v1/ip/geo.json", "https://ipwho.is/")
    }
}
