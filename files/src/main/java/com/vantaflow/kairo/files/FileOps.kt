package com.vantaflow.kairo.files

import java.io.File
import java.text.Normalizer
import java.util.Locale

/** Type d'un élément, pour son icône et la façon de l'ouvrir. */
enum class Kind(val icon: String, val color: Long) {
    Folder("📁", 0xFF4F8DF7),
    Video("🎬", 0xFFE5534B),
    Audio("🎵", 0xFFB46BF0),
    Image("🖼", 0xFF2FB67C),
    Apk("📦", 0xFF7BC74D),
    Archive("🗜", 0xFFE0A33A),
    Document("📄", 0xFF8A94A6),
    Other("📄", 0xFF6B7385),
}

enum class SortBy(val label: String) { Name("Nom"), Date("Date"), Size("Taille") }

private val EXT_KIND: Map<String, Kind> = buildMap {
    listOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m2ts", "wmv", "flv", "m4v", "3gp", "mpg", "mpeg").forEach { put(it, Kind.Video) }
    listOf("mp3", "flac", "aac", "m4a", "ogg", "opus", "wav", "wma").forEach { put(it, Kind.Audio) }
    listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif").forEach { put(it, Kind.Image) }
    listOf("apk", "apks", "xapk").forEach { put(it, Kind.Apk) }
    listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz").forEach { put(it, Kind.Archive) }
    listOf("pdf", "txt", "srt", "ass", "vtt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "json", "xml", "html", "m3u", "m3u8").forEach { put(it, Kind.Document) }
}

private val EXT_MIME = mapOf(
    "mp4" to "video/mp4", "mkv" to "video/x-matroska", "avi" to "video/x-msvideo", "mov" to "video/quicktime",
    "webm" to "video/webm", "ts" to "video/mp2t", "m2ts" to "video/mp2t", "wmv" to "video/x-ms-wmv", "flv" to "video/x-flv",
    "m4v" to "video/mp4", "3gp" to "video/3gpp", "mpg" to "video/mpeg", "mpeg" to "video/mpeg",
    "mp3" to "audio/mpeg", "flac" to "audio/flac", "aac" to "audio/aac", "m4a" to "audio/mp4", "ogg" to "audio/ogg",
    "opus" to "audio/opus", "wav" to "audio/wav", "wma" to "audio/x-ms-wma",
    "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif", "webp" to "image/webp",
    "bmp" to "image/bmp", "heic" to "image/heic", "heif" to "image/heif",
    "apk" to "application/vnd.android.package-archive",
    "zip" to "application/zip", "rar" to "application/vnd.rar", "7z" to "application/x-7z-compressed",
    "pdf" to "application/pdf", "txt" to "text/plain", "srt" to "application/x-subrip", "html" to "text/html",
    "json" to "application/json", "xml" to "text/xml", "csv" to "text/csv",
    "m3u" to "audio/x-mpegurl", "m3u8" to "application/vnd.apple.mpegurl",
)

/** Lu par Kairo Player : vidéos, musiques, PDF et textes. */
fun opensInPlayer(name: String): Boolean {
    val ext = extensionOf(name)
    return EXT_KIND[ext] == Kind.Video || EXT_KIND[ext] == Kind.Audio || ext in setOf("pdf", "txt", "srt", "log", "md", "csv", "json", "xml", "nfo")
}

fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

fun kindOf(name: String, directory: Boolean): Kind =
    if (directory) Kind.Folder else EXT_KIND[extensionOf(name)] ?: Kind.Other

/** Type MIME pour « Ouvrir » (« * / * » si inconnu : Android propose toutes les applis). */
fun mimeOf(name: String): String = EXT_MIME[extensionOf(name)] ?: "*/*"

/** Taille lisible, en unités françaises (o, Ko, Mo, Go, To). */
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes o"
    val units = listOf("Ko", "Mo", "Go", "To")
    var value = bytes / 1024.0
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    val text = if (value >= 100) "%.0f".format(Locale.FRANCE, value) else "%.1f".format(Locale.FRANCE, value)
    return "$text ${units[i]}"
}

private val marks = Regex("\\p{Mn}+")
private fun plain(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").lowercase(Locale.ROOT)

/** Un élément d'un dossier, lu une fois (le disque USB est lent : pas de relecture au dessin). */
data class Entry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long,
    val modified: Long,
    val hidden: Boolean,
    /** Nombre d'éléments pour un dossier (null si illisible). */
    val children: Int? = null,
) {
    val kind: Kind get() = kindOf(name, directory)
}

