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

/** Raison de la dernière sortie du process principal de Kairo (null si inconnue ou illisible). */
fun lastExitReason(context: Context): Int? = runCatching {
    context.getSystemService(ActivityManager::class.java)
        .getHistoricalProcessExitReasons(context.packageName, 0, 5)
        .firstOrNull { it.processName == context.packageName }
        ?.reason
}.getOrNull()
