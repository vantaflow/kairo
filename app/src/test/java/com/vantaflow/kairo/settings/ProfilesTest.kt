package com.vantaflow.kairo.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProfilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.prefs(): LauncherPrefs {
        val file = File(tmp.root, "prefs.preferences_pb")
        return LauncherPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
    }

    @Test
    fun defaultProfileExists() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        assertEquals(DEFAULT_PROFILE_ID, prefs.currentProfile.first().id)
        assertEquals(listOf(DEFAULT_PROFILE_ID), prefs.profiles.first().map { it.id })
    }

    @Test
    fun favoritesAndWallpaperIsolatedPerProfile() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setFavorites(listOf("a"))
        prefs.setWallpaper(WallpaperConfig.Solid(0xFF000000))
        val kid = prefs.addProfile("Enfants", kids = true, pin = "1234")
        prefs.switchProfile(kid.id)
        assertEquals(emptyList<String>(), prefs.favorites.first())
        assertEquals(WallpaperConfig.Default, prefs.wallpaper.first())
        prefs.setFavorites(listOf("b"))
        prefs.switchProfile(DEFAULT_PROFILE_ID)
        assertEquals(listOf("a"), prefs.favorites.first())
        assertEquals(WallpaperConfig.Solid(0xFF000000), prefs.wallpaper.first())
    }

    @Test
    fun pinChecked() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        val kid = prefs.addProfile("Enfants", kids = true, pin = "1234")
        assertTrue(checkPin("1234", kid.pinHash))
        assertFalse(checkPin("0000", kid.pinHash))
        assertTrue(checkPin("", null))
    }

    @Test
    fun removingCurrentProfileFallsBackToDefault() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        val p = prefs.addProfile("Invité", kids = false, pin = null)
        prefs.switchProfile(p.id)
        prefs.removeProfile(p.id)
        assertEquals(DEFAULT_PROFILE_ID, prefs.currentProfile.first().id)
        assertEquals(1, prefs.profiles.first().size)
    }

    @Test
    fun exportImportRoundTrip() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setFavorites(listOf("x", "y"))
        prefs.setThemeId("ambre")
        val json = prefs.exportJson()
        prefs.setFavorites(emptyList())
        prefs.setThemeId("minimal")
        assertTrue(prefs.importJson(json))
        assertEquals(listOf("x", "y"), prefs.favorites.first())
        assertEquals("ambre", prefs.themeId.first())
    }

    @Test(expected = IllegalArgumentException::class)
    fun kidsProfileRequiresPin() = runTest(UnconfinedTestDispatcher()) {
        prefs().addProfile("Enfants", kids = true, pin = null)
    }

    @Test
    fun kidsProfileStartsWithoutFavorites() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        val kid = prefs.addProfile("Enfants", kids = true, pin = "1234")
        prefs.switchProfile(kid.id)
        assertEquals(true, prefs.favoritesInitialized.first())
        assertEquals(emptyList<String>(), prefs.favorites.first())
    }

    @Test
    fun favoritesInitIgnoredAfterProfileSwitch() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        val guest = prefs.addProfile("Invité", kids = false, pin = null)
        prefs.switchProfile(guest.id)
        prefs.initializeFavorites(DEFAULT_PROFILE_ID, listOf("a", "b"))
        assertEquals(false, prefs.favoritesInitialized.first())
        prefs.initializeFavorites(guest.id, listOf("c"))
        assertEquals(listOf("c"), prefs.favorites.first())
    }

    @Test
    fun backupExcludesAndPreservesSecrets() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setGithubToken("ghp_secret")
        prefs.setPixabayKey("pix")
        val json = prefs.exportJson()
        assertFalse(json.contains("ghp_secret"))
        assertFalse(json.contains("\"pix\""))
        assertTrue(prefs.importJson(json))
        assertEquals("ghp_secret", prefs.githubToken.first())
        assertEquals("pix", prefs.pixabayKey.first())
    }

    @Test
    fun pixabayImageIsPerProfile() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setPixabayCurrent("https://principal.jpg")
        val guest = prefs.addProfile("Invité", kids = false, pin = null)
        prefs.switchProfile(guest.id)
        assertEquals(null, prefs.pixabayCurrent.first())
        prefs.switchProfile(DEFAULT_PROFILE_ID)
        assertEquals("https://principal.jpg", prefs.pixabayCurrent.first())
    }

    @Test
    fun removingProfileDeletesItsSettings() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        val guest = prefs.addProfile("Invité", kids = false, pin = null)
        prefs.switchProfile(guest.id)
        prefs.setFavorites(listOf("x"))
        prefs.switchProfile(DEFAULT_PROFILE_ID)
        prefs.removeProfile(guest.id)
        assertFalse(prefs.exportJson().contains("@${guest.id}"))
    }

    @Test
    fun unknownCurrentProfileUsesDefaultKeys() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setFavorites(listOf("a"))
        prefs.switchProfile("disparu")
        assertEquals(DEFAULT_PROFILE_ID, prefs.currentProfile.first().id)
        assertEquals(listOf("a"), prefs.favorites.first())
    }

    @Test
    fun pinLockedAfterFiveFailures() {
        var now = 0L
        val guard = PinGuard(now = { now })
        repeat(5) { assertFalse(guard.attempt("0000", hashPin("1234"))) }
        assertTrue(guard.lockedForMs() > 0)
        assertFalse(guard.attempt("1234", hashPin("1234")))
        now += 31_000
        assertTrue(guard.attempt("1234", hashPin("1234")))
        assertEquals(0L, guard.lockedForMs())
    }

    @Test
    fun githubTokenRoundTrip() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setGithubToken(" ghp_abc ")
        assertEquals("ghp_abc", prefs.githubToken.first())
    }
}
