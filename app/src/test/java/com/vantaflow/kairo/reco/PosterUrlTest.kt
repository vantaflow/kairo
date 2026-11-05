package com.vantaflow.kairo.reco

import org.junit.Assert.assertEquals
import org.junit.Test

class PosterUrlTest {
    @Test
    fun expiredYoutubeThumbnailFallsBackToStandardOne() {
        assertEquals(
            "https://i.ytimg.com/vi/fFeYAfz40UQ/hqdefault.jpg",
            stablePosterUrl("https://i.ytimg.com/vi/fFeYAfz40UQ/sddefault_custom_2.jpg?sqp=COSJoNcG&rs=AOn4CLB916"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc_DEF-123/hqdefault.jpg",
            stablePosterUrl("https://i9.ytimg.com/vi_webp/abc_DEF-123/maxresdefault.webp"),
        )
    }

    @Test
    fun otherPostersAreKept() {
        val tmdb = "https://image.tmdb.org/t/p/w780/x.jpg"
        assertEquals(tmdb, stablePosterUrl(tmdb))
        assertEquals(null, stablePosterUrl(null))
        assertEquals("content://x/y", stablePosterUrl("content://x/y"))
    }
}
