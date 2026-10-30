package com.vantaflow.kairo.catalog

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Requête TMDB authentifiée (jeton en Bearer) ; null si erreur ou hors ligne. */
fun interface TmdbGet {
    fun get(path: String): String?
}

fun tmdbHttp(token: String) = TmdbGet { path ->
    runCatching {
        val sep = if ('?' in path) '&' else '?'
        val conn = URL("https://api.themoviedb.org/3/$path${sep}language=$CATALOG_LANGUAGE").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

/** Durée de validité du catalogue gardé sur la box. */
private const val CATALOG_TTL_MS = 20L * 3_600_000

/** Titres gardés par rangée (le reste n'est jamais affiché). */
private const val ROW_ITEMS = 16

/**
 * Catalogue TMDB : construit au plus une fois par jour (tendances + une rangée par plateforme
 * installée), gardé dans [file] et relu tel quel au démarrage. Toutes les méthodes sont bloquantes :
 * à appeler hors du fil principal.
 */
class CatalogRepository(
    private val http: TmdbGet?,
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Plateformes installées sur la box (paquet de l'appli trouvée). */
    fun installedProviders(installed: Set<String>): List<Pair<Provider, String>> =
        Providers.mapNotNull { p -> p.packages.firstOrNull { it in installed }?.let { p to it } }

    /** Rangées : celles gardées si récentes et pour les mêmes plateformes, sinon téléchargées. */
    fun rows(installed: Set<String>, force: Boolean = false): List<CatalogRow> {
        val providers = installedProviders(installed)
        val signature = providers.joinToString(",") { it.first.tmdbId.toString() }
        val cached = runCatching { JSONObject(file.readText()) }.getOrNull()
        val fresh = cached != null && cached.optString("sig") == signature && now() - cached.optLong("at") < CATALOG_TTL_MS
        if (cached != null && (fresh && !force || http == null)) return decodeRows(cached)
        val built = download(providers) ?: return cached?.let(::decodeRows).orEmpty()
        runCatching { file.writeText(encodeRows(built, signature).toString()) }
        return built
    }

    /** Plateformes installées où le titre se regarde au Maroc. */
    fun whereToWatch(item: CatalogItem, installed: Set<String>): List<Pair<Provider, String>> {
        val ids = http?.get("${item.type.path}/${item.id}/watch/providers")?.let { parseProviders(it) }.orEmpty()
        return installedProviders(installed).filter { it.first.tmdbId in ids }
    }

    /** Résumé du titre (absent des titres de « Ma liste »). */
    fun overview(item: CatalogItem): String? =
        http?.get("${item.type.path}/${item.id}")?.let { runCatching { JSONObject(it).optString("overview") }.getOrNull() }?.ifBlank { null }

    private fun download(providers: List<Pair<Provider, String>>): List<CatalogRow>? {
        val http = http ?: return null
        fun list(path: String, type: MediaType?, pkg: String? = null) = http.get(path)?.let { parseItems(it, type, pkg) }
        val trending = list("trending/all/week", null) ?: return null
        val rows = mutableListOf(CatalogRow("trending", "Tendances de la semaine", null, trending.take(ROW_ITEMS)))
        list("trending/movie/week", MediaType.Movie)?.let { rows += CatalogRow("movies", "Films tendance", MediaType.Movie, it.take(ROW_ITEMS)) }
        list("trending/tv/week", MediaType.Tv)?.let { rows += CatalogRow("shows", "Séries tendance", MediaType.Tv, it.take(ROW_ITEMS)) }
        providers.forEach { (p, pkg) ->
            val filter = "with_watch_providers=${p.tmdbId}&watch_region=$CATALOG_REGION&sort_by=popularity.desc"
            list("discover/movie?$filter", MediaType.Movie, pkg)?.takeIf { it.isNotEmpty() }?.let {
                rows += CatalogRow("movies-${p.tmdbId}", "Films sur ${p.name}", MediaType.Movie, it.take(ROW_ITEMS))
            }
            list("discover/tv?$filter", MediaType.Tv, pkg)?.takeIf { it.isNotEmpty() }?.let {
                rows += CatalogRow("shows-${p.tmdbId}", "Séries sur ${p.name}", MediaType.Tv, it.take(ROW_ITEMS))
            }
        }
        list("movie/top_rated?region=$CATALOG_REGION", MediaType.Movie)?.let { rows += CatalogRow("top-movies", "Films les mieux notés", MediaType.Movie, it.take(ROW_ITEMS)) }
        list("tv/top_rated", MediaType.Tv)?.let { rows += CatalogRow("top-shows", "Séries les mieux notées", MediaType.Tv, it.take(ROW_ITEMS)) }
        return rows
    }
}

private fun encodeRows(rows: List<CatalogRow>, signature: String) = JSONObject().apply {
    put("at", System.currentTimeMillis())
    put("sig", signature)
    put("rows", JSONArray(rows.map { r ->
        JSONObject().put("key", r.key).put("title", r.title).put("type", r.type?.path.orEmpty())
            .put("items", JSONArray(r.items.map { encodeListItem(it) + "\t" + it.overview.replace('\t', ' ').replace('\n', ' ') }))
    }))
}

private fun decodeRows(o: JSONObject): List<CatalogRow> {
    val rows = o.optJSONArray("rows") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { i ->
        val r = rows.optJSONObject(i) ?: return@mapNotNull null
        val items = r.optJSONArray("items") ?: JSONArray()
        CatalogRow(
            r.optString("key"), r.optString("title"), MediaType.entries.firstOrNull { it.path == r.optString("type") },
            (0 until items.length()).mapNotNull { j ->
                val line = items.optString(j)
                decodeListItem(line)?.copy(overview = line.split('\t').getOrNull(8).orEmpty())
            },
        )
    }
}
