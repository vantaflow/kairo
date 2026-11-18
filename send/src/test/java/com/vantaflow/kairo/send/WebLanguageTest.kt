package com.vantaflow.kairo.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class WebLanguageTest {
    @Test
    fun browserLanguageFirstThenBox() {
        assertEquals("ar", pickLanguage("ar-MA,ar;q=0.9,fr;q=0.8", "fr"))
        assertEquals("en", pickLanguage("de-DE,de;q=0.9,en-US;q=0.8", "fr"))
        assertEquals("es", pickLanguage("fr;q=0.5,es;q=0.9", "en"))
        assertEquals("es", pickLanguage("de-DE", "es"))
        assertEquals("fr", pickLanguage(null, "de"))
        assertEquals("en", pickLanguage("*, fr;q=0", "en"))
    }

    @Test
    fun pageTextsAreSafeInScript() {
        val json = i18nJson(mapOf("web_send" to "Envoyer </script> « l'été »"))
        assertFalse(json.contains("<"))
        assertEquals("{\"web_send\":\"Envoyer \\u003c/script> « l'été »\"}", json)
    }

    @Test
    fun arabicKeepsWesternDigits() {
        assertEquals("12", "%d".format(BoxLocale.locale(Locale("ar", "EG")), 12))
    }
}
