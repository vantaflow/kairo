package com.vantaflow.kairo

import com.vantaflow.kairo.apps.OurAppsStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import com.vantaflow.kairo.remote.PhoneRemoteServer
import com.vantaflow.kairo.remote.PhoneRemoteActions
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.vantaflow.kairo.apps.AppRepository
import com.vantaflow.kairo.apps.TileImage
import com.vantaflow.kairo.apps.TileImageCache
import com.vantaflow.kairo.clock.SntpClient
import com.vantaflow.kairo.clock.TrustedClock
import com.vantaflow.kairo.net.UrlConnectionHttpGet
import com.vantaflow.kairo.settings.LauncherPrefs
import com.vantaflow.kairo.weather.Place
import com.vantaflow.kairo.weather.Forecast
import com.vantaflow.kairo.weather.Weather
import com.vantaflow.kairo.weather.WeatherRepository
import com.vantaflow.kairo.wallpaper.PixabayClient
import com.vantaflow.kairo.wallpaper.RotationFileStore
import com.vantaflow.kairo.wallpaper.WallpaperRotator
import kotlinx.coroutines.flow.first
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "launcher")

/** Dépendances partagées de l'appli, construites à la main (pas de framework DI). */
class AppContainer(val app: Application) {
    val appRepository = AppRepository(app)
    val prefs = LauncherPrefs(app.dataStore)
    val clock = TrustedClock(SntpClient())
    val tileImages = TileImageCache<TileImage>()
    val http = UrlConnectionHttpGet
    /** Nos applis (Kairo Player, Ultra TV) : installation et mises à jour depuis GitHub. */
    val ourApps = OurAppsStore(app)

    private val weatherRepo = WeatherRepository(http)
    @Volatile private var placeCache: Pair<String, Place>? = null

    /** Météo pour une ville (vide = position par IP) ; la position est gardée en mémoire. */
    val weather: suspend (String, Boolean) -> Weather? = { city, fahrenheit ->
        placeFor(city)?.let { weatherRepo.current(it, fahrenheit) }
    }

    /** Prévisions sur 7 jours (écran Météo). */
    val forecast: suspend (String, Boolean) -> Forecast? = { city, fahrenheit ->
        placeFor(city)?.let { weatherRepo.forecast(it, fahrenheit) }
    }

    private fun placeFor(city: String): Place? =
        placeCache?.takeIf { it.first == city }?.second
            ?: (if (city.isBlank()) weatherRepo.locateByIp() else weatherRepo.geocode(city))
                ?.also { placeCache = city to it }

    /** Nettoyage automatique : applis inutilisées et mémoire basse. */
    val autoCleaner = com.vantaflow.kairo.system.AutoCleaner(
        app,
        enabled = { prefs.autoClean.first() },
        idleMinutes = { prefs.idleCloseMin.first() },
        installed = { appRepository.installedApps().map { it.packageName } },
    )

    fun startAutoClean() {
        autoCleaner.start(scope)
    }

    /** Bloqueur de pubs : filtre suivi à chaud (profil enfant = filtre famille). */
    fun watchAdBlock() {
        scope.launch {
            kotlinx.coroutines.flow.combine(prefs.adBlockFilter, prefs.currentProfile) { f, p ->
                if (p.kids) com.vantaflow.kairo.adblock.AdFilter.Family
                else com.vantaflow.kairo.adblock.AdFilter.entries.firstOrNull { it.name == f } ?: com.vantaflow.kairo.adblock.AdFilter.Ads
            }.collect { com.vantaflow.kairo.adblock.AdBlock.filter = it }
        }
    }

    /** Relance le bloqueur s'il est activé (au démarrage de la box, à l'arrivée sur l'accueil). */
    fun ensureAdBlock() {
        scope.launch {
            val strictDns = com.vantaflow.kairo.adblock.privateDnsHost(app) != null
            when {
                // DNS privé strict activé entre-temps : le VPN couperait internet, on l'arrête.
                strictDns && com.vantaflow.kairo.adblock.AdBlock.running.value -> com.vantaflow.kairo.adblock.AdBlock.stop(app)
                prefs.adBlockEnabled.first() && !com.vantaflow.kairo.adblock.AdBlock.running.value -> com.vantaflow.kairo.adblock.AdBlock.start(app)
            }
        }
    }

    /** Minuteur de mise en veille : coupe la radio puis met la box en veille (service d'accessibilité). */
    val sleepTimer = com.vantaflow.kairo.tools.SleepTimer(CoroutineScope(SupervisorJob() + Dispatchers.Main), onSleep = {
        com.vantaflow.kairo.radio.Radio.stop(app)
        com.vantaflow.kairo.remote.KeyRemapService.instance?.sleep()
    })

    /** Télécommande sur téléphone : démarrée ou arrêtée selon le réglage. */
    @Volatile private var phoneToken = ""
    val phoneRemote = PhoneRemoteServer({ phoneToken }, PhoneRemoteActions(app)::route)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun watchPhoneRemote() {
        scope.launch {
            prefs.phoneRemoteEnabled.collectLatest { on ->
                if (on) {
                    phoneToken = prefs.phoneRemoteToken()
                    phoneRemote.start()
                    // Le jeton peut être renouvelé depuis les réglages.
                    prefs.phoneRemoteTokenFlow.collect { phoneToken = it }
                } else {
                    phoneRemote.stop()
                }
            }
        }
    }

    private val rotationStore = RotationFileStore(File(app.filesDir, "pixabay_rotation.txt"))
    private val rotator = WallpaperRotator({ q -> PixabayClient(http) { pixabayKeyNow() }.search(q) }, rotationStore::store, rotationStore::load)
    @Volatile private var pixabayKeyCache: String = ""

    private fun pixabayKeyNow() = pixabayKeyCache.ifBlank { BuildConfig.PIXABAY_API_KEY }

    /** Prochaine image Pixabay (la liste d'un autre thème est ignorée par le rotator). */
    val nextWallpaper: suspend (String) -> String? = { query ->
        pixabayKeyCache = prefs.pixabayKey.first()
        rotator.next(query)
    }
}

class TvHomeApp : Application(), coil.ImageLoaderFactory {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.watchPhoneRemote()
        container.startAutoClean()
        container.watchAdBlock()
        container.ensureAdBlock()
        container.ourApps.checkUpdates()
    }

    /** Cache d'images plafonné : par défaut Coil prendrait 25 % du tas (64 Mo sur cette box). */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizeBytes(IMAGE_CACHE_BYTES).build() }
        .allowRgb565(true)
        .build()

    /** Box à 2 Go : Kairo en arrière-plan rend la mémoire des tuiles et des images à l'appli lancée. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            container.tileImages.clear()
            coil.Coil.imageLoader(this).memoryCache?.clear()
        }
    }

    private companion object {
        const val IMAGE_CACHE_BYTES = 24 * 1024 * 1024
    }
}
