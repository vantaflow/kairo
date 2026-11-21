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
import kotlinx.coroutines.plus

class AppsViewModel(
    installedApps: () -> List<AppInfo>,
    appChanges: Flow<Unit>,
    private val prefs: LauncherPrefs,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    /** Une exception de lecture ou d'écriture est journalisée, elle ne fait plus tomber Kairo. */
    private val scope = viewModelScope + com.vantaflow.kairo.system.SafeHandler
    val showHidden = MutableStateFlow(false)

    val favorites: StateFlow<List<String>> =
        prefs.favorites.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val hidden: StateFlow<Set<String>> =
        prefs.hidden.stateIn(scope, SharingStarted.Eagerly, emptySet())

    private val installed = merge(flowOf(Unit), appChanges).map { runCatching { installedApps() }.getOrDefault(emptyList()) }.flowOn(ioDispatcher)

    val apps: StateFlow<List<AppInfo>> =
        combine(installed, prefs.hidden, showHidden) { all, hiddenSet, show ->
            if (show) all else all.filterNot { it.packageName in hiddenSet }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Applis non masquées, pour l'accueil et la recherche : l'interrupteur de l'écran Applis n'y change rien. */
    val visibleApps: StateFlow<List<AppInfo>> =
        combine(installed, prefs.hidden) { all, hiddenSet -> all.filterNot { it.packageName in hiddenSet } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun toggleFavorite(pkg: String) {
        scope.launch {
            if (pkg in prefs.favorites.first()) prefs.removeFavorite(pkg) else prefs.addFavorite(pkg)
        }
    }

    /** Déplace un favori d'une position vers la gauche (-1) ou la droite (+1). */
    fun moveFavoriteBy(pkg: String, delta: Int) {
        scope.launch {
            val list = prefs.favorites.first()
            // Voisin pris parmi les favoris affichés : la liste enregistrée garde les applis masquées ou
            // désinstallées, par-dessus lesquelles un appui « ne faisait rien ».
            val shown = visibleApps.value.map { it.packageName }.toSet()
            val target = favoriteMoveTarget(list, list.filter { it in shown }, pkg, delta) ?: return@launch
            prefs.moveFavorite(pkg, target)
        }
    }

    fun toggleHidden(pkg: String) {
        scope.launch {
            prefs.setHidden(pkg, pkg !in prefs.hidden.first())
        }
    }
}

/**
 * Position, dans la liste enregistrée [all], où placer [pkg] pour qu'il passe par-dessus son voisin
 * affiché ([shown], dans le même ordre) vers la gauche (-1) ou la droite (+1) ; null s'il est au bout.
 */
fun favoriteMoveTarget(all: List<String>, shown: List<String>, pkg: String, delta: Int): Int? {
    val neighbour = shown.getOrNull(shown.indexOf(pkg).takeIf { it >= 0 }?.plus(delta) ?: return null) ?: return null
    return all.indexOf(neighbour).takeIf { it >= 0 }
}
