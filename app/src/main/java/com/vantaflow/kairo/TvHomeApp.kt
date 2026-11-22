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
import com.vantaflow.kairo.settings.launcherCorruptionHandler
import com.vantaflow.kairo.system.CrashGuard
import com.vantaflow.kairo.system.SafeHandler
import com.vantaflow.kairo.weather.Place
import com.vantaflow.kairo.weather.Forecast
import com.vantaflow.kairo.weather.Weather
import com.vantaflow.kairo.weather.WeatherRepository
import com.vantaflow.kairo.wallpaper.PixabayClient
import com.vantaflow.kairo.wallpaper.RotationFileStore
import com.vantaflow.kairo.wallpaper.WallpaperRotator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/** Fichier corrompu remplacé par des préférences vides : sinon chaque écriture échouerait à chaque lancement. */
private val Context.dataStore by preferencesDataStore(name = "launcher", corruptionHandler = launcherCorruptionHandler())

/** Dépendances partagées de l'appli, construites à la main (pas de framework DI). */
class AppContainer(val app: Application) {
    val appRepository = AppRepository(app)
    val prefs = LauncherPrefs(app.dataStore) { e -> android.util.Log.w("Kairo", "écriture des préférences impossible", e) }
    val clock = TrustedClock(SntpClient())
    val tileImages = TileImageCache<TileImage>()
    val http = UrlConnectionHttpGet

    /** Catalogue de films et séries (TMDB), gardé sur la box une journée. */
    val catalog = com.vantaflow.kairo.catalog.CatalogRepository(
        BuildConfig.TMDB_READ_TOKEN.takeIf { it.isNotBlank() }?.let { com.vantaflow.kairo.catalog.tmdbHttp(it, ::catalogLanguage) },
        java.io.File(app.filesDir, "catalog.json"),
        region = ::catalogRegion,
        language = ::catalogLanguage,
        // Titres des rangées dans la langue de l'appli (la langue fait partie de la clé du cache).
        text = { id, args -> app.getString(id, *args) },
    )

    /**
     * Pays du catalogue : là où se trouve la box (adresse IP, gardée pour la session), sinon celui de
     * la langue de la box (une box en français au Maroc est réglée « France »), sinon les États-Unis.
     */
    @Volatile private var ipRegion: String? = null

    private fun catalogRegion(): String {
        ipRegion?.let { return it }
        val fromIp = runCatching {
            org.json.JSONObject(http.get("https://get.geojs.io/v1/ip/country.json")!!).optString("country")
        }.getOrNull()?.takeIf { it.length == 2 }?.uppercase()
        if (fromIp != null) return fromIp.also { ipRegion = it }
        return java.util.Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase() ?: com.vantaflow.kairo.catalog.DEFAULT_REGION
    }

    /** Langue des titres : celle de l'appli (Réglages › Langue, par défaut celle de la box : « fr-FR », « ar-MA »…). */
    private fun catalogLanguage(): String = com.vantaflow.kairo.i18n.AppLocale.catalogLanguage()
    /** Nos applis (Kairo Player, Ultra TV) : installation et mises à jour depuis GitHub. */
    val ourApps = OurAppsStore(app, autoUpdate = { prefs.autoUpdate.first() })

    private val weatherRepo = WeatherRepository(http)
    @Volatile private var placeCache: Pair<String, Place>? = null

    /** Météo pour une ville (vide = position par IP) ; la position est gardée en mémoire. */
    val weather: suspend (String, Boolean) -> Weather? = { city, fahrenheit ->
        // Libellé du ciel dans la langue de l'appli (l'en-tête n'a pas de Context).
        placeFor(city)?.let { weatherRepo.current(it, fahrenheit) }
            ?.let { w -> w.copy(label = com.vantaflow.kairo.weather.weatherLabel(app, w.code)) }
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
        // Appel direct (pas l'inventaire partagé, qui ne tourne qu'avec l'accueil) ; une erreur ne nettoie rien.
        installed = { runCatching { appRepository.installedApps().map { it.packageName } }.getOrDefault(emptyList()) },
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
    val sleepTimer = com.vantaflow.kairo.tools.SleepTimer(CoroutineScope(SupervisorJob() + Dispatchers.Main + SafeHandler), onSleep = {
        com.vantaflow.kairo.radio.Radio.stop(app)
        com.vantaflow.kairo.remote.KeyRemapService.instance?.sleep()
    })

    /** Télécommande sur téléphone : démarrée ou arrêtée selon le réglage. */
    @Volatile private var phoneToken = ""
    val phoneRemote = PhoneRemoteServer({ phoneToken }, PhoneRemoteActions(app)::route, { app.getString(R.string.phoneremote_forbidden) })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + SafeHandler)

