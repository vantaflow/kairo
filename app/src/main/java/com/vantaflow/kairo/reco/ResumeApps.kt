package com.vantaflow.kairo.reco

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlin.math.absoluteValue

/**
 * Cartes « Reprendre » de « À suivre » : les applis de films, séries ou musique ouvertes
 * récemment qui ne publient rien dans Android (Apple TV, Prime Video, Netflix, Shahid…).
 * Ouvrir l'appli la ramène là où elle en était (la plupart reprennent la lecture).
 */

/** Fenêtre de « Reprendre » : au-delà, l'appli n'est plus proposée. */
const val RESUME_WINDOW_MS = 14L * 24 * 3_600_000

/** Nombre maximal de cartes « Reprendre ». */
const val MAX_RESUME = 6

/** Mots des noms de paquet des applis de films, séries et musique, pour celles qui ne déclarent pas leur catégorie. */
private val MediaKeywords = listOf(
    "netflix", "amazonvideo", "primevideo", "appletv", "atve", "disney", "shahid", "viu", "osn", "starz",
    "mbc", "spotify", "anghami", "deezer", "tidal", "smarttube", "youtube", "tivimate", "tvplayer", "plex",
    "jellyfin", "emby", "kodi", "vlc", "canal", "molotov", "dazn", "bein", "hbo", "paramount", "crunchyroll",
    "mubi", "rakuten", "twitch", "soundcloud", "pluto", "tubi", "ultratv", "iptv", "kairo.player", "stremio",
)

/** Appli de films, séries ou musique (d'après le nom du paquet). */
fun isMediaPackage(pkg: String): Boolean = MediaKeywords.any { pkg.contains(it, ignoreCase = true) }

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
fun resumePrograms(context: Context, usage: Map<String, Long>, exclude: Set<String>, now: Long): List<Program> {
    val pm = context.packageManager
    return resumeCandidates(usage, now, exclude + context.packageName, { pkg -> isMediaApp(pm, pkg) }).mapNotNull { (pkg, t) ->
        val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
        val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
        Program(
            id = -(pkg.hashCode().toLong().absoluteValue + 1),
            packageName = pkg,
            title = pm.getApplicationLabel(app).toString(),
            subtitle = "Reprendre · ${formatAgo(now - t)}",
            posterUri = bannerUri(pm, pkg, app, launch.component),
            intentUri = launch.toUri(Intent.URI_INTENT_SCHEME),
            progressPercent = null,
            engagedAt = t,
        )
    }
}

private fun isMediaApp(pm: PackageManager, pkg: String): Boolean {
    if (isMediaPackage(pkg)) return true
    val category = runCatching { pm.getApplicationInfo(pkg, 0).category }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
    return category == ApplicationInfo.CATEGORY_VIDEO || category == ApplicationInfo.CATEGORY_AUDIO
}

/** Bannière TV de l'appli (celle de l'activité, sinon de l'appli), lue par Coil depuis l'autre paquet. */
private fun bannerUri(pm: PackageManager, pkg: String, app: ApplicationInfo, activity: ComponentName?): String? {
    val res = activity?.let { runCatching { pm.getActivityInfo(it, 0).banner }.getOrNull() }?.takeIf { it != 0 }
        ?: app.banner.takeIf { it != 0 }
        ?: return null
    return "android.resource://$pkg/$res"
}
