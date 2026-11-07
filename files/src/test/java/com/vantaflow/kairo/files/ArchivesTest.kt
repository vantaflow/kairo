package com.vantaflow.kairo.files

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchivesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(file: File, entries: Map<String, String>) = ZipOutputStream(file.outputStream()).use { z ->
        entries.forEach { (name, text) ->
            z.putNextEntry(ZipEntry(name))
            z.write(text.toByteArray())
            z.closeEntry()
        }
    }

    @Test
    fun recognisesFormats() {
        assertTrue(isArchive("Photos.ZIP"))
        assertTrue(isArchive("film.rar"))
        assertTrue(isArchive("site.tar.gz"))
        assertFalse(isArchive("film.mkv"))
        assertEquals("Photos", archiveBaseName("Photos.zip"))
        assertEquals("site", archiveBaseName("site.tar.gz"))
    }

    @Test
    fun listsAndExtractsZip() {
        val file = tmp.newFile("Vacances été.zip")
        zip(file, mapOf("photos/a.txt" to "bonjour", "b.txt" to "été"))
        val items = listArchive(file)
        assertEquals(listOf("photos/a.txt", "b.txt"), items.map { it.name })
        val dest = File(tmp.root, "out")
        var bytes = 0L
        extractArchive(file, dest, { bytes += it }, { false })
        assertEquals("bonjour", File(dest, "photos/a.txt").readText())
        assertEquals("été", File(dest, "b.txt").readText())
        assertEquals(7L + "été".toByteArray().size, bytes)
    }

    @Test
    fun zipSlipIsIgnored() {
        val file = tmp.newFile("piege.zip")
        zip(file, mapOf("../../evil.txt" to "x", "ok.txt" to "y"))
        val dest = File(tmp.root, "out")
        extractArchive(file, dest, {}, { false })
        assertFalse(File(tmp.root, "evil.txt").exists())
        assertFalse(File(tmp.root.parentFile, "evil.txt").exists())
        assertTrue(File(dest, "ok.txt").exists())
        assertNull(safeTarget(dest, "../x"))
    }

    @Test
    fun extractsTarGz() {
        val file = tmp.newFile("site.tar.gz")
        TarArchiveOutputStream(GzipCompressorOutputStream(file.outputStream())).use { tar ->
            val data = "contenu".toByteArray()
            tar.putArchiveEntry(TarArchiveEntry("dossier/index.html").apply { size = data.size.toLong() })
            tar.write(data)
            tar.closeArchiveEntry()
        }
        assertEquals(listOf("dossier/index.html"), listArchive(file).map { it.name })
        val dest = File(tmp.root, "site")
        extractArchive(file, dest, {}, { false })
        assertEquals("contenu", File(dest, "dossier/index.html").readText())
    }

    @Test(expected = InterruptedException::class)
    fun cancellationStops() {
        val file = tmp.newFile("a.zip")
        zip(file, mapOf("a.txt" to "x".repeat(10_000)))
        extractArchive(file, File(tmp.root, "o"), {}, { true })
    }
}
