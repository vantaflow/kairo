package com.vantaflow.kairo.settings

import com.vantaflow.kairo.dream.DreamConfig
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.ui.theme.CUSTOM_THEME_ID
import com.vantaflow.kairo.ui.theme.HomeLayout
import com.vantaflow.kairo.ui.theme.LauncherTheme
import com.vantaflow.kairo.ui.theme.Themes
import com.vantaflow.kairo.remote.decodeBindings
import com.vantaflow.kairo.remote.encodeBindings
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.io.IOException
import kotlinx.coroutines.flow.map

private const val DEFAULT_FAVORITES_COUNT = 6

/** Préférences sensibles, exclues des sauvegardes. */
private val SECRET_KEYS = setOf("github_token", "pixabay_key", "phone_remote_token")

/**
 * Favoris effectifs : ceux enregistrés encore installés, dans l'ordre enregistré.
 * Au premier lancement (rien d'enregistré), les premières applis installées.
 */
/** Nombre d'applis gardées dans « Récentes ». */
const val MAX_RECENT_APPS = 12

fun resolveFavorites(saved: List<String>, installed: List<AppInfo>, initialized: Boolean): List<AppInfo> {
    if (!initialized) return installed.take(DEFAULT_FAVORITES_COUNT)
    val byPackage = installed.associateBy { it.packageName }
    return saved.mapNotNull { byPackage[it] }
}

class LauncherPrefs(private val store: DataStore<Preferences>) {
    /** Un fichier de préférences illisible ne doit jamais faire planter le launcher : valeurs par défaut. */
    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    /** Une préférence ne réémet que si sa valeur change (sinon toute écriture relance les abonnés). */
    private fun <T> pref(read: (Preferences) -> T): Flow<T> = data.map(read).distinctUntilChanged()

    /** Suffixe des clés propres au profil actif (vide pour le profil principal). */
    private val Preferences.sfx: String
        get() {
            // Un profil disparu (ex. sauvegarde restaurée sans lui) retombe sur le profil principal.
            val id = this[Keys.currentProfile]?.takeIf { id -> id != DEFAULT_PROFILE_ID && decodeProfiles(this[Keys.profiles]).any { it.id == id } }
            return id?.let { "@$it" } ?: ""
        }

    /** Jeton GitHub (lecture) pour les mises à jour depuis le dépôt privé. */
    val githubToken: Flow<String> = pref { it[Keys.githubToken].orEmpty() }

    suspend fun setGithubToken(token: String) {
        store.edit { it[Keys.githubToken] = token.trim() }
    }

    val profiles: Flow<List<Profile>> = pref { listOf(DefaultProfile) + decodeProfiles(it[Keys.profiles]) }
    val currentProfile: Flow<Profile> = pref { p ->
        val id = p[Keys.currentProfile]
        decodeProfiles(p[Keys.profiles]).firstOrNull { it.id == id } ?: DefaultProfile
    }

    /** Crée un profil ; un profil enfant exige un PIN et démarre sans aucun favori. */
    suspend fun addProfile(name: String, kids: Boolean, pin: String?): Profile {
        val cleanPin = pin?.takeIf { it.isNotBlank() }
        require(!kids || cleanPin != null) { "Un profil enfant doit avoir un PIN" }
        val profile = Profile(java.util.UUID.randomUUID().toString().take(8), name.trim().ifBlank { "Profil" }, kids, cleanPin?.let(::hashPin))
        store.edit {
            it[Keys.profiles] = encodeProfiles(decodeProfiles(it[Keys.profiles]) + profile)
            if (kids) it[Keys.favoritesInitialized("@${profile.id}")] = true
        }
        return profile
    }

    /**
     * Favoris par défaut du premier lancement, écrits seulement si [profileId] est toujours
     * le profil actif et n'a pas encore de favoris (évite d'écrire dans un autre profil).
     */
    suspend fun initializeFavorites(profileId: String, packages: List<String>) {
        store.edit {
            val current = it[Keys.currentProfile] ?: DEFAULT_PROFILE_ID
            if (current != profileId || it[Keys.favoritesInitialized(it.sfx)] == true) return@edit
            it[Keys.favorites(it.sfx)] = encode(packages.distinct())
            it[Keys.favoritesInitialized(it.sfx)] = true
        }
    }

