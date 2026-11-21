package com.vantaflow.kairo.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashGuardTest {
    /** Stockage en mémoire et horloge réglable. */
    private var stored: List<Long> = emptyList()
    private var time = 0L
    private val guard = CrashGuard(load = { stored }, save = { stored = it }, now = { time })

    private fun startAt(ms: Long): Boolean {
        time = ms
        return guard.recordStart()
    }

    @Test
    fun thirdStartWithinWindowEntersSafeMode() {
        assertFalse(startAt(0))
        assertFalse(startAt(30_000))
        assertTrue(startAt(60_000))
    }

    @Test
    fun oldStartsAreForgotten() {
        assertFalse(startAt(0))
        assertFalse(startAt(30_000))
        assertFalse(startAt(200_000))
        assertEquals(listOf(200_000L), stored)
    }

    @Test
    fun markStableClearsHistory() {
        assertFalse(startAt(0))
        assertFalse(startAt(30_000))
        guard.markStable()
        assertFalse(startAt(60_000))
        assertFalse(startAt(61_000))
    }

    @Test
    fun resetLeavesSafeMode() {
        startAt(0)
        startAt(1_000)
        assertTrue(startAt(2_000))
        guard.reset()
        assertFalse(startAt(3_000))
    }

    @Test
    fun corruptedHistoryIsIgnored() {
        val broken = CrashGuard(load = { throw NumberFormatException("abc") }, save = { stored = it }, now = { 5_000L })
        assertFalse(broken.recordStart())
        assertEquals(listOf(5_000L), stored)
    }
}
