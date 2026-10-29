package com.vantaflow.kairo.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vantaflow.kairo.settings.LauncherPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppsViewModel(
    installedApps: () -> List<AppInfo>,
    appChanges: Flow<Unit>,
    private val prefs: LauncherPrefs,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val showHidden = MutableStateFlow(false)

    val favorites: StateFlow<List<String>> =
        prefs.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hidden: StateFlow<Set<String>> =
        prefs.hidden.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val installed = merge(flowOf(Unit), appChanges).map { installedApps() }.flowOn(ioDispatcher)

    val apps: StateFlow<List<AppInfo>> =
        combine(installed, prefs.hidden, showHidden) { all, hiddenSet, show ->
            if (show) all else all.filterNot { it.packageName in hiddenSet }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleFavorite(pkg: String) {
        viewModelScope.launch {
            if (pkg in prefs.favorites.first()) prefs.removeFavorite(pkg) else prefs.addFavorite(pkg)
        }
    }

    /** Déplace un favori d'une position vers la gauche (-1) ou la droite (+1). */
    fun moveFavoriteBy(pkg: String, delta: Int) {
        viewModelScope.launch {
            val list = prefs.favorites.first()
            val index = list.indexOf(pkg)
            if (index < 0) return@launch
            val target = (index + delta).coerceIn(0, list.lastIndex)
            if (target != index) prefs.moveFavorite(pkg, target)
        }
    }

    fun toggleHidden(pkg: String) {
        viewModelScope.launch {
            prefs.setHidden(pkg, pkg !in prefs.hidden.first())
        }
    }
}