    suspend fun removeProfile(id: String) {
        if (id == DEFAULT_PROFILE_ID) return
        store.edit {
            it[Keys.profiles] = encodeProfiles(decodeProfiles(it[Keys.profiles]).filterNot { p -> p.id == id })
            if (it[Keys.currentProfile] == id) it.remove(Keys.currentProfile)
            // Réglages propres au profil supprimé (favoris, fond, applis masquées…).
            it.asMap().keys.filter { k -> k.name.endsWith("@$id") }.forEach { k -> it.remove(k) }
        }
    }

    suspend fun switchProfile(id: String) {
        store.edit { it[Keys.currentProfile] = id }
    }

    /** Sauvegarde de toutes les préférences (tous profils) en JSON. */
    suspend fun exportJson(): String =
        exportPrefs(data.first().asMap().mapKeys { it.key.name }.filterKeys { it !in SECRET_KEYS })

    /** Restaure une sauvegarde ; false si le fichier n'en est pas une. */
    suspend fun importJson(json: String): Boolean {
        val values = importPrefs(json) ?: return false
        store.edit { prefs ->
            // Les secrets ne sont jamais dans une sauvegarde : on garde ceux de la box.
            val kept = prefs.asMap().filterKeys { it.name in SECRET_KEYS }
            prefs.clear()
            kept.forEach { (k, v) -> if (v is String) prefs[stringPreferencesKey(k.name)] = v }
            values.forEach { (name, value) ->
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is String -> prefs[stringPreferencesKey(name)] = value
                    is Boolean -> prefs[booleanPreferencesKey(name)] = value
                    is Int -> prefs[intPreferencesKey(name)] = value
                    is Long -> prefs[longPreferencesKey(name)] = value
                    is Float -> prefs[floatPreferencesKey(name)] = value
                    is Set<*> -> prefs[stringSetPreferencesKey(name)] = value as Set<String>
                }
            }
        }
        return true
    }

    private object Keys {
        fun favorites(s: String) = stringPreferencesKey("favorites$s")
        fun favoritesInitialized(s: String) = booleanPreferencesKey("favorites_initialized$s")
        fun hidden(s: String) = stringSetPreferencesKey("hidden$s")
        fun wallpaperType(s: String) = stringPreferencesKey("wallpaper_type$s")
        val themeId = stringPreferencesKey("theme_id")
        val customAccent = longPreferencesKey("custom_accent")
        val customFocus = longPreferencesKey("custom_focus")
        val customRadius = intPreferencesKey("custom_radius")
        val customScale = floatPreferencesKey("custom_scale")
        val showClock = booleanPreferencesKey("show_clock")
        val showDate = booleanPreferencesKey("show_date")
        val showFavoritesLabel = booleanPreferencesKey("show_favorites_label")
        val showRowTitles = booleanPreferencesKey("show_row_titles")
        val showRecentApps = booleanPreferencesKey("show_recent_apps")
        val showLiveTv = booleanPreferencesKey("show_live_tv")
        val showOurApps = booleanPreferencesKey("show_our_apps")
        val showTools = booleanPreferencesKey("show_tools")
        val autoClean = booleanPreferencesKey("auto_clean")
        val adBlockEnabled = booleanPreferencesKey("adblock_enabled")
        val adBlockFilter = stringPreferencesKey("adblock_filter")
        val idleCloseMin = intPreferencesKey("idle_close_min")
        val radioFavorites = stringPreferencesKey("radio_favorites")
        val radioCountry = stringPreferencesKey("radio_country")
        val setupShown = booleanPreferencesKey("setup_shown")
        val dreamMode = stringPreferencesKey("dream_mode")
        val dreamQuery = stringPreferencesKey("dream_query")
        val dreamPalette = stringPreferencesKey("dream_palette")
        val dreamInterval = intPreferencesKey("dream_interval")
        val dreamTransition = stringPreferencesKey("dream_transition")
        val dreamClock = booleanPreferencesKey("dream_clock")
        val dreamQuote = booleanPreferencesKey("dream_quote")
        val phoneRemoteEnabled = booleanPreferencesKey("phone_remote_enabled")
        val phoneRemoteToken = stringPreferencesKey("phone_remote_token")
        fun recentApps(s: String) = stringPreferencesKey("recent_apps$s")
        val clockSize = intPreferencesKey("clock_size")
        val tileWidth = intPreferencesKey("tile_width")
        val heroTransition = stringPreferencesKey("hero_transition")
        val animationPercent = intPreferencesKey("animation_percent")
        val weatherEnabled = booleanPreferencesKey("weather_enabled")
        val weatherCity = stringPreferencesKey("weather_city")
        val useFahrenheit = booleanPreferencesKey("use_fahrenheit")
        fun pixabayQuery(s: String) = stringPreferencesKey("pixabay_query$s")
        fun pixabayInterval(s: String) = intPreferencesKey("pixabay_interval$s")
        fun pixabayCurrent(s: String) = stringPreferencesKey("pixabay_current$s")
        val pixabayKey = stringPreferencesKey("pixabay_key")
        fun wallpaperStart(s: String) = longPreferencesKey("wallpaper_start$s")
        fun wallpaperEnd(s: String) = longPreferencesKey("wallpaper_end$s")
        fun wallpaperAngle(s: String) = intPreferencesKey("wallpaper_angle$s")
        val use24h = booleanPreferencesKey("use_24h")
        val keyBindings = stringPreferencesKey("key_bindings")
        val showWatchNext = booleanPreferencesKey("show_watch_next")
        val hiddenChannels = stringSetPreferencesKey("hidden_channels")
        val hiddenRecoApps = stringSetPreferencesKey("hidden_reco_apps")
        val recoAppOrder = stringPreferencesKey("reco_app_order")
        val currentProfile = stringPreferencesKey("current_profile")
        val githubToken = stringPreferencesKey("github_token")
        val profiles = stringPreferencesKey("profiles")
    }

    val favorites: Flow<List<String>> = pref { decode(it[Keys.favorites(it.sfx)]) }
    val favoritesInitialized: Flow<Boolean> = pref { it[Keys.favoritesInitialized(it.sfx)] ?: false }
    val hidden: Flow<Set<String>> = pref { it[Keys.hidden(it.sfx)].orEmpty() }

    /** Télécommande sur téléphone (serveur web du réseau local) : désactivée par défaut. */
    val phoneRemoteEnabled: Flow<Boolean> = pref { it[Keys.phoneRemoteEnabled] ?: false }

    suspend fun setPhoneRemoteEnabled(value: Boolean) {
        store.edit { it[Keys.phoneRemoteEnabled] = value }
    }

    val phoneRemoteTokenFlow: Flow<String> = pref { it[Keys.phoneRemoteToken].orEmpty() }

    /** Jeton du lien (QR code), créé au premier besoin. */
    suspend fun phoneRemoteToken(): String {
        data.first()[Keys.phoneRemoteToken]?.takeIf { it.isNotBlank() }?.let { return it }
        return renewPhoneRemoteToken()
    }

    /** Nouveau jeton : les téléphones déjà connectés doivent rescanner le QR code. */
    suspend fun renewPhoneRemoteToken(): String {
        val bytes = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        store.edit { it[Keys.phoneRemoteToken] = token }
        return token
    }

    /** Réglages de l'écran de veille. */
    val dreamConfig: Flow<DreamConfig> = pref {
        val d = DreamConfig()
        DreamConfig(
            mode = it[Keys.dreamMode] ?: d.mode,
            query = it[Keys.dreamQuery] ?: d.query,
            palette = it[Keys.dreamPalette] ?: d.palette,
            intervalSec = it[Keys.dreamInterval] ?: d.intervalSec,
            transition = it[Keys.dreamTransition] ?: d.transition,
            showClock = it[Keys.dreamClock] ?: d.showClock,
            showQuote = it[Keys.dreamQuote] ?: d.showQuote,
        )
    }

    suspend fun setDreamConfig(c: DreamConfig) {
        store.edit {
            it[Keys.dreamMode] = c.mode
            it[Keys.dreamQuery] = c.query
            it[Keys.dreamPalette] = c.palette
            it[Keys.dreamInterval] = c.intervalSec
            it[Keys.dreamTransition] = c.transition
            it[Keys.dreamClock] = c.showClock
            it[Keys.dreamQuote] = c.showQuote
        }
    }

    /** Vrai une fois l'assistant de configuration proposé (il ne s'ouvre seul qu'une fois). */
    val setupShown: Flow<Boolean> = pref { it[Keys.setupShown] ?: false }

    suspend fun setSetupShown() {
        store.edit { it[Keys.setupShown] = true }
    }

    /** Bloqueur de pubs (VPN DNS local) et filtre choisi. */
    val adBlockEnabled: Flow<Boolean> = pref { it[Keys.adBlockEnabled] ?: false }
    val adBlockFilter: Flow<String> = pref { it[Keys.adBlockFilter] ?: "Ads" }

    suspend fun setAdBlockEnabled(on: Boolean) {
        store.edit { it[Keys.adBlockEnabled] = on }
    }

    suspend fun setAdBlockFilter(id: String) {
        store.edit { it[Keys.adBlockFilter] = id }
    }

    /** Nettoyage automatique de la mémoire (actif par défaut) et délai avant de fermer une appli inutilisée. */
    val autoClean: Flow<Boolean> = pref { it[Keys.autoClean] ?: true }
    val idleCloseMin: Flow<Int> = pref { it[Keys.idleCloseMin] ?: 30 }

    suspend fun setAutoClean(on: Boolean) {
        store.edit { it[Keys.autoClean] = on }
    }

    suspend fun setIdleCloseMin(min: Int) {
        store.edit { it[Keys.idleCloseMin] = min }
    }

    /** Radios favorites (dans l'ordre d'ajout) et pays choisi dans l'écran Radio (null = jamais choisi). */
    val radioFavorites: Flow<List<com.vantaflow.kairo.radio.Station>> = pref { com.vantaflow.kairo.radio.decodeStations(it[Keys.radioFavorites]) }
    val radioCountry: Flow<String?> = pref { it[Keys.radioCountry] }

    suspend fun toggleRadioFavorite(station: com.vantaflow.kairo.radio.Station) {
        store.edit {
            val list = com.vantaflow.kairo.radio.decodeStations(it[Keys.radioFavorites])
            val next = if (list.any { s -> s.url == station.url }) list.filterNot { s -> s.url == station.url } else list + station
            it[Keys.radioFavorites] = com.vantaflow.kairo.radio.encodeStations(next)
        }
    }

    suspend fun setRadioCountry(code: String) {
        store.edit { it[Keys.radioCountry] = code }
    }

    /** Applis ouvertes depuis Kairo, la plus récente en tête (par profil). */
    val recentApps: Flow<List<String>> = pref { decode(it[Keys.recentApps(it.sfx)]) }

    suspend fun recordLaunch(pkg: String) {
        store.edit {
            val list = decode(it[Keys.recentApps(it.sfx)])
            if (list.firstOrNull() != pkg) it[Keys.recentApps(it.sfx)] = encode((listOf(pkg) + (list - pkg)).take(MAX_RECENT_APPS))
        }
    }
    val wallpaper: Flow<WallpaperConfig> = pref(::readWallpaper)
    val use24h: Flow<Boolean> = pref { it[Keys.use24h] ?: true }

    /** Image Pixabay affichée en ce moment (gardée pour un affichage immédiat au démarrage). */
    val pixabayCurrent: Flow<String?> = pref { it[Keys.pixabayCurrent(it.sfx)] }

    /** Clé Pixabay saisie dans les réglages ; vide = clé intégrée au build. */
    val pixabayKey: Flow<String> = pref { it[Keys.pixabayKey].orEmpty() }

    suspend fun setPixabayCurrent(url: String?) {
        store.edit { if (url == null) it.remove(Keys.pixabayCurrent(it.sfx)) else it[Keys.pixabayCurrent(it.sfx)] = url }
    }

    suspend fun setPixabayKey(key: String) {
        store.edit { it[Keys.pixabayKey] = key.trim() }
    }

    val themeId: Flow<String> = pref { it[Keys.themeId] ?: "google-tv" }
    val weatherEnabled: Flow<Boolean> = pref { it[Keys.weatherEnabled] ?: true }

    /** Ville choisie pour la météo ; vide = position détectée automatiquement. */
    val weatherCity: Flow<String> = pref { it[Keys.weatherCity].orEmpty() }
    val useFahrenheit: Flow<Boolean> = pref { it[Keys.useFahrenheit] ?: false }

    val customTheme: Flow<LauncherTheme> = pref {
        val base = Themes.first()
        LauncherTheme(
            CUSTOM_THEME_ID, "Personnalisé",
            accent = it[Keys.customAccent] ?: base.accent,
            focusBorder = it[Keys.customFocus] ?: base.focusBorder,
            tileRadiusDp = it[Keys.customRadius] ?: base.tileRadiusDp,
            focusScale = it[Keys.customScale] ?: base.focusScale,
            background = base.background,
            surface = base.surface,
        )
    }

    suspend fun setCustomTheme(accent: Long, focusBorder: Long, radiusDp: Int, scale: Float) {
        store.edit {
            it[Keys.customAccent] = accent
            it[Keys.customFocus] = focusBorder
            it[Keys.customRadius] = radiusDp
            it[Keys.customScale] = scale
        }
    }

    val homeLayout: Flow<HomeLayout> = pref {
        val d = HomeLayout()
        HomeLayout(
            showClock = it[Keys.showClock] ?: d.showClock,
            showDate = it[Keys.showDate] ?: d.showDate,
            showFavoritesLabel = it[Keys.showFavoritesLabel] ?: d.showFavoritesLabel,
            showRowTitles = it[Keys.showRowTitles] ?: d.showRowTitles,
            showRecentApps = it[Keys.showRecentApps] ?: d.showRecentApps,
            showLiveTv = it[Keys.showLiveTv] ?: d.showLiveTv,
            showOurApps = it[Keys.showOurApps] ?: d.showOurApps,
            showTools = it[Keys.showTools] ?: d.showTools,
            clockSizeSp = it[Keys.clockSize] ?: d.clockSizeSp,
            tileWidthDp = it[Keys.tileWidth] ?: d.tileWidthDp,
            heroTransition = it[Keys.heroTransition] ?: d.heroTransition,
            animationPercent = it[Keys.animationPercent] ?: d.animationPercent,
        )
    }

    suspend fun setHomeLayout(layout: HomeLayout) {
        store.edit {
            it[Keys.showClock] = layout.showClock
            it[Keys.showDate] = layout.showDate
            it[Keys.showFavoritesLabel] = layout.showFavoritesLabel
            it[Keys.showRowTitles] = layout.showRowTitles
            it[Keys.showRecentApps] = layout.showRecentApps
            it[Keys.showLiveTv] = layout.showLiveTv
            it[Keys.showOurApps] = layout.showOurApps
            it[Keys.showTools] = layout.showTools
            it[Keys.clockSize] = layout.clockSizeSp
            it[Keys.tileWidth] = layout.tileWidthDp
            it[Keys.heroTransition] = layout.heroTransition
            it[Keys.animationPercent] = layout.animationPercent
        }
    }

    suspend fun setThemeId(id: String) {
        store.edit { it[Keys.themeId] = id }
    }

    suspend fun setWeatherEnabled(value: Boolean) {
        store.edit { it[Keys.weatherEnabled] = value }
    }

    suspend fun setWeatherCity(city: String) {
        store.edit { it[Keys.weatherCity] = city.trim() }
    }

    suspend fun setUseFahrenheit(value: Boolean) {
        store.edit { it[Keys.useFahrenheit] = value }
    }

    val showWatchNext: Flow<Boolean> = pref { it[Keys.showWatchNext] ?: true }
    val hiddenChannels: Flow<Set<Long>> = pref { p -> p[Keys.hiddenChannels].orEmpty().mapNotNull { it.toLongOrNull() }.toSet() }
    val hiddenRecoApps: Flow<Set<String>> = pref { it[Keys.hiddenRecoApps].orEmpty() }
    val recoAppOrder: Flow<List<String>> = pref { decode(it[Keys.recoAppOrder]) }

    suspend fun setShowWatchNext(value: Boolean) {
        store.edit { it[Keys.showWatchNext] = value }
    }

    suspend fun setChannelHidden(id: Long, hidden: Boolean) {
        store.edit {
            val current = it[Keys.hiddenChannels].orEmpty()
            it[Keys.hiddenChannels] = if (hidden) current + id.toString() else current - id.toString()
        }
    }

    suspend fun setRecoAppHidden(pkg: String, hidden: Boolean) {
        store.edit {
            val current = it[Keys.hiddenRecoApps].orEmpty()
            it[Keys.hiddenRecoApps] = if (hidden) current + pkg else current - pkg
        }
    }

    suspend fun setRecoAppOrder(packages: List<String>) {
        store.edit { it[Keys.recoAppOrder] = encode(packages.distinct()) }
    }

    val keyBindings: Flow<Map<Int, String>> = pref { decodeBindings(it[Keys.keyBindings]) }

    /** Lie une touche à une appli ; pkg = null supprime la liaison. */
    suspend fun setKeyBinding(keyCode: Int, pkg: String?) {
        store.edit {
            val current = decodeBindings(it[Keys.keyBindings]).toMutableMap()
            if (pkg == null) current.remove(keyCode) else current[keyCode] = pkg
            it[Keys.keyBindings] = encodeBindings(current)
        }
    }

    suspend fun setUse24h(value: Boolean) {
        store.edit { it[Keys.use24h] = value }
    }

    suspend fun setFavorites(packages: List<String>) {
        store.edit {
            it[Keys.favorites(it.sfx)] = encode(packages.distinct())
            it[Keys.favoritesInitialized(it.sfx)] = true
        }
    }

    suspend fun addFavorite(pkg: String) = updateFavorites { if (pkg in it) it else it + pkg }

    suspend fun removeFavorite(pkg: String) = updateFavorites { it - pkg }

    suspend fun moveFavorite(pkg: String, toIndex: Int) = updateFavorites { list ->
        if (pkg !in list) return@updateFavorites list
        val rest = list - pkg
        rest.toMutableList().apply { add(toIndex.coerceIn(0, rest.size), pkg) }
    }

    suspend fun setHidden(pkg: String, hidden: Boolean) {
        store.edit {
            val current = it[Keys.hidden(it.sfx)].orEmpty()
            it[Keys.hidden(it.sfx)] = if (hidden) current + pkg else current - pkg
        }
    }

    suspend fun setWallpaper(c: WallpaperConfig) {
        store.edit {
            when (c) {
                is WallpaperConfig.Solid -> {
                    it[Keys.wallpaperType(it.sfx)] = "solid"
                    it[Keys.wallpaperStart(it.sfx)] = c.argb
                }
                is WallpaperConfig.Pixabay -> {
                    it[Keys.wallpaperType(it.sfx)] = "pixabay"
                    it[Keys.pixabayQuery(it.sfx)] = c.query
                    it[Keys.pixabayInterval(it.sfx)] = c.intervalMin
                }
                is WallpaperConfig.Gradient -> {
                    it[Keys.wallpaperType(it.sfx)] = "gradient"
                    it[Keys.wallpaperStart(it.sfx)] = c.startArgb
                    it[Keys.wallpaperEnd(it.sfx)] = c.endArgb
                    it[Keys.wallpaperAngle(it.sfx)] = c.angleDeg
                }
            }
        }
    }

    private suspend fun updateFavorites(transform: (List<String>) -> List<String>) {
        store.edit {
            it[Keys.favorites(it.sfx)] = encode(transform(decode(it[Keys.favorites(it.sfx)])))
            it[Keys.favoritesInitialized(it.sfx)] = true
        }
    }

    private fun readWallpaper(p: Preferences): WallpaperConfig = when (p[Keys.wallpaperType(p.sfx)]) {
        "solid" -> WallpaperConfig.Solid(p[Keys.wallpaperStart(p.sfx)] ?: 0xFF101216)
        "gradient" -> WallpaperConfig.Gradient(
            p[Keys.wallpaperStart(p.sfx)] ?: 0xFF1C2A3A,
            p[Keys.wallpaperEnd(p.sfx)] ?: 0xFF101216,
            p[Keys.wallpaperAngle(p.sfx)] ?: 135,
        )
        "pixabay" -> WallpaperConfig.Pixabay(p[Keys.pixabayQuery(p.sfx)] ?: "nature landscape", p[Keys.pixabayInterval(p.sfx)] ?: 60)
        else -> WallpaperConfig.Default
    }

    private fun encode(list: List<String>) = list.joinToString("\n")
    private fun decode(raw: String?) = raw?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
}
