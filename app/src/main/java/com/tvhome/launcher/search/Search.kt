package com.tvhome.launcher.search

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.tvhome.launcher.apps.AppInfo
import com.tvhome.launcher.reco.Program
import java.text.Normalizer

/** Minuscules sans accents, pour comparer « télé » et « Tele ». */
fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().trim()

/** Applis dont le nom contient la recherche ; celles qui commencent par elle d'abord. */
fun searchApps(query: String, apps: List<AppInfo>): List<AppInfo> {
    val q = normalize(query)
    if (q.isEmpty()) return emptyList()
    val matches = apps.filter { normalize(it.label).contains(q) }
    return matches.sortedBy { if (normalize(it.label).startsWith(q)) 0 else 1 }
}

fun searchPrograms(query: String, programs: List<Program>): List<Program> {
    val q = normalize(query)
    if (q.isEmpty()) return emptyList()
    return programs.filter { normalize(it.title).contains(q) || normalize(it.subtitle.orEmpty()).contains(q) }
        .distinctBy { it.packageName to it.title }
}

/** Recherche à lancer dans une appli. */
data class SearchTarget(val label: String, val packageName: String, val intent: Intent)

private fun searchIn(label: String, pkg: String, query: String) =
    SearchTarget(label, pkg, Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, query))

/**
 * Recherches proposées : applis installées qui savent recevoir la recherche ([canResolve]),
 * puis la recherche globale de la TV en repli.
 */
fun searchTargets(query: String, installed: Set<String>, canResolve: (Intent) -> Boolean): List<SearchTarget> {
    val direct = appSearchIntents(query).filter { it.packageName in installed && canResolve(it.intent) }
    val global = SearchTarget("Recherche Google TV", "", Intent("android.search.action.GLOBAL_SEARCH").putExtra(SearchManager.QUERY, query))
    return direct + listOfNotNull(global.takeIf { canResolve(it.intent) })
}

/** Recherche dans les principales applis vidéo et le Play Store (celles absentes sont ignorées à l'affichage). */
fun appSearchIntents(query: String): List<SearchTarget> = listOf(
    searchIn("YouTube", "com.google.android.youtube.tv", query),
    searchIn("Netflix", "com.netflix.ninja", query),
    searchIn("Prime Video", "com.amazon.amazonvideo.livingroom", query),
    searchIn("Disney+", "com.disney.disneyplus", query),
    SearchTarget(
        "Play Store",
        "com.android.vending",
        Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(query)}&c=apps")).setPackage("com.android.vending"),
    ),
)
