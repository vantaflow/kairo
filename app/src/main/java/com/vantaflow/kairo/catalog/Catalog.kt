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

/** Plateforme de streaming : identifiant TMDB et paquets Android de son appli TV. */
data class Provider(val tmdbId: Int, val name: String, val packages: List<String>)

/** Plateformes disponibles au Maroc (et alentour) avec une appli Android TV. */
val Providers = listOf(
    Provider(8, "Netflix", listOf("com.netflix.ninja", "com.netflix.mediaclient")),
    Provider(119, "Prime Video", listOf("com.amazon.amazonvideo.livingroom")),
    Provider(350, "Apple TV", listOf("com.apple.atve.androidtv.appletv")),
    Provider(337, "Disney+", listOf("com.disney.disneyplus")),
    Provider(1715, "Shahid", listOf("net.mbc.shahidTV", "net.mbc.shahid")),
    Provider(630, "STARZPLAY", listOf("com.parsifal.starz", "com.starzplay.tv")),
    Provider(629, "OSN+", listOf("com.osn.go", "com.osnplus.tv")),
    Provider(344, "Rakuten Viki", listOf("com.viki.android")),
    Provider(283, "Crunchyroll", listOf("com.crunchyroll.crunchyroid")),
    Provider(11, "MUBI", listOf("com.mubi")),
)

/** Région des disponibilités (plateformes) et langue des titres. */
const val CATALOG_REGION = "MA"
const val CATALOG_LANGUAGE = "fr-FR"

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

/** Plateformes (identifiants TMDB) où un titre se regarde dans [region], abonnement d'abord. */
fun parseProviders(json: String, region: String = CATALOG_REGION): List<Int> = try {
    val r = JSONObject(json).optJSONObject("results")?.optJSONObject(region)
    if (r == null) emptyList() else listOf("flatrate", "free", "ads", "rent", "buy").flatMap { kind ->
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
