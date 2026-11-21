package com.vantaflow.kairo.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFallbackTest {
    @Test
    fun withdrawsOnSecondCrashInSafeMode() {
        assertTrue(shouldWithdrawFromHome(previousExitWasCrash = true, previousRunWasSafeMode = true, crashesInSafeMode = 2, otherHomeAvailable = true))
    }

    @Test
    fun firstCrashInSafeModeDoesNotWithdraw() {
        assertFalse(shouldWithdrawFromHome(previousExitWasCrash = true, previousRunWasSafeMode = true, crashesInSafeMode = 1, otherHomeAvailable = true))
    }

    @Test
    fun neverWithdrawsWithoutAnotherHome() {
        assertFalse(shouldWithdrawFromHome(previousExitWasCrash = true, previousRunWasSafeMode = true, crashesInSafeMode = 5, otherHomeAvailable = false))
    }

    @Test
    fun cleanExitNeverWithdraws() {
        assertFalse(shouldWithdrawFromHome(previousExitWasCrash = false, previousRunWasSafeMode = true, crashesInSafeMode = 5, otherHomeAvailable = true))
    }

    @Test
    fun crashInNormalModeNeverWithdraws() {
        assertFalse(shouldWithdrawFromHome(previousExitWasCrash = true, previousRunWasSafeMode = false, crashesInSafeMode = 5, otherHomeAvailable = true))
    }

    @Test
    fun thresholdIsTwoCrashesInSafeMode() {
        assertEquals(2, SAFE_MODE_CRASHES_BEFORE_WITHDRAW)
    }

    @Test
    fun crashInSafeModeIsCountedOnlyAfterSafeModeRun() {
        assertEquals(1, nextSafeModeCrashCount(previousExitWasCrash = true, previousRunWasSafeMode = true, crashesInSafeMode = 0))
        assertEquals(3, nextSafeModeCrashCount(previousExitWasCrash = true, previousRunWasSafeMode = true, crashesInSafeMode = 2))
        assertEquals(2, nextSafeModeCrashCount(previousExitWasCrash = false, previousRunWasSafeMode = true, crashesInSafeMode = 2))
        assertEquals(2, nextSafeModeCrashCount(previousExitWasCrash = true, previousRunWasSafeMode = false, crashesInSafeMode = 2))
    }

    @Test
    fun googleTvHomeCountsAsAnotherHome() {
        val homes = listOf(
            HomeCandidate(KAIRO, "com.vantaflow.kairo.HomeActivity"),
            HomeCandidate("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity"),
        )
        assertTrue(hasOtherHome(homes, KAIRO))
    }

    @Test
    fun kairoAloneIsNotAnotherHome() {
        assertFalse(hasOtherHome(listOf(HomeCandidate(KAIRO, "com.vantaflow.kairo.HomeActivity")), KAIRO))
        assertFalse(hasOtherHome(emptyList(), KAIRO))
    }

    @Test
    fun systemFallbackHomesDoNotCount() {
        val homes = listOf(
            HomeCandidate(KAIRO, "com.vantaflow.kairo.HomeActivity"),
            HomeCandidate("com.android.settings", "com.android.settings.FallbackHome"),
            HomeCandidate("com.android.tv.settings", "com.android.tv.settings.FallbackHome"),
            HomeCandidate("com.android.tv.settings", "com.android.tv.settings.SomeOtherHome"),
            HomeCandidate("com.android.settings", "com.android.settings.CryptKeeper"),
            HomeCandidate("com.example.vendor", "com.example.vendor.FallbackHome"),
        )
        assertFalse(hasOtherHome(homes, KAIRO))
    }

    private companion object {
        const val KAIRO = "com.vantaflow.kairo"
    }
}
