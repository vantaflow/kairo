package com.vantaflow.kairo.files

import com.github.junrar.Archive
import com.github.junrar.exception.UnsupportedRarV5Exception
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Un élément d'une archive (pour « Voir le contenu »). */
data class ArchiveItem(val name: String, val size: Long, val directory: Boolean)

/** Archive illisible : le message est montré tel quel. */
class ArchiveException(message: String) : IOException(message)

private enum class Format { Zip, SevenZ, Rar, Tar, TarGz, TarBz2, TarXz, Gz, Bz2, Xz }

private fun formatOf(name: String): Format? {
    val n = name.lowercase()
    return when {
        n.endsWith(".tar.gz") || n.endsWith(".tgz") -> Format.TarGz
        n.endsWith(".tar.bz2") || n.endsWith(".tbz2") -> Format.TarBz2
        n.endsWith(".tar.xz") || n.endsWith(".txz") -> Format.TarXz
        n.endsWith(".zip") -> Format.Zip
        n.endsWith(".7z") -> Format.SevenZ
        n.endsWith(".rar") -> Format.Rar
        n.endsWith(".tar") -> Format.Tar
        n.endsWith(".gz") -> Format.Gz
        n.endsWith(".bz2") -> Format.Bz2
        n.endsWith(".xz") -> Format.Xz
        else -> null
    }
}

fun isArchive(name: String): Boolean = formatOf(name) != null

/** Nom du dossier d'extraction : « Photos.zip » donne « Photos », « site.tar.gz » donne « site ». */
fun archiveBaseName(name: String): String {
    val lower = name.lowercase()
    val suffix = listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".txz", ".zip", ".7z", ".rar", ".tar", ".gz", ".bz2", ".xz")
        .firstOrNull { lower.endsWith(it) } ?: return name
    return name.dropLast(suffix.length).ifBlank { name }
}

/**
 * Fichier de destination d'un élément, ou null s'il sortirait du dossier d'extraction
 * (« ../../ » ou chemin absolu dans une archive piégée).
 */
fun safeTarget(destDir: File, entryName: String): File? {
    val clean = entryName.replace('\\', '/').trimStart('/')
    if (clean.isEmpty()) return null
    val target = File(destDir, clean)
    val root = destDir.canonicalPath
    val path = target.canonicalPath
    return target.takeIf { path == root || path.startsWith("$root/") }
}

private fun rarError(e: Throwable): Nothing = when (e) {
    is UnsupportedRarV5Exception -> throw ArchiveException("RAR 5 non pris en charge : utilisez « Ouvrir avec… »")
    else -> throw ArchiveException(if (e.message?.contains("encrypt", true) == true) "Archive protégée par mot de passe" else "Archive RAR illisible")
}

/** Contenu de l'archive (sans rien extraire). */
fun listArchive(file: File): List<ArchiveItem> = when (formatOf(file.name)) {
    Format.Zip -> ZipFile.builder().setFile(file).get().use { zip ->
        zip.entries.toList().map { ArchiveItem(it.name, it.size, it.isDirectory) }
    }
    Format.SevenZ -> SevenZFile.builder().setFile(file).get().use { z ->
        z.entries.map { ArchiveItem(it.name, it.size, it.isDirectory) }
    }
    Format.Rar -> try {
        Archive(file).use { rar -> rar.fileHeaders.map { ArchiveItem(it.fileName.replace('\\', '/'), it.fullUnpackSize, it.isDirectory) } }
    } catch (e: Exception) {
        rarError(e)
    }
    Format.Tar, Format.TarGz, Format.TarBz2, Format.TarXz -> tarStream(file).use { tar ->
        generateSequence { tar.nextEntry }.map { ArchiveItem(it.name, it.size, it.isDirectory) }.toList()
    }
    Format.Gz, Format.Bz2, Format.Xz -> listOf(ArchiveItem(archiveBaseName(file.name), -1, false))
    null -> throw ArchiveException("Format d'archive inconnu")
}

