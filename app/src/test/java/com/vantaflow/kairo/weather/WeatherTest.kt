package com.vantaflow.kairo.weather

import com.vantaflow.kairo.R
import com.vantaflow.kairo.net.HttpGet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeatherTest {
    private val tunis = Place("Tunis", 36.8, 10.18)

    @Test
    fun parsesCurrent() {
        var url = ""
        val repo = WeatherRepository(HttpGet {
            url = it
            """{"current":{"time":"2026-10-28T20:45","temperature_2m":22.4,"weather_code":2}}"""
        })
        val w = repo.current(tunis, fahrenheit = false)
        assertEquals(Weather(22.4, 2, "Tunis"), w)
        assertTrue(url, url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=36.8&longitude=10.18&current=temperature_2m,weather_code"))
    }

    @Test
    fun fahrenheitRequested() {
        var url = ""
        WeatherRepository(HttpGet { url = it; null }).current(tunis, fahrenheit = true)
        assertTrue(url, url.contains("temperature_unit=fahrenheit"))
    }

    @Test
    fun ipLocationParsedFromGeojs() {
        var url = ""
        val repo = WeatherRepository(HttpGet { url = it; """{"city":"Rabat","latitude":"34.0072","longitude":"-6.8407"}""" })
        assertEquals(Place("Rabat", 34.0072, -6.8407), repo.locateByIp())
        assertTrue(url, url.startsWith("https://get.geojs.io/"))
    }

    @Test
    fun ipLocationFallsBackToIpwho() {
        val repo = WeatherRepository(HttpGet { url ->
            if (url.contains("geojs")) null else """{"success":true,"city":"Sousse","latitude":35.82,"longitude":10.63}"""
        })
        assertEquals(Place("Sousse", 35.82, 10.63), repo.locateByIp())
    }

    @Test
    fun geocodeFirstResult() {
        var url = ""
        val repo = WeatherRepository(HttpGet {
            url = it
            """{"results":[{"name":"Casablanca","latitude":33.58,"longitude":-7.61},{"name":"Autre","latitude":0,"longitude":0}]}"""
        })
        assertEquals(Place("Casablanca", 33.58, -7.61), repo.geocode("Casa blanca", language = "fr"))
        assertTrue(url, url.contains("name=Casa+blanca") && url.contains("language=fr"))
        repo.geocode("Madrid", language = "es")
        assertTrue(url, url.contains("language=es"))
    }

    @Test
    fun networkErrorGivesNull() {
        val repo = WeatherRepository(HttpGet { null })
        assertNull(repo.locateByIp())
        assertNull(repo.geocode("Tunis"))
        assertNull(repo.current(tunis, fahrenheit = false))
        assertNull(WeatherRepository(HttpGet { "{}" }).current(tunis, fahrenheit = false))
    }

    @Test
    fun wmoCodesMapped() {
        assertEquals(R.string.weather_code_clear, weatherLabelRes(0))
        assertEquals(R.string.weather_code_partly_cloudy, weatherLabelRes(2))
        assertEquals(R.string.weather_code_fog, weatherLabelRes(45))
        assertEquals(R.string.weather_code_rain, weatherLabelRes(63))
        assertEquals(R.string.weather_code_snow, weatherLabelRes(73))
        assertEquals(R.string.weather_code_thunderstorm, weatherLabelRes(95))
        assertNull(weatherLabelRes(1234))
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(context.getString(R.string.weather_code_partly_cloudy), weatherLabel(context, 2))
        assertEquals("", weatherLabel(context, 1234))
    }

    @Test
    fun windAndUvInAppLanguage() {
        assertEquals("NO", windDirection(315))
        assertEquals("NW", windDirection(315, listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")))
        assertEquals("", windDirection(-1))
        assertEquals(R.string.weather_uv_moderate, uvLabelRes(4.0))
    }

    @Test
    fun formatsHeaderText() {
        assertEquals("22° · Partiellement nuageux", Weather(22.4, 2, "Tunis", "Partiellement nuageux").headerText())
        assertEquals("-3°", Weather(-2.6, 1234, "X").headerText())
    }
}
