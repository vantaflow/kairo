package com.vantaflow.kairo.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.MediaStore

enum class Kind { Video, Photo, Music }

/** Un fichier de la box ou d'une clé USB, vu par l'index des médias d'Android. */
data class Media(
    val uri: Uri,
    val name: String,
    val kind: Kind,
    /** Volume (« external_primary » = stockage interne, sinon une clé ou un disque USB). */
    val volume: String,
    /** Dossier relatif au volume, ex. « Movies/Séries/ ». */
    val folder: String,
    val durationMs: Long,
    val dateAdded: Long,
    val size: Long,
)

/** Dossier contenant des médias : ce qu'on parcourt dans Kairo Player. */
data class Folder(val volume: String, val path: String, val items: List<Media>) {
    val key get() = "$volume:$path"
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifBlank { "Racine" }
    val cover: Media? get() = items.firstOrNull { it.kind != Kind.Music } ?: items.firstOrNull()
}

/** Regroupe par dossier, les plus récents en tête ; à l'intérieur, par nom (ordre naturel des épisodes). */
fun groupFolders(media: List<Media>): List<Folder> =
    media.groupBy { it.volume to it.folder }
        .map { (k, items) -> Folder(k.first, k.second, items.sortedWith(compareBy(NaturalOrder) { it.name })) }
        .sortedByDescending { f -> f.items.maxOf { it.dateAdded } }

/** Ordre naturel : « Épisode 2 » avant « Épisode 10 ». */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")
    private val marks = Regex("\\p{Mn}+")

    /** Sans accents ni majuscules : « Épisode » se range avec « episode ». */
    private fun plain(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(marks, "").lowercase()
    override fun compare(a: String, b: String): Int {
        val x = chunk.findAll(plain(a)).map { it.value }.toList()
        val y = chunk.findAll(plain(b)).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                p.trimStart('0').length.compareTo(q.trimStart('0').length).takeIf { it != 0 } ?: p.trimStart('0').compareTo(q.trimStart('0'))
            } else {
                p.compareTo(q)
            }
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }
}

/** Lit tous les volumes (interne + USB) : vidéos, photos et musique. */
class MediaLibrary(private val context: Context) {

    fun all(): List<Media> = MediaStore.getExternalVolumeNames(context).flatMap { volume ->
        query(volume, MediaStore.Video.Media.getContentUri(volume), Kind.Video) +
            query(volume, MediaStore.Images.Media.getContentUri(volume), Kind.Photo) +
            query(volume, MediaStore.Audio.Media.getContentUri(volume), Kind.Music)
    }

    /** Nom lisible du volume : « Stockage interne » ou le nom de la clé USB. */
    fun volumeLabel(volume: String): String {
        if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) return "Stockage interne"
        val sm = context.getSystemService(StorageManager::class.java)
        return sm?.storageVolumes?.firstOrNull { it.mediaStoreVolumeName == volume }?.getDescription(context)
            ?: "Clé USB"
    }

    private fun query(volume: String, collection: Uri, kind: Kind): List<Media> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            if (kind != Kind.Photo) add(MediaStore.MediaColumns.DURATION)
        }.toTypedArray()
        return runCatching {
            context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val path = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val added = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val size = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val duration = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
                buildList {
                    while (c.moveToNext()) {
                        add(
                            Media(
                                uri = ContentUris.withAppendedId(collection, c.getLong(id)),
                                name = c.getString(name).orEmpty(),
                                kind = kind,
                                volume = volume,
                                folder = c.getString(path).orEmpty(),
                                durationMs = if (duration >= 0) c.getLong(duration) else 0L,
                                dateAdded = c.getLong(added),
                                size = c.getLong(size),
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}

/** « 1 h 32 », « 45 min », « 3:20 » pour la musique. */
fun formatDuration(ms: Long, music: Boolean = false): String {
    val s = ms / 1000
    return when {
        music -> "%d:%02d".format(s / 60, s % 60)
        s >= 3600 -> "${s / 3600} h ${"%02d".format((s % 3600) / 60)}"
        else -> "${maxOf(1, s / 60)} min"
    }
}

/** Titre lisible : nom de fichier sans extension, points et tirets bas en espaces. */
fun displayTitle(fileName: String): String =
    fileName.substringBeforeLast('.').replace('_', ' ').replace(Regex("\\.(?=\\S)"), " ").trim().ifBlank { fileName }
