package com.vantaflow.kairo.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    @Test
    fun episodesInNaturalOrder() {
        val names = listOf("Episode 10.mkv", "Episode 2.mkv", "Episode 1.mkv", "épisode 3.mkv")
        assertEquals(listOf("Episode 1.mkv", "Episode 2.mkv", "épisode 3.mkv", "Episode 10.mkv"), names.sortedWith(NaturalOrder))
    }

    @Test
    fun resumeOnlyBetween30sAnd95Percent() {
        assertFalse(shouldResume(Progress(10_000, 3_600_000, 0)))
        assertTrue(shouldResume(Progress(600_000, 3_600_000, 0)))
        assertFalse(shouldResume(Progress(3_500_000, 3_600_000, 0)))
        assertFalse(shouldResume(null))
    }

    @Test
    fun readableTitles() {
        assertEquals("Le Grand Bleu 1988", displayTitle("Le.Grand.Bleu.1988.mkv"))
        assertEquals("mon film", displayTitle("mon_film.mp4"))
        assertEquals("1 h 05", formatDuration(3_900_000))
        assertEquals("3:20", formatDuration(200_000, music = true))
    }
}
