package com.vantaflow.kairo.reco

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlin.math.absoluteValue

/**
 * Cartes « Reprendre » de « À suivre » : les applis de films ou séries ouvertes récemment
 * qui ne publient rien dans Android (Apple TV, Prime Video, Netflix, Shahid…).
 * Ouvrir l'appli la ramène là où elle en était (la plupart reprennent la lecture).
 */

/** Fenêtre de « Reprendre » : au-delà, l'appli n'est plus proposée. */
const val RESUME_WINDOW_MS = 14L * 24 * 3_600_000

/** Durée maximale entre le lancement d'un titre depuis Kairo et la sortie de l'appli pour garder son affiche. */
const val RESUME_TITLE_MS = 6L * 3_600_000

/** Nombre maximal de cartes « Reprendre ». */
const val MAX_RESUME = 6

/** Mots des noms de paquet des applis de films et séries, pour celles qui ne déclarent pas leur catégorie. */
private val VideoKeywords = listOf(
    "netflix", "amazonvideo", "primevideo", "appletv", "atve", "disney", "shahid", "viu", "osn", "starz",
    "mbc", "smarttube", "youtube", "tivimate", "tvplayer", "plex", "jellyfin", "emby", "kodi", "vlc", "canal",
    "molotov", "dazn", "bein", "hbo", "paramount", "crunchyroll", "mubi", "rakuten", "twitch", "pluto", "tubi",
    "ultratv", "iptv", "kairo.player", "stremio",
)

/**
 * Applis de musique et d'audio : traitées comme la radio (pastille « ♪ », petit lecteur, carte de
 * l'écran de veille), jamais en carte « Reprendre ».
 */
private val AudioKeywords = listOf(
    "spotify", "anghami", "deezer", "tidal", "soundcloud", "tvmusic", "music", "radio", "podcast", "audible",
    "napster", "qobuz", "pandora", "iheart", "tunein", "shazam",
)

fun isAudioPackage(pkg: String): Boolean = AudioKeywords.any { pkg.contains(it, ignoreCase = true) }

/** Appli de films ou séries (d'après le nom du paquet) ; les applis audio n'en font pas partie. */
fun isMediaPackage(pkg: String): Boolean = !isAudioPackage(pkg) && VideoKeywords.any { pkg.contains(it, ignoreCase = true) }

/**
 * Applis à proposer, la plus récente en tête : utilisées dans la fenêtre, de films ou de musique,
 * et absentes de [exclude] (applis qui ont déjà leurs propres contenus dans « À suivre »).
 */
fun resumeCandidates(
    usage: Map<String, Long>,
    now: Long,
    exclude: Set<String>,
    isMedia: (String) -> Boolean,
    max: Int = MAX_RESUME,
): List<Pair<String, Long>> =
    usage.entries
        .filter { (pkg, t) -> pkg !in exclude && now - t in 0..RESUME_WINDOW_MS && isMedia(pkg) }
        .sortedByDescending { it.value }
        .take(max)
        .map { it.key to it.value }

/** « à l'instant », « il y a 12 min », « il y a 3 h », « hier », « il y a 4 j ». */
fun formatAgo(elapsedMs: Long): String {
    val min = elapsedMs / 60_000
    return when {
        min < 2 -> "à l'instant"
        min < 60 -> "il y a $min min"
        min < 24 * 60 -> "il y a ${min / 60} h"
        min < 48 * 60 -> "hier"
        else -> "il y a ${min / (24 * 60)} j"
    }
}

/** Cartes « Reprendre » prêtes pour la rangée (lecture du gestionnaire de paquets : à appeler hors du fil principal). */
/**
 * Dernier film ou série lancé depuis Kairo dans chaque appli (« Regarder sur » de la fiche) : la
 * carte « Reprendre » prend son affiche et son titre, puisque l'appli elle-même ne dit rien.
 */
object LaunchedTitles {
    private fun file(context: Context) = java.io.File(context.filesDir, "launched_titles.txt")

    fun record(context: Context, pkg: String, item: com.vantaflow.kairo.catalog.CatalogItem) {
        val now = System.currentTimeMillis()
        val lines = read(context).filterKeys { it != pkg }.map { (p, v) -> "$p\t${v.first}\t${com.vantaflow.kairo.catalog.encodeListItem(v.second)}" }
        runCatching { file(context).writeText((listOf("$pkg\t$now\t${com.vantaflow.kairo.catalog.encodeListItem(item)}") + lines).take(20).joinToString("\n")) }
    }

    /** Appli → (moment du lancement, titre). */
    fun read(context: Context): Map<String, Pair<Long, com.vantaflow.kairo.catalog.CatalogItem>> =
        runCatching { file(context).readLines() }.getOrDefault(emptyList()).mapNotNull { line ->
            val p = line.split('\t', limit = 3)
            if (p.size < 3) return@mapNotNull null
            val item = com.vantaflow.kairo.catalog.decodeListItem(p[2]) ?: return@mapNotNull null
            p[0] to ((p[1].toLongOrNull() ?: return@mapNotNull null) to item)
        }.toMap()
}

fun resumePrograms(context: Context, usage: Map<String, Long>, exclude: Set<String>, now: Long): List<Program> {
    val pm = context.packageManager
    val launched = LaunchedTitles.read(context)
    return resumeCandidates(usage, now, exclude + context.packageName, { pkg -> isMediaApp(pm, pkg) }).mapNotNull { (pkg, t) ->
        val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
        val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
        // Titre lancé depuis Kairo pendant cette séance dans l'appli (avant sa dernière utilisation).
        val title = launched[pkg]?.takeIf { (at, _) -> at <= t && t - at < RESUME_TITLE_MS }?.second
        Program(
            id = -(pkg.hashCode().toLong().absoluteValue + 1),
            packageName = pkg,
            title = title?.title ?: pm.getApplicationLabel(app).toString(),
            subtitle = "Reprendre · ${formatAgo(now - t)}",
            posterUri = title?.let { com.vantaflow.kairo.catalog.tmdbImage(it.backdrop ?: it.poster) } ?: bannerUri(pm, pkg, app, launch.component),
            intentUri = launch.toUri(Intent.URI_INTENT_SCHEME),
            progressPercent = null,
            engagedAt = t,
        )
    }
}

private fun isMediaApp(pm: PackageManager, pkg: String): Boolean {
    if (isAudioPackage(pkg)) return false
    if (isMediaPackage(pkg)) return true
    val category = runCatching { pm.getApplicationInfo(pkg, 0).category }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
    return category == ApplicationInfo.CATEGORY_VIDEO
}

/** Bannière TV de l'appli (celle de l'activité, sinon de l'appli), lue par Coil depuis l'autre paquet. */
private fun bannerUri(pm: PackageManager, pkg: String, app: ApplicationInfo, activity: ComponentName?): String? {
    val res = activity?.let { runCatching { pm.getActivityInfo(it, 0).banner }.getOrNull() }?.takeIf { it != 0 }
        ?: app.banner.takeIf { it != 0 }
        ?: return null
    return "android.resource://$pkg/$res"
}
