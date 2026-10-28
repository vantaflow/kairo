package com.tvhome.launcher.system

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import java.util.Locale

/** Taille lisible en français : « 512 Mo », « 1,5 Go ». */
fun formatMb(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1024) "${mb.toLong()} Mo"
    else String.format(Locale.FRENCH, "%.1f Go", mb / 1024).replace(",0 Go", " Go")
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

    fun clean(installed: List<String>): Int {
        // On garde le clavier actif, sinon la saisie disparaît.
        val ime = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
        val targets = killablePackages(installed, context.packageName, keep = setOfNotNull(ime))
        targets.forEach { runCatching { am.killBackgroundProcesses(it) } }
        return targets.size
    }
}
