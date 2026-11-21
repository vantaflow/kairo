package com.vantaflow.kairo.system

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashGuardTest {
    /** Stockage en mémoire, numéro de démarrage de la box et temps depuis ce démarrage réglables. */
    private var stored: List<BootTime> = emptyList()
    private var time = BootTime(1, 0L)
    private val guard = CrashGuard(load = { stored }, save = { stored = it }, now = { time })

    /** Démarrage à [ms] depuis le démarrage [boot] de la box, après une sortie plantée ([crash]) ou non. */
    private fun startAt(ms: Long, crash: Boolean = true, boot: Int = 1): Boolean {
        time = BootTime(boot, ms)
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
        assertFalse(startAt(700_000))
        assertEquals(listOf(BootTime(1, 700_000L)), stored)
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
        val broken = CrashGuard(load = { throw NumberFormatException("abc") }, save = { stored = it }, now = { BootTime(1, 5_000L) })
        assertFalse(broken.recordStart(previousExitWasCrash = true))
        assertEquals(listOf(BootTime(1, 5_000L)), stored)
    }

    @Test
    fun startsAfterLowMemoryKillsNeverEnterSafeMode() {
        assertFalse(startAt(0, crash = false))
        assertFalse(startAt(10_000, crash = false))
        assertFalse(startAt(20_000, crash = false))
        assertFalse(startAt(30_000, crash = false))
        assertEquals(emptyList<BootTime>(), stored)
    }

    @Test
    fun memoryKillBetweenCrashesNeitherCountsNorClears() {
        assertFalse(startAt(0))
        assertFalse(startAt(10_000))
        assertFalse(startAt(20_000, crash = false))
        assertEquals(listOf(BootTime(1, 0L), BootTime(1, 10_000L)), stored)
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
        assertFalse(startAt(700_000, crash = false))
    }

    @Test
    fun crashBeforeARebootCountsInTheSameLoop() {
        // Plantage, redémarrage de la box, nouveaux plantages : c'est la même boucle.
        assertFalse(startAt(90_000, boot = 1))
        assertFalse(startAt(5_000, boot = 2))
        assertTrue(startAt(10_000, boot = 2))
    }

    @Test
    fun slowLoopWithinTenMinutesEntersSafeMode() {
        // Plantages espacés de 4 min (après l'ouverture de la porte) : toujours une boucle.
        assertFalse(startAt(0))
        assertFalse(startAt(240_000))
        assertTrue(startAt(480_000))
    }

    @Test
    fun crashesOfPreviousBootCountOnlyRightAfterBoot() {
        assertFalse(startAt(3_000_000, boot = 1))
        assertFalse(startAt(3_100_000, boot = 1))
        // Démarrage actuel à 30 min du boot : les plantages de la veille ne comptent plus.
        assertFalse(startAt(1_800_000, boot = 2))
    }

    @Test
    fun crashesOfPreviousBootCountTwoMinutesAfterBoot() {
        assertFalse(startAt(3_000_000, boot = 1))
        assertFalse(startAt(3_100_000, boot = 1))
        assertTrue(startAt(120_000, boot = 2))
    }

    @Test
    fun crashTwoRebootsAgoIsForgotten() {
        assertFalse(startAt(5_000, boot = 1))
        assertFalse(startAt(5_000, boot = 3))
        assertFalse(startAt(6_000, boot = 3))
    }

    @Test
    fun earlierBootWithBiggerElapsedTimeDoesNotCountAsSameBoot() {
        // Numéro de démarrage indisponible (0) : le temps depuis le démarrage repart de zéro au redémarrage.
        assertFalse(startAt(500_000, boot = 0))
        assertFalse(startAt(1_000, boot = 0))
        assertFalse(startAt(2_000, boot = 0))
        assertTrue(startAt(3_000, boot = 0))
    }

    @Test
    fun onlyCrashesAnrsAndInitFailuresCountAsCrashExits() {
        val crashes = listOf(
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
        )
        val others = listOf(
            ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
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

    @Test
    fun anrAndStartTimeoutCountForSafeModeOnlyLongAfterBoot() {
        val always = listOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE)
        val afterBoot = listOf(ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_INITIALIZATION_FAILURE)
        val never = listOf(
            ApplicationExitInfo.REASON_UNKNOWN,
            ApplicationExitInfo.REASON_EXIT_SELF,
            ApplicationExitInfo.REASON_SIGNALED,
            ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_USER_REQUESTED,
            ApplicationExitInfo.REASON_PACKAGE_UPDATED,
        )
        for (recent in listOf(true, false)) {
            always.forEach { assertTrue("raison $it, boot récent $recent", countsForSafeMode(it, recent)) }
            never.forEach { assertFalse("raison $it, boot récent $recent", countsForSafeMode(it, recent)) }
            assertFalse(countsForSafeMode(null, recent))
        }
        afterBoot.forEach {
            assertFalse("raison $it juste après le boot", countsForSafeMode(it, bootIsRecent = true))
            assertTrue("raison $it longtemps après le boot", countsForSafeMode(it, bootIsRecent = false))
        }
    }

    @Test
    fun stableFiveMinutesAfterTheGateOpened() {
        assertFalse(isStable(gateOpenForMs = null))
        assertFalse(isStable(gateOpenForMs = 299_999))
        assertTrue(isStable(gateOpenForMs = 300_000))
    }
}