fun entryOf(file: File): Entry = Entry(
    name = file.name,
    path = file.absolutePath,
    directory = file.isDirectory,
    size = if (file.isDirectory) 0 else file.length(),
    modified = file.lastModified(),
    hidden = file.name.startsWith("."),
    children = if (file.isDirectory) file.list()?.size else null,
)

/** Dossiers d'abord, puis tri choisi ; ordre naturel pour les noms (« Épisode 2 » avant « Épisode 10 »). */
fun sortEntries(entries: List<Entry>, by: SortBy, showHidden: Boolean): List<Entry> {
    val visible = if (showHidden) entries else entries.filterNot { it.hidden }
    val byName = Comparator<Entry> { a, b -> naturalCompare(plain(a.name), plain(b.name)) }
    val second = when (by) {
        SortBy.Name -> byName
        SortBy.Date -> compareByDescending<Entry> { it.modified }.then(byName)
        SortBy.Size -> compareByDescending<Entry> { it.size }.then(byName)
    }
    return visible.sortedWith(compareByDescending<Entry> { it.directory }.then(second))
}

/** Compare en traitant les suites de chiffres comme des nombres. */
fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            val si = i
            val sj = j
            while (i < a.length && a[i].isDigit()) i++
            while (j < b.length && b[j].isDigit()) j++
            val na = a.substring(si, i).trimStart('0')
            val nb = b.substring(sj, j).trimStart('0')
            if (na.length != nb.length) return na.length - nb.length
            val c = na.compareTo(nb)
            if (c != 0) return c
        } else {
            if (ca != cb) return ca - cb
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

/** Nom libre dans [dir] : « film.mkv » devient « film (2).mkv » s'il existe déjà. */
fun freeName(dir: File, name: String): String {
    if (!File(dir, name).exists()) return name
    val dot = name.lastIndexOf('.').takeIf { it > 0 && !File(dir, name).isDirectory } ?: name.length
    val base = name.substring(0, dot)
    val ext = name.substring(dot)
    var n = 2
    while (File(dir, "$base ($n)$ext").exists()) n++
    return "$base ($n)$ext"
}

/** Nom accepté pour renommer ou créer : non vide, sans « / », ni « . » ni « .. ». */
fun validName(name: String): Boolean {
    val n = name.trim()
    return n.isNotEmpty() && n != "." && n != ".." && '/' !in n && '\u0000' !in n && n.length <= 255
}

/** Vrai si [target] est [source] ou se trouve dedans (copier un dossier dans lui-même). */
fun isInside(target: File, source: File): Boolean {
    val s = source.canonicalPath
    val t = target.canonicalPath
    return t == s || t.startsWith("$s/")
}

/** Taille totale (fichiers d'un dossier compris), pour la progression. */
fun totalSize(file: File): Long =
    if (file.isDirectory) file.listFiles()?.sumOf { totalSize(it) } ?: 0 else file.length()

/**
 * Copie [source] (fichier ou dossier) dans [destDir] sous un nom libre ; [onBytes] reçoit les octets copiés.
 * [cancelled] est consulté entre deux blocs. Rend le fichier créé.
 */
fun copyInto(source: File, destDir: File, onBytes: (Long) -> Unit, cancelled: () -> Boolean): File {
    val target = File(destDir, freeName(destDir, source.name))
    copyTo(source, target, onBytes, cancelled)
    return target
}

private fun copyTo(source: File, target: File, onBytes: (Long) -> Unit, cancelled: () -> Boolean) {
    if (cancelled()) throw InterruptedException()
    if (source.isDirectory) {
        if (!target.mkdirs() && !target.isDirectory) throw java.io.IOException("Impossible de créer ${target.name}")
        source.listFiles()?.forEach { copyTo(it, File(target, it.name), onBytes, cancelled) }
    } else {
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    if (cancelled()) throw InterruptedException()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    onBytes(read.toLong())
                }
            }
        }
        target.setLastModified(source.lastModified())
    }
}

/**
 * Déplace [source] dans [destDir] : simple renommage sur le même support, sinon copie puis suppression
 * (d'une clé USB vers la box par exemple).
 */
fun moveInto(source: File, destDir: File, onBytes: (Long) -> Unit, cancelled: () -> Boolean): File {
    val target = File(destDir, freeName(destDir, source.name))
    if (source.renameTo(target)) {
        onBytes(totalSize(target))
        return target
    }
    copyTo(source, target, onBytes, cancelled)
    source.deleteRecursively()
    return target
}
