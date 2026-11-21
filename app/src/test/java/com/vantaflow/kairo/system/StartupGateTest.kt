package com.vantaflow.kairo.system

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupGateTest {
    /** Avance l'horloge virtuelle jusqu'à [ms] depuis le début du test. */
    private fun TestScope.at(ms: Long) {
        advanceTimeBy(ms - testScheduler.currentTime)
        runCurrent()
    }

    @Test fun opensThirtySecondsAfterHome() = runTest {
        val gate = StartupGate(backgroundScope)
        at(5_000)
        gate.homeShown()
        at(34_999)
        assertFalse(gate.open.value)
        at(35_000)
        assertTrue(gate.open.value)
    }

    @Test fun opensAfterTwoMinutesWithoutHome() = runTest {
        val gate = StartupGate(backgroundScope)
        at(119_999)
        assertFalse(gate.open.value)
        at(120_000)
        assertTrue(gate.open.value)
    }

    @Test fun blocksRunOnceInOrderAtOpening() = runTest {
        val gate = StartupGate(backgroundScope)
        val ran = mutableListOf<Int>()
        gate.whenOpen { ran += 1 }
        gate.whenOpen { ran += 2 }
        gate.whenOpen { ran += 3 }
        at(119_999)
        assertEquals(emptyList<Int>(), ran)
        at(120_000)
        assertEquals(listOf(1, 2, 3), ran)
        at(600_000)
        assertEquals(listOf(1, 2, 3), ran)
    }

    @Test fun blockAfterOpeningRunsImmediately() = runTest {
        val gate = StartupGate(backgroundScope)
        at(120_000)
        var ran = 0
        gate.whenOpen { ran++ }
        runCurrent()
        assertEquals(1, ran)
        assertEquals(120_000, testScheduler.currentTime)
    }

    @Test fun secondHomeShownDoesNotDelayOpening() = runTest {
        val gate = StartupGate(backgroundScope)
        at(5_000)
        gate.homeShown()
        at(20_000)
        gate.homeShown()
        at(34_999)
        assertFalse(gate.open.value)
        at(35_000)
        assertTrue(gate.open.value)
    }

    @Test fun failingBlockDoesNotStopTheOthers() = runTest {
        val errors = mutableListOf<Throwable>()
        val gate = StartupGate(CoroutineScope(backgroundScope.coroutineContext + CoroutineExceptionHandler { _, e -> errors += e }))
        val ran = mutableListOf<Int>()
        gate.whenOpen { ran += 1 }
        gate.whenOpen { error("panne") }
        gate.whenOpen { ran += 3 }
        at(120_000)
        assertEquals(listOf(1, 3), ran)
        assertEquals(listOf("panne"), errors.map { it.message })
    }

    @Test fun blockThatNeverEndsDoesNotHoldBackTheNext() = runTest {
        val gate = StartupGate(backgroundScope)
        val ran = mutableListOf<Int>()
        gate.whenOpen { ran += 1; kotlinx.coroutines.awaitCancellation() }
        gate.whenOpen { ran += 2 }
        at(120_000)
        assertEquals(listOf(1, 2), ran)
    }

    @Test fun whenOpenDuringOpeningRuns() = runTest {
        val gate = StartupGate(backgroundScope)
        val ran = mutableListOf<Int>()
        gate.whenOpen {
            ran += 1
            gate.whenOpen { ran += 2 }
        }
        at(120_000)
        assertEquals(listOf(1, 2), ran)
    }

    @Test fun homeShownLateStillWaitsThirtySeconds() = runTest {
        val gate = StartupGate(backgroundScope)
        at(110_000)
        gate.homeShown()
        at(139_999)
        assertFalse(gate.open.value)
        at(140_000)
        assertTrue(gate.open.value)
    }

    @Test fun openDurationIsMeasuredFromOpening() = runTest {
        val gate = StartupGate(backgroundScope, now = { testScheduler.currentTime })
        at(119_999)
        assertEquals(null, gate.openForMs())
        at(150_000)
        assertEquals(30_000L, gate.openForMs())
    }

    @Test fun stableOnlyWhenGateAndHomeHaveLastedThirtySeconds() {
        assertFalse(isStable(gateOpenForMs = 29_999, homeForegroundForMs = 60_000))
        assertTrue(isStable(gateOpenForMs = 30_000, homeForegroundForMs = 30_000))
        assertFalse(isStable(gateOpenForMs = 60_000, homeForegroundForMs = 29_999))
        assertFalse(isStable(gateOpenForMs = null, homeForegroundForMs = 600_000))
    }

    @Test fun bootIsRecentBelowTenMinutes() {
        assertTrue(bootIsRecent(599_999))
        assertFalse(bootIsRecent(600_000))
    }
}
