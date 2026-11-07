package com.vantaflow.kairo.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileOpsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun entry(name: String, dir: Boolean = false, size: Long = 0, modified: Long = 0) =
        Entry(name, "/x/$name", dir, size, modified, name.startsWith("."))

    @Test
    fun sizesInFrench() {
        assertEquals("512 o", formatSize(512))
        assertEquals("1,5 Ko", formatSize(1536))
        assertEquals("700 Mo", formatSize(700L * 1024 * 1024))
        assertEquals("4,2 Go", formatSize((4.2 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun kindsAndMimes() {
        assertEquals(Kind.Video, kindOf("Film.MKV", false))
        assertEquals(Kind.Apk, kindOf("app.apk", false))
        assertEquals(Kind.Folder, kindOf("Films.mkv", true))
        assertEquals("application/vnd.android.package-archive", mimeOf("x.apk"))
        assertEquals("*/*", mimeOf("inconnu.xyz"))
    }

    @Test
    fun foldersFirstNaturalOrderHiddenFiltered() {
        val list = listOf(entry("Épisode 10.mkv"), entry("épisode 2.mkv"), entry("Zeta", dir = true), entry(".cache", dir = true), entry("alpha", dir = true))
        val sorted = sortEntries(list, SortBy.Name, showHidden = false).map { it.name }
        assertEquals(listOf("alpha", "Zeta", "épisode 2.mkv", "Épisode 10.mkv"), sorted)
        assertEquals(5, sortEntries(list, SortBy.Name, showHidden = true).size)
    }

    @Test
    fun sortBySizeAndDate() {
        val list = listOf(entry("a", size = 1, modified = 3), entry("b", size = 3, modified = 1), entry("c", size = 2, modified = 2))
        assertEquals(listOf("b", "c", "a"), sortEntries(list, SortBy.Size, false).map { it.name })
        assertEquals(listOf("a", "c", "b"), sortEntries(list, SortBy.Date, false).map { it.name })
    }

    @Test
    fun freeNameAddsCounter() {
        val dir = tmp.newFolder()
        assertEquals("film.mkv", freeName(dir, "film.mkv"))
        File(dir, "film.mkv").writeText("x")
        assertEquals("film (2).mkv", freeName(dir, "film.mkv"))
        File(dir, "film (2).mkv").writeText("x")
        assertEquals("film (3).mkv", freeName(dir, "film.mkv"))
        File(dir, "Dossier").mkdir()
        assertEquals("Dossier (2)", freeName(dir, "Dossier"))
    }

    @Test
    fun namesValidated() {
        assertTrue(validName("Mes films"))
        assertFalse(validName(" "))
        assertFalse(validName("a/b"))
        assertFalse(validName(".."))
    }

    @Test
    fun copyFolderWithProgressAndMove() {
        val src = tmp.newFolder("src")
        File(src, "sub").mkdir()
        File(src, "a.txt").writeText("hello")
        File(src, "sub/b.txt").writeText("world!")
        val dest = tmp.newFolder("dest")
        var copied = 0L
        val out = copyInto(src, dest, { copied += it }, { false })
        assertEquals(11L, copied)
        assertEquals("world!", File(out, "sub/b.txt").readText())
        assertTrue(isInside(File(src, "sub"), src))
        assertFalse(isInside(dest, src))

        val moved = moveInto(File(src, "a.txt"), dest, {}, { false })
        assertFalse(File(src, "a.txt").exists())
        assertEquals("hello", moved.readText())
    }
}
