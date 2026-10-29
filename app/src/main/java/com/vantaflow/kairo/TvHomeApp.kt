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
        val place = placeCache?.takeIf { it.first == city }?.second
            ?: (if (city.isBlank()) weatherRepo.locateByIp() else weatherRepo.geocode(city))
                ?.also { placeCache = city to it }
        place?.let { weatherRepo.current(it, fahrenheit) }
    }

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

class TvHomeApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.watchPhoneRemote()
        container.ourApps.checkUpdates()
    }

    /** Box à 2 Go : Kairo en arrière-plan rend la mémoire des tuiles à l'appli lancée. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) container.tileImages.clear()
    }
}
