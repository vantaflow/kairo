package com.vantaflow.kairo.ultratv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UltraTvRowModelTest {
    private val channel = UtvChannel(
        providerId = 1L, remoteId = "r1", name = "Al Aoula", number = "1", logo = null,
        nowTitle = "Journal", nowStartMs = 0L, nowEndMs = 0L, nowImage = null,
        nextTitle = null, nextStartMs = 0L, nextEndMs = 0L,
        deepLink = "ultratv://channel/1", locked = false,
    )
    private val installed = UtvState(pkg = ULTRA_TV_RELEASE, channels = listOf(channel))

    @Test
    fun shownWhenInstalledAndAllowed() {
        assertTrue(utvRowVisible(installed, kairoKids = false, shown = true))
    }

    @Test
    fun shownWithoutChannels() {
        // Raccourcis seuls : la rangée existe dès qu'Ultra TV est installée.
        assertTrue(utvRowVisible(UtvState(pkg = ULTRA_TV_DEBUG), kairoKids = false, shown = true))
    }

    @Test
    fun hiddenWhenSettingOff() {
        assertFalse(utvRowVisible(installed, kairoKids = false, shown = false))
    }

    @Test
    fun hiddenWhenNotInstalled() {
        assertFalse(utvRowVisible(UtvState(), kairoKids = false, shown = true))
        // Même avec des chaînes restées en mémoire, sans paquet rien n'est montré.
        assertFalse(utvRowVisible(UtvState(pkg = null, channels = listOf(channel)), kairoKids = false, shown = true))
    }

    @Test
    fun hiddenForKairoKidsProfile() {
        assertFalse(utvRowVisible(installed, kairoKids = true, shown = true))
    }

    @Test
    fun hiddenForUltraTvKidsProfile() {
        assertFalse(utvRowVisible(installed.copy(kids = true), kairoKids = false, shown = true))
        assertFalse(utvRowVisible(UtvState(pkg = ULTRA_TV_RELEASE, kids = true), kairoKids = false, shown = true))
    }
}
