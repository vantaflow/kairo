package com.vantaflow.kairo.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLocaleTest {
    private val system = Locale.forLanguageTag("fr-MA")

    @Test
    fun systemLanguageKeepsTheBoxLocale() {
        assertEquals(system, AppLocale.localeFor(AppLocale.SYSTEM, system))
    }

    @Test
    fun chosenLanguageKeepsTheBoxCountry() {
        assertEquals(Locale.forLanguageTag("en-MA"), AppLocale.localeFor("en", system))
    }

    @Test
    fun arabicUsesWesternDigits() {
        val ar = AppLocale.localeFor("ar", system)
        assertEquals("ar", ar.language)
        assertEquals("latn", ar.getUnicodeLocaleType("nu"))
        assertEquals("12", String.format(ar, "%d", 12))
    }

    @Test
    fun catalogLanguageIsLanguageAndCountry() {
        assertEquals("ar-MA", AppLocale.catalogLanguage(AppLocale.localeFor("ar", system)))
        assertEquals("en-US", AppLocale.catalogLanguage(Locale("en")))
        assertEquals("fr-FR", AppLocale.catalogLanguage(Locale.forLanguageTag("fr-FR")))
    }
}
