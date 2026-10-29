package com.vantaflow.kairo.wallpaper

import androidx.compose.ui.graphics.Color
import com.vantaflow.kairo.settings.WallpaperConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperBrushTest {
    @Test
    fun solidMapsToSolidColor() {
        assertEquals(listOf(Color(0xFF202124)), WallpaperConfig.Solid(0xFF202124).colors())
    }

    @Test
    fun gradientAngle0IsHorizontal() {
        val (start, end) = gradientEndpoints(0, 1920f, 1080f)
        assertEquals(0f, start.x, 0.5f)
        assertEquals(1920f, end.x, 0.5f)
        assertEquals(start.y, end.y, 0.5f)
    }

    @Test
    fun gradientAngle90IsVertical() {
        val (start, end) = gradientEndpoints(90, 1920f, 1080f)
        assertEquals(start.x, end.x, 0.5f)
        assertEquals(0f, start.y, 0.5f)
        assertEquals(1080f, end.y, 0.5f)
    }

    @Test
    fun presetGradientsHaveSixEntries() {
        assertEquals(6, PresetGradients.size)
    }

    private fun assertEquals(expected: Float, actual: Float, delta: Float) =
        org.junit.Assert.assertEquals(expected.toDouble(), actual.toDouble(), delta.toDouble())

}
