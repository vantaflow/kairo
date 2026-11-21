package com.vantaflow.kairo.system

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashGuardTest {
    /** Stockage en mémoire et horloge réglable. */
    private var stored: List<Long> = emptyList()
    private var time = 0L
    private val guard = CrashGuard(load = { stored }, save = { stored = it }, now = { time })

    /** Démarrage à [ms] après une sortie plantée ([crash]) ou non. */
    private fun startAt(ms: Long, crash: Boolean = true): Boolean {
        time = ms
        return guard.recordStart(previousExitWasCrash = crash)
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
        assertFalse(broken.recordStart(previousExitWasCrash = true))
        assertEquals(listOf(5_000L), stored)
    }

    @Test
    fun startsAfterLowMemoryKillsNeverEnterSafeMode() {
        assertFalse(startAt(0, crash = false))
        assertFalse(startAt(10_000, crash = false))
        assertFalse(startAt(20_000, crash = false))
        assertFalse(startAt(30_000, crash = false))
        assertEquals(emptyList<Long>(), stored)
    }

    @Test
    fun memoryKillBetweenCrashesNeitherCountsNorClears() {
        assertFalse(startAt(0))
        assertFalse(startAt(10_000))
        assertFalse(startAt(20_000, crash = false))
        assertEquals(listOf(0L, 10_000L), stored)
        assertTrue(startAt(30_000))
    }

    @Test
    fun nonCrashStartDuringOngoingLoopStaysInSafeMode() {
        startAt(0)
        startAt(10_000)
        assertTrue(startAt(20_000))
        // Kairo tué par manque de mémoire pendant le mode sans échec : la boucle n'est pas finie.
        assertTrue(startAt(30_000, crash = false))
        // Hors de la fenêtre : mode normal.
        assertFalse(startAt(200_000, crash = false))
    }

    @Test
    fun onlyCrashesAnrsAndInitFailuresCountAsCrashExits() {
        val crashes = listOf(
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        )
        val others = listOf(
            ApplicationExitInfo.REASON_UNKNOWN,
            ApplicationExitInfo.REASON_EXIT_SELF,
            ApplicationExitInfo.REASON_SIGNALED,
            ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_PERMISSION_CHANGE,
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            ApplicationExitInfo.REASON_USER_REQUESTED,
            ApplicationExitInfo.REASON_USER_STOPPED,
            ApplicationExitInfo.REASON_DEPENDENCY_DIED,
            ApplicationExitInfo.REASON_OTHER,
            ApplicationExitInfo.REASON_FREEZER,
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
            ApplicationExitInfo.REASON_PACKAGE_UPDATED,
        )
        crashes.forEach { assertTrue("raison $it", isCrashExit(it)) }
        others.forEach { assertFalse("raison $it", isCrashExit(it)) }
        assertFalse(isCrashExit(null))
    }
}
