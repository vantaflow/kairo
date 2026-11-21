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

/** Trace de sortie d'un process laissée par Android : moment (horloge murale) et raison. */
data class ExitTrace(val timestamp: Long, val reason: Int)

/**
 * Raison de la sortie du process précédent : la trace la plus récente, seulement si elle est
 * postérieure au démarrage précédent ([previousStart], 0 = inconnu). Après une coupure de courant,
 * Android n'a écrit aucune trace pour le process précédent : la dernière trace est alors plus
 * ancienne et ne doit pas être recomptée.
 */
fun previousExitReason(traces: List<ExitTrace>, previousStart: Long): Int? =
    traces.maxByOrNull { it.timestamp }?.takeIf { it.timestamp > previousStart }?.reason

/**
 * Raison de la sortie du process principal de Kairo qui a suivi le démarrage [previousStart]
 * (null si inconnue, illisible ou trace périmée, voir [previousExitReason]).
 */
fun lastExitReason(context: Context, previousStart: Long): Int? = runCatching {
    val traces = context.getSystemService(ActivityManager::class.java)
        .getHistoricalProcessExitReasons(context.packageName, 0, 5)
        .filter { it.processName == context.packageName }
        .map { ExitTrace(it.timestamp, it.reason) }
    previousExitReason(traces, previousStart)
}.getOrNull()
