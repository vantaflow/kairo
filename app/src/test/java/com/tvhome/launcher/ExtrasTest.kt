package com.tvhome.launcher

import android.content.Intent
import com.tvhome.launcher.apps.AppInfo
import com.tvhome.launcher.reco.Program
import com.tvhome.launcher.search.appSearchIntents
import com.tvhome.launcher.search.searchApps
import com.tvhome.launcher.search.searchPrograms
import com.tvhome.launcher.settings.exportPrefs
import com.tvhome.launcher.settings.importPrefs
import com.tvhome.launcher.system.formatMb
import com.tvhome.launcher.system.killablePackages
import com.tvhome.launcher.update.isNewer
import com.tvhome.launcher.update.parseLatestRelease
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtrasTest {
    // --- Mémoire
    @Test
    fun formatsMegabytes() {
        assertEquals("512 Mo", formatMb(512L * 1024 * 1024))
        assertEquals("1,5 Go", formatMb(1536L * 1024 * 1024))
    }

    @Test
    fun killableExcludesLauncherAndKept() {
        val result = killablePackages(listOf("a", "com.tvhome.launcher", "b", "c"), own = "com.tvhome.launcher", keep = setOf("b"))
        assertEquals(listOf("a", "c"), result)
    }

    // --- Sauvegarde
    @Test
    fun backupRoundTripsAllTypes() {
        val values = mapOf(
            "s" to "texte", "b" to true, "i" to 42, "l" to 0xFF101216, "f" to 1.15f, "ss" to setOf("x", "y"),
        )
        assertEquals(values, importPrefs(exportPrefs(values)))
    }

    @Test
    fun invalidBackupIsRejected() {
        assertNull(importPrefs("pas du json"))
        assertNull(importPrefs("""{"format":"autre"}"""))
    }

    // --- Recherche
    private val apps = listOf("Netflix", "YouTube", "Télé Maroc", "Kodi").map { AppInfo("pkg.$it", it, false) }

    @Test
    fun searchIgnoresCaseAndAccentsPrefixFirst() {
        assertEquals(listOf("Télé Maroc"), searchApps("tele", apps).map { it.label })
        assertEquals(listOf("YouTube"), searchApps("you", apps).map { it.label })
        assertEquals(emptyList<AppInfo>(), searchApps(" ", apps))
    }

    @Test
    fun searchProgramsByTitle() {
        val programs = listOf(Program(1, "a", "Le Bureau des légendes", null, null, null, null), Program(2, "a", "Autre", null, null, null, null))
        assertEquals(listOf(1L), searchPrograms("legendes", programs).map { it.id })
    }

    @Test
    fun appSearchIntentsCarryQuery() {
        val targets = appSearchIntents("dune")
        val yt = targets.first { it.packageName == "com.google.android.youtube.tv" }
        assertEquals(Intent.ACTION_SEARCH, yt.intent.action)
        assertEquals("dune", yt.intent.getStringExtra("query"))
        assertTrue(targets.any { it.packageName == "com.netflix.ninja" })
    }

    @Test
    fun unsupportedSearchFallsBackToGlobalSearch() {
        val targets = com.tvhome.launcher.search.searchTargets("dune", installed = setOf("com.google.android.youtube.tv", "com.netflix.ninja")) { intent ->
            intent.`package` == "com.google.android.youtube.tv" || intent.action == "android.search.action.GLOBAL_SEARCH"
        }
        assertEquals(listOf("YouTube", "Recherche Google TV"), targets.map { it.label })
    }

    // --- Mise à jour
    @Test
    fun versionComparison() {
        assertTrue(isNewer("v0.2.0", "0.1.9"))
        assertTrue(isNewer("v1.0.0", "0.9.12"))
        assertFalse(isNewer("v0.1.0", "0.1.0"))
        assertFalse(isNewer("v0.1.0", "0.2.0"))
        assertFalse(isNewer("nimporte", "0.1.0"))
    }

    @Test
    fun parsesReleaseApkAsset() {
        val json = """{"tag_name":"v0.3.0","body":"Notes","assets":[
            {"name":"notes.txt","url":"https://api/1"},
            {"name":"tvhome-0.3.0.apk","url":"https://api.github.com/repos/x/y/releases/assets/2"}]}"""
        val release = parseLatestRelease(json)!!
        assertEquals("v0.3.0", release.tag)
        assertEquals("https://api.github.com/repos/x/y/releases/assets/2", release.apkAssetUrl)
        assertNull(parseLatestRelease("""{"tag_name":"v1","assets":[]}"""))
    }

    @Test
    fun kidsBindingsLimitedToFavorites() {
        val bindings = mapOf(1 to "netflix", 2 to "store")
        assertEquals(bindings, com.tvhome.launcher.remote.effectiveBindings(bindings, kids = false, favorites = emptySet()))
        assertEquals(mapOf(1 to "netflix"), com.tvhome.launcher.remote.effectiveBindings(bindings, kids = true, favorites = setOf("netflix")))
    }

}
