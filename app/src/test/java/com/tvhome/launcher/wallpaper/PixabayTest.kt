package com.tvhome.launcher.wallpaper

import com.tvhome.launcher.net.HttpGet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PixabayTest {
    private val json = """{"total":2,"hits":[{"largeImageURL":"https://a.jpg"},{"largeImageURL":"https://b.jpg"}]}"""

    @Test
    fun parsesLargeImageUrls() {
        val client = PixabayClient(HttpGet { json }) { "KEY" }
        assertEquals(listOf("https://a.jpg", "https://b.jpg"), client.search("nature"))
    }

    @Test
    fun errorGivesEmptyList() {
        assertEquals(emptyList<String>(), PixabayClient(HttpGet { null }) { "KEY" }.search("nature"))
        assertEquals(emptyList<String>(), PixabayClient(HttpGet { "pas du json" }) { "KEY" }.search("nature"))
    }

    @Test
    fun missingKeyDoesNotCallNetwork() {
        var called = false
        val client = PixabayClient(HttpGet { called = true; json }) { "" }
        assertEquals(emptyList<String>(), client.search("nature"))
        assertEquals(false, called)
    }

    @Test
    fun queryIsUrlEncoded() {
        var url = ""
        PixabayClient(HttpGet { url = it; json }) { "K&Y" }.search("coucher de soleil")
        assertTrue(url, url.startsWith("https://pixabay.com/api/?key=K%26Y&q=coucher+de+soleil&"))
        assertTrue(url, url.contains("orientation=horizontal"))
    }

    @Test
    fun rotatorCycles() {
        var stored = RotationState(emptyList(), 0)
        val rotator = WallpaperRotator({ listOf("a", "b") }, { stored = it }, { stored })
        assertEquals("a", rotator.next("q"))
        assertEquals("b", rotator.next("q"))
        assertEquals(listOf("a", "b"), stored.urls)
        assertEquals(2, stored.nextIndex)
    }

    @Test
    fun rotatorUsesCacheWhenOffline() {
        var stored = RotationState(listOf("x", "y"), 2)
        val rotator = WallpaperRotator({ emptyList() }, { stored = it }, { stored })
        assertEquals("x", rotator.next("q"))
    }

    @Test
    fun rotatorRefetchesExpiredList() {
        val day = 24 * 60 * 60 * 1000L
        var stored = RotationState(listOf("old1", "old2"), 0, fetchedAtMs = 0L)
        val rotator = WallpaperRotator({ listOf("new") }, { stored = it }, { stored }, now = { day })
        assertEquals("new", rotator.next("q"))
        assertEquals(day, stored.fetchedAtMs)
    }

    @Test
    fun rotatorDropsListOfAnotherTheme() {
        var stored = RotationState(listOf("plage"), 0, fetchedAtMs = Long.MAX_VALUE / 2, query = "ocean")
        val rotator = WallpaperRotator({ q -> listOf("$q-1") }, { stored = it }, { stored }, now = { Long.MAX_VALUE / 2 })
        assertEquals("city-1", rotator.next("city"))
        assertEquals("city", stored.query)
    }

    @Test
    fun rotatorWithNothingGivesNull() {
        var stored = RotationState(emptyList(), 0)
        assertNull(WallpaperRotator({ emptyList() }, { stored = it }, { stored }).next("q"))
    }
}