    /**
     * Chaînes d'Ultra TV (programme en cours et suivant), lues dans le fournisseur qu'elle réserve à
     * Kairo. Démarrée par la porte de démarrage : la lecture démarre le process d'Ultra TV.
     */
    val ultraTv = com.vantaflow.kairo.ultratv.UltraTvSource(
        // Inventaire partagé : aucun parcours du PackageManager en plus.
        installed = appRepository.apps.filterNotNull().map { apps -> apps.mapTo(HashSet()) { it.packageName } },
        query = ::queryUltraTv,
        changes = ::ultraTvChanges,
        // Paquet qui déclare l'autorité (sans démarrer son process) ; illisible → aucune lecture.
        ownerOf = { authority -> runCatching { app.packageManager.resolveContentProvider(authority, 0)?.packageName }.getOrNull() },
        uptimeMs = android.os.SystemClock::elapsedRealtime,
        scope = scope,
    )

    /** Un seul fil, à part, pour les requêtes vers Ultra TV : une appli bloquée n'affame jamais le pool IO. */
    private val ultraTvExecutor by lazy {
        java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "kairo-ultratv").apply { isDaemon = true } }
    }

    /**
     * Lecture annulable : la requête tourne sur le fil dédié ; si la coroutine est annulée (délai
     * dépassé, changement de paquet), le signal interrompt la requête. La fonction ne rend la main
     * qu'une fois la requête réellement terminée : la source sait ainsi qu'elle est encore en cours.
     */
    private suspend fun queryUltraTv(authority: String, path: String): List<Map<String, Any?>>? {
        val signal = android.os.CancellationSignal()
        val done = kotlinx.coroutines.CompletableDeferred<List<Map<String, Any?>>?>()
        ultraTvExecutor.execute {
            try {
                // Annulée pendant son attente : surtout ne pas interroger (cela démarrerait Ultra TV).
                done.complete(if (signal.isCanceled) null else readUltraTv(authority, path, signal))
            } finally {
                done.complete(null)
            }
        }
        return try {
            done.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            runCatching { signal.cancel() }
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { done.await() }
            throw e
        }
    }

    /** Lignes de content://[authority]/[path] (nom de colonne → valeur), 20 au plus ; null en cas d'échec ou d'annulation. */
    private fun readUltraTv(authority: String, path: String, signal: android.os.CancellationSignal): List<Map<String, Any?>>? = runCatching {
        app.contentResolver.query(android.net.Uri.parse("content://$authority/$path"), null, null, null, null, signal)?.use { cursor ->
            val rows = ArrayList<Map<String, Any?>>()
            while (rows.size < com.vantaflow.kairo.ultratv.UTV_MAX_ROWS && cursor.moveToNext()) {
                rows += (0 until cursor.columnCount).associate { i ->
                    cursor.getColumnName(i) to when (cursor.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                        else -> null
                    }
                }
            }
            rows
        }
    }.getOrNull()

    /** Notifications de changement du fournisseur d'Ultra TV ; l'observateur est retiré à l'arrêt de la collecte. */
    private fun ultraTvChanges(authority: String): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.callbackFlow {
        val observer = object : android.database.ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        app.contentResolver.registerContentObserver(android.net.Uri.parse("content://$authority"), true, observer)
        awaitClose { app.contentResolver.unregisterContentObserver(observer) }
    }.buffer(kotlinx.coroutines.channels.Channel.CONFLATED)

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

    /** Démarrages notés dans un fichier à part, écrit aussitôt (le process peut mourir juste après). */
    val crashGuard by lazy {
        val prefs = getSharedPreferences(com.vantaflow.kairo.system.CRASH_GUARD_PREFS, MODE_PRIVATE)
        CrashGuard(
            // « <numéro de démarrage de la box>:<temps depuis ce démarrage> » ; l'ancien format (heure
            // murale seule) est illisible et ignoré.
            load = {
                prefs.getString(CRASH_GUARD_STARTS, "").orEmpty().split(',').filter { it.isNotBlank() }.map {
                    val (boot, elapsed) = it.split(':').also { parts -> require(parts.size == 2) }
                    com.vantaflow.kairo.system.BootTime(boot.toInt(), elapsed.toLong())
                }
            },
            save = { starts -> prefs.edit().putString(CRASH_GUARD_STARTS, starts.joinToString(",") { "${it.boot}:${it.elapsedMs}" }).commit() },
            now = { com.vantaflow.kairo.system.BootTime(com.vantaflow.kairo.system.bootCount(this), android.os.SystemClock.elapsedRealtime()) },
        )
    }

    /**
     * Mode sans échec : Kairo a planté plusieurs fois de suite sans démarrage réussi. Fixé une fois
     * pour toute la vie du process ; aucune fonction de fond n'est lancée (ni vérification ni
     * installation automatique des mises à jour). Réglages › Mise à jour reste utilisable à la main :
     * une version défectueuse de Kairo peut être remplacée depuis le mode sans échec.
     */
    var safeMode = false
        private set

    /**
     * Kairo s'est retiré de l'accueil (son mode sans échec plantait aussi) : le process ne démarre rien,
     * pas même le conteneur. Services, écran de veille et réparation doivent le vérifier avant tout.
     */
    var withdrawn = false
        private set

    /** Dernier filet : retrait de l'accueil (seulement préférences et PackageManager, jamais d'exception). */
    private val homeFallback by lazy { com.vantaflow.kairo.system.HomeFallback(this) }

    /**
     * Porte de démarrage : les fonctions de fond attendent que la box et l'accueil aient démarré.
     * Null en mode sans échec : rien ne part.
     */
    var gate: com.vantaflow.kairo.system.StartupGate? = null
        private set

    /** Vrai une fois le démarrage jugé réussi dans ce process (porte ouverte depuis 5 min). */
    @Volatile private var stable = false

    /** Démarrage réussi : démarrages et plantages en mode sans échec précédents oubliés (une seule fois par process). */
    fun markStable() {
        if (stable) return
        stable = true
        crashGuard.markStable()
        homeFallback.resetCrashes()
    }

    override fun onCreate() {
        super.onCreate()
        // Avant tout, à chaque démarrage : seules les sorties survenues depuis le démarrage précédent
        // comptent (traces jamais vues, sans se fier à l'horloge) ; une sortie plantée compte, un arrêt
        // pour manque de mémoire ou une mise à jour non.
        val previousExit = homeFallback.previousExit()
        // Vrai plantage (exception, plantage natif) : seul compté pour le retrait de l'accueil.
        val previousExitWasCrash = previousExit.crashed
        // Dernier filet, avant la langue, le conteneur et tout le reste : Kairo retiré de l'accueil ne
        // démarre plus rien jusqu'à « Remettre Kairo comme accueil ». Les composants sont réalignés sur
        // l'indicateur à chaque démarrage ; plus d'autre accueil : Kairo le reprend, en mode sans échec.
        // Puis : le mode sans échec plante encore → Kairo se retire et la box retombe sur l'autre accueil ;
        // sinon une boucle de plantage est repérée avant que quoi que ce soit d'autre ne démarre (mode
        // sans échec : plantages, et ANR ou échecs de démarrage loin du boot de la box).
        val decision = com.vantaflow.kairo.system.decideStartup(
            repair = homeFallback.repairAtStartup(),
            withdrawNow = { homeFallback.withdrawIfSafeModeKeepsCrashing(previousExitWasCrash) },
            loopDetected = { crashGuard.recordStart(previousExit.countsForSafeMode) },
            safeModeForced = homeFallback.isSafeModeForced(),
        )
        if (decision.withdrawn) {
            withdrawn = true
            return
        }
        safeMode = decision.safeMode
        homeFallback.recordRun(safeMode, previousExit.countsForSafeMode)
        // Langue choisie dans Kairo (sinon celle de la box) pour les services et les textes formatés.
        com.vantaflow.kairo.i18n.AppLocale.applyToApp(this)
        container = AppContainer(this)
        if (safeMode) return
        // Process souvent créé au boot par le service d'accessibilité, bien avant l'accueil : rien de
        // lourd tant que la porte n'est pas ouverte (30 s après la première image de l'accueil, 2 min sans accueil).
        val gate = com.vantaflow.kairo.system.StartupGate(CoroutineScope(SupervisorJob() + Dispatchers.Default + SafeHandler))
        this.gate = gate
        gate.whenOpen { container.watchPhoneRemote() }
        gate.whenOpen { container.startAutoClean() }
        gate.whenOpen {
            container.watchAdBlock()
            container.ensureAdBlock()
        }
        gate.whenOpen { com.vantaflow.kairo.media.NowPlaying.start(this) }
        gate.whenOpen { container.ourApps.checkUpdates() }
        gate.whenOpen { container.ultraTv.start() }
        // Porte ouverte depuis 5 min sans plantage, accueil à l'écran ou non : le démarrage est réussi.
        gate.whenOpen {
            while (!com.vantaflow.kairo.system.isStable(gate.openForMs())) kotlinx.coroutines.delay(10_000)
            kotlinx.coroutines.withContext(Dispatchers.IO) { markStable() }
        }
    }

    /** Un changement de configuration de la box remet sa langue : on réapplique celle de Kairo. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (withdrawn) return
        com.vantaflow.kairo.i18n.AppLocale.applyToApp(this)
    }

    /** Cache d'images plafonné : par défaut Coil prendrait 25 % du tas (64 Mo sur cette box). */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizeBytes(IMAGE_CACHE_BYTES).build() }
        .allowRgb565(true)
        .build()

    /** Box à 2 Go : Kairo en arrière-plan rend la mémoire des tuiles et des images à l'appli lancée. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Kairo retiré : ni conteneur ni images en mémoire.
        if (withdrawn) return
        if (level >= TRIM_MEMORY_BACKGROUND) {
            container.tileImages.clear()
            coil.Coil.imageLoader(this).memoryCache?.clear()
        }
    }

    private companion object {
        const val IMAGE_CACHE_BYTES = 24 * 1024 * 1024
        const val CRASH_GUARD_STARTS = "starts"
    }
}
