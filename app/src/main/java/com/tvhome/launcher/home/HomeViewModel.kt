package com.tvhome.launcher.home

import com.tvhome.launcher.reco.LiveChannel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvhome.launcher.apps.AppInfo
import com.tvhome.launcher.clock.TrustedClock
import com.tvhome.launcher.reco.Channel
import com.tvhome.launcher.reco.EpgSource
import com.tvhome.launcher.reco.Program
import kotlinx.coroutines.flow.first
import com.tvhome.launcher.reco.orderRows
import com.tvhome.launcher.weather.Weather
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import com.tvhome.launcher.settings.LauncherPrefs
import com.tvhome.launcher.settings.WallpaperConfig
import com.tvhome.launcher.settings.resolveFavorites
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HomeUiState(
    val timeText: String = "",
    val dateText: String = "",
    val favorites: List<AppInfo> = emptyList(),
    val wallpaper: WallpaperConfig = WallpaperConfig.Default,
    val watchNext: List<Program> = emptyList(),
    val recoRows: List<Channel> = emptyList(),
    val wallpaperImageUrl: String? = null,
    val weatherText: String? = null,
    /** Chaînes des tuners et entrées HDMI : rangées affichées seulement si la box en a. */
    val liveChannels: List<LiveChannel> = emptyList(),
    val tvInputs: List<LiveChannel> = emptyList(),
)

/** Données brutes lues dans le TvProvider. */
private class EpgSnapshot(
    val channels: List<Channel>,
    val watchNext: List<Program>,
    val live: List<LiveChannel> = emptyList(),
    val inputs: List<LiveChannel> = emptyList(),
)

private class RecoState(val watchNext: List<Program>, val rows: List<Channel>, val live: List<LiveChannel>, val inputs: List<LiveChannel>)

private const val WEATHER_REFRESH_MS = 30 * 60 * 1000L
private const val WEATHER_RETRY_MS = 2 * 60 * 1000L
private const val WEATHER_MAX_AGE_MS = 2 * 60 * 60 * 1000L

/** Prochain essai météo : 30 min après un succès, 2 min après un échec. */
fun weatherRetryDelayMs(success: Boolean): Long = if (success) WEATHER_REFRESH_MS else WEATHER_RETRY_MS

/** Météo trop ancienne pour être affichée (plus de 2 h sans mise à jour). */
fun weatherIsStale(lastSuccessMs: Long, nowMs: Long): Boolean = nowMs - lastSuccessMs > WEATHER_MAX_AGE_MS

private val TimeFormat24 = DateTimeFormatter.ofPattern("HH:mm")
private val TimeFormat12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

fun formatTime(epochMs: Long, zone: ZoneId, use24h: Boolean = true): String =
    (if (use24h) TimeFormat24 else TimeFormat12).format(Instant.ofEpochMilli(epochMs).atZone(zone))

fun formatDate(epochMs: Long, zone: ZoneId): String =
    DateFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))
        .replaceFirstChar { it.titlecase(Locale.FRENCH) }

/** Émet tout de suite puis à chaque changement de minute. */
fun minuteTicks(now: () -> Long = System::currentTimeMillis): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(60_000 - now() % 60_000)
    }
}

