package com.vantaflow.kairo.apps

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
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
    @StringRes val tagline: Int,
    /** Le premier est celui qu'on installe ; les suivants comptent aussi comme « installée » (debug…). */
    val packages: List<String>,
    val repo: String,
    val banner: Int,
    /** Nom de l'APK à prendre parmi ceux d'une publication (selon le processeur de la box). */
    val pickAsset: (names: List<String>) -> String?,
    /** Doit pouvoir s'afficher par-dessus les autres applis (autorisation donnée par ADB). */
    val needsOverlay: Boolean = false,
)

val OurApps: List<OurApp> = listOf(
    OurApp(
        id = "player",
        name = "Kairo Player",
        tagline = R.string.apps_tagline_player,
        packages = listOf("com.vantaflow.kairo.player"),
        repo = "vantaflow/kairo",
        banner = R.drawable.app_kairo_player,
        pickAsset = { names -> "kairo-player.apk".takeIf { it in names } },
    ),
    OurApp(
        id = "cast",
        name = "Kairo Cast",
        tagline = R.string.apps_tagline_cast,
        packages = listOf("com.vantaflow.kairo.cast"),
        repo = "vantaflow/kairo-cast",
        banner = R.drawable.app_kairo_cast,
        pickAsset = { names -> "kairo-cast.apk".takeIf { it in names } },
        needsOverlay = true,
    ),
    OurApp(
        id = "files",
        name = "Kairo Files",
        tagline = R.string.apps_tagline_files,
        packages = listOf("com.vantaflow.kairo.files"),
        repo = "vantaflow/kairo",
        banner = R.drawable.app_kairo_files,
        pickAsset = { names -> "kairo-files.apk".takeIf { it in names } },
    ),
    OurApp(
        id = "send",
        name = "Kairo Send Files",
        tagline = R.string.apps_tagline_send,
        packages = listOf("com.vantaflow.kairo.send"),
        repo = "vantaflow/kairo",
        banner = R.drawable.app_kairo_send,
        pickAsset = { names -> "kairo-send.apk".takeIf { it in names } },
    ),
    OurApp(
        id = "ultratv",
        name = "Ultra TV",
        tagline = R.string.apps_tagline_ultratv,
        packages = listOf("com.ultratv.tv.nativeapp", "com.ultratv.tv.nativeapp.debug"),
        repo = "vantaflow/ultra-tv",
        banner = R.drawable.app_ultratv,
        // Un APK par processeur : celui de la box (32 bits sur les petites box).
        pickAsset = { names -> Build.SUPPORTED_ABIS.map { "UltraTV-$it.apk" }.firstOrNull { it in names } },
    ),
)

/** Kairo lui-même : vérifié comme nos autres applis, proposé sur l'accueil seulement s'il a une mise à jour. */
val KairoSelf = OurApp(
    id = "kairo",
    name = "Kairo",
    tagline = R.string.apps_tagline_kairo,
    packages = listOf(com.vantaflow.kairo.BuildConfig.APPLICATION_ID),
    repo = "vantaflow/kairo",
    banner = R.drawable.banner,
    pickAsset = { names -> "kairo.apk".takeIf { it in names } },
)

/** Applis dont Kairo surveille les mises à jour. */
private val Checked: List<OurApp> get() = OurApps + KairoSelf

/** Écart entre deux vérifications au retour sur l'accueil (la box reste allumée des jours). */
private const val CHECK_EVERY_MS = 12 * 60 * 60 * 1000L
private const val PENDING_CHECK_EVERY_MS = 30 * 60 * 1000L

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
    /** [message] : texte de l'erreur affichée. */
    data class Failed(@StringRes val message: Int) : InstallState
}

