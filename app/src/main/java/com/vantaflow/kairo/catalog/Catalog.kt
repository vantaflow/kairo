package com.vantaflow.kairo.catalog

import com.vantaflow.kairo.reco.Channel
import com.vantaflow.kairo.reco.Program
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Catalogue « films et séries à voir », comme Google TV : tendances et titres populaires de chaque
 * plateforme installée sur la box, d'après TMDB (base gratuite). Une seule mise à jour par jour,
 * gardée sur la box : l'accueil n'attend jamais le réseau.
 */

enum class MediaType(val path: String) { Movie("movie"), Tv("tv") }

/** Film ou série du catalogue. */
data class CatalogItem(
    val type: MediaType,
    val id: Long,
    val title: String,
    val overview: String,
    val year: String?,
    val rating: Double,
    /** Image paysage (fond) et affiche, chemins TMDB. */
    val backdrop: String?,
    val poster: String?,
    /** Appli de la box où le regarder (rangées par plateforme), sinon null. */
    val packageName: String? = null,
)

/**
 * Plateforme de streaming : identifiants TMDB (une même plateforme en a parfois plusieurs selon
 * les pays, ex. Prime Video) et paquets Android de son appli TV.
 */
data class Provider(val tmdbIds: List<Int>, val name: String, val packages: List<String>)

/** Plateformes avec une appli Android TV, dans le monde entier (la disponibilité dépend du pays). */
val Providers = listOf(
    Provider(listOf(8), "Netflix", listOf("com.netflix.ninja", "com.netflix.mediaclient")),
    Provider(listOf(119, 9), "Prime Video", listOf("com.amazon.amazonvideo.livingroom")),
    Provider(listOf(350), "Apple TV", listOf("com.apple.atve.androidtv.appletv")),
    Provider(listOf(337), "Disney+", listOf("com.disney.disneyplus")),
    Provider(listOf(1899, 384), "Max", listOf("com.wbd.stream", "com.hbo.hbonow")),
    Provider(listOf(531), "Paramount+", listOf("com.cbs.ott")),
    Provider(listOf(15), "Hulu", listOf("com.hulu.livingroomplus")),
    Provider(listOf(386), "Peacock", listOf("com.peacocktv.peacockandroid")),
    Provider(listOf(381), "Canal+", listOf("com.canal.android.canal", "com.canalplus.canalplus")),
    Provider(listOf(1715), "Shahid", listOf("net.mbc.shahidTV", "net.mbc.shahid")),
    Provider(listOf(630), "STARZPLAY", listOf("com.parsifal.starz", "com.starzplay.tv")),
    Provider(listOf(629), "OSN+", listOf("com.osn.go", "com.osnplus.tv")),
    Provider(listOf(158), "Viu", listOf("com.vuclip.viu", "com.viu.tv")),
    Provider(listOf(344), "Rakuten Viki", listOf("com.viki.android")),
    Provider(listOf(35), "Rakuten TV", listOf("tv.wuaki.apptv")),
    Provider(listOf(283), "Crunchyroll", listOf("com.crunchyroll.crunchyroid")),
    Provider(listOf(11), "MUBI", listOf("com.mubi")),
    Provider(listOf(300), "Pluto TV", listOf("tv.pluto.android")),
    Provider(listOf(73), "Tubi", listOf("com.tubitv", "com.tubitv.ott")),
)

/** Plateforme proposée par TMDB pour un pays : identifiant, nom, rang d'affichage. */
data class RegionProvider(val id: Int, val name: String, val priority: Int)

/** Plateformes d'un pays (liste TMDB « watch/providers »), les plus importantes d'abord. */
fun parseRegionProviders(json: String, region: String): List<RegionProvider> = try {
    val a = JSONObject(json).optJSONArray("results") ?: JSONArray()
    (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val name = o.optString("provider_name").ifBlank { return@mapNotNull null }
        val priority = o.optJSONObject("display_priorities")?.optInt(region, 999) ?: o.optInt("display_priority", 999)
        RegionProvider(o.optInt("provider_id"), name, priority)
    }.sortedBy { it.priority }
} catch (_: JSONException) {
    emptyList()
}

private fun normalize(s: String) = s.lowercase().replace("+", "plus").filter { it.isLetterOrDigit() }

/** Noms de plateformes trop vagues pour être reconnus à leur seul nom d'appli. */
private val GenericNames = setOf("tv", "plus", "video", "play", "films", "cinema", "app")

/**
 * Applis de la box qui correspondent aux plateformes du pays : d'abord les paquets connus
 * ([Providers]), puis par nom (« Amazon Prime Video » ↔ appli « Prime Video », « OSN+ » ↔ « OSN+ »…).
 * Ainsi toute plateforme listée par TMDB pour le pays est reconnue, même absente de [Providers].
 */
