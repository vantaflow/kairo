package com.vantaflow.kairo.system

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitReasonTest {
    @Test
    fun traceAfterPreviousStartIsThePreviousExit() {
        val traces = listOf(ExitTrace(5_000, ApplicationExitInfo.REASON_CRASH))
        assertEquals(ApplicationExitInfo.REASON_CRASH, previousExitReason(traces, previousStart = 4_000))
    }

    @Test
    fun staleTraceIsIgnored() {
        // Coupure de courant : le process précédent n'a laissé aucune trace, celle-ci date d'avant lui.
        val traces = listOf(ExitTrace(3_000, ApplicationExitInfo.REASON_CRASH))
        assertNull(previousExitReason(traces, previousStart = 4_000))
        assertNull(previousExitReason(listOf(ExitTrace(4_000, ApplicationExitInfo.REASON_CRASH)), previousStart = 4_000))
    }

    @Test
    fun newestTraceWins() {
        val traces = listOf(
            ExitTrace(5_000, ApplicationExitInfo.REASON_CRASH),
            ExitTrace(6_000, ApplicationExitInfo.REASON_LOW_MEMORY),
        )
        assertEquals(ApplicationExitInfo.REASON_LOW_MEMORY, previousExitReason(traces, previousStart = 4_000))
    }

    @Test
    fun unknownPreviousStartAcceptsNewestTrace() {
        val traces = listOf(ExitTrace(3_000, ApplicationExitInfo.REASON_CRASH))
        assertEquals(ApplicationExitInfo.REASON_CRASH, previousExitReason(traces, previousStart = 0))
    }

    @Test
    fun noTraceGivesNoReason() {
        assertNull(previousExitReason(emptyList(), previousStart = 0))
    }
}
