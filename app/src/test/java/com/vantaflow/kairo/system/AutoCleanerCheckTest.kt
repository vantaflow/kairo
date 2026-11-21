package com.vantaflow.kairo.system

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoCleanerCheckTest {
    @Test
    fun checkDoesNothingDuringTheFirstTenMinutesAfterBoot() = runTest {
        var asked = false
        val cleaner = AutoCleaner(
            ApplicationProvider.getApplicationContext(),
            enabled = { asked = true; true },
            idleMinutes = { asked = true; 0 },
            installed = { asked = true; listOf("com.example") },
            now = { 599_999L },
        )
        cleaner.onForeground("com.example")
        cleaner.onForeground("com.other")
        cleaner.check()
        assertFalse(asked)
    }
}
