package com.vantaflow.kairo.radio

import com.vantaflow.kairo.net.HttpGet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RadioTest {
    private val body = """[
        {"stationuuid":"a","name":"Radio Aswat","url":"http://x/a.pls","url_resolved":"http://x/a.mp3","favicon":"http://x/a.png","country":"Morocco"},
        {"stationuuid":"b","name":"radio aswat ","url":"http://x/b.mp3","url_resolved":""},
        {"stationuuid":"c","name":"Sans flux","url":"","url_resolved":""},
        {"stationuuid":"d","name":"Aswat HD","url":"http://x/a.mp3","url_resolved":"http://x/a.mp3"}
    ]"""

    @Test
    fun parsesResolvedUrlsWithoutDuplicates() {
        val list = parseStations(body)
        assertEquals(1, list.size)
        assertEquals("http://x/a.mp3", list[0].url)
    }

    @Test
    fun favoritesRoundTrip() {
        val list = parseStations(body)
        assertEquals(list.map { it.url }, decodeStations(encodeStations(list)).map { it.url })
        assertTrue(decodeStations("pas du json").isEmpty())
    }

    @Test
    fun topFallsBackToNextServer() {
        val asked = mutableListOf<String>()
        val browser = RadioBrowser(HttpGet { url -> asked += url; if (url.contains("//all.")) null else body })
        assertEquals(1, browser.top("MA").size)
        assertTrue(asked.last().contains("de1.") && asked.last().contains("countrycode=MA"))
    }

    @Test
    fun startsOnUserCountry() {
        assertEquals("MA", initialRadioTab("MA", "FR", "FR"))
        assertEquals("FR", initialRadioTab("", "FR", "MA"))
        assertEquals("MA", initialRadioTab(null, null, "MA"))
        assertEquals("", initialRadioTab(null, null, ""))
    }

    @Test
    fun userCountryComesFirst() {
        assertEquals("MA", radioCountriesFor("MA").first().first)
        assertEquals(RadioCountries.size, radioCountriesFor("FR").size)
        assertEquals("SN" to "Sénégal", radioCountriesFor("SN").first())
    }
}
