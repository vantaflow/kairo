package com.vantaflow.kairo.system

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerSettingsTest {
    @Test
    fun labelsMatchGoogleTv() {
        assertEquals(listOf("20 min", "30 min", "1 h", "3 h", "4 h", "8 h", "12 h", "24 h"), IdleChoices.map(::timeoutLabel))
        assertEquals(listOf("3 h", "4 h", "6 h", "8 h", "Jamais"), WatchingChoices.map(::timeoutLabel))
    }
}
