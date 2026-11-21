package com.vantaflow.kairo.system

import android.app.ApplicationExitInfo.REASON_CRASH
import android.app.ApplicationExitInfo.REASON_EXIT_SELF
import android.app.ApplicationExitInfo.REASON_LOW_MEMORY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitReasonTest {
    private val old = listOf(ExitTrace("100:1700000000000", REASON_CRASH), ExitTrace("90:1690000000000", REASON_EXIT_SELF))
    private val seen = old.map { it.key }.toSet()

    @Test
    fun newTraceDatedBeforeOldOnesIsDetected() {
        // Box redémarrée sans réseau : son horloge est revenue à la date de compilation.
        val current = old + ExitTrace("200:1600000000000", REASON_CRASH)
        assertEquals(listOf(REASON_CRASH), newExitTraces(seen, current))
        assertTrue(previousExit(seen, current, bootIsRecent = false).crashed)
    }

    @Test
    fun noNewTraceCountsNothing() {
        // Coupure de courant : aucune trace écrite, l'ancien plantage n'est pas recompté.
        assertEquals(emptyList<Int>(), newExitTraces(seen, old))
        assertEquals(PreviousExit(crashed = false, countsForSafeMode = false), previousExit(seen, old, bootIsRecent = false))
    }

    @Test
    fun firstStartCountsNothing() {
        assertNull(decodeSeenExitKeys(null))
        assertEquals(emptyList<Int>(), newExitTraces(null, old))
        assertFalse(previousExit(null, old, bootIsRecent = false).crashed)
    }

    @Test
    fun twoNewTracesWithOneCrashIsACrash() {
        val current = listOf(ExitTrace("300:5", REASON_LOW_MEMORY), ExitTrace("301:4", REASON_CRASH)) + old
        assertEquals(listOf(REASON_LOW_MEMORY, REASON_CRASH), newExitTraces(seen, current))
        assertTrue(previousExit(seen, current, bootIsRecent = false).crashed)
        assertFalse(previousExit(seen, listOf(ExitTrace("300:5", REASON_LOW_MEMORY)) + old, bootIsRecent = false).countsForSafeMode)
    }

    @Test
    fun corruptedMemoryIsAFirstStart() {
        assertNull(decodeSeenExitKeys("100:17,abc"))
        assertNull(decodeSeenExitKeys("1700000000000"))
        assertFalse(previousExit(decodeSeenExitKeys("garbage"), old, bootIsRecent = false).crashed)
    }

    @Test
    fun memoryRoundTrips() {
        assertEquals(seen, decodeSeenExitKeys(encodeSeenExitKeys(old)))
        assertEquals(emptySet<String>(), decodeSeenExitKeys(encodeSeenExitKeys(emptyList())))
        // Aucune trace la première fois, puis un plantage : il compte.
        assertTrue(previousExit(emptySet(), listOf(ExitTrace("1:1", REASON_CRASH)), bootIsRecent = false).crashed)
    }

    @Test
    fun anrIsNotACrashButCountsForSafeModeLongAfterBoot() {
        val current = listOf(ExitTrace("400:1", android.app.ApplicationExitInfo.REASON_ANR)) + old
        assertEquals(PreviousExit(crashed = false, countsForSafeMode = true), previousExit(seen, current, bootIsRecent = false))
        assertEquals(PreviousExit(crashed = false, countsForSafeMode = false), previousExit(seen, current, bootIsRecent = true))
    }

    @Test
    fun memoryIsBounded() {
        val many = (1..40).map { ExitTrace("$it:$it", REASON_EXIT_SELF) }
        assertEquals(MAX_SEEN_EXITS, encodeSeenExitKeys(many).split(',').size)
        // Les traces au-delà de la borne ne sont jamais prises pour nouvelles.
        assertEquals(emptyList<Int>(), newExitTraces(decodeSeenExitKeys(encodeSeenExitKeys(many)), many))
    }
}
