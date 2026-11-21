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
        assertTrue(previousExitWasCrash(seen, current))
    }

    @Test
    fun noNewTraceCountsNothing() {
        // Coupure de courant : aucune trace écrite, l'ancien plantage n'est pas recompté.
        assertEquals(emptyList<Int>(), newExitTraces(seen, old))
        assertFalse(previousExitWasCrash(seen, old))
    }

    @Test
    fun firstStartCountsNothing() {
        assertNull(decodeSeenExitKeys(null))
        assertEquals(emptyList<Int>(), newExitTraces(null, old))
        assertFalse(previousExitWasCrash(null, old))
    }

    @Test
    fun twoNewTracesWithOneCrashIsACrash() {
        val current = listOf(ExitTrace("300:5", REASON_LOW_MEMORY), ExitTrace("301:4", REASON_CRASH)) + old
        assertEquals(listOf(REASON_LOW_MEMORY, REASON_CRASH), newExitTraces(seen, current))
        assertTrue(previousExitWasCrash(seen, current))
        assertFalse(previousExitWasCrash(seen, listOf(ExitTrace("300:5", REASON_LOW_MEMORY)) + old))
    }

    @Test
    fun corruptedMemoryIsAFirstStart() {
        assertNull(decodeSeenExitKeys("100:17,abc"))
        assertNull(decodeSeenExitKeys("1700000000000"))
        assertFalse(previousExitWasCrash(decodeSeenExitKeys("garbage"), old))
    }

    @Test
    fun memoryRoundTrips() {
        assertEquals(seen, decodeSeenExitKeys(encodeSeenExitKeys(old)))
        assertEquals(emptySet<String>(), decodeSeenExitKeys(encodeSeenExitKeys(emptyList())))
        // Aucune trace la première fois, puis un plantage : il compte.
        assertTrue(previousExitWasCrash(emptySet(), listOf(ExitTrace("1:1", REASON_CRASH))))
    }

    @Test
    fun memoryIsBounded() {
        val many = (1..40).map { ExitTrace("$it:$it", REASON_EXIT_SELF) }
        assertEquals(MAX_SEEN_EXITS, encodeSeenExitKeys(many).split(',').size)
        // Les traces au-delà de la borne ne sont jamais prises pour nouvelles.
        assertEquals(emptyList<Int>(), newExitTraces(decodeSeenExitKeys(encodeSeenExitKeys(many)), many))
    }
}
