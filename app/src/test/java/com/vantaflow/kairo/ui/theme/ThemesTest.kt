package com.vantaflow.kairo.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemesTest {
    @Test
    fun unknownIdFallsBackToDefault() {
        assertEquals("google-tv", themeById("inconnu").id)
        assertEquals("google-tv", themeById(null).id)
    }

    @Test
    fun knownIdReturned() {
        assertEquals("ambre", themeById("ambre").id)
    }

    @Test
    fun idsAreUnique() {
        assertEquals(Themes.size, Themes.map { it.id }.toSet().size)
    }
}
