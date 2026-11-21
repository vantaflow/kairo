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

/** Nombre de contenus montrés au plus par la bannière. */
const val BANNER_LIMIT = 5

/** Délai avant de passer au contenu suivant. */
const val BANNER_ADVANCE_MS = 8_000L

/**
 * Contenus de la bannière pour l'onglet [tab] : Ma liste, puis À suivre, puis la première rangée
 * du catalogue qui convient à l'onglet. Films et Séries ne gardent que leur type et pas d'À suivre ;
 * Applis n'a pas de bannière. Sans doublon, sans contenu du catalogue dépourvu d'image de fond.
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
    val seen = mutableSetOf<String>()
    val result = mutableListOf<BannerItem>()

    fun addCatalog(items: List<CatalogItem>) {
        for (item in items) {
            if (result.size >= BANNER_LIMIT) return
            if (type != null && item.type != type) continue
            val backdrop = tmdbImage(item.backdrop ?: continue, "w1280")
            if (seen.add(catalogUri(item))) result += BannerItem(item.toProgram(), item, backdrop)
        }
    }

    if (BannerSource.MyList in sources) addCatalog(myList)
    if (BannerSource.WatchNext in sources && type == null) {
        for (program in watchNext) {
            if (result.size >= BANNER_LIMIT) break
            if (seen.add("watch-next:${program.id}")) result += BannerItem(program, null, null)
        }
    }
    if (BannerSource.Catalog in sources) addCatalog(catalogRowFor(type, rows))
    return result
}

/** Éléments de la première rangée non vide qui convient au type ; une rangée sans type est filtrée. */
private fun catalogRowFor(type: MediaType?, rows: List<CatalogRow>): List<CatalogItem> {
    for (row in rows) {
        val items = when {
            type == null -> row.items
            row.type == type -> row.items
            row.type == null -> row.items.filter { it.type == type }
            else -> emptyList()
        }
        if (items.isNotEmpty()) return items
    }
    return emptyList()
}

/** Index suivant ([delta] = 1) ou précédent ([delta] = -1), en boucle ; 0 s'il n'y a qu'un contenu ou aucun. */
fun stepBanner(index: Int, size: Int, delta: Int): Int =
    if (size <= 1) 0 else Math.floorMod(index + delta, size)
