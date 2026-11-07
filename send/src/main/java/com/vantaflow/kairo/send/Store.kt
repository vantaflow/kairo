package com.vantaflow.kairo.send

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

/** Dossier des fichiers échangés : Téléchargements › Kairo Send (visible dans Kairo Files et Kairo Player). */
const val FOLDER = "Download/Kairo Send/"

/** Un fichier du dossier, reçu du téléphone ou proposé par la box. */
data class SharedFile(val id: Long, val name: String, val size: Long, val added: Long, val mime: String?) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
}

/** Envoi en cours (téléphone → box ou fichier ajouté depuis la box). */
data class Transfer(val id: Long, val name: String, val total: Long, val done: Long, val failed: Boolean = false)

/** Texte ou lien envoyé depuis le téléphone. */
data class Note(val id: Long, val text: String, val at: Long)

/** Fichiers du dossier Kairo Send, lus et écrits par MediaStore (aucune autorisation de stockage). */
class Store(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun list(): List<SharedFile> {
        val cols = arrayOf(
            MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_ADDED, MediaStore.Downloads.MIME_TYPE,
        )
        return runCatching {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, cols,
                "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.IS_PENDING} = 0", arrayOf(FOLDER),
                "${MediaStore.Downloads.DATE_ADDED} DESC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) add(SharedFile(c.getLong(0), c.getString(1) ?: "fichier", c.getLong(2), c.getLong(3) * 1000, c.getString(4)))
                }
            }
        }.getOrNull().orEmpty()
    }

    fun find(id: Long): SharedFile? = list().firstOrNull { it.id == id }

    fun open(file: SharedFile): InputStream? = runCatching { resolver.openInputStream(file.uri) }.getOrNull()

    fun delete(file: SharedFile): Boolean = runCatching { resolver.delete(file.uri, null, null) > 0 }.getOrDefault(false)

    /**
     * Enregistre [input] sous [name] ([length] octets si connu, sinon jusqu'à la fin du flux).
     * Le fichier reste « en attente » (invisible) tant qu'il n'est pas complet ; supprimé en cas d'échec.
     */
    fun save(name: String, mime: String?, input: InputStream, length: Long, onBytes: (Long) -> Unit): SharedFile {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeFileName(name))
            put(MediaStore.Downloads.RELATIVE_PATH, FOLDER)
            put(MediaStore.Downloads.IS_PENDING, 1)
            if (mime != null) put(MediaStore.Downloads.MIME_TYPE, mime)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Stockage plein ou indisponible")
        try {
            resolver.openOutputStream(uri)!!.use { out ->
                val buffer = ByteArray(256 * 1024)
                var left = if (length >= 0) length else Long.MAX_VALUE
                while (left > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (read < 0) {
                        if (length >= 0) error("Envoi interrompu")
                        break
                    }
                    out.write(buffer, 0, read)
                    left -= read
                    onBytes(read.toLong())
                }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        val id = ContentUris.parseId(uri)
        return find(id) ?: SharedFile(id, name, length, System.currentTimeMillis(), mime)
    }
}

/** État partagé entre le serveur, le service et l'écran. */
object Hub {
    private val ids = AtomicLong()
    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes
    /** Change à chaque fichier ajouté ou supprimé : l'écran relit la liste. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    val busy: Boolean get() = _transfers.value.any { !it.failed && it.done < it.total }

    fun start(name: String, total: Long): Long {
        val id = ids.incrementAndGet()
        _transfers.update { it + Transfer(id, name, total, 0) }
        return id
    }

    fun progress(id: Long, bytes: Long) = _transfers.update { list -> list.map { if (it.id == id) it.copy(done = it.done + bytes) else it } }

    fun finish(id: Long, ok: Boolean) {
        if (ok) _transfers.update { list -> list.filterNot { it.id == id } }
        else _transfers.update { list -> list.map { if (it.id == id) it.copy(failed = true) else it } }
        changed()
    }

    fun dismissFailed() = _transfers.update { list -> list.filterNot { it.failed } }

    fun note(text: String) {
        _notes.update { (listOf(Note(ids.incrementAndGet(), text, System.currentTimeMillis())) + it).take(20) }
    }

    fun removeNote(id: Long) = _notes.update { list -> list.filterNot { it.id == id } }

    fun changed() = _version.update { it + 1 }
}
