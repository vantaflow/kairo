package com.vantaflow.kairo.home

import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.reco.Channel
import com.vantaflow.kairo.reco.LiveChannel
import com.vantaflow.kairo.reco.Program
import com.vantaflow.kairo.settings.WallpaperConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeModeStateTest {
    private val program = Program(1, "com.example.video", "Film", null, null, null, null)
    private val live = LiveChannel("tv-1", "com.example.tv", "1", "Chaîne 1", "content://tv/1")
    private val favorites = (1..3).map { AppInfo("com.example.app$it", "App $it", hasBanner = false) }

    private val full = HomeUiState(
        timeText = "20:45",
        dateText = "Mercredi 28 octobre · 18°",
        favorites = favorites,
        wallpaper = WallpaperConfig.Pixabay("nature", 30),
        watchNext = listOf(program),
        recoRows = listOf(Channel(7, "com.example.video", "Tendances", listOf(program))),
        wallpaperImageUrl = "https://example.com/image.jpg",
        weatherText = "18° · Couvert",
        liveChannels = listOf(live),
        tvInputs = listOf(live),
        loaded = true,
    )

    @Test
    fun recommendationsAndOnlineContentAreRemoved() {
        val safe = safeModeState(full, 0xFF101216)
        assertTrue(safe.watchNext.isEmpty())
        assertTrue(safe.recoRows.isEmpty())
        assertTrue(safe.liveChannels.isEmpty())
        assertTrue(safe.tvInputs.isEmpty())
        assertNull(safe.weatherText)
        assertNull(safe.wallpaperImageUrl)
        assertEquals(WallpaperConfig.Solid(0xFF101216), safe.wallpaper)
    }

    @Test
    fun favoritesClockAndLoadingAreKept() {
        val safe = safeModeState(full, 0xFF101216)
        assertEquals(favorites, safe.favorites)
        assertEquals("20:45", safe.timeText)
        assertEquals(full.dateText, safe.dateText)
        assertTrue(safe.loaded)
    }
}
