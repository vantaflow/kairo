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

/** Symbole d'un code météo WMO (emoji, lisible de loin) ; [night] : lune au lieu du soleil. */
fun weatherEmoji(code: Int, night: Boolean = false): String = when (code) {
    0 -> if (night) "🌙" else "☀️"
    1 -> if (night) "🌙" else "🌤️"
    2 -> "⛅"
    3 -> "☁️"
    // Le symbole brouillard d'Android est un carré gris : un nuage se lit mieux.
    45, 48 -> "☁️"
    in 51..57, in 61..67, in 80..82 -> "🌧️"
    in 71..77, 85, 86 -> "❄️"
    in 95..99 -> "⛈️"
    else -> "🌡️"
}

data class HourForecast(val time: java.time.LocalDateTime, val temperature: Double, val code: Int, val rainChance: Int)
data class DayForecast(
    val date: java.time.LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val rainChance: Int,
    val sunrise: java.time.LocalDateTime? = null,
    val sunset: java.time.LocalDateTime? = null,
    val uvMax: Double = 0.0,
    val rainMm: Double = 0.0,
    val windMax: Double = 0.0,
    val gustMax: Double = 0.0,
    /** Direction dominante du vent, en degrés (d'où il vient). */
    val windDir: Int = -1,
)

/** Point cardinal d'où vient le vent : « N », « NE »… */
fun windDirection(deg: Int): String =
    if (deg < 0) "" else listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[((deg % 360 + 22) / 45) % 8]

/** Niveau d'UV en mots (échelle OMS). */
fun uvLabel(uv: Double): String = when {
    uv < 3 -> "faible"
    uv < 6 -> "modéré"
    uv < 8 -> "élevé"
    uv < 11 -> "très élevé"
    else -> "extrême"
}

/** Prévisions : maintenant, heure par heure et sur 7 jours. */
data class Forecast(
    val place: String,
    /** Heure locale du lieu au moment de la mesure. */
    val now: java.time.LocalDateTime,
    val temperature: Double,
    val feelsLike: Double,
    val code: Int,
    val humidity: Int,
    val windKmh: Double,
    val isDay: Boolean,
    val gustKmh: Double = 0.0,
    val windDir: Int = -1,
    val pressure: Int = -1,
    val cloudCover: Int = -1,
    val hours: List<HourForecast>,
    val days: List<DayForecast>,
)

/** Lit la réponse Open-Meteo (current + hourly + daily) ; null si elle est incomplète. */
fun parseForecast(o: JSONObject, place: String): Forecast? = try {
    val c = o.getJSONObject("current")
    val h = o.getJSONObject("hourly")
    val d = o.getJSONObject("daily")
    val hTime = h.getJSONArray("time")
    val hTemp = h.getJSONArray("temperature_2m")
    val hCode = h.getJSONArray("weather_code")
    val hRain = h.optJSONArray("precipitation_probability")
    val dTime = d.getJSONArray("time")
    val dCode = d.getJSONArray("weather_code")
    val dMax = d.getJSONArray("temperature_2m_max")
    val dMin = d.getJSONArray("temperature_2m_min")
    val dRain = d.optJSONArray("precipitation_probability_max")
    Forecast(
        place = place,
        now = java.time.LocalDateTime.parse(c.getString("time")),
        temperature = c.getDouble("temperature_2m"),
        feelsLike = c.optDouble("apparent_temperature", c.getDouble("temperature_2m")),
        code = c.optInt("weather_code", -1),
        humidity = c.optInt("relative_humidity_2m", -1),
        windKmh = c.optDouble("wind_speed_10m", 0.0),
        isDay = c.optInt("is_day", 1) == 1,
        gustKmh = c.optDouble("wind_gusts_10m", 0.0),
        windDir = c.optInt("wind_direction_10m", -1),
        pressure = c.optDouble("pressure_msl", -1.0).let { if (it < 0) -1 else kotlin.math.round(it).toInt() },
        cloudCover = c.optInt("cloud_cover", -1),
        hours = (0 until hTime.length()).map { i ->
            HourForecast(java.time.LocalDateTime.parse(hTime.getString(i)), hTemp.getDouble(i), hCode.optInt(i, -1), hRain?.optInt(i, 0) ?: 0)
        },
        days = (0 until dTime.length()).map { i ->
            DayForecast(
                java.time.LocalDate.parse(dTime.getString(i)), dCode.optInt(i, -1), dMax.getDouble(i), dMin.getDouble(i), dRain?.optInt(i, 0) ?: 0,
                sunrise = d.optJSONArray("sunrise")?.optString(i)?.takeIf { it.isNotBlank() }?.let(java.time.LocalDateTime::parse),
                sunset = d.optJSONArray("sunset")?.optString(i)?.takeIf { it.isNotBlank() }?.let(java.time.LocalDateTime::parse),
                uvMax = d.optJSONArray("uv_index_max")?.optDouble(i, 0.0) ?: 0.0,
                rainMm = d.optJSONArray("precipitation_sum")?.optDouble(i, 0.0) ?: 0.0,
                windMax = d.optJSONArray("wind_speed_10m_max")?.optDouble(i, 0.0) ?: 0.0,
                gustMax = d.optJSONArray("wind_gusts_10m_max")?.optDouble(i, 0.0) ?: 0.0,
                windDir = d.optJSONArray("wind_direction_10m_dominant")?.optInt(i, -1) ?: -1,
            )
        },
    )
} catch (_: JSONException) {
    null
} catch (_: java.time.format.DateTimeParseException) {
    null
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

    fun forecast(place: Place, fahrenheit: Boolean): Forecast? {
        val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,wind_gusts_10m,wind_direction_10m," +
            "pressure_msl,cloud_cover,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code,precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset," +
            "uv_index_max,precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant" +
            "&forecast_days=7&timezone=auto$unit"
        return json(url) { parseForecast(it, place.name) }
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
