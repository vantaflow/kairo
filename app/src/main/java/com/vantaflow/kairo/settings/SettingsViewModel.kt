package com.vantaflow.kairo.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vantaflow.kairo.clock.TrustedClock
import com.vantaflow.kairo.ui.theme.CUSTOM_THEME_ID
import com.vantaflow.kairo.ui.theme.HomeLayout
import com.vantaflow.kairo.ui.theme.LauncherTheme
import com.vantaflow.kairo.ui.theme.Themes
import com.vantaflow.kairo.reco.Channel
import com.vantaflow.kairo.reco.EpgSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class SettingsViewModel(
    private val prefs: LauncherPrefs,
    private val clock: TrustedClock,
    private val epg: EpgSource? = null,
    private val nextWallpaper: (suspend (String) -> String?)? = null,
) : ViewModel() {
    /** Une exception (TvProvider, réseau, écriture) est journalisée, elle ne fait plus tomber Kairo. */
    private val scope = viewModelScope + com.vantaflow.kairo.system.SafeHandler
    val themeId: StateFlow<String> = prefs.themeId.stateIn(scope, SharingStarted.Eagerly, "google-tv")
    val weatherEnabled: StateFlow<Boolean> = prefs.weatherEnabled.stateIn(scope, SharingStarted.Eagerly, true)
    val weatherCity: StateFlow<String> = prefs.weatherCity.stateIn(scope, SharingStarted.Eagerly, "")
    val useFahrenheit: StateFlow<Boolean> = prefs.useFahrenheit.stateIn(scope, SharingStarted.Eagerly, false)

    val customTheme: StateFlow<LauncherTheme> =
        prefs.customTheme.stateIn(scope, SharingStarted.Eagerly, Themes.first())
    val homeLayout: StateFlow<HomeLayout> =
        prefs.homeLayout.stateIn(scope, SharingStarted.Eagerly, HomeLayout())

    fun setCustomTheme(theme: LauncherTheme) {
        scope.launch {
            prefs.setCustomTheme(theme.accent, theme.focusBorder, theme.tileRadiusDp, theme.focusScale)
            prefs.setThemeId(CUSTOM_THEME_ID)
        }
    }

    fun setHomeLayout(layout: HomeLayout) {
        scope.launch { prefs.setHomeLayout(layout) }
    }

    fun setThemeId(id: String) {
        scope.launch { prefs.setThemeId(id) }
    }

    fun setWeatherEnabled(value: Boolean) {
        scope.launch { prefs.setWeatherEnabled(value) }
    }

    fun setWeatherCity(city: String) {
        scope.launch { prefs.setWeatherCity(city) }
    }

    fun setUseFahrenheit(value: Boolean) {
        scope.launch { prefs.setUseFahrenheit(value) }
    }

    val pixabayKey: StateFlow<String> =
        prefs.pixabayKey.stateIn(scope, SharingStarted.Eagerly, "")

    fun setPixabayKey(key: String) {
        scope.launch { prefs.setPixabayKey(key) }
    }

    /** Passe tout de suite à l'image suivante du thème Pixabay choisi. */
    fun nextPixabayImage() {
        val next = nextWallpaper ?: return
        scope.launch {
            val config = prefs.wallpaper.first() as? WallpaperConfig.Pixabay ?: return@launch
            withContext(Dispatchers.IO) { next(config.query) }?.let { prefs.setPixabayCurrent(it) }
        }
    }

    val showWatchNext: StateFlow<Boolean> =
        prefs.showWatchNext.stateIn(scope, SharingStarted.Eagerly, true)
    val recoAppOrder: StateFlow<List<String>> =
        prefs.recoAppOrder.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val hiddenChannels: StateFlow<Set<Long>> =
        prefs.hiddenChannels.stateIn(scope, SharingStarted.Eagerly, emptySet())
    val hiddenRecoApps: StateFlow<Set<String>> =
        prefs.hiddenRecoApps.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Chaînes lues pour l'écran de réglages, et accès au TvProvider. */
    val recoChannels = MutableStateFlow<List<Channel>>(emptyList())
    val recoAccess = MutableStateFlow(false)

    fun loadRecoChannels() {
        val source = epg ?: return
        scope.launch {
            recoChannels.value = withContext(Dispatchers.IO) { source.channels() }
            recoAccess.value = source.hasAccess()
        }
    }

    /** Déplace une appli dans l'ordre des rangées ; [displayed] = ordre actuellement affiché. */
    fun moveRecoApp(pkg: String, delta: Int, displayed: List<String>) {
        val index = displayed.indexOf(pkg)
        if (index < 0) return
        val target = (index + delta).coerceIn(0, displayed.lastIndex)
        if (target == index) return
        val reordered = displayed.toMutableList().apply { removeAt(index); add(target, pkg) }
        scope.launch { prefs.setRecoAppOrder(reordered) }
    }

    fun setShowWatchNext(value: Boolean) {
        scope.launch { prefs.setShowWatchNext(value) }
    }

    fun setChannelHidden(id: Long, hidden: Boolean) {
        scope.launch { prefs.setChannelHidden(id, hidden) }
    }

    fun setRecoAppHidden(pkg: String, hidden: Boolean) {
        scope.launch { prefs.setRecoAppHidden(pkg, hidden) }
    }

    val favorites: StateFlow<List<String>> =
        prefs.favorites.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val wallpaper: StateFlow<WallpaperConfig> =
        prefs.wallpaper.stateIn(scope, SharingStarted.Eagerly, WallpaperConfig.Default)
    val use24h: StateFlow<Boolean> =
        prefs.use24h.stateIn(scope, SharingStarted.Eagerly, true)
    val keyBindings: StateFlow<Map<Int, String>> =
        prefs.keyBindings.stateIn(scope, SharingStarted.Eagerly, emptyMap())
    val lastSyncMs: StateFlow<Long?> = clock.lastSyncMs
    val offsetMs: StateFlow<Long> = clock.offsetMs

    /** Déplace un favori d'une position vers la gauche (-1) ou la droite (+1). */
    fun moveFavoriteBy(pkg: String, delta: Int) {
        scope.launch {
            val list = prefs.favorites.first()
            val index = list.indexOf(pkg)
            if (index < 0) return@launch
            val target = (index + delta).coerceIn(0, list.lastIndex)
            if (target != index) prefs.moveFavorite(pkg, target)
        }
    }

    fun removeFavorite(pkg: String) {
        scope.launch { prefs.removeFavorite(pkg) }
    }

    fun setWallpaper(config: WallpaperConfig) {
        scope.launch { prefs.setWallpaper(config) }
    }

    fun setUse24h(value: Boolean) {
        scope.launch { prefs.setUse24h(value) }
    }

    fun setKeyBinding(keyCode: Int, pkg: String?) {
        scope.launch { prefs.setKeyBinding(keyCode, pkg) }
    }

    fun syncNow() {
        scope.launch { clock.sync() }
    }
}
