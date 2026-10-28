package com.tvhome.launcher.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvhome.launcher.clock.TrustedClock
import com.tvhome.launcher.ui.theme.CUSTOM_THEME_ID
import com.tvhome.launcher.ui.theme.HomeLayout
import com.tvhome.launcher.ui.theme.LauncherTheme
import com.tvhome.launcher.ui.theme.Themes
import com.tvhome.launcher.reco.Channel
import com.tvhome.launcher.reco.EpgSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: LauncherPrefs,
    private val clock: TrustedClock,
    private val epg: EpgSource? = null,
    private val nextWallpaper: (suspend (String) -> String?)? = null,
) : ViewModel() {
    val themeId: StateFlow<String> = prefs.themeId.stateIn(viewModelScope, SharingStarted.Eagerly, "google-tv")
    val weatherEnabled: StateFlow<Boolean> = prefs.weatherEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val weatherCity: StateFlow<String> = prefs.weatherCity.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val useFahrenheit: StateFlow<Boolean> = prefs.useFahrenheit.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val customTheme: StateFlow<LauncherTheme> =
        prefs.customTheme.stateIn(viewModelScope, SharingStarted.Eagerly, Themes.first())
    val homeLayout: StateFlow<HomeLayout> =
        prefs.homeLayout.stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayout())

    fun setCustomTheme(theme: LauncherTheme) {
        viewModelScope.launch {
            prefs.setCustomTheme(theme.accent, theme.focusBorder, theme.tileRadiusDp, theme.focusScale)
            prefs.setThemeId(CUSTOM_THEME_ID)
        }
    }

    fun setHomeLayout(layout: HomeLayout) {
        viewModelScope.launch { prefs.setHomeLayout(layout) }
    }

    fun setThemeId(id: String) {
        viewModelScope.launch { prefs.setThemeId(id) }
    }

    fun setWeatherEnabled(value: Boolean) {
        viewModelScope.launch { prefs.setWeatherEnabled(value) }
    }

    fun setWeatherCity(city: String) {
        viewModelScope.launch { prefs.setWeatherCity(city) }
    }

    fun setUseFahrenheit(value: Boolean) {
        viewModelScope.launch { prefs.setUseFahrenheit(value) }
    }

    val pixabayKey: StateFlow<String> =
        prefs.pixabayKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setPixabayKey(key: String) {
        viewModelScope.launch { prefs.setPixabayKey(key) }
    }

    /** Passe tout de suite à l'image suivante du thème Pixabay choisi. */
    fun nextPixabayImage() {
        val next = nextWallpaper ?: return
        viewModelScope.launch {
            val config = prefs.wallpaper.first() as? WallpaperConfig.Pixabay ?: return@launch
            withContext(Dispatchers.IO) { next(config.query) }?.let { prefs.setPixabayCurrent(it) }
        }
    }

    val showWatchNext: StateFlow<Boolean> =
        prefs.showWatchNext.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val recoAppOrder: StateFlow<List<String>> =
        prefs.recoAppOrder.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hiddenChannels: StateFlow<Set<Long>> =
        prefs.hiddenChannels.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val hiddenRecoApps: StateFlow<Set<String>> =
        prefs.hiddenRecoApps.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Chaînes lues pour l'écran de réglages, et accès au TvProvider. */
    val recoChannels = MutableStateFlow<List<Channel>>(emptyList())
    val recoAccess = MutableStateFlow(false)

    fun loadRecoChannels() {
        val source = epg ?: return
        viewModelScope.launch {
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
        viewModelScope.launch { prefs.setRecoAppOrder(reordered) }
    }

    fun setShowWatchNext(value: Boolean) {
        viewModelScope.launch { prefs.setShowWatchNext(value) }
    }

    fun setChannelHidden(id: Long, hidden: Boolean) {
        viewModelScope.launch { prefs.setChannelHidden(id, hidden) }
    }

    fun setRecoAppHidden(pkg: String, hidden: Boolean) {
        viewModelScope.launch { prefs.setRecoAppHidden(pkg, hidden) }
    }

    val favorites: StateFlow<List<String>> =
        prefs.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val wallpaper: StateFlow<WallpaperConfig> =
        prefs.wallpaper.stateIn(viewModelScope, SharingStarted.Eagerly, WallpaperConfig.Default)
    val use24h: StateFlow<Boolean> =
        prefs.use24h.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val keyBindings: StateFlow<Map<Int, String>> =
        prefs.keyBindings.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val lastSyncMs: StateFlow<Long?> = clock.lastSyncMs
    val offsetMs: StateFlow<Long> = clock.offsetMs

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

    fun removeFavorite(pkg: String) {
        viewModelScope.launch { prefs.removeFavorite(pkg) }
    }

    fun setWallpaper(config: WallpaperConfig) {
        viewModelScope.launch { prefs.setWallpaper(config) }
    }

    fun setUse24h(value: Boolean) {
        viewModelScope.launch { prefs.setUse24h(value) }
    }

    fun setKeyBinding(keyCode: Int, pkg: String?) {
        viewModelScope.launch { prefs.setKeyBinding(keyCode, pkg) }
    }

    fun syncNow() {
        viewModelScope.launch { clock.sync() }
    }
}
