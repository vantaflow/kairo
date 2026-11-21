package com.vantaflow.kairo.home

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.apps.sharedApps
import com.vantaflow.kairo.clock.SntpClient
import com.vantaflow.kairo.clock.TrustedClock
import com.vantaflow.kairo.reco.Channel
import com.vantaflow.kairo.reco.EpgSource
import com.vantaflow.kairo.reco.Program
import com.vantaflow.kairo.settings.LauncherPrefs
import com.vantaflow.kairo.system.orDefaultAfter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Première image de l'accueil : rien n'attend les préférences lentes, les recommandations ni plusieurs inventaires. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeStateTest {
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

    // --- Préférences lentes : valeurs par défaut après 700 ms ---

    @Test
    fun lookFallsBackToDefaultAfterTimeoutThenTakesRealValue() = runTest(StandardTestDispatcher()) {
        val prefs = MutableSharedFlow<String>()
        val seen = mutableListOf<String>()
        backgroundScope.launch { prefs.orDefaultAfter(LOOK_TIMEOUT_MS, "défaut").toList(seen) }
        advanceTimeBy(LOOK_TIMEOUT_MS - 1)
        runCurrent()
        assertEquals(emptyList<String>(), seen)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("défaut"), seen)
        prefs.emit("lu")
        runCurrent()
        assertEquals(listOf("défaut", "lu"), seen)
    }

    @Test
    fun lookReadInTimeShowsNoDefaultFrame() = runTest(StandardTestDispatcher()) {
        val prefs = flow { kotlinx.coroutines.delay(300); emit("lu"); kotlinx.coroutines.delay(5_000); emit("changé") }
        val seen = prefs.orDefaultAfter(LOOK_TIMEOUT_MS, "défaut").toList()
        assertEquals(listOf("lu", "changé"), seen)
    }

    // --- Un seul inventaire des applis ---

    @Test
    fun oneInventoryForThreeCollectors() = runTest(StandardTestDispatcher()) {
        val calls = AtomicInteger()
        val changes = MutableSharedFlow<Unit>()
        val shared = sharedApps(backgroundScope, { calls.incrementAndGet(); installed }, changes, StandardTestDispatcher(testScheduler))
        val got = List(3) { mutableListOf<List<AppInfo>>() }
        got.forEach { sink -> shared.filterNotNull().onEach { sink += it }.launchIn(backgroundScope) }
        runCurrent()
        assertEquals(1, calls.get())
        got.forEach { assertEquals(listOf(installed), it) }
        // Une installation : un seul nouvel inventaire (après le regroupement des événements), vu par les trois.
        changes.emit(Unit)
        advanceTimeBy(com.vantaflow.kairo.apps.PACKAGE_EVENTS_DEBOUNCE_MS)
        runCurrent()
        assertEquals(2, calls.get())
    }

    // --- État de l'accueil progressif ---

    private class FakeEpg(val calls: AtomicInteger = AtomicInteger()) : EpgSource {
        override fun channels(): List<Channel> { calls.incrementAndGet(); return emptyList() }
        override fun watchNext() = emptyList<Program>()
        override fun hasAccess() = true
    }

    @Test(timeout = 15_000)
    fun clockAndFavoritesShownWhileRecommendationsStillLoading() = runTest(UnconfinedTestDispatcher()) {
        val prefs = prefs()
        prefs.setFavorites(listOf("c", "a"))
        val blocked = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val io = executor.asCoroutineDispatcher()
        val vm = HomeViewModel(
            apps = flowOf(installed),
            prefs = prefs,
            clock = failingClock(),
            launcher = {},
            zone = zone,
            minuteTicks = flowOf(Unit),
            ioDispatcher = io,
            epg = FakeEpg(),
            // Source des recommandations qui ne répond pas.
            resumePrograms = { _, _ -> blocked.await(60, TimeUnit.SECONDS); emptyList() },
        )
        try {
            val state = vm.state.first { it.loaded }
            assertEquals("20:45", state.timeText)
            assertEquals(listOf("c", "a"), state.favorites.map { it.packageName })
            assertTrue(state.recoRows.isEmpty() && state.watchNext.isEmpty())
        } finally {
            // Arrêt complet avant resetMain : rien ne doit plus repartir vers le fil principal du test.
            vm.viewModelScope.cancel()
            blocked.countDown()
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun recommendationsReadOnceAtStartup() = runTest(UnconfinedTestDispatcher()) {
        val calls = AtomicInteger()
        var uptime = 0L
        val vm = HomeViewModel(
            apps = flowOf(installed),
            prefs = prefs(),
            clock = failingClock(),
            launcher = {},
            zone = zone,
            minuteTicks = flowOf(Unit),
            ioDispatcher = Dispatchers.Main,
            epg = FakeEpg(calls),
            uptimeMs = { uptime },
        )
        try {
            // Premier onResume juste après la création : pas de seconde lecture.
            uptime = 1_000
            vm.refreshReco()
            assertEquals(1, calls.get())
            // Retour sur l'accueil plus tard : relu.
            uptime = 60_000
            vm.refreshReco()
            assertEquals(2, calls.get())
        } finally {
            vm.viewModelScope.cancel()
        }
    }
}
