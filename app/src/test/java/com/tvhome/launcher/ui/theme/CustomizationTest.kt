package com.tvhome.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CustomizationTest {
    private val custom = LauncherTheme(CUSTOM_THEME_ID, "Personnalisé", 0xFFFF0000, 0xFF00FF00, 20, 1.15f)

    @Test
    fun customIdUsesCustomTheme() {
        assertSame(custom, themeFor(CUSTOM_THEME_ID, custom))
    }

    @Test
    fun presetIdIgnoresCustomTheme() {
        assertEquals("ambre", themeFor("ambre", custom).id)
    }

    @Test
    fun layoutDefaultsMatchMockup() {
        val layout = HomeLayout()
        assertEquals(true, layout.showClock)
        assertEquals(true, layout.showDate)
        assertEquals(72, layout.clockSizeSp)
        assertEquals(192, layout.tileWidthDp)
    }
}
