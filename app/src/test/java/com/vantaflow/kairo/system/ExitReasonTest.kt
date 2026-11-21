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
        val keys = rememberExitKeys(null, old)
        assertEquals(seen, decodeSeenExitKeys(encodeSeenExitKeys(keys))!!.toSet())
        assertEquals(emptyList<String>(), decodeSeenExitKeys(encodeSeenExitKeys(rememberExitKeys(null, emptyList()))))
        // Aucune trace la première fois, puis un plantage : il compte.
        assertTrue(previousExit(emptySet(), listOf(ExitTrace("1:1", REASON_CRASH)), bootIsRecent = false).crashed)
    }

    @Test
    fun resurrectedTraceIsNotCountedAgain() {
        // Démarrage 1 : la trace A est vue et mémorisée.
        val a = ExitTrace("500:1", REASON_CRASH)
        val memory1 = rememberExitKeys(null, listOf(a))
        // Démarrage 2 : A a été évincée de la liste en mémoire du système ; la mémoire garde A (union).
        val b = ExitTrace("501:2", REASON_EXIT_SELF)
        val memory2 = rememberExitKeys(memory1, listOf(b))
        assertTrue("500:1" in memory2)
        // Coupure de courant : le système relit son vieux fichier, A réapparaît ; elle n'est pas nouvelle.
        assertEquals(emptyList<Int>(), newExitTraces(memory2, listOf(b, a)))
    }

    @Test
    fun anrIsNotACrashButCountsForSafeModeLongAfterBoot() {
        val current = listOf(ExitTrace("400:1", android.app.ApplicationExitInfo.REASON_ANR)) + old
        assertEquals(PreviousExit(crashed = false, countsForSafeMode = true), previousExit(seen, current, bootIsRecent = false))
        assertEquals(PreviousExit(crashed = false, countsForSafeMode = false), previousExit(seen, current, bootIsRecent = true))
    }

    @Test
    fun memoryIsBoundedOldestOut() {
        var memory: List<String>? = null
        for (i in 1..70) memory = rememberExitKeys(memory, listOf(ExitTrace("$i:$i", REASON_EXIT_SELF)))
        assertEquals(MAX_SEEN_EXITS, memory!!.size)
        assertEquals(64, MAX_SEEN_EXITS)
        assertEquals("7:7", memory.first())
        assertEquals("70:70", memory.last())
        // Toute la liste rendue par le système est comparée (pas seulement ses premières traces).
        val many = (1..40).map { ExitTrace("$it:$it", REASON_EXIT_SELF) }
        val known = rememberExitKeys(null, many)
        assertEquals(emptyList<Int>(), newExitTraces(known, many))
        assertEquals(listOf(REASON_CRASH), newExitTraces(known, many + ExitTrace("99:99", REASON_CRASH)))
    }
}
