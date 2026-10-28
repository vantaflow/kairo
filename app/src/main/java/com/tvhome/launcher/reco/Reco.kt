package com.tvhome.launcher.reco

import android.content.Intent
import java.net.URISyntaxException

/** Contenu proposé par une appli (chaîne de recommandations ou « À suivre »). */
data class Program(
    val id: Long,
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val posterUri: String?,
    val intentUri: String?,
    val progressPercent: Int?,
    val description: String? = null,
)

/** Chaîne publiée par une appli : une rangée sur l'accueil. */
data class Channel(
    val id: Long,
    val packageName: String,
    val name: String,
    val programs: List<Program>,
)

/**
 * Rangées affichées : chaînes non vides et non masquées, applis dans l'ordre choisi
 * puis les autres par nom de chaîne.
 */
fun orderRows(
    channels: List<Channel>,
    appOrder: List<String>,
    hiddenChannels: Set<Long>,
    hiddenApps: Set<String>,
): List<Channel> {
    val rank = appOrder.withIndex().associate { it.value to it.index }
    return channels
        .filter { it.programs.isNotEmpty() && it.id !in hiddenChannels && it.packageName !in hiddenApps }
        .sortedWith(compareBy<Channel> { rank[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.name.lowercase() })
}

/** Applis qui publient des recommandations, dans l'ordre d'affichage (pour les réglages). */
fun recoApps(channels: List<Channel>, appOrder: List<String>): List<String> {
    val withPrograms = channels.filter { it.programs.isNotEmpty() }.map { it.packageName }.distinct()
    val ordered = appOrder.filter { it in withPrograms }
    return ordered + withPrograms.filterNot { it in ordered }.sorted()
}

fun progressPercent(positionMs: Long, durationMs: Long): Int? =
    if (positionMs <= 0 || durationMs <= 0) null else (positionMs * 100 / durationMs).toInt().coerceIn(0, 100)

private const val OWN_PACKAGE = "com.tvhome.launcher"

/**
 * Intent publié par l'appli pour ouvrir le contenu, nettoyé : ni sélecteur, ni droits d'accès
 * aux URI, ni cible vers le launcher lui-même. Null si absent ou inutilisable.
 */
fun parseProgramIntent(program: Program): Intent? {
    val uri = program.intentUri?.takeIf { it.isNotBlank() } ?: return null
    val intent = try {
        Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
    } catch (_: URISyntaxException) {
        return null
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (intent.component?.packageName == OWN_PACKAGE || intent.`package` == OWN_PACKAGE) return null
    intent.selector = null
    intent.flags = intent.flags and (
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        ).inv()
    return intent
}

/** Intent qui ouvre le contenu dans son appli ; à défaut, l'appli elle-même. */
fun programLaunchIntent(program: Program, fallback: (String) -> Intent?): Intent? =
    parseProgramIntent(program) ?: fallback(program.packageName)

/** Lance le contenu ; si son intent ne mène nulle part, lance l'appli d'origine. */
fun launchProgram(program: Program, fallback: (String) -> Intent?, start: (Intent) -> Boolean): Boolean =
    parseProgramIntent(program)?.let(start) == true || fallback(program.packageName)?.let(start) == true
