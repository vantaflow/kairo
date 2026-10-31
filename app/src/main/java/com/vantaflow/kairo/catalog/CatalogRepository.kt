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

/** [language] : langue des titres et résumés (« fr-FR », « ar-MA »…). */
fun tmdbHttp(token: String, language: () -> String) = TmdbGet { path ->
    runCatching {
        val sep = if ('?' in path) '&' else '?'
        val conn = URL("https://api.themoviedb.org/3/$path${sep}language=${language()}").openConnection() as HttpURLConnection
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
    /** Pays de la box (code ISO : « MA », « FR »…) et langue : les deux changent le catalogue. */
    val region: () -> String = { DEFAULT_REGION },
    private val language: () -> String = { "fr-FR" },
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Plateformes du pays reconnues parmi les applis de la box (relues avec le catalogue). */
    @Volatile private var matched: List<Pair<Provider, String>> = emptyList()

    /** Plateformes du pays (TMDB, films et séries) rapprochées des applis installées ([installed] : paquet → nom). */
    private fun providersFor(installed: Map<String, String>, country: String): List<Pair<Provider, String>> {
        val regional = listOf("movie", "tv").flatMap { t ->
            http?.get("watch/providers/$t?watch_region=$country")?.let { parseRegionProviders(it, country) }.orEmpty()
        }.distinctBy { it.id }.sortedBy { it.priority }
        return matchProviders(regional, installed)
    }

    /**
     * Rangées : celles gardées si récentes, pour le même pays, la même langue et les mêmes applis ;
     * sinon téléchargées (plateformes du pays, puis leurs titres).
     */
    fun rows(installed: Map<String, String>, force: Boolean = false): List<CatalogRow> {
        val country = region()
        val signature = "v4/" + country + "/" + language() + "/" + installed.keys.sorted().hashCode()
        val cached = runCatching { JSONObject(file.readText()) }.getOrNull()
        val fresh = cached != null && cached.optString("sig") == signature && now() - cached.optLong("at") < CATALOG_TTL_MS
        if (cached != null && (fresh && !force || http == null)) {
            matched = decodeProviders(cached)
            return decodeRows(cached)
        }
        val providers = providersFor(installed, country)
        val built = download(providers, country) ?: return cached?.let { matched = decodeProviders(it); decodeRows(it) }.orEmpty()
        matched = providers
        runCatching { file.writeText(encodeRows(built, signature, providers).toString()) }
        return built
    }

    /** Applis de la box où le titre se regarde dans le pays de la box. */
    fun whereToWatch(item: CatalogItem): List<Pair<Provider, String>> {
        val ids = http?.get("${item.type.path}/${item.id}/watch/providers")?.let { parseProviders(it, region()) }.orEmpty()
        return matched.filter { (p, _) -> p.tmdbIds.any { it in ids } }
    }

    /** Résumé du titre (absent des titres de « Ma liste »). */
    fun overview(item: CatalogItem): String? =
        http?.get("${item.type.path}/${item.id}")?.let { runCatching { JSONObject(it).optString("overview") }.getOrNull() }?.ifBlank { null }

    private fun download(providers: List<Pair<Provider, String>>, country: String): List<CatalogRow>? {
        val http = http ?: return null
        fun list(path: String, type: MediaType?, pkg: String? = null) = http.get(path)?.let { parseItems(it, type, pkg) }
        // Inclus dans l'abonnement ou gratuits seulement (pas de location ni d'achat).
        val included = "with_watch_monetization_types=flatrate|free|ads"
        // Titres de chaque plateforme ; seules celles qui en ont au moins un comptent ensuite.
        val perProvider = providers.mapNotNull { (p, pkg) ->
            val filter = "with_watch_providers=${p.tmdbIds.joinToString("|")}&watch_region=$country&$included&sort_by=popularity.desc"
            val m = list("discover/movie?$filter", MediaType.Movie, pkg).orEmpty()
            val t = list("discover/tv?$filter", MediaType.Tv, pkg).orEmpty()
            if (m.isEmpty() && t.isEmpty()) null else Triple(p, m, t)
        }
        // Tendances et mieux notés : seulement les titres disponibles dans les applis de la box, pour
        // ce pays (toutes plateformes confondues si aucune n'est reconnue).
        val all = perProvider.flatMap { it.first.tmdbIds }.distinct().joinToString("|")
        val mine = if (all.isEmpty()) "" else "with_watch_providers=$all&watch_region=$country&$included&"
        val suffix = when (perProvider.size) {
            0 -> ""
            1 -> " sur ${perProvider.first().first.name}"
            else -> " sur vos applis"
        }
        val movies = list("discover/movie?${mine}sort_by=popularity.desc", MediaType.Movie) ?: return null
        val shows = list("discover/tv?${mine}sort_by=popularity.desc", MediaType.Tv).orEmpty()
        val mixed = movies.zip(shows).flatMap { (m, s) -> listOf(m, s) } + movies.drop(shows.size) + shows.drop(movies.size)
        val rows = mutableListOf(CatalogRow("trending", "Tendances$suffix", null, mixed.take(ROW_ITEMS)))
        rows += CatalogRow("movies", "Films tendance$suffix", MediaType.Movie, movies.take(ROW_ITEMS))
        if (shows.isNotEmpty()) rows += CatalogRow("shows", "Séries tendance$suffix", MediaType.Tv, shows.take(ROW_ITEMS))
        // Une rangée par plateforme, seulement s'il y en a plusieurs (sinon elle répète les tendances).
        if (perProvider.size > 1) perProvider.forEach { (p, m, t) ->
            if (m.isNotEmpty()) rows += CatalogRow("movies-${p.tmdbIds.first()}", "Films sur ${p.name}", MediaType.Movie, m.take(ROW_ITEMS))
            if (t.isNotEmpty()) rows += CatalogRow("shows-${p.tmdbIds.first()}", "Séries sur ${p.name}", MediaType.Tv, t.take(ROW_ITEMS))
        }
        val top = "sort_by=vote_average.desc&vote_count.gte=1000"
        list("discover/movie?$mine$top", MediaType.Movie)?.takeIf { it.isNotEmpty() }?.let { rows += CatalogRow("top-movies", "Films les mieux notés$suffix", MediaType.Movie, it.take(ROW_ITEMS)) }
        list("discover/tv?$mine$top", MediaType.Tv)?.takeIf { it.isNotEmpty() }?.let { rows += CatalogRow("top-shows", "Séries les mieux notées$suffix", MediaType.Tv, it.take(ROW_ITEMS)) }
        return rows
    }
}

private fun encodeRows(rows: List<CatalogRow>, signature: String, providers: List<Pair<Provider, String>>) = JSONObject().apply {
    put("at", System.currentTimeMillis())
    put("sig", signature)
    put("providers", JSONArray(providers.map { (p, pkg) ->
        JSONObject().put("name", p.name).put("pkg", pkg).put("ids", JSONArray(p.tmdbIds))
    }))
    put("rows", JSONArray(rows.map { r ->
        JSONObject().put("key", r.key).put("title", r.title).put("type", r.type?.path.orEmpty())
            .put("items", JSONArray(r.items.map { encodeListItem(it) + "\t" + it.overview.replace('\t', ' ').replace('\n', ' ') }))
    }))
}

private fun decodeProviders(o: JSONObject): List<Pair<Provider, String>> {
    val a = o.optJSONArray("providers") ?: return emptyList()
    return (0 until a.length()).mapNotNull { i ->
        val p = a.optJSONObject(i) ?: return@mapNotNull null
        val ids = p.optJSONArray("ids") ?: JSONArray()
        val pkg = p.optString("pkg").ifBlank { return@mapNotNull null }
        Provider((0 until ids.length()).map { ids.optInt(it) }, p.optString("name"), listOf(pkg)) to pkg
    }
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
