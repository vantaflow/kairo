package com.vantaflow.kairo.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCleanerTest {
    @Test
    fun lowMemoryUnderAQuarter() {
        assertTrue(memoryIsLow(400, 2000))
        assertFalse(memoryIsLow(600, 2000))
        assertFalse(memoryIsLow(0, 0))
    }

    @Test
    fun closesOnlyAppsIdleLongEnoughAndNotOnScreen() {
        val seen = mapOf("netflix" to 0L, "youtube" to 50_000L, "tivimate" to 0L)
        assertEquals(listOf("netflix"), idleApps(seen, 60_000L, 60_000L, foreground = "tivimate"))
        assertEquals(emptyList<String>(), idleApps(seen, 60_000L, 0L, foreground = null))
    }
}
