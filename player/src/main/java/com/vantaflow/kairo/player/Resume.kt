package com.vantaflow.kairo.player

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Size
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import java.io.File
import java.security.MessageDigest

/** Où en est la lecture d'un fichier. */
data class Progress(val positionMs: Long, val durationMs: Long, val updatedAt: Long) {
    val percent: Int get() = if (durationMs <= 0) 0 else (positionMs * 100 / durationMs).toInt().coerceIn(0, 100)
}

/** Au-delà, la vidéo est considérée comme vue ; en deçà de 30 s, pas de reprise. */
const val WATCHED_PERCENT = 95
const val MIN_RESUME_MS = 30_000L

fun shouldResume(p: Progress?): Boolean = p != null && p.positionMs >= MIN_RESUME_MS && p.percent < WATCHED_PERCENT

/** Positions de lecture, gardées sur la box (fichier de préférences). */
class ResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)

    fun get(uri: Uri): Progress? = prefs.getString(uri.toString(), null)?.split(';')?.let { p ->
        if (p.size == 3) Progress(p[0].toLong(), p[1].toLong(), p[2].toLong()) else null
    }

    fun save(uri: Uri, positionMs: Long, durationMs: Long) {
        prefs.edit().putString(uri.toString(), "$positionMs;$durationMs;${System.currentTimeMillis()}").apply()
    }

    fun clear(uri: Uri) = prefs.edit().remove(uri.toString()).apply()

    /** Fichiers à reprendre, du plus récent au plus ancien. */
    fun inProgress(): List<Pair<Uri, Progress>> = prefs.all.mapNotNull { (k, _) ->
        get(Uri.parse(k))?.takeIf(::shouldResume)?.let { Uri.parse(k) to it }
    }.sortedByDescending { it.second.updatedAt }
}

/**
 * « À suivre » de Kairo et de Google TV : une ligne par vidéo commencée, mise à jour pendant la
 * lecture et retirée une fois la vidéo vue.
 */
class WatchNext(private val context: Context) {
    private val rows = context.getSharedPreferences("watch_next_rows", Context.MODE_PRIVATE)

    fun publish(uri: Uri, title: String, progress: Progress) {
        val key = uri.toString()
        if (!shouldResume(progress)) {
            remove(uri)
            return
        }
        val program = WatchNextProgram.Builder()
            .setType(TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setTitle(title)
            .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
            .setLastPlaybackPositionMillis(progress.positionMs.toInt())
            .setDurationMillis(progress.durationMs.toInt())
            .setPosterArtUri(ThumbProvider.uriFor(context, uri))
            .setIntentUri(Uri.parse(playIntent(context, uri).toUri(Intent.URI_INTENT_SCHEME)))
            .setInternalProviderId(key)
            .build()
        runCatching {
            val id = rows.getLong(key, -1)
            val resolver = context.contentResolver
            if (id >= 0 && resolver.update(TvContractCompat.buildWatchNextProgramUri(id), program.toContentValues(), null, null) > 0) return
            resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
                ?.let { rows.edit().putLong(key, ContentUrisCompat.id(it)).apply() }
        }
    }

    fun remove(uri: Uri) {
        val key = uri.toString()
        val id = rows.getLong(key, -1)
        if (id < 0) return
        runCatching { context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null) }
        rows.edit().remove(key).apply()
    }
}

private object ContentUrisCompat {
    fun id(uri: Uri): Long = uri.lastPathSegment?.toLongOrNull() ?: -1
}

/** Ouvre le lecteur sur ce fichier (aussi utilisé par « À suivre »). */
fun playIntent(context: Context, uri: Uri): Intent =
    Intent(context, PlayerActivity::class.java).setAction(Intent.ACTION_VIEW).setData(uri)

/**
 * Vignettes des vidéos commencées, lisibles par Kairo pour « À suivre ». N'expose que des JPEG
 * générés ici (jamais les fichiers eux-mêmes) ; aucune écriture possible.
 */
class ThumbProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") return null
        val name = uri.lastPathSegment?.takeIf { it.matches(Regex("[0-9a-f]{32}\\.jpg")) } ?: return null
        val file = File(thumbDir(context!!), name).takeIf { it.isFile } ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        private fun thumbDir(context: Context) = File(context.cacheDir, "thumbs").apply { mkdirs() }

        private fun nameFor(media: Uri): String =
            MessageDigest.getInstance("MD5").digest(media.toString().toByteArray()).joinToString("") { "%02x".format(it) } + ".jpg"

        /** Écrit la vignette (si besoin) et renvoie son adresse publique. */
        fun uriFor(context: Context, media: Uri): Uri {
            val file = File(thumbDir(context), nameFor(media))
            if (!file.isFile) {
                runCatching {
                    context.contentResolver.loadThumbnail(media, Size(640, 360), null).let { bmp ->
                        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    }
                }
            }
            return Uri.parse("content://com.vantaflow.kairo.player.thumbs/${file.name}")
        }
    }
}
