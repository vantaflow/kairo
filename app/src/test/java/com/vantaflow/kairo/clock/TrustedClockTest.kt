package com.vantaflow.kairo.clock

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrustedClockTest {
    private val local = 1_790_000_000_000L

    private fun sntp(responses: Map<String, ByteArray?>) = SntpClient { host, _, _ -> responses[host] }

    @Test
    fun firstHostFailsSecondUsed() = runTest {
        val clock = TrustedClock(
            sntp(mapOf("a" to null, "b" to NtpPackets.response(local, 3_000))),
            hosts = listOf("a", "b"),
            systemNow = { local },
            elapsedNow = { 0L },
        )
        clock.sync()
        assertEquals(3_000.0, clock.offsetMs.value.toDouble(), 5.0)
    }

    @Test
    fun allHostsFailKeepsZeroOffset() = runTest {
        val clock = TrustedClock(sntp(emptyMap()), hosts = listOf("a", "b"), systemNow = { local })
        clock.sync()
        assertEquals(0L, clock.offsetMs.value)
        assertEquals(local, clock.now())
        assertNull(clock.lastSyncMs.value)
    }

    @Test
    fun nowIsSystemPlusOffsetAfterSync() = runTest {
        val clock = TrustedClock(
            sntp(mapOf("a" to NtpPackets.response(local, -2_000))),
            hosts = listOf("a"),
            systemNow = { local },
            elapsedNow = { 0L },
        )
        clock.sync()
        assertEquals((local - 2_000).toDouble(), clock.now().toDouble(), 5.0)
        assertEquals(local, clock.lastSyncMs.value)
    }

    @Test
    fun systemClockCorrectionDoesNotDoubleOffset() = runTest {
        var system = local
        var elapsed = 10_000L
        val clock = TrustedClock(
            sntp(mapOf("a" to NtpPackets.response(local, 3_000))),
            hosts = listOf("a"),
            systemNow = { system },
            elapsedNow = { elapsed },
        )
        clock.sync()
        // Android corrige ensuite sa propre horloge de +3 s, 1 s plus tard.
        system += 3_000 + 1_000
        elapsed += 1_000
        assertEquals((local + 3_000 + 1_000).toDouble(), clock.now().toDouble(), 5.0)
    }
}
