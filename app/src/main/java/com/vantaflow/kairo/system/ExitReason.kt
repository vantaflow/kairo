package com.vantaflow.kairo.system

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context

/**
 * Sortie du process due à un vrai plantage de Kairo : exception ou plantage natif. Sert au retrait
 * de l'accueil et au compteur de plantages en mode sans échec. Un ANR ou un échec d'initialisation
 * (« start timeout » d'une box saturée au démarrage) n'en est pas un : Kairo n'y est souvent pour rien.
 */
fun isCrashExit(reason: Int?): Boolean =
    reason == ApplicationExitInfo.REASON_CRASH || reason == ApplicationExitInfo.REASON_CRASH_NATIVE

/**
 * Sortie qui compte pour entrer en mode sans échec : un plantage toujours ; un ANR ou un échec
 * d'initialisation seulement plus de 10 min après le démarrage de la box ([bootIsRecent] faux),
 * quand la box n'est plus saturée par le démarrage des autres applis.
 */
fun countsForSafeMode(reason: Int?, bootIsRecent: Boolean): Boolean = isCrashExit(reason) ||
    (!bootIsRecent && (reason == ApplicationExitInfo.REASON_ANR || reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE))

/** Sortie du process précédent : [crashed] (vrai plantage) et [countsForSafeMode]. */
data class PreviousExit(val crashed: Boolean, val countsForSafeMode: Boolean)

/** Traces de sortie mémorisées au plus (les plus récentes de la liste rendue par Android). */
const val MAX_SEEN_EXITS = 32

/**
 * Trace de sortie d'un process laissée par Android : [key] l'identifie (`"<pid>:<timestamp>"`), sans
 * que sa date serve à quoi que ce soit (l'horloge de la box peut être fausse), et [reason] sa raison.
 */
data class ExitTrace(val key: String, val reason: Int)

private val EXIT_KEY = Regex("""\d+:-?\d+""")

/**
 * Clés des traces vues au démarrage précédent ; null si rien n'a été mémorisé ou si la valeur est
 * illisible (traité comme un premier démarrage : rien à compter).
 */
fun decodeSeenExitKeys(stored: String?): Set<String>? {
    if (stored == null) return null
    if (stored.isEmpty()) return emptySet()
    val keys = stored.split(',')
    return if (keys.all { EXIT_KEY.matches(it) }) keys.toSet() else null
}

/** Clés à mémoriser : les [MAX_SEEN_EXITS] premières traces de la liste. */
fun encodeSeenExitKeys(current: List<ExitTrace>): String = current.take(MAX_SEEN_EXITS).joinToString(",") { it.key }

/**
 * Raisons des sorties survenues depuis le démarrage précédent : les traces dont la clé n'avait pas
 * été vue, quelle que soit leur date. Aucune (coupure de courant, aucune trace écrite) : liste vide.
 * Premier démarrage ([seenKeys] null) : rien à compter. Seules les [MAX_SEEN_EXITS] premières traces
 * sont comparées, comme celles qui sont mémorisées.
 */
fun newExitTraces(seenKeys: Set<String>?, current: List<ExitTrace>): List<Int> {
    if (seenKeys == null) return emptyList()
    return current.take(MAX_SEEN_EXITS).filter { it.key !in seenKeys }.map { it.reason }
}

/** Sortie du process précédent : au moins une des sorties nouvelles est un plantage (resp. compte). */
fun previousExit(seenKeys: Set<String>?, current: List<ExitTrace>, bootIsRecent: Boolean): PreviousExit {
    val reasons = newExitTraces(seenKeys, current)
    return PreviousExit(
        crashed = reasons.any(::isCrashExit),
        countsForSafeMode = reasons.any { countsForSafeMode(it, bootIsRecent) },
    )
}

/** Toutes les traces de sortie du process principal de Kairo ; null si illisibles. */
fun exitTraces(context: Context): List<ExitTrace>? = runCatching {
    context.getSystemService(ActivityManager::class.java)
        .getHistoricalProcessExitReasons(context.packageName, 0, 0)
        .filter { it.processName == context.packageName }
        .map { ExitTrace("${it.pid}:${it.timestamp}", it.reason) }
}.getOrNull()

/** Numéro du démarrage de la box (0 si indisponible) : compte les redémarrages sans dépendre de l'horloge. */
fun bootCount(context: Context): Int = runCatching {
    android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT)
}.getOrDefault(0)
