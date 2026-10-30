package com.vantaflow.kairo.reco

import com.vantaflow.kairo.home.interleave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeAppsTest {
    private val hour = 3_600_000L
    private val now = 100L * 24 * hour

    @Test
    fun `les applis récentes de films passent en tête, les autres sont écartées`() {
        val usage = mapOf(
            "com.apple.atve.androidtv.appletv" to now - 2 * hour,
            "com.amazon.amazonvideo.livingroom" to now - 1 * hour,
            "com.android.tv.settings" to now - 1,
            "com.netflix.ninja" to now - 20 * 24 * hour,
        )
        val result = resumeCandidates(usage, now, exclude = emptySet(), isMedia = ::isMediaPackage)
        assertEquals(listOf("com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv"), result.map { it.first })
    }

    @Test
    fun `une appli qui publie déjà ses contenus n'a pas de carte Reprendre`() {
        val usage = mapOf("org.smarttube.stable" to now - hour)
        assertTrue(resumeCandidates(usage, now, exclude = setOf("org.smarttube.stable"), isMedia = ::isMediaPackage).isEmpty())
    }

    @Test
    fun `films et séries en carte Reprendre, musique traitée comme la radio`() {
        listOf("net.mbc.shahid", "com.viu.tv", "com.netflix.ninja", "com.apple.atve.androidtv.appletv").forEach {
            assertTrue(it, isMediaPackage(it))
        }
        listOf("com.spotify.tv.android", "com.anghami", "deezer.android.tv", "com.google.android.youtube.tvmusic").forEach {
            assertTrue(it, isAudioPackage(it))
            assertFalse(it, isMediaPackage(it))
        }
        assertFalse(isMediaPackage("com.android.tv.settings"))
    }

    @Test
    fun `temps écoulé lisible`() {
        assertEquals("à l'instant", formatAgo(30_000))
        assertEquals("il y a 12 min", formatAgo(12 * 60_000L))
        assertEquals("il y a 3 h", formatAgo(3 * hour))
        assertEquals("hier", formatAgo(30 * hour))
        assertEquals("il y a 4 j", formatAgo(4 * 24 * hour))
    }

    @Test
    fun `recommandations et applis en alternance`() {
        assertEquals(listOf("r1", "a1", "r2", "a2", "r3", "r4"), interleave(listOf("r1", "r2", "r3", "r4"), listOf("a1", "a2")))
        assertEquals(listOf("a1", "a2"), interleave(emptyList(), listOf("a1", "a2")))
    }
}
