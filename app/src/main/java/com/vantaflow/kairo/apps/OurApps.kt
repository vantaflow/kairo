package com.vantaflow.kairo.apps

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.vantaflow.kairo.R
import com.vantaflow.kairo.update.Updater
import com.vantaflow.kairo.update.isNewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Une appli Vantaflow proposée dans Kairo (accueil, premier lancement, Mise à jour). */
data class OurApp(
    val id: String,
    val name: String,
    val tagline: String,
    /** Le premier est celui qu'on installe ; les suivants comptent aussi comme « installée » (debug…). */
    val packages: List<String>,
    val repo: String,
    val banner: Int,
    /** Nom de l'APK à prendre parmi ceux d'une publication (selon le processeur de la box). */
    val pickAsset: (names: List<String>) -> String?,
)

val OurApps: List<OurApp> = listOf(
    OurApp(
        id = "player",
        name = "Kairo Player",
        tagline = "Vidéos, photos et musique de la box et des clés USB, avec reprise dans « À suivre ».",
        packages = listOf("com.vantaflow.kairo.player"),
        repo = "vantaflow/kairo",
        banner = R.drawable.app_kairo_player,
        pickAsset = { names -> "kairo-player.apk".takeIf { it in names } },
    ),
    OurApp(
        id = "ultratv",
        name = "Ultra TV",
        tagline = "Lecteur IPTV : Xtream Codes et M3U, guide TV, replay, pause du direct, profils.",
        packages = listOf("com.ultratv.tv.nativeapp", "com.ultratv.tv.nativeapp.debug"),
        repo = "vantaflow/ultra-tv",
        banner = R.drawable.app_ultratv,
        // Un APK par processeur : celui de la box (32 bits sur les petites box).
        pickAsset = { names -> Build.SUPPORTED_ABIS.map { "UltraTV-$it.apk" }.firstOrNull { it in names } },
    ),
)

/** APK trouvé dans les publications GitHub d'une appli. */
data class RemoteApk(val version: String, val assetUrl: String, val size: Long)

/**
 * Dans les dernières publications de [repo] (JSON de l'API GitHub), la plus récente qui contient
 * l'APK choisi par [pick] (une publication « bureau » sans APK Android est ignorée).
 */
fun findApk(releasesJson: String, pick: (List<String>) -> String?): RemoteApk? = runCatching {
    val releases = JSONArray(releasesJson)
    for (i in 0 until releases.length()) {
        val r = releases.getJSONObject(i)
        if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
        val assets = r.optJSONArray("assets") ?: continue
        val list = (0 until assets.length()).map(assets::getJSONObject)
        val name = pick(list.map { it.optString("name") }) ?: continue
        val asset = list.first { it.optString("name") == name }
        return@runCatching RemoteApk(r.optString("tag_name").removePrefix("v"), asset.getString("url"), asset.optLong("size"))
    }
    null
}.getOrNull()

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val percent: Int) : InstallState
    /** Téléchargé : Android affiche sa demande de confirmation. */
    data object Confirm : InstallState
    data class Failed(val message: String) : InstallState
}

/** Installe et met à jour nos applis depuis leurs publications GitHub. */
class OurAppsStore(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updater = Updater(context)
    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val states: StateFlow<Map<String, InstallState>> = _states
    private val _updates = MutableStateFlow<Set<String>>(emptySet())
    /** Applis installées pour lesquelles une version plus récente est publiée. */
    val updates: StateFlow<Set<String>> = _updates

    /** Vérifie les mises à jour de nos applis installées (au démarrage, sans le ralentir). */
    fun checkUpdates(delayMs: Long = 20_000) {
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            _updates.value = OurApps.filter { isInstalled(it) && hasUpdate(it, latest(it)) }.map { it.id }.toSet()
        }
    }

    /** Version installée (null si absente). */
    fun installedVersion(app: OurApp): String? = app.packages.firstNotNullOfOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName ?: "?" }.getOrNull()
    }

    fun isInstalled(app: OurApp) = installedVersion(app) != null

    suspend fun latest(app: OurApp): RemoteApk? = withContext(Dispatchers.IO) {
        updater.getText("https://api.github.com/repos/${app.repo}/releases?per_page=10")?.let { findApk(it, app.pickAsset) }
    }

    /** Vrai si une version plus récente que celle installée est publiée. */
    fun hasUpdate(app: OurApp, remote: RemoteApk?): Boolean {
        val installed = installedVersion(app) ?: return false
        return remote != null && isNewer(remote.version, installed.substringBefore('-'))
    }

    fun install(app: OurApp) {
        if (_states.value[app.id] is InstallState.Downloading) return
        set(app, InstallState.Downloading(0))
        scope.launch {
            val remote = latest(app)
            if (remote == null) {
                set(app, InstallState.Failed("Impossible de joindre GitHub"))
                return@launch
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Téléchargement de ${app.name} (${remote.size / 1_048_576} Mo)…", Toast.LENGTH_SHORT).show()
            }
            val ok = runCatching {
                updater.installAsset(remote.assetUrl, app.packages.first()) { bytes ->
                    if (remote.size > 0) set(app, InstallState.Downloading((bytes * 100 / remote.size).toInt().coerceIn(0, 100)))
                }
            }.getOrDefault(false)
            set(app, if (ok) InstallState.Confirm else InstallState.Failed("Échec du téléchargement"))
            // L'installation finit après la confirmation : on la guette (2 min au plus) pour mettre l'écran à jour.
            if (ok) {
                val before = installedVersion(app)
                repeat(60) {
                    kotlinx.coroutines.delay(2000)
                    val now = installedVersion(app)
                    if (now != null && now != before) {
                        _updates.update { it - app.id }
                        set(app, InstallState.Idle)
                        return@launch
                    }
                }
            }
        }
    }

    /** Au retour dans Kairo : une appli installée entre-temps repasse à l'état normal. */
    fun refresh() {
        _states.update { states -> states.filterNot { (id, s) -> s is InstallState.Confirm && OurApps.firstOrNull { it.id == id }?.let(::isInstalled) == true } }
        // Mise à jour installée : la version ne dépasse plus celle publiée, on la retire au prochain contrôle.
        if (_updates.value.isNotEmpty()) checkUpdates(delayMs = 0)
    }

    private fun set(app: OurApp, state: InstallState) = _states.update { it + (app.id to state) }
}
