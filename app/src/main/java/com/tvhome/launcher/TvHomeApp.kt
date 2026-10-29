package com.tvhome.launcher

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import com.tvhome.launcher.remote.PhoneRemoteServer
import com.tvhome.launcher.remote.PhoneRemoteActions
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.tvhome.launcher.apps.AppRepository
import com.tvhome.launcher.apps.TileImage
import com.tvhome.launcher.apps.TileImageCache
import com.tvhome.launcher.clock.SntpClient
import com.tvhome.launcher.clock.TrustedClock
import com.tvhome.launcher.net.UrlConnectionHttpGet
import com.tvhome.launcher.settings.LauncherPrefs
import com.tvhome.launcher.weather.Place
import com.tvhome.launcher.weather.Weather
import com.tvhome.launcher.weather.WeatherRepository
import com.tvhome.launcher.wallpaper.PixabayClient
import com.tvhome.launcher.wallpaper.RotationFileStore
import com.tvhome.launcher.wallpaper.WallpaperRotator
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
    }

    /** Box à 2 Go : Kairo en arrière-plan rend la mémoire des tuiles à l'appli lancée. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) container.tileImages.clear()
    }
}
