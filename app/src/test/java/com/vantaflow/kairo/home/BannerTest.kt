package com.vantaflow.kairo.home

import com.vantaflow.kairo.catalog.CatalogItem
import com.vantaflow.kairo.catalog.CatalogRow
import com.vantaflow.kairo.catalog.MediaType
import com.vantaflow.kairo.reco.Program
import com.vantaflow.kairo.ui.theme.BannerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerTest {
    private val all = BannerSource.entries.toSet()

    private fun movie(id: Long, backdrop: String? = "/b$id.jpg") =
        CatalogItem(MediaType.Movie, id, "Film $id", "", "2024", 7.0, backdrop, "/p$id.jpg")

    private fun show(id: Long) = CatalogItem(MediaType.Tv, id, "Série $id", "", "2024", 7.0, "/b$id.jpg", "/p$id.jpg")

    private fun resume(id: Long) = Program(id, "com.app", "Reprise $id", null, "content://poster/$id", "intent:#Intent;end", 40)

    private fun row(vararg items: CatalogItem, type: MediaType? = null) = CatalogRow("trending", "Tendances", type, items.toList())

    /** Identifiant du contenu : celui du catalogue, sinon celui du programme « À suivre ». */
    private fun ids(items: List<BannerItem>) = items.map { it.catalog?.id ?: it.program.id }

    @Test
    fun orderIsMyListThenWatchNextThenCatalog() {
        val items = bannerItems(HomeTab.ForYou, all, listOf(movie(1)), listOf(resume(9)), listOf(row(movie(2))))
        assertEquals(listOf(1L, 9L, 2L), ids(items))
    }

    @Test
    fun limitIsFive() {
        val myList = (1L..4L).map { movie(it) }
        val catalog = row(*(11L..14L).map { movie(it) }.toTypedArray())
        val items = bannerItems(null, all, myList, emptyList(), listOf(catalog))
        assertEquals(BANNER_LIMIT, items.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 11L), ids(items))
    }

    @Test
    fun noDuplicateBetweenMyListAndCatalog() {
        val items = bannerItems(HomeTab.ForYou, all, listOf(movie(1)), emptyList(), listOf(row(movie(1), movie(2))))
        assertEquals(listOf(1L, 2L), ids(items))
    }

    @Test
    fun moviesTabKeepsMoviesOnlyAndNoWatchNext() {
        val items = bannerItems(HomeTab.Movies, all, listOf(movie(1), show(2)), listOf(resume(9)), emptyList())
        assertEquals(listOf(1L), ids(items))
    }

    @Test
    fun appsTabIsEmpty() {
        val items = bannerItems(HomeTab.Apps, all, listOf(movie(1)), listOf(resume(9)), listOf(row(movie(2))))
        assertTrue(items.isEmpty())
    }

    @Test
    fun disabledSourceIsSkipped() {
        val items = bannerItems(HomeTab.ForYou, setOf(BannerSource.Catalog), listOf(movie(1)), listOf(resume(9)), listOf(row(movie(2))))
        assertEquals(listOf(2L), ids(items))
    }

    @Test
    fun catalogItemWithoutBackdropIsDropped() {
        val items = bannerItems(HomeTab.ForYou, all, emptyList(), emptyList(), listOf(row(movie(1, backdrop = null), movie(2))))
        assertEquals(listOf(2L), ids(items))
    }

    @Test
    fun myListItemWithoutBackdropIsDropped() {
        val items = bannerItems(HomeTab.ForYou, all, listOf(movie(1, backdrop = null), movie(2)), emptyList(), emptyList())
        assertEquals(listOf(2L), ids(items))
    }

    @Test
    fun rowWithoutAnyBackdropIsSkippedForNextRow() {
        val first = row(movie(1, backdrop = null), movie(2, backdrop = null))
        val second = row(movie(3))
        val items = bannerItems(HomeTab.ForYou, all, emptyList(), emptyList(), listOf(first, second))
        assertEquals(listOf(3L), ids(items))
    }

    @Test
    fun watchNextItemHasNoBackdropAndNoCatalog() {
        val items = bannerItems(HomeTab.ForYou, all, emptyList(), listOf(resume(9)), emptyList())
        assertEquals(1, items.size)
        assertEquals(resume(9), items[0].program)
        assertNull(items[0].backdropUrl)
        assertNull(items[0].catalog)
    }

    @Test
    fun catalogBackdropIsW1280() {
        val items = bannerItems(HomeTab.ForYou, all, emptyList(), emptyList(), listOf(row(movie(2))))
        val url = items.single().backdropUrl
        assertNotNull(url)
        assertTrue(url!!.contains("/w1280/"))
        assertEquals(movie(2), items.single().catalog)
    }

    @Test
    fun stepWrapsAndSingleItemStays() {
        assertEquals(0, stepBanner(4, 5, 1))
        assertEquals(4, stepBanner(0, 5, -1))
        assertEquals(0, stepBanner(0, 1, 1))
        assertEquals(0, stepBanner(0, 0, 1))
    }
}
