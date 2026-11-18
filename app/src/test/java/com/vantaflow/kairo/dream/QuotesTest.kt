package com.vantaflow.kairo.dream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class QuotesTest {

    private val translations = mapOf("en" to QuotesEn, "es" to QuotesEs, "ar" to QuotesAr)

    @Test
    fun `les listes traduites ont la même taille que la liste française`() {
        translations.forEach { (lang, list) -> assertEquals("taille $lang", Quotes.size, list.size) }
    }

    @Test
    fun `aucune citation ni auteur vide`() {
        (translations + ("fr" to Quotes)).forEach { (lang, list) ->
            list.forEachIndexed { i, q ->
                assertTrue("texte vide $lang[$i]", q.text.isNotBlank())
                assertTrue("auteur vide $lang[$i]", q.author.isNotBlank())
            }
        }
    }

    @Test
    fun `la citation du jour occupe la même position dans chaque langue`() {
        var date = LocalDate.of(2026, 1, 1)
        repeat(400) {
            val i = quoteIndexOfDay(date)
            assertSame(Quotes[i], quoteOfDay(date, "fr"))
            translations.forEach { (lang, list) -> assertSame(list[i], quoteOfDay(date, lang)) }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `le français garde l'ordre quotidien d'origine`() {
        val ancien = Quotes.distinctBy { it.text }.shuffled(java.util.Random(2026))
        val date = LocalDate.of(2026, 11, 18)
        val attendu = ancien[Math.floorMod(date.toEpochDay(), ancien.size.toLong()).toInt()]
        assertEquals(attendu, quoteOfDay(date, "fr"))
    }

    @Test
    fun `une langue inconnue retombe sur le français`() {
        val date = LocalDate.of(2026, 3, 14)
        assertSame(Quotes, quotesFor("de"))
        assertEquals(quoteOfDay(date, "fr"), quoteOfDay(date, "de"))
        assertEquals(quoteOfDay(date, "fr"), quoteOfDay(date, ""))
    }

    @Test
    fun `par défaut la langue courante de l'appli est utilisée`() {
        val avant = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-u-nu-latn"))
            val date = LocalDate.of(2026, 6, 1)
            assertEquals(quoteOfDay(date, "ar"), quoteOfDay(date))
        } finally {
            Locale.setDefault(avant)
        }
    }
}
