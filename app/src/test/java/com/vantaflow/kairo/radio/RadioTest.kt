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

    @Test
    fun logoFromStationSiteBeforeFavicon() {
        val site = "https://www.google.com/s2/favicons?domain=radioaswat.ma&sz=256"
        // favicon.ico (souvent WordPress ou 16 px) : l'icône du site d'abord.
        assertEquals(
            listOf(site, "http://www.radioaswat.ma/favicon.ico"),
            Station("1", "Aswat", "u", "http://www.radioaswat.ma/favicon.ico", homepage = "http://www.radioaswat.ma/").logoCandidates(),
        )
        // Vraie image de l'annuaire : gardée en premier.
        assertEquals("https://x.ma/logo.png", Station("2", "X", "u", "https://x.ma/logo.png", homepage = "https://x.ma").logoCandidates().first())
        // Page d'un annuaire ou d'un flux : pas son icône.
        assertTrue(Station("3", "Z", "u", homepage = "https://stream.zeno.fm/abc").logoCandidates().isEmpty())
    }
}
