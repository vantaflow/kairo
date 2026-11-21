package com.vantaflow.kairo.system

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context

/**
 * Sortie du process due à un plantage : exception, plantage natif, ANR ou échec d'initialisation.
 * Tout le reste (mémoire basse, mise à jour, arrêt demandé, arrêt volontaire, signal…) n'en est pas un.
 */
fun isCrashExit(reason: Int?): Boolean = when (reason) {
    ApplicationExitInfo.REASON_CRASH,
    ApplicationExitInfo.REASON_CRASH_NATIVE,
    ApplicationExitInfo.REASON_ANR,
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> true
    else -> false
}

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

/** Le process précédent a planté : au moins une des sorties nouvelles est un plantage. */
fun previousExitWasCrash(seenKeys: Set<String>?, current: List<ExitTrace>): Boolean =
    newExitTraces(seenKeys, current).any(::isCrashExit)

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