/** Installe et met à jour nos applis depuis leurs publications GitHub. */
class OurAppsStore(
    private val context: Context,
    /** Réglage « Mise à jour automatique ». */
    private val autoUpdate: suspend () -> Boolean = { false },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + com.vantaflow.kairo.system.SafeHandler)
    private val updater = Updater(context)
    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val states: StateFlow<Map<String, InstallState>> = _states
    private val _updates = MutableStateFlow<Set<String>>(emptySet())
    /** Applis installées pour lesquelles une version plus récente est publiée. */
    val updates: StateFlow<Set<String>> = _updates

    /** Vérifie les mises à jour de nos applis installées (au démarrage, sans le ralentir). */
    fun checkUpdates(delayMs: Long = 20_000) {
        lastCheckMs = System.currentTimeMillis()
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            val results = Checked.filter(::isInstalled).map { it to latest(it) }
            val found = results.mapNotNull { (app, remote) -> remote?.takeIf { hasUpdate(app, it) }?.let { app to it } }
            // GitHub injoignable pour une appli : on garde ce qu'on savait, la mise à jour ne « disparaît » pas.
            val unknown = results.filter { (app, remote) -> remote == null && app.id in _updates.value }.map { it.first.id }
            _updates.value = (found.map { it.first.id } + unknown).toSet()
            if (found.isEmpty()) return@launch
            // Box allumée depuis peu : les applis démarrent encore, l'installation automatique attend.
            while (com.vantaflow.kairo.system.bootIsRecent(android.os.SystemClock.elapsedRealtime())) kotlinx.coroutines.delay(60_000)
            // Après l'attente : seulement celles encore à faire (installée entre-temps, ou retirée de la liste).
            val pending = found.filter { (app, remote) -> app.id in _updates.value && hasUpdate(app, remote) }
            if (pending.isNotEmpty() && runCatching { autoUpdate() }.getOrDefault(false)) autoInstall(pending.toMap())
        }
    }

    /** L'accueil de Kairo est à l'écran : sa propre mise à jour attend qu'il ne le soit plus. */
    @Volatile var homeVisible = false
    private val autoMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Installe sans rien demander les mises à jour trouvées. Si Android exige une confirmation
     * (box trop ancienne, source non autorisée), rien ne s'affiche : la mise à jour reste proposée.
     */
    private suspend fun autoInstall(found: Map<OurApp, RemoteApk>) {
        if (!autoMutex.tryLock()) return
        try {
            val byId = found.mapKeys { it.key.id }
            val apps = found.keys.associateBy { it.id }
            val plan = com.vantaflow.kairo.update.autoInstallPlan(
                byId.mapValues { it.value.version }, com.vantaflow.kairo.update.AutoUpdateLog(context).refused(), KairoSelf.id,
            )
            for (step in plan) {
                val app = apps.getValue(step.appId)
                if (_states.value[app.id] is InstallState.Downloading) continue
                // Kairo se relance en s'installant : on attend que l'utilisateur soit ailleurs (autre appli, veille).
                if (app.id == KairoSelf.id) while (homeVisible) kotlinx.coroutines.delay(30_000)
                val before = installedVersion(app)
                val sent = runCatching { updater.installAsset(byId.getValue(step.appId).assetUrl, app.packages.first(), auto = step) }.getOrDefault(false)
                // Installée : elle n'est plus proposée sur l'accueil (une minute au plus pour le constater).
                if (sent) repeat(30) {
                    if (installedVersion(app) != before) {
                        _updates.update { it - app.id }
                        return@repeat
                    }
                    kotlinx.coroutines.delay(2000)
                }
            }
        } finally {
            autoMutex.unlock()
        }
    }

    @Volatile private var lastCheckMs = 0L

    /** Version installée (null si absente). */
    fun installedVersion(app: OurApp): String? = app.packages.firstNotNullOfOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName ?: "?" }.getOrNull()
    }

    fun isInstalled(app: OurApp) = installedVersion(app) != null

    /** Ouvre une appli installée (écran d'accueil TV de l'appli). */
    fun open(app: OurApp) {
        val pm = context.packageManager
        val pkg = app.packages.firstOrNull { runCatching { pm.getPackageInfo(it, 0) }.isSuccess } ?: return
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return
        runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Commande ADB à lancer si l'appli doit s'afficher par-dessus les autres et que la box ne
     * l'y autorise pas encore (null sinon : autorisation déjà donnée, ou appli absente).
     */
    fun missingOverlayCommand(app: OurApp): String? {
        if (!app.needsOverlay) return null
        val pkg = app.packages.first()
        val uid = runCatching { context.packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull() ?: return null
        val ops = context.getSystemService(android.app.AppOpsManager::class.java)
        val mode = runCatching { ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg) }.getOrNull()
        return if (mode == android.app.AppOpsManager.MODE_ALLOWED) null
        else "adb shell appops set $pkg SYSTEM_ALERT_WINDOW allow"
    }

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
                set(app, InstallState.Failed(R.string.apps_github_unreachable))
                return@launch
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.apps_downloading_toast, app.name, (remote.size / 1_048_576).toInt()), Toast.LENGTH_SHORT).show()
            }
            val ok = runCatching {
                updater.installAsset(remote.assetUrl, app.packages.first()) { bytes: Long ->
                    if (remote.size > 0) set(app, InstallState.Downloading((bytes * 100 / remote.size).toInt().coerceIn(0, 100)))
                }
            }.getOrDefault(false)
            set(app, if (ok) InstallState.Confirm else InstallState.Failed(R.string.apps_download_failed))
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
                // Installation annulée ou refusée : la carte redevient normale au lieu de rester sur « Confirmez ».
                set(app, InstallState.Idle)
            }
        }
    }

    /**
     * Au retour dans Kairo : une appli installée entre-temps repasse à l'état normal. [check] faux
     * (porte de démarrage encore fermée) : pas de nouvelle vérification sur GitHub.
     */
    fun refresh(check: Boolean = true) {
        _states.update { states -> states.filterNot { (id, s) -> s is InstallState.Confirm && id != KairoSelf.id && OurApps.firstOrNull { it.id == id }?.let(::isInstalled) == true } }
        // Mise à jour installée : la version ne dépasse plus celle publiée, on la retire au prochain contrôle.
        // Sinon, nouvelle vérification toutes les 12 h : la box reste allumée et Kairo n'est pas relancé.
        // Mise à jour en attente : contrôle plus rapproché, mais pas à chaque retour (GitHub limite à 60 requêtes par heure).
        val every = if (_updates.value.isEmpty()) CHECK_EVERY_MS else PENDING_CHECK_EVERY_MS
        if (check && System.currentTimeMillis() - lastCheckMs > every) checkUpdates(delayMs = 0)
    }

    private fun set(app: OurApp, state: InstallState) = _states.update { it + (app.id to state) }
}
