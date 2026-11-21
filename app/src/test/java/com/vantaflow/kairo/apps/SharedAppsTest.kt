package com.vantaflow.kairo.apps

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedAppsTest {
    @Test
    fun burstOfPackageEventsDoesNotReloadForEachEvent() = runTest {
        val changes = MutableSharedFlow<Unit>()
        var loads = 0
        val apps = sharedApps(backgroundScope, { loads++; emptyList() }, changes, StandardTestDispatcher(testScheduler))
        backgroundScope.launch { apps.collect {} }
        runCurrent()
        assertEquals(1, loads)
        // Installation d'un paquet : ajout, remplacement, changement, à quelques millisecondes d'écart.
        repeat(3) {
            changes.emit(Unit)
            advanceTimeBy(50)
        }
        advanceTimeBy(1_000)
        runCurrent()
        val reloads = loads - 1
        assertTrue("$reloads inventaires pour 3 événements", reloads in 1..2)
    }
}
