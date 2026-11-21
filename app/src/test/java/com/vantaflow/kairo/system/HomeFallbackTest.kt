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

    // Démarrage : l'indicateur de retrait fait foi, l'état réel des composants est réaligné dessus.

    @Test
    fun withdrawnAndConsistentStaysWithdrawn() {
        assertEquals(
            StartupPlan(stop = true, restore = false, homeEnabled = null, recoveryEnabled = null),
            planStartup(withdrawn = true, homeEnabled = false, recoveryEnabled = true, otherHomeAvailable = true),
        )
    }

    @Test
    fun withdrawnButComponentsNotWrittenReappliesThem() {
        // Coupure de courant juste après le retrait : l'état des composants n'était pas encore sur disque.
        assertEquals(
            StartupPlan(stop = true, restore = false, homeEnabled = false, recoveryEnabled = true),
            planStartup(withdrawn = true, homeEnabled = true, recoveryEnabled = false, otherHomeAvailable = true),
        )
        assertEquals(
            StartupPlan(stop = true, restore = false, homeEnabled = false, recoveryEnabled = null),
            planStartup(withdrawn = true, homeEnabled = true, recoveryEnabled = true, otherHomeAvailable = true),
        )
        assertEquals(
            StartupPlan(stop = true, restore = false, homeEnabled = null, recoveryEnabled = true),
            planStartup(withdrawn = true, homeEnabled = false, recoveryEnabled = false, otherHomeAvailable = true),
        )
    }

    @Test
    fun withdrawnWithoutAnotherHomeRestoresWhateverTheComponents() {
        // L'autre accueil a disparu après le retrait : Kairo reprend l'accueil.
        for (home in listOf(true, false)) for (recovery in listOf(true, false)) {
            assertEquals(
                StartupPlan(stop = false, restore = true, homeEnabled = null, recoveryEnabled = null),
                planStartup(withdrawn = true, homeEnabled = home, recoveryEnabled = recovery, otherHomeAvailable = false),
            )
        }
    }

    @Test
    fun notWithdrawnAndConsistentChangesNothing() {
        for (other in listOf(true, false)) {
            assertEquals(
                StartupPlan(stop = false, restore = false, homeEnabled = null, recoveryEnabled = null),
                planStartup(withdrawn = false, homeEnabled = true, recoveryEnabled = false, otherHomeAvailable = other),
            )
        }
    }

    @Test
    fun notWithdrawnButHomeDisabledGivesKairoItsHomeBack() {
        // Remise en place ou retrait interrompus : sans indicateur, l'accueil de Kairo est rendu.
        for (other in listOf(true, false)) {
            assertEquals(
                StartupPlan(stop = false, restore = false, homeEnabled = true, recoveryEnabled = false),
                planStartup(withdrawn = false, homeEnabled = false, recoveryEnabled = true, otherHomeAvailable = other),
            )
            assertEquals(
                StartupPlan(stop = false, restore = false, homeEnabled = true, recoveryEnabled = null),
                planStartup(withdrawn = false, homeEnabled = false, recoveryEnabled = false, otherHomeAvailable = other),
            )
            assertEquals(
                StartupPlan(stop = false, restore = false, homeEnabled = null, recoveryEnabled = false),
                planStartup(withdrawn = false, homeEnabled = true, recoveryEnabled = true, otherHomeAvailable = other),
            )
        }
    }

    private companion object {
        const val KAIRO = "com.vantaflow.kairo"
    }
}