class HomeViewModel(
    private val installedApps: () -> List<AppInfo>,
    appChanges: Flow<Unit>,
    private val prefs: LauncherPrefs,
    private val clock: TrustedClock,
    private val launcher: (String) -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
    minuteTicks: Flow<Unit> = minuteTicks(clock::now),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val epg: EpgSource? = null,
    /** Prochaine image de fond pour un thème Pixabay (null si indisponible). */
    private val nextWallpaper: (suspend (String) -> String?)? = null,
    /** Météo pour une ville (vide = position automatique) ; null si indisponible. */
    private val weather: (suspend (String, Boolean) -> Weather?)? = null,
    /** Entrées HDMI de la box (vide si elle n'en a pas). */
    private val tvInputs: (() -> List<LiveChannel>)? = null,
) : ViewModel() {


    private val weatherText = MutableStateFlow<String?>(null)

    // Déclaré avant init (qui lance la rotation) : sinon encore nul au premier appel.
    /** Vrai tant que l'accueil est visible : la rotation des fonds s'arrête sinon (écran de veille, autre appli). */
    private val foreground = MutableStateFlow(true)


    private val epgSnapshot = MutableStateFlow(EpgSnapshot(emptyList(), emptyList()))

    private val reco: Flow<RecoState> = combine(
        epgSnapshot, prefs.showWatchNext, prefs.recoAppOrder, prefs.hiddenChannels, prefs.hiddenRecoApps,
    ) { snap, showWatchNext, order, hiddenChannels, hiddenApps ->
        val watchNext = if (showWatchNext) snap.watchNext.filter { it.packageName !in hiddenApps } else emptyList()
        RecoState(watchNext, orderRows(snap.channels, order, hiddenChannels, hiddenApps), snap.live, snap.inputs)
    }

    private val installed: Flow<List<AppInfo>> =
        merge(flowOf(Unit), appChanges).map { installedApps() }.flowOn(ioDispatcher)

    private val favorites: Flow<List<AppInfo>> =
        combine(prefs.favorites, prefs.favoritesInitialized, installed, prefs.currentProfile) { saved, initialized, apps, profile ->
            val resolved = resolveFavorites(saved, apps, initialized)
            if (!initialized && resolved.isNotEmpty()) {
                viewModelScope.launch { prefs.initializeFavorites(profile.id, resolved.map { it.packageName }) }
            }
            resolved
        }

    private val clockText: Flow<Pair<String, String>> =
        combine(minuteTicks, clock.offsetMs, prefs.use24h, weatherText) { _, _, use24h, weather ->
            val now = clock.now()
            val date = formatDate(now, zone)
            formatTime(now, zone, use24h) to (if (weather != null) "$date · $weather" else date)
        }

    val state: StateFlow<HomeUiState> =
        combine(clockText, favorites, prefs.wallpaper, reco, prefs.pixabayCurrent) { (time, date), favs, wallpaper, r, image ->
            HomeUiState(time, date, favs, wallpaper, r.watchNext, r.rows, image, weatherText.value, r.live, r.inputs)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        refreshReco()
        startWallpaperRotation()
        startWeather()
        viewModelScope.launch {
            while (true) {
                clock.sync()
                delay(TrustedClock.RESYNC_INTERVAL_MS)
            }
        }
    }

    /** À appeler quand le réseau revient. */
    fun syncClock() {
        viewModelScope.launch { clock.sync() }
    }

    /** Met à jour la météo toutes les 30 minutes tant qu'elle est activée. */
    private fun startWeather() {
        val fetch = weather ?: return
        viewModelScope.launch {
            combine(prefs.weatherEnabled, prefs.weatherCity, prefs.useFahrenheit, ::Triple).collectLatest { (enabled, city, fahrenheit) ->
                if (!enabled) {
                    weatherText.value = null
                    return@collectLatest
                }
                var lastSuccess = clock.now()
                while (true) {
                    val result = withContext(ioDispatcher) { runCatching { fetch(city, fahrenheit) }.getOrNull() }
                    if (result != null) {
                        weatherText.value = result.headerText()
                        lastSuccess = clock.now()
                    } else if (weatherIsStale(lastSuccess, clock.now())) {
                        weatherText.value = null
                    }
                    delay(weatherRetryDelayMs(result != null))
                }
            }
        }
    }

    /** Change l'image de fond Pixabay à l'intervalle choisi, tant que ce type de fond est actif. */
    fun setForeground(visible: Boolean) {
        foreground.value = visible
    }

    private fun startWallpaperRotation() {
        val next = nextWallpaper ?: return
        // Dernier changement d'image : au retour sur l'accueil, on attend la fin de l'intervalle
        // au lieu de changer d'image à chaque fois.
        var lastQuery: String? = null
        var lastChangeMs = 0L
        viewModelScope.launch {
            combine(prefs.wallpaper, foreground, ::Pair).collectLatest { (config, visible) ->
                if (config !is WallpaperConfig.Pixabay || !visible) return@collectLatest
                val interval = config.intervalMin.coerceAtLeast(5) * 60_000L
                while (true) {
                    val elapsed = System.currentTimeMillis() - lastChangeMs
                    if (config.query == lastQuery && elapsed in 0 until interval) delay(interval - elapsed)
                    withContext(ioDispatcher) { next(config.query) }?.let { prefs.setPixabayCurrent(it) }
                    lastQuery = config.query
                    lastChangeMs = System.currentTimeMillis()
                }
            }
        }
    }

    /** Relit les recommandations (au démarrage et à chaque retour sur l'accueil). */
    fun refreshReco() {
        viewModelScope.launch {
            if (epg == null && tvInputs == null) return@launch
            epgSnapshot.value = withContext(ioDispatcher) {
                val inputs = runCatching { tvInputs?.invoke() }.getOrNull().orEmpty()
                epg?.let { source ->
                    runCatching { EpgSnapshot(source.channels(), source.watchNext(), source.liveChannels(), inputs) }.getOrNull()
                } ?: EpgSnapshot(emptyList(), emptyList(), inputs = inputs)
            }
        }
    }

    fun launch(pkg: String) = launcher(pkg)
}
