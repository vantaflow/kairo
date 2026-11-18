package com.vantaflow.kairo.files

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class BoxLocaleTest {
    @Test
    fun arabicKeepsWesternDigits() {
        val locale = BoxLocale.locale(Locale("ar", "MA"))
        assertEquals("ar", locale.language)
        assertEquals("12", "%d".format(locale, 12))
        assertEquals(true, "%.1f".format(locale, 1.5).matches(Regex("1.5")))
        assertEquals(Locale.FRANCE, BoxLocale.locale(Locale.FRANCE))
    }

    @Test
    fun sizesInBoxLanguage() {
        val english = listOf("%1\$s B", "%1\$s KB", "%1\$s MB", "%1\$s GB", "%1\$s TB")
        assertEquals("512 B", formatSize(512, english, Locale.US))
        assertEquals("1.5 KB", formatSize(1536, english, Locale.US))
        assertEquals("700 MB", formatSize(700L * 1024 * 1024, english, Locale.US))
    }
}
