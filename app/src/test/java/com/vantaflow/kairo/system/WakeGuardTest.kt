package com.vantaflow.kairo.system

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WakeGuardTest {
    @Test fun sleepsWhenNobodyTouches() = runTest {
        var slept = 0
        val g = WakeGuard(this, busy = { false }, onSleep = { slept++ })
        g.screenOn()
        advanceTimeBy(WAKE_GUARD_MS - WAKE_GUARD_WARNING_MS + 1); runCurrent()
        assertEquals((WAKE_GUARD_WARNING_MS / 1000).toInt(), g.warning.value)
        advanceTimeBy(WAKE_GUARD_WARNING_MS); runCurrent()
        assertEquals(1, slept)
        assertNull(g.warning.value)
    }

    @Test fun keyPressCancels() = runTest {
        var slept = 0
        val g = WakeGuard(this, busy = { false }, onSleep = { slept++ })
        g.screenOn()
        advanceTimeBy(WAKE_GUARD_MS - 5_000); runCurrent()
        g.stop()
        advanceTimeBy(WAKE_GUARD_MS); runCurrent()
        assertEquals(0, slept)
        assertNull(g.warning.value)
    }

    @Test fun playbackKeepsAwake() = runTest {
        var slept = 0
        val g = WakeGuard(this, busy = { true }, onSleep = { slept++ })
        g.screenOn()
        advanceTimeBy(WAKE_GUARD_MS * 2); runCurrent()
        assertEquals(0, slept)
        assertNull(g.warning.value)
    }
}
