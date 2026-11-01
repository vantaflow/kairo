package com.vantaflow.kairo.media

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.Executors

/**
 * Dernier contenu lu dans chaque appli (titre et image annoncés par son lecteur) : la carte
 * « Reprendre » de « À suivre » montre ainsi la vidéo en cours au lieu de la bannière de l'appli.
 * Les applis qui n'annoncent rien (Apple TV) gardent leur bannière.
 */
object LastPlayed {
    data class Entry(val at: Long, val title: String, val subtitle: String?, val image: File?)

    private const val MAX_APPS = 12
    private const val IMAGE_W = 640
    private val io = Executors.newSingleThreadExecutor()
    private var lastKey: String? = null

    private fun dir(context: Context) = File(context.filesDir, "last_played").apply { mkdirs() }
    private fun index(context: Context) = File(dir(context), "index.txt")

    /** Appelé à chaque changement du lecteur : n'écrit que si le titre ou l'image change. */
    fun record(context: Context, pkg: String, title: String, subtitle: String?, art: Bitmap?) {
        val key = "$pkg|$title|${art?.let { System.identityHashCode(it) }}"
        if (key == lastKey) return
        lastKey = key
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        // Copie réduite tout de suite : le lecteur peut recycler sa propre image.
        val small = art?.let { runCatching { scaled(it) }.getOrNull() }
        io.execute {
            runCatching {
                val d = dir(app)
                val image = small?.let { bmp ->
                    File(d, "$pkg-$now.jpg").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                }
                val old = read(app)
                val keepImage = image ?: old[pkg]?.takeIf { it.title == title }?.image
                old[pkg]?.image?.takeIf { it != keepImage }?.delete()
                val entries = (listOf(pkg to Entry(now, title, subtitle, keepImage)) + old.filterKeys { it != pkg }.toList())
                    .sortedByDescending { it.second.at }
                entries.drop(MAX_APPS).forEach { it.second.image?.delete() }
                index(app).writeText(entries.take(MAX_APPS).joinToString("\n") { (p, e) ->
                    listOf(p, e.at, clean(e.title), clean(e.subtitle.orEmpty()), e.image?.name.orEmpty()).joinToString("\t")
                })
            }
        }
    }

    fun read(context: Context): Map<String, Entry> {
        val d = dir(context)
        return runCatching { index(context).readLines() }.getOrDefault(emptyList()).mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 5) return@mapNotNull null
            val at = p[1].toLongOrNull() ?: return@mapNotNull null
            p[0] to Entry(at, p[2], p[3].ifBlank { null }, p[4].takeIf { it.isNotBlank() }?.let { File(d, it) }?.takeIf { it.exists() })
        }.toMap()
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    private fun scaled(src: Bitmap): Bitmap {
        if (src.width <= IMAGE_W) return src.copy(Bitmap.Config.ARGB_8888, false)
        val h = (src.height * IMAGE_W.toFloat() / src.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, IMAGE_W, h, true)
    }
}
