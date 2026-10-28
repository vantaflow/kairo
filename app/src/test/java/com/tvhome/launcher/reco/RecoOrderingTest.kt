package com.tvhome.launcher.reco

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoOrderingTest {
    private fun program(id: Long, pkg: String) = Program(id, pkg, "t$id", null, null, null, null)
    private fun channel(id: Long, pkg: String, name: String, programs: Int = 1) =
        Channel(id, pkg, name, (1..programs).map { program(id * 100 + it, pkg) })

    private val yt = channel(1, "com.youtube", "Tendances")
    private val nf = channel(2, "com.netflix", "Top 10")
    private val shahid = channel(3, "net.shahid", "Séries")
    private val empty = channel(4, "com.netflix", "Vide", programs = 0)

    @Test
    fun appOrderRespectedThenAlphabetical() {
        val rows = orderRows(listOf(yt, shahid, nf), appOrder = listOf("net.shahid"), hiddenChannels = emptySet(), hiddenApps = emptySet())
        assertEquals(listOf(3L, 1L, 2L), rows.map { it.id })
    }

    @Test
    fun hiddenChannelsRemoved() {
        val rows = orderRows(listOf(yt, nf), emptyList(), hiddenChannels = setOf(1L), hiddenApps = emptySet())
        assertEquals(listOf(2L), rows.map { it.id })
    }

    @Test
    fun hiddenAppRemoved() {
        val rows = orderRows(listOf(yt, nf), emptyList(), emptySet(), hiddenApps = setOf("com.netflix"))
        assertEquals(listOf(1L), rows.map { it.id })
    }

    @Test
    fun emptyChannelsRemoved() {
        val rows = orderRows(listOf(empty, nf), emptyList(), emptySet(), emptySet())
        assertEquals(listOf(2L), rows.map { it.id })
    }

    @Test
    fun recoAppsListedOnceInDisplayOrder() {
        val apps = recoApps(listOf(yt, nf, empty, shahid), appOrder = listOf("com.netflix"))
        assertEquals(listOf("com.netflix", "com.youtube", "net.shahid"), apps)
    }

    @Test
    fun progressComputedFromPositionAndDuration() {
        assertEquals(50, progressPercent(positionMs = 30_000, durationMs = 60_000))
        assertEquals(null, progressPercent(positionMs = 0, durationMs = 60_000))
        assertEquals(null, progressPercent(positionMs = 10, durationMs = 0))
    }
}
