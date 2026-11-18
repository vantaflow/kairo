package com.vantaflow.kairo.system

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import java.util.Locale

/** Unités (Mo, Go) dans la langue de [locale]. */
private fun sizeUnits(locale: Locale): Pair<String, String> = when (locale.language) {
    "fr" -> "Mo" to "Go"
    "ar" -> "ميغابايت" to "غيغابايت"
    else -> "MB" to "GB"
}

/** Taille lisible dans la langue de l'appli : « 512 Mo », « 1,5 Go », « 1.5 GB ». */
fun formatMb(bytes: Long, locale: Locale = Locale.getDefault()): String {
    val (mbUnit, gbUnit) = sizeUnits(locale)
    val mb = bytes / (1024.0 * 1024.0)
    if (mb < 1024) return "${mb.toLong()} $mbUnit"
    val gb = mb / 1024
    val tenths = kotlin.math.round(gb * 10).toLong()
    val number = if (tenths % 10 == 0L) (tenths / 10).toString() else String.format(locale, "%.1f", gb)
    return "$number $gbUnit"
}

/** Applis à fermer : toutes sauf le launcher et celles à garder (ex. service de touches, clavier). */
fun killablePackages(installed: List<String>, own: String, keep: Set<String>): List<String> =
    installed.filter { it != own && it !in keep }

data class MemoryState(val availableBytes: Long, val totalBytes: Long)

/** Libère la mémoire en fermant les applis en arrière-plan (KILL_BACKGROUND_PROCESSES). */
class MemoryCleaner(private val context: Context) {
    private val am = context.getSystemService(ActivityManager::class.java)

    fun state(): MemoryState {
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return MemoryState(info.availMem, info.totalMem)
    }

    /** Ferme une appli en arrière-plan (sans effet sur celle à l'écran). */
    fun close(pkg: String) {
        runCatching { am.killBackgroundProcesses(pkg) }
    }

    fun clean(installed: List<String>): Int {
        // On garde le clavier actif, sinon la saisie disparaît.
        val ime = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
        val targets = killablePackages(installed, context.packageName, keep = setOfNotNull(ime))
        targets.forEach { runCatching { am.killBackgroundProcesses(it) } }
        return targets.size
    }
}
