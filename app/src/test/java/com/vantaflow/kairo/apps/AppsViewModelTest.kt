package com.vantaflow.kairo.apps

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.vantaflow.kairo.settings.LauncherPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
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
class AppsViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val installed = listOf("a", "b", "c").map { AppInfo(it, it, hasBanner = false) }
    private val created = mutableListOf<AppsViewModel>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.prefs(): LauncherPrefs {
        val file = File(tmp.root, "prefs.preferences_pb")
        return LauncherPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
    }

    private fun vm(prefs: LauncherPrefs) =
        AppsViewModel({ installed }, emptyFlow(), prefs, ioDispatcher = Dispatchers.Main).also { created += it }

    private suspend fun withVm(block: suspend () -> Unit) = try { block() } finally {
        created.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun hiddenAppsExcludedByDefault() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setHidden("b", true)
        val apps = vm(prefs).apps.first { it.isNotEmpty() && it.none { a -> a.packageName == "b" } }
        assertEquals(listOf("a", "c"), apps.map { it.packageName })
    } }

    @Test
    fun showHiddenIncludesThem() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setHidden("b", true)
        val vm = vm(prefs)
        vm.showHidden.value = true
        val apps = vm.apps.first { it.size == 3 }
        assertEquals(listOf("a", "b", "c"), apps.map { it.packageName })
    } }

    @Test
    fun toggleFavoriteAddsThenRemoves() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        prefs.setFavorites(listOf("a"))
        val vm = vm(prefs)
        vm.toggleFavorite("b")
        assertEquals(listOf("a", "b"), prefs.favorites.first { it.size == 2 })
        vm.toggleFavorite("b")
        assertEquals(listOf("a"), prefs.favorites.first { it.size == 1 })
    } }

    @Test
    fun toggleHiddenHidesThenShows() = runTest(UnconfinedTestDispatcher()) { withVm {
        val prefs = prefs()
        val vm = vm(prefs)
        vm.toggleHidden("c")
        assertEquals(setOf("c"), prefs.hidden.first { it.isNotEmpty() })
        vm.toggleHidden("c")
        assertEquals(emptySet<String>(), prefs.hidden.first { it.isEmpty() })
    } }
}
