package com.tvhome.launcher.wallpaper

import com.tvhome.launcher.net.HttpGet
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

/** Thèmes d'images proposés : libellé affiché → requête Pixabay. */
val PixabayThemes: List<Pair<String, String>> = listOf(
    "Nature" to "nature landscape",
    "Ville" to "city night",
    "Montagne" to "mountains",
    "Mer" to "ocean beach",
    "Espace" to "space galaxy",
    "Abstrait" to "abstract background",
)

/** Recherche d'images Pixabay (paysage, ≥ 1920 px, filtrées). Liste vide en cas d'erreur. */
class PixabayClient(private val http: HttpGet, private val keyProvider: () -> String) {
    fun search(query: String): List<String> {
        val key = keyProvider().trim()
        if (key.isEmpty()) return emptyList()
        val url = "https://pixabay.com/api/?key=${enc(key)}&q=${enc(query)}" +
            "&image_type=photo&orientation=horizontal&min_width=1920&safesearch=true&per_page=50"
        val body = http.get(url) ?: return emptyList()
        return try {
            val hits = JSONObject(body).optJSONArray("hits") ?: return emptyList()
            (0 until hits.length()).mapNotNull { hits.optJSONObject(it)?.optString("largeImageURL")?.takeIf(String::isNotEmpty) }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

/** Liste d'images en cours, position de la prochaine et date de récupération de la liste. */
data class RotationState(
    val urls: List<String>,
    val nextIndex: Int,
    val fetchedAtMs: Long = 0L,
    /** Thème Pixabay de la liste ; une liste d'un autre thème est ignorée. */
    val query: String = "",
)

/** Les liens d'images Pixabay expirent après 24 h : on renouvelle la liste avant. */
private const val LIST_MAX_AGE_MS = 20 * 60 * 60 * 1000L

/**
 * Fait tourner les fonds : recharge une liste quand la précédente est épuisée ;
 * hors ligne, repart du début de la dernière liste connue.
 */
class WallpaperRotator(
    private val fetch: (String) -> List<String>,
    private val store: (RotationState) -> Unit,
    private val load: () -> RotationState,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Synchronisé : « Image suivante » et la rotation automatique peuvent appeler en même temps. */
    @Synchronized
    fun next(query: String): String? {
        var state = load().let { if (it.query.isNotEmpty() && it.query != query) RotationState(emptyList(), 0) else it }
        val expired = now() - state.fetchedAtMs > LIST_MAX_AGE_MS
        if (state.nextIndex >= state.urls.size || expired) {
            val fresh = fetch(query)
            state = when {
                fresh.isNotEmpty() -> RotationState(fresh, 0, now(), query)
                state.nextIndex < state.urls.size -> state
                state.urls.isNotEmpty() -> state.copy(nextIndex = 0)
                else -> return null
            }
        }
        val url = state.urls[state.nextIndex]
        store(state.copy(nextIndex = state.nextIndex + 1))
        return url
    }
}