private fun tarStream(file: File): TarArchiveInputStream {
    val raw = file.inputStream().buffered()
    val inner: InputStream = when (formatOf(file.name)) {
        Format.TarGz -> GzipCompressorInputStream(raw)
        Format.TarBz2 -> BZip2CompressorInputStream(raw)
        Format.TarXz -> XZCompressorInputStream(raw)
        else -> raw
    }
    return TarArchiveInputStream(inner)
}

/** Taille décompressée totale (pour la progression ; 0 si inconnue). */
fun archiveSize(file: File): Long = runCatching { listArchive(file).sumOf { it.size.coerceAtLeast(0) } }.getOrDefault(0)

/**
 * Extrait [file] dans [destDir] (créé si besoin). Les éléments qui sortiraient du dossier sont ignorés.
 * [onBytes] reçoit les octets écrits ; [cancelled] est consulté entre deux blocs.
 */
fun extractArchive(file: File, destDir: File, onBytes: (Long) -> Unit, cancelled: () -> Boolean) {
    if (!destDir.isDirectory && !destDir.mkdirs()) throw IOException("Impossible de créer ${destDir.name}")
    fun write(target: File, input: InputStream) {
        target.parentFile?.mkdirs()
        target.outputStream().use { out ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                if (cancelled()) throw InterruptedException()
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                onBytes(n.toLong())
            }
        }
    }
    fun entry(name: String, directory: Boolean, open: () -> InputStream) {
        val target = safeTarget(destDir, name) ?: return
        if (directory) target.mkdirs() else open().use { write(target, it) }
    }
    when (formatOf(file.name)) {
        Format.Zip -> ZipFile.builder().setFile(file).get().use { zip ->
            zip.entries.toList().forEach { e -> entry(e.name, e.isDirectory) { zip.getInputStream(e) } }
        }
        Format.SevenZ -> SevenZFile.builder().setFile(file).get().use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                entry(e.name, e.isDirectory) { z.getInputStream(e) }
            }
        }
        Format.Rar -> try {
            Archive(file).use { rar ->
                if (rar.isEncrypted) throw ArchiveException("Archive protégée par mot de passe")
                rar.fileHeaders.forEach { h ->
                    val name = h.fileName.replace('\\', '/')
                    val target = safeTarget(destDir, name) ?: return@forEach
                    if (h.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        CountingOutput(target.outputStream(), onBytes, cancelled).use { rar.extractFile(h, it) }
                    }
                }
            }
        } catch (e: ArchiveException) {
            throw e
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            if (e.cause is InterruptedException) throw InterruptedException()
            rarError(e)
        }
        Format.Tar, Format.TarGz, Format.TarBz2, Format.TarXz -> tarStream(file).use { tar ->
            readEntries(tar) { e -> entry(e.name, e.isDirectory) { NonClosing(tar) } }
        }
        Format.Gz, Format.Bz2, Format.Xz -> {
            val raw = file.inputStream().buffered()
            val input = when (formatOf(file.name)) {
                Format.Gz -> GzipCompressorInputStream(raw)
                Format.Bz2 -> BZip2CompressorInputStream(raw)
                else -> XZCompressorInputStream(raw)
            }
            input.use { write(File(destDir, freeName(destDir, archiveBaseName(file.name))), it) }
        }
        null -> throw ArchiveException("Format d'archive inconnu")
    }
}

private fun <E : ArchiveEntry> readEntries(stream: ArchiveInputStream<E>, each: (E) -> Unit) {
    while (true) {
        val e = stream.nextEntry ?: break
        each(e)
    }
}

/** Flux d'une entrée TAR : le fermer ne doit pas fermer toute l'archive. */
private class NonClosing(private val inner: InputStream) : InputStream() {
    override fun read(): Int = inner.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)
    override fun close() = Unit
}

/** Sortie qui compte les octets écrits (progression des RAR) et s'arrête à l'annulation. */
private class CountingOutput(
    private val inner: java.io.OutputStream,
    private val onBytes: (Long) -> Unit,
    private val cancelled: () -> Boolean,
) : java.io.OutputStream() {
    override fun write(b: Int) {
        inner.write(b)
        onBytes(1)
    }
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (cancelled()) throw InterruptedException()
        inner.write(b, off, len)
        onBytes(len.toLong())
    }
    override fun close() = inner.close()
}
