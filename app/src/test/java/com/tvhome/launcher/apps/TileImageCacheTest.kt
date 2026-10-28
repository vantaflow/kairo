package com.tvhome.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TileImageCacheTest {
    @Test
    fun loadsOncePerKey() {
        var loads = 0
        val cache = TileImageCache<Any>(maxEntries = 10)
        val first = cache.getOrLoad("a") { loads++; Any() }
        val second = cache.getOrLoad("a") { loads++; Any() }
        assertSame(first, second)
        assertEquals(1, loads)
    }

    @Test
    fun nullResultIsNotCached() {
        var loads = 0
        val cache = TileImageCache<Any>(maxEntries = 10)
        cache.getOrLoad("a") { loads++; null }
        cache.getOrLoad("a") { loads++; null }
        assertEquals(2, loads)
    }

    @Test
    fun evictsLeastRecentlyUsed() {
        var loads = 0
        val cache = TileImageCache<Any>(maxEntries = 2)
        cache.getOrLoad("a") { loads++; Any() }
        cache.getOrLoad("b") { loads++; Any() }
        cache.getOrLoad("c") { loads++; Any() }
        cache.getOrLoad("a") { loads++; Any() }
        assertEquals(4, loads)
    }
}
