package com.tvhome.launcher.home

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tvhome.launcher.apps.AppInfo
import com.tvhome.launcher.reco.Channel
import com.tvhome.launcher.reco.EpgSource
import com.tvhome.launcher.reco.Program
import com.tvhome.launcher.clock.SntpClient
import com.tvhome.launcher.clock.TrustedClock
import com.tvhome.launcher.settings.LauncherPrefs
import com.tvhome.launcher.settings.WallpaperConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Africa/Casablanca")
    private val nowMs = LocalDateTime.of(2026, 10, 28, 20, 45).atZone(zone).toInstant().toEpochMilli()
    private val installed = listOf("a", "b", "c").map { AppInfo(it, it.uppercase(), hasBanner = false) }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.prefs(): LauncherPrefs {
        val file = File(tmp.root, "prefs.preferences_pb")
        return LauncherPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
    }

    private fun failingClock() = TrustedClock(SntpClient { _, _, _ -> null }, systemNow = { nowMs }, ioDispatcher = Dispatchers.Main)

    private val created = mutableListOf<HomeViewModel>()

    private fun program(id: Long, pkg: String) = Program(id, pkg, "t$id", null, null, null, null)

    private class FakeEpg(val ch: List<Channel>, val wn: List<Program>, val access: Boolean = true) : EpgSource {
        override fun channels() = ch
        override fun watchNext() = wn
        override fun hasAccess() = access
    }

    private var epg: EpgSource = FakeEpg(emptyList(), emptyList(), access = false)
    private var nextWallpaper: suspend (String) -> String? = { null }
    private var weather: suspend (String, Boolean) -> com.tvhome.launcher.weather.Weather? = { _, _ -> null }

    private fun vm(prefs: LauncherPrefs, launched: MutableList<String> = mutableListOf()) = HomeViewModel(
        installedApps = { installed },
        appChanges = emptyFlow(),
        prefs = prefs,
        clock = failingClock(),
        launcher = { launched += it },
        zone = zone,
        minuteTicks = flowOf(Unit),
        ioDispatcher = Dispatchers.Main,
        epg = epg,
        nextWallpaper = nextWallpaper,
        weather = weather,
    ).also { created += it }

    /** Arrête les coroutines du ViewModel avant la fin du test (DataStore lié au scope du test). */
    private suspend fun <T> withVm(block: suspend () -> T): T = try { block() } finally {
        created.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun favoritesComeFromResolvedPrefs() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setFavorites(listOf("c", "gone", "a"))
        val state = vm(prefs).state.first { it.favorites.isNotEmpty() }
        assertEquals(listOf("c", "a"), state.favorites.map { it.packageName })
    } }

    @Test
    fun firstLaunchPersistsDefaultFavorites() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        vm(prefs).state.first { it.favorites.isNotEmpty() }
        prefs.favoritesInitialized.first { it }
        assertEquals(listOf("a", "b", "c"), prefs.favorites.first())
    } }

    @Test
    fun stateAvailableWithoutNetwork() = runTest(UnconfinedTestDispatcher()) { withVm {
        val state = vm(prefs()).state.first { it.timeText.isNotEmpty() }
        assertEquals("20:45", state.timeText)
        assertEquals(WallpaperConfig.Default, state.wallpaper)
    } }

    @Test
    fun dateFormattedInFrench() {
        assertEquals("Mercredi 28 octobre", formatDate(nowMs, zone))
    }

    @Test
    fun timeFormatted24h() {
        assertEquals("20:45", formatTime(nowMs, zone))
    }

    @Test
    fun timeFormatted12h() {
        assertEquals("8:45 PM", formatTime(nowMs, zone, use24h = false))
    }

    @Test
    fun launchDelegatesToLauncher() = runTest(UnconfinedTestDispatcher()) { withVm {
        val launched = mutableListOf<String>()
        vm(prefs(), launched).launch("b")
        assertEquals(listOf("b"), launched)
    } }

    @Test
    fun recoRowsOrderedAndFiltered() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setRecoAppOrder(listOf("com.netflix"))
        prefs.setChannelHidden(3L, true)
        epg = FakeEpg(
            listOf(
                Channel(1, "com.youtube", "Tendances", listOf(program(10, "com.youtube"))),
                Channel(2, "com.netflix", "Top 10", listOf(program(20, "com.netflix"))),
                Channel(3, "com.netflix", "Masquée", listOf(program(30, "com.netflix"))),
            ),
            listOf(program(99, "com.netflix")),
        )
        val state = vm(prefs).state.first { it.recoRows.isNotEmpty() }
        assertEquals(listOf(2L, 1L), state.recoRows.map { it.id })
        assertEquals(listOf(99L), state.watchNext.map { it.id })
    } }

    @Test
    fun watchNextHiddenWhenDisabled() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setShowWatchNext(false)
        epg = FakeEpg(listOf(Channel(1, "a", "c", listOf(program(1, "a")))), listOf(program(99, "a")))
        val state = vm(prefs).state.first { it.recoRows.isNotEmpty() }
        assertEquals(emptyList<Program>(), state.watchNext)
    } }

    @Test
    fun pixabayRotationSetsCurrentImage() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setWallpaper(com.tvhome.launcher.settings.WallpaperConfig.Pixabay("nature", 30))
        nextWallpaper = { q -> "https://img/$q.jpg" }
        val state = vm(prefs).state.first { it.wallpaperImageUrl != null }
        assertEquals("https://img/nature.jpg", state.wallpaperImageUrl)
    } }

    @Test
    fun weatherShownInHeader() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setWeatherCity("Tunis")
        var asked = ""
        weather = { city, _ -> asked = city; com.tvhome.launcher.weather.Weather(22.4, 0, "Tunis") }
        val state = vm(prefs).state.first { it.weatherText != null }
        assertEquals("22° · Ensoleillé", state.weatherText)
        assertEquals("Tunis", asked)
    } }

    @Test
    fun weatherHiddenWhenDisabled() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setWeatherEnabled(false)
        weather = { _, _ -> com.tvhome.launcher.weather.Weather(22.4, 0, "Tunis") }
        val state = vm(prefs).state.first { it.timeText.isNotEmpty() }
        assertNull(state.weatherText)
    } }

    // Délai maximal : sans dédoublonnage des préférences, la rotation boucle sans fin.
    @Test(timeout = 20_000)
    fun pixabayFetchedOnceDespitePrefWrites() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setWallpaper(com.tvhome.launcher.settings.WallpaperConfig.Pixabay("nature", 30))
        var calls = 0
        nextWallpaper = { calls++; "https://img/$calls.jpg" }
        vm(prefs).state.first { it.wallpaperImageUrl != null }
        prefs.setThemeId("ambre")
        prefs.setUse24h(false)
        kotlinx.coroutines.yield()
        assertEquals(1, calls)
    } }

    @Test
    fun weatherDelays() {
        assertEquals(30 * 60_000L, weatherRetryDelayMs(success = true))
        assertEquals(2 * 60_000L, weatherRetryDelayMs(success = false))
        assertEquals(true, weatherIsStale(lastSuccessMs = 0L, nowMs = 2 * 60 * 60_000L + 1))
        assertEquals(false, weatherIsStale(lastSuccessMs = 0L, nowMs = 60 * 60_000L))
    }

    @Test
    fun pixabayRotationPausedInBackground() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        var calls = 0
        nextWallpaper = { calls++; "https://img/$calls.jpg" }
        val vm = vm(prefs)
        vm.setForeground(false)
        prefs.setWallpaper(com.tvhome.launcher.settings.WallpaperConfig.Pixabay("nature", 30))
        kotlinx.coroutines.yield()
        assertEquals(0, calls)
        vm.setForeground(true)
        vm.state.first { it.wallpaperImageUrl != null }
        assertEquals(1, calls)
    } }

    @Test(timeout = 20_000)
    fun pixabayNotChangedOnEachReturnHome() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setWallpaper(com.tvhome.launcher.settings.WallpaperConfig.Pixabay("nature", 30))
        var calls = 0
        nextWallpaper = { calls++; "https://img/$calls.jpg" }
        val vm = vm(prefs)
        vm.state.first { it.wallpaperImageUrl != null }
        // Lancer une appli puis revenir : même image tant que l'intervalle n'est pas écoulé.
        vm.setForeground(false)
        vm.setForeground(true)
        kotlinx.coroutines.yield()
        assertEquals(1, calls)
    } }

    @Test
    fun noEpgAccessGivesNoRows() = runTest(UnconfinedTestDispatcher()) { withVm {
        val state = vm(prefs()).state.first { it.timeText.isNotEmpty() }
        assertEquals(emptyList<Channel>(), state.recoRows)
        assertEquals(emptyList<Program>(), state.watchNext)
    } }
}
