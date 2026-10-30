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
