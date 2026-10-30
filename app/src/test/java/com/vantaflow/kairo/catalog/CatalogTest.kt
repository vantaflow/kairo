package com.vantaflow.kairo.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CatalogTest {
    private val trending = """
        {"results":[
          {"media_type":"movie","id":1,"title":"Verity","overview":"Résumé","release_date":"2026-10-01","vote_average":6.6,"backdrop_path":"/a.jpg","poster_path":"/p.jpg"},
          {"media_type":"tv","id":2,"name":"Lupin","first_air_date":"2021-01-08","vote_average":7.7,"backdrop_path":"/b.jpg"},
          {"media_type":"person","id":3,"name":"Acteur"},
          {"media_type":"movie","id":4,"title":"Sans image"}
        ]}
    """.trimIndent()

    @Test
    fun `lit films et séries, ignore personnes et titres sans image`() {
        val items = parseItems(trending)
        assertEquals(listOf("Verity", "Lupin"), items.map { it.title })
        assertEquals(MediaType.Tv, items[1].type)
        assertEquals("2021", items[1].year)
    }

    @Test
    fun `plateformes du Maroc, abonnement d'abord`() {
        val json = """{"results":{"MA":{"buy":[{"provider_id":2}],"flatrate":[{"provider_id":8}]},"FR":{"flatrate":[{"provider_id":381}]}}}"""
        assertEquals(listOf(8, 2), parseProviders(json))
    }

    @Test
    fun `Ma liste garde un titre à l'identique`() {
        val item = parseItems(trending).first()
        val back = decodeListItem(encodeListItem(item))
        assertNotNull(back)
        assertEquals(item.copy(overview = ""), back)
    }

    @Test
    fun `catalogue construit une fois puis relu sans réseau`() {
        var calls = 0
        val http = TmdbGet { path -> calls++; if (path.startsWith("trending") || path.startsWith("discover")) trending else """{"results":[]}""" }
        val file = File.createTempFile("catalog", ".json").apply { delete() }
        val installed = setOf("com.netflix.ninja")
        val rows = CatalogRepository(http, file).rows(installed)
        assertTrue(rows.any { it.title == "Films sur Netflix" })
        val before = calls
        val again = CatalogRepository(http, file).rows(installed)
        assertEquals(before, calls)
        assertEquals(rows.map { it.title }, again.map { it.title })
        file.delete()
    }
}
