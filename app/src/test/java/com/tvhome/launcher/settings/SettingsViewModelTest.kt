package com.tvhome.launcher.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.tvhome.launcher.clock.SntpClient
import com.tvhome.launcher.clock.TrustedClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private val created = mutableListOf<SettingsViewModel>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.prefs(): LauncherPrefs {
        val file = File(tmp.root, "prefs.preferences_pb")
        return LauncherPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
    }

    private fun vm(prefs: LauncherPrefs) = SettingsViewModel(
        prefs,
        TrustedClock(SntpClient { _, _, _ -> null }, ioDispatcher = Dispatchers.Main),
    ).also { created += it }

    private suspend fun withVm(block: suspend () -> Unit) = try { block() } finally {
        created.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun moveRightSwapsWithNext() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setFavorites(listOf("a", "b", "c"))
        vm(prefs).moveFavoriteBy("a", +1)
        assertEquals(listOf("b", "a", "c"), prefs.favorites.first { it.first() == "b" })
    } }

    @Test
    fun moveLeftAtStartStays() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setFavorites(listOf("a", "b"))
        val vm = vm(prefs)
        vm.moveFavoriteBy("a", -1)
        vm.moveFavoriteBy("b", +1)
        assertEquals(listOf("a", "b"), prefs.favorites.first())
    } }

    @Test
    fun moveRecoAppStartsFromDisplayedOrder() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        val vm = vm(prefs)
        // Ordre affiché (aucun ordre enregistré) : a, b, c ; on monte c d'un cran.
        vm.moveRecoApp("c", -1, displayed = listOf("a", "b", "c"))
        assertEquals(listOf("a", "c", "b"), prefs.recoAppOrder.first { it.isNotEmpty() })
    } }
}
