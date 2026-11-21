package com.vantaflow.kairo.home

import com.vantaflow.kairo.catalog.CatalogItem
import com.vantaflow.kairo.catalog.CatalogRow
import com.vantaflow.kairo.catalog.MediaType
import com.vantaflow.kairo.catalog.catalogUri
import com.vantaflow.kairo.catalog.tmdbImage
import com.vantaflow.kairo.catalog.toProgram
import com.vantaflow.kairo.reco.Program
import com.vantaflow.kairo.ui.theme.BannerSource

/** Contenu de la bannière ; [catalog] et [backdropUrl] sont nuls pour un contenu « À suivre ». */
data class BannerItem(val program: Program, val catalog: CatalogItem?, val backdropUrl: String?)

/** Identité d'un contenu de la bannière, stable quand la liste se réordonne. */
fun bannerKey(item: BannerItem): String =
    item.catalog?.let(::catalogUri) ?: "watch-next:${item.program.id}"

/** Nombre de contenus montrés au plus par la bannière. */
const val BANNER_LIMIT = 5

/** Délai avant de passer au contenu suivant. */
const val BANNER_ADVANCE_MS = 8_000L

/** Contenus de Ma liste pris en tête, avant À suivre et le catalogue. */
const val BANNER_MY_LIST_FIRST = 2

/** Contenus « À suivre » pris après Ma liste, avant le catalogue. */
const val BANNER_WATCH_NEXT_FIRST = 1

/**
 * Contenus de la bannière pour l'onglet [tab], [BANNER_LIMIT] au plus : d'abord
 * [BANNER_MY_LIST_FIRST] de Ma liste et [BANNER_WATCH_NEXT_FIRST] d'À suivre, puis la première
 * rangée du catalogue qui convient à l'onglet pour compléter ; s'il reste de la place (catalogue
 * trop court ou coupé), le reste de Ma liste puis le reste d'À suivre, dans leur ordre. Films et
 * Séries ne gardent que leur type et pas d'À suivre ; Applis n'a pas de bannière. Une source
 * coupée est sautée. Sans doublon, sans contenu du catalogue dépourvu d'image de fond.
 */
fun bannerItems(
    tab: HomeTab?,
    sources: Set<BannerSource>,
    myList: List<CatalogItem>,
    watchNext: List<Program>,
    rows: List<CatalogRow>,
): List<BannerItem> {
    if (tab == HomeTab.Apps) return emptyList()
    val type = when (tab) {
        HomeTab.Movies -> MediaType.Movie
        HomeTab.Shows -> MediaType.Tv
        else -> null
    }

    fun catalogItems(items: List<CatalogItem>) = items.mapNotNull { item ->
        val backdrop = item.backdrop
        if (backdrop == null || (type != null && item.type != type)) null
        else BannerItem(item.toProgram(), item, tmdbImage(backdrop, "w1280"))
    }

    val mine = if (BannerSource.MyList in sources) catalogItems(myList) else emptyList()
    val next = if (BannerSource.WatchNext in sources && type == null) watchNext.map { BannerItem(it, null, null) } else emptyList()
    val catalog = if (BannerSource.Catalog in sources) catalogItems(catalogRowFor(type, rows)) else emptyList()

    val seen = mutableSetOf<String>()
    val result = mutableListOf<BannerItem>()

    /** Ajoute les contenus nouveaux de [items], [quota] au plus, sans dépasser la limite. */
    fun take(items: List<BannerItem>, quota: Int = BANNER_LIMIT) {
        var added = 0
        for (item in items) {
            if (result.size >= BANNER_LIMIT || added >= quota) return
            if (seen.add(bannerKey(item))) {
                result += item
                added++
            }
        }
    }

    take(mine, BANNER_MY_LIST_FIRST)
    take(next, BANNER_WATCH_NEXT_FIRST)
    take(catalog)
    take(mine)
    take(next)
    return result
}

/**
 * Éléments de la première rangée qui garde au moins un contenu du bon type avec image de fond ;
 * une rangée sans type est filtrée, une rangée d'un autre type est sautée.
 */
private fun catalogRowFor(type: MediaType?, rows: List<CatalogRow>): List<CatalogItem> {
    for (row in rows) {
        if (type != null && row.type != null && row.type != type) continue
        val items = row.items.filter { it.backdrop != null && (type == null || it.type == type) }
        if (items.isNotEmpty()) return items
    }
    return emptyList()
}

/** Index suivant ([delta] = 1) ou précédent ([delta] = -1), en boucle ; 0 s'il n'y a qu'un contenu ou aucun. */
fun stepBanner(index: Int, size: Int, delta: Int): Int =
    if (size <= 1) 0 else Math.floorMod(index + delta, size)
