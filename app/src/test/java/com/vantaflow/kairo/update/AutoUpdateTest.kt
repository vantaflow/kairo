package com.vantaflow.kairo.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoUpdateTest {
    @Test
    fun kairoIsInstalledLastBecauseItRestartsItself() {
        val plan = autoInstallPlan(mapOf("kairo" to "1.26.0", "player" to "1.26.0", "files" to "1.26.0"), emptyMap(), selfId = "kairo")
        assertEquals(listOf("player", "files", "kairo"), plan.map { it.appId })
    }

    @Test
    fun versionThatNeededConfirmationIsNotRetried() {
        val plan = autoInstallPlan(mapOf("kairo" to "1.26.0", "cast" to "1.3.0"), refused = mapOf("cast" to "1.3.0"), selfId = "kairo")
        assertEquals(listOf(AutoInstall("kairo", "1.26.0")), plan)
    }

    @Test
    fun newerVersionIsTriedAgainAfterARefusal() {
        val plan = autoInstallPlan(mapOf("cast" to "1.4.0"), refused = mapOf("cast" to "1.3.0"), selfId = "kairo")
        assertEquals(listOf(AutoInstall("cast", "1.4.0")), plan)
    }
}
