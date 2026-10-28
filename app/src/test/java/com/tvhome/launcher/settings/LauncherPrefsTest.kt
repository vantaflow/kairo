package com.tvhome.launcher.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tvhome.launcher.apps.AppInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class LauncherPrefsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun app(pkg: String) = AppInfo(pkg, pkg, hasBanner = false)

    private fun TestScope.prefs(): LauncherPrefs {
        val file = File(tmp.root, "prefs.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        return LauncherPrefs(store)
    }

    @Test
    fun uninstalledFavoriteIsDropped() {
        val result = resolveFavorites(listOf("a", "gone", "b"), listOf(app("a"), app("b")), initialized = true)
        assertEquals(listOf("a", "b"), result.map { it.packageName })
    }

    @Test
    fun firstLaunchUsesFirstSixLeanbackApps() {
        val installed = (1..8).map { app("p$it") }
        val result = resolveFavorites(emptyList(), installed, initialized = false)
        assertEquals((1..6).map { "p$it" }, result.map { it.packageName })
    }

    @Test
    fun initializedEmptyStaysEmpty() {
        val result = resolveFavorites(emptyList(), listOf(app("a")), initialized = true)
        assertEquals(emptyList<AppInfo>(), result)
    }

    @Test
    fun moveFavoriteReorders() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setFavorites(listOf("a", "b", "c"))
        prefs.moveFavorite("c", 0)
        assertEquals(listOf("c", "a", "b"), prefs.favorites.first())
    }

    @Test
    fun addFavoriteIgnoresDuplicates() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.addFavorite("a")
        prefs.addFavorite("a")
        assertEquals(listOf("a"), prefs.favorites.first())
    }

    @Test
    fun setFavoritesMarksInitialized() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        assertEquals(false, prefs.favoritesInitialized.first())
        prefs.setFavorites(emptyList())
        assertEquals(true, prefs.favoritesInitialized.first())
    }

    @Test
    fun hiddenToggles() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setHidden("a", true)
        assertEquals(setOf("a"), prefs.hidden.first())
        prefs.setHidden("a", false)
        assertEquals(emptySet<String>(), prefs.hidden.first())
    }

    @Test
    fun wallpaperDefaultIsGradient() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(WallpaperConfig.Default, prefs().wallpaper.first())
        assertEquals(WallpaperConfig.Gradient(0xFF1C2A3A, 0xFF101216, 135), WallpaperConfig.Default)
    }

    @Test
    fun use24hDefaultsTrueAndToggles() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        assertEquals(true, prefs.use24h.first())
        prefs.setUse24h(false)
        assertEquals(false, prefs.use24h.first())
    }

    @Test
    fun wallpaperRoundTrips()= runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setWallpaper(WallpaperConfig.Solid(0xFF202124))
        assertEquals(WallpaperConfig.Solid(0xFF202124), prefs.wallpaper.first())
    }

    @Test
    fun unreadableStoreFallsBackToDefaults() = runTest(UnconfinedTestDispatcher()) {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("corrompu") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) = throw IOException()
        }
        val prefs = LauncherPrefs(broken)
        assertEquals(emptyList<String>(), prefs.favorites.first())
        assertEquals(WallpaperConfig.Default, prefs.wallpaper.first())
    }

    @Test
    fun keyBindingsSetAndRemove() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setKeyBinding(183, "com.netflix.ninja")
        assertEquals(mapOf(183 to "com.netflix.ninja"), prefs.keyBindings.first())
        prefs.setKeyBinding(183, null)
        assertEquals(emptyMap<Int, String>(), prefs.keyBindings.first())
    }

    @Test
    fun pixabayConfigRoundTrips() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setWallpaper(WallpaperConfig.Pixabay("mountains", 30))
        prefs.setPixabayCurrent("https://img.jpg")
        prefs.setPixabayKey("perso")
        assertEquals(WallpaperConfig.Pixabay("mountains", 30), prefs.wallpaper.first())
        assertEquals("https://img.jpg", prefs.pixabayCurrent.first())
        assertEquals("perso", prefs.pixabayKey.first())
    }

    @Test
    fun themeAndWeatherPrefsRoundTrip() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        assertEquals("google-tv", prefs.themeId.first())
        assertEquals(true, prefs.weatherEnabled.first())
        assertEquals("", prefs.weatherCity.first())
        assertEquals(false, prefs.useFahrenheit.first())
        prefs.setThemeId("ambre")
        prefs.setWeatherEnabled(false)
        prefs.setWeatherCity("Tunis")
        prefs.setUseFahrenheit(true)
        assertEquals("ambre", prefs.themeId.first())
        assertEquals(false, prefs.weatherEnabled.first())
        assertEquals("Tunis", prefs.weatherCity.first())
        assertEquals(true, prefs.useFahrenheit.first())
    }

    @Test
    fun customThemeAndLayoutRoundTrip() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setCustomTheme(accent = 0xFFFF0000, focusBorder = 0xFF00FF00, radiusDp = 20, scale = 1.15f)
        val theme = prefs.customTheme.first()
        assertEquals(0xFFFF0000, theme.accent)
        assertEquals(0xFF00FF00, theme.focusBorder)
        assertEquals(20, theme.tileRadiusDp)
        assertEquals(1.15f, theme.focusScale, 0.001f)
        val layout = com.tvhome.launcher.ui.theme.HomeLayout(showClock = false, showDate = true, showFavoritesLabel = false, showRowTitles = false, clockSizeSp = 96, tileWidthDp = 240)
        prefs.setHomeLayout(layout)
        assertEquals(layout, prefs.homeLayout.first())
    }

    @Test
    fun unrelatedWriteDoesNotReemitWallpaper() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        val seen = mutableListOf<WallpaperConfig>()
        val job = launch { prefs.wallpaper.collect { seen += it } }
        prefs.setThemeId("ambre")
        prefs.setPixabayCurrent("x")
        job.cancel()
        assertEquals(1, seen.size)
    }

    @Test
    fun recoPrefsRoundTrip() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        assertEquals(true, prefs.showWatchNext.first())
        prefs.setShowWatchNext(false)
        prefs.setChannelHidden(7L, true)
        prefs.setRecoAppHidden("com.netflix", true)
        prefs.setRecoAppOrder(listOf("a", "b"))
        assertEquals(false, prefs.showWatchNext.first())
        assertEquals(setOf(7L), prefs.hiddenChannels.first())
        assertEquals(setOf("com.netflix"), prefs.hiddenRecoApps.first())
        assertEquals(listOf("a", "b"), prefs.recoAppOrder.first())
        prefs.setChannelHidden(7L, false)
        assertEquals(emptySet<Long>(), prefs.hiddenChannels.first())
    }
}
