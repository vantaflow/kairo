package com.vantaflow.kairo.network

import android.content.Intent
import android.provider.Settings
import com.vantaflow.kairo.system.SettingsShortcuts
import com.vantaflow.kairo.system.playStoreIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkTest {
    @Test
    fun rssiToLevel() {
        assertEquals(4, signalLevel(-45))
        assertEquals(3, signalLevel(-60))
        assertEquals(2, signalLevel(-70))
        assertEquals(1, signalLevel(-80))
        assertEquals(0, signalLevel(-95))
    }

    @Test
    fun ssidQuotesRemoved() {
        assertEquals("Maison", cleanSsid("\"Maison\""))
        assertEquals("Box 5G", cleanSsid("Box 5G"))
    }

    @Test
    fun unknownSsidIsNull() {
        assertNull(cleanSsid("<unknown ssid>"))
        assertNull(cleanSsid(null))
        assertNull(cleanSsid("\"\""))
    }

    @Test
    fun securityDetectedFromCapabilities() {
        assertEquals(WifiSecurity.Open, securityOf("[ESS]"))
        assertEquals(WifiSecurity.Wpa2, securityOf("[WPA2-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurity.Wpa3, securityOf("[RSN-SAE-CCMP][ESS]"))
        // Mode mixte WPA2/WPA3 : WPA2, compatible avec toutes les box.
        assertEquals(WifiSecurity.Wpa2, securityOf("[RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals(WifiSecurity.Unsupported, securityOf("[WEP][ESS]"))
    }

    @Test
    fun everySettingsShortcutFallsBackToMainSettings() {
        SettingsShortcuts.forEach { assertEquals(it.title, Settings.ACTION_SETTINGS, it.actions.last()) }
        assertEquals(SettingsShortcuts.size, SettingsShortcuts.map { it.title }.toSet().size)
        assertTrue(SettingsShortcuts.any { it.title.contains("CEC") })
    }

    @Test
    fun playStoreOpensAppPage() {
        val intents = playStoreIntents("com.netflix.ninja")
        assertEquals(Intent.ACTION_VIEW, intents.first().action)
        assertEquals("market://details?id=com.netflix.ninja", intents.first().dataString)
        assertEquals("https://play.google.com/store/apps/details?id=com.netflix.ninja", intents.last().dataString)
    }

    @Test
    fun playStoreHomeWithoutPackage() {
        val intents = playStoreIntents(null)
        assertEquals("com.android.vending", intents.first().`package`)
    }
}
