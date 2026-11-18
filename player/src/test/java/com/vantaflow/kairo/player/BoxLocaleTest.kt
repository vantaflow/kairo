package com.vantaflow.kairo.player

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class BoxLocaleTest {
    @Test
    fun arabicKeepsWesternDigits() {
        val locale = BoxLocale.locale(Locale("ar", "MA"))
        assertEquals("ar", locale.language)
        assertEquals("12", "%d".format(locale, 12))
        assertEquals(Locale.FRANCE, BoxLocale.locale(Locale.FRANCE))
    }

    @Test
    fun durationInBoxLanguage() {
        assertEquals("1 س 05 د", formatDuration(3_900_000, hours = "%1\$d س %2\$02d د", minutes = "%1\$d د"))
        assertEquals("45 min", formatDuration(2_700_000, hours = "%1\$d h %2\$02d", minutes = "%1\$d min"))
    }
}