fun matchProviders(regional: List<RegionProvider>, installed: Map<String, String>): List<Pair<Provider, String>> {
    val result = mutableListOf<Pair<Provider, String>>()
    val usedPackages = mutableSetOf<String>()
    regional.forEach { rp ->
        val known = Providers.firstOrNull { rp.id in it.tmdbIds }
        val pkg = known?.packages?.firstOrNull { it in installed && it !in usedPackages }
            ?: installed.entries.firstOrNull { (p, label) ->
                if (p in usedPackages) return@firstOrNull false
                val l = normalize(label)
                val n = normalize(rp.name)
                l.length >= 4 && l !in GenericNames && (l == n || n.startsWith(l) || n.endsWith(l) || l.startsWith(n) && n.length >= 4)
            }?.key
            ?: return@forEach
        val existing = result.indexOfFirst { it.second == pkg }
        if (existing >= 0) {
            // Même appli pour deux identifiants (ex. Prime Video avec ou sans publicité) : ils sont regroupés.
            val (p, _) = result[existing]
            result[existing] = p.copy(tmdbIds = (p.tmdbIds + rp.id).distinct()) to pkg
            return@forEach
        }
        result += Provider(listOf(rp.id) + known?.tmdbIds.orEmpty().minus(rp.id), known?.name ?: rp.name, listOf(pkg)) to pkg
    }
    // Paquets connus absents de la liste du pays (TMDB incomplet) : gardés quand même.
    Providers.forEach { p ->
        val pkg = p.packages.firstOrNull { it in installed } ?: return@forEach
        if (result.none { it.second == pkg }) result += p to pkg
    }
    return result
}

/** Pays utilisé quand celui de la box est inconnu. */
const val DEFAULT_REGION = "US"

/** Nom du pays dans la langue de la box (« Maroc », « France »…). */
fun countryName(region: String): String = java.util.Locale("", region).displayCountry.ifBlank { region }

/** Image TMDB à une largeur donnée (w300, w780…). */
fun tmdbImage(path: String?, size: String = "w780"): String? = path?.let { "https://image.tmdb.org/t/p/$size$it" }

/** Résultats d'une liste TMDB (tendances, découverte) ; [forced] quand la liste ne précise pas le type. */
fun parseItems(json: String, forced: MediaType? = null, packageName: String? = null): List<CatalogItem> = try {
    val results = JSONObject(json).optJSONArray("results") ?: JSONArray()
    buildList {
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val type = forced ?: when (o.optString("media_type")) {
                "movie" -> MediaType.Movie
                "tv" -> MediaType.Tv
                else -> null
            } ?: continue
            val title = o.optString(if (type == MediaType.Movie) "title" else "name")
            if (title.isBlank()) continue
            val date = o.optString(if (type == MediaType.Movie) "release_date" else "first_air_date")
            add(
                CatalogItem(
                    type = type,
                    id = o.optLong("id"),
                    title = title,
                    overview = o.optString("overview"),
                    year = date.take(4).takeIf { it.length == 4 },
                    rating = o.optDouble("vote_average", 0.0),
                    backdrop = o.optString("backdrop_path").takeIf { it.startsWith("/") },
                    poster = o.optString("poster_path").takeIf { it.startsWith("/") },
                    packageName = packageName,
                ),
            )
        }
    }.filter { it.backdrop != null || it.poster != null }
} catch (_: JSONException) {
    emptyList()
}

/** Plateformes (identifiants TMDB) où un titre se regarde dans le pays [region], abonnement d'abord. */
fun parseProviders(json: String, region: String): List<Int> = try {
    val r = JSONObject(json).optJSONObject("results")?.optJSONObject(region)
    // Abonnement, gratuit ou avec pub seulement : la location et l'achat (boutiques) ne comptent pas.
    if (r == null) emptyList() else listOf("flatrate", "free", "ads").flatMap { kind ->
        val a = r.optJSONArray(kind) ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optInt("provider_id") }
    }.distinct()
} catch (_: JSONException) {
    emptyList()
}

/** Rangée du catalogue (Google TV : « Tendances », « Populaire sur Netflix »…). */
data class CatalogRow(val key: String, val title: String, val type: MediaType?, val items: List<CatalogItem>)

/** Lien interne d'une carte du catalogue : ouvre la fiche dans Kairo. */
const val CATALOG_SCHEME = "kairo-catalog:"

fun catalogUri(item: CatalogItem) = "$CATALOG_SCHEME${item.type.path}/${item.id}"

/** Carte de rangée ; le badge est l'appli où le regarder, sinon « TMDB ». */
fun CatalogItem.toProgram(): Program = Program(
    id = -(type.ordinal * 10_000_000_000L + id + 1),
    packageName = packageName.orEmpty(),
    title = title,
    subtitle = listOfNotNull(year, rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString(" · ").ifBlank { null },
    posterUri = tmdbImage(backdrop ?: poster),
    intentUri = catalogUri(this),
    progressPercent = null,
    description = overview.ifBlank { null },
)

/** Rangée affichable par l'accueil (même carte que les recommandations). */
fun CatalogRow.toChannel(index: Int): Channel = Channel(id = -(1_000L + index), packageName = "", name = title, programs = items.map { it.toProgram() })

/** Encodage d'un titre de « Ma liste » (une ligne). */
fun encodeListItem(i: CatalogItem): String =
    listOf(i.type.path, i.id.toString(), i.title, i.year.orEmpty(), i.backdrop.orEmpty(), i.poster.orEmpty(), i.rating.toString(), i.packageName.orEmpty())
        .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

fun decodeListItem(line: String): CatalogItem? {
    val p = line.split('\t')
    if (p.size < 7) return null
    val type = MediaType.entries.firstOrNull { it.path == p[0] } ?: return null
    return CatalogItem(
        type, p[1].toLongOrNull() ?: return null, p[2], "", p[3].ifBlank { null }, p[6].toDoubleOrNull() ?: 0.0,
        p[4].ifBlank { null }, p[5].ifBlank { null }, p.getOrNull(7)?.ifBlank { null },
    )
}
