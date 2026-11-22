package com.vantaflow.kairo.ultratv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UltraTvTest {
    private val link = "ultratv://channel/42"

    @Test
    fun releasePackageIsPreferred() {
        assertEquals(ULTRA_TV_RELEASE, pickUltraTvPackage(setOf(ULTRA_TV_RELEASE, ULTRA_TV_DEBUG)))
        assertEquals(ULTRA_TV_DEBUG, pickUltraTvPackage(setOf(ULTRA_TV_DEBUG, "com.autre.appli")))
        assertNull(pickUltraTvPackage(emptySet()))
        assertEquals("com.ultratv.tv.nativeapp.kairo", ultraTvAuthority(ULTRA_TV_RELEASE))
    }

    @Test
    fun fullRowIsParsed() {
        val row = mapOf<String, Any?>(
            COL_PROVIDER_ID to 7L,
            COL_REMOTE_ID to "r-42",
            COL_NAME to "Al Aoula",
            COL_NUMBER to "1",
            COL_LOGO to "https://x/logo.png",
            COL_NOW_TITLE to "Journal",
            COL_NOW_START_MS to 1_000L,
            COL_NOW_END_MS to 5_000L,
            COL_NOW_IMAGE to "https://x/now.jpg",
            COL_NEXT_TITLE to "Météo",
            COL_NEXT_START_MS to 5_000L,
            COL_NEXT_END_MS to 6_000L,
            COL_DEEP_LINK to link,
            COL_LOCKED to 0L,
        )
        assertEquals(
            UtvChannel(
                providerId = 7L, remoteId = "r-42", name = "Al Aoula", number = "1",
                logo = "https://x/logo.png", nowTitle = "Journal", nowStartMs = 1_000L, nowEndMs = 5_000L,
                nowImage = "https://x/now.jpg", nextTitle = "Météo", nextStartMs = 5_000L, nextEndMs = 6_000L,
                deepLink = link, locked = false,
            ),
            utvChannelOf(row),
        )
    }

    @Test
    fun missingColumnsDefaultSafely() {
        val ch = utvChannelOf(mapOf(COL_NAME to "2M", COL_DEEP_LINK to link))
        assertEquals(
            UtvChannel(
                providerId = 0L, remoteId = "", name = "2M", number = null, logo = null,
                nowTitle = null, nowStartMs = 0L, nowEndMs = 0L, nowImage = null,
                nextTitle = null, nextStartMs = 0L, nextEndMs = 0L, deepLink = link, locked = false,
            ),
            ch,
        )
        // Textes vides ou faits d'espaces : null
        val blanks = utvChannelOf(mapOf(COL_NAME to "2M", COL_DEEP_LINK to link, COL_NOW_TITLE to "   ", COL_LOGO to ""))
        assertNull(blanks!!.nowTitle)
        assertNull(blanks.logo)
    }

    @Test
    fun rowWithoutNameOrLinkIsDropped() {
        assertNull(utvChannelOf(mapOf(COL_NAME to "", COL_DEEP_LINK to link)))
        assertNull(utvChannelOf(mapOf(COL_NAME to "   ", COL_DEEP_LINK to link)))
        assertNull(utvChannelOf(mapOf(COL_DEEP_LINK to link)))
        assertNull(utvChannelOf(mapOf(COL_NAME to "2M", COL_DEEP_LINK to "")))
        assertNull(utvChannelOf(mapOf(COL_NAME to "2M")))
        assertNull(utvChannelOf(mapOf(COL_NAME to "2M", COL_DEEP_LINK to "http://exemple.com/x")))
    }

    @Test
    fun numbersAcceptStringsAndInts() {
        val ch = utvChannelOf(
            mapOf(
                COL_NAME to "2M", COL_DEEP_LINK to link,
                COL_PROVIDER_ID to "12", COL_NOW_START_MS to 5, COL_LOCKED to "1",
                COL_NUMBER to 3, COL_NOW_END_MS to " 9 ",
            ),
        )
        assertNotNull(ch)
        assertEquals(12L, ch!!.providerId)
        assertEquals(5L, ch.nowStartMs)
        assertEquals(9L, ch.nowEndMs)
        assertTrue(ch.locked)
        assertEquals("3", ch.number)
        // provider_id illisible : 0, la ligne reste
        assertEquals(0L, utvChannelOf(mapOf(COL_NAME to "2M", COL_DEEP_LINK to link, COL_PROVIDER_ID to "abc"))!!.providerId)
    }

    @Test
    fun unexpectedTypesNeverThrow() {
        val weird = listOf<Any?>(byteArrayOf(1, 2), 3.7, Any(), null, Double.NaN, listOf(1), Float.POSITIVE_INFINITY)
        for (v in weird) {
            val row = (ALL_COLUMNS.associateWith { v }) + mapOf(COL_NAME to "2M", COL_DEEP_LINK to link)
            val ch = utvChannelOf(row)
            assertNotNull(ch)
            // Types non pris en charge : 0 / null / false
            assertEquals(0L, ch!!.providerId)
            assertEquals(0L, ch.nowStartMs)
            assertNull(ch.nowTitle)
            assertEquals("", ch.remoteId)
            assertFalse(ch.locked)
            // La table entière avec la valeur bizarre : jamais d'exception, ligne écartée
            assertNull(utvChannelOf(ALL_COLUMNS.associateWith { v }))
        }
        assertNull(utvChannelOf(emptyMap()))
    }

    @Test
    fun progressInsideInterval() {
        val ch = channel(start = 1_000L, end = 5_000L)
        assertEquals(25, nowProgressPercent(ch, 2_000L))
        assertEquals(0, nowProgressPercent(ch, 1_000L))
        assertEquals(100, nowProgressPercent(ch, 5_000L))
    }

    @Test
    fun noProgressWhenLockedOrUnknown() {
        assertNull(nowProgressPercent(channel(1_000L, 5_000L, locked = true), 2_000L))
        assertNull(nowProgressPercent(channel(0L, 5_000L), 2_000L))
        assertNull(nowProgressPercent(channel(1_000L, 0L), 2_000L))
        assertNull(nowProgressPercent(channel(5_000L, 5_000L), 5_000L))
        assertNull(nowProgressPercent(channel(5_000L, 1_000L), 2_000L))
        assertNull(nowProgressPercent(channel(1_000L, 5_000L), 999L))
        assertNull(nowProgressPercent(channel(1_000L, 5_000L), 5_001L))
    }

    @Test
    fun kidsFlag() {
        assertTrue(utvKidsOf(mapOf("kids" to 1)))
        assertFalse(utvKidsOf(mapOf("kids" to "0")))
        assertFalse(utvKidsOf(null))
        assertFalse(utvKidsOf(mapOf("kids" to byteArrayOf(1))))
        assertTrue(utvKidsOf(mapOf("kids" to "1")))
    }

    private fun channel(start: Long, end: Long, locked: Boolean = false) = UtvChannel(
        providerId = 1L, remoteId = "r", name = "n", number = null, logo = null,
        nowTitle = "t", nowStartMs = start, nowEndMs = end, nowImage = null,
        nextTitle = null, nextStartMs = 0L, nextEndMs = 0L, deepLink = link, locked = locked,
    )
}
