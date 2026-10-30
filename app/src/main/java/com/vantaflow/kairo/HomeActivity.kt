package com.vantaflow.kairo

import com.vantaflow.kairo.apps.OurApps
import com.vantaflow.kairo.radio.RadioScreen
import com.vantaflow.kairo.radio.Radio
import com.vantaflow.kairo.remote.KeyRemapService
import com.vantaflow.kairo.system.MemoryCleaner
import com.vantaflow.kairo.system.formatMb
import com.vantaflow.kairo.tools.CleanerScreen
import com.vantaflow.kairo.tools.SleepTimerDialog
import com.vantaflow.kairo.tools.Tool
import com.vantaflow.kairo.tools.ToolsMenu
import com.vantaflow.kairo.tools.WeatherScreen
import com.vantaflow.kairo.tools.formatMinutes
import com.vantaflow.kairo.remote.KairoCommands
import com.vantaflow.kairo.settings.Section
import com.vantaflow.kairo.onboarding.Onboarding
import kotlinx.coroutines.flow.first
import com.vantaflow.kairo.settings.setupNeeded
import com.vantaflow.kairo.reco.passthroughInputs
import com.vantaflow.kairo.reco.LiveChannel
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import com.vantaflow.kairo.ui.disableComposeAccessibilityTracking
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vantaflow.kairo.apps.AppActionsDialog
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.apps.AppsScreen
import com.vantaflow.kairo.apps.AppsViewModel
import com.vantaflow.kairo.home.HomeScreen
import com.vantaflow.kairo.home.HomeViewModel
import com.vantaflow.kairo.network.NetworkMonitor
import android.view.KeyEvent
import com.vantaflow.kairo.reco.Program
import com.vantaflow.kairo.remote.HomeFocus
import com.vantaflow.kairo.search.SearchScreen
import com.vantaflow.kairo.settings.DefaultProfile
import com.vantaflow.kairo.settings.PinGuard
import com.vantaflow.kairo.settings.PinPrompt
import com.vantaflow.kairo.system.openPlayStore
import com.vantaflow.kairo.reco.TvProviderEpgSource
import com.vantaflow.kairo.reco.launchProgram
import com.vantaflow.kairo.settings.SettingsScreen
import com.vantaflow.kairo.settings.SettingsViewModel
import com.vantaflow.kairo.ui.DesignScale
import com.vantaflow.kairo.ui.startSafely
import com.vantaflow.kairo.ui.theme.TvHomeTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.vantaflow.kairo.ui.theme.Background
import kotlinx.coroutines.flow.combine
import com.vantaflow.kairo.ui.theme.LocalHomeLayout
import com.vantaflow.kairo.ui.theme.themeFor

enum class Destination { Home, Apps, Settings, Search, Radio, Weather, Cleaner }

class HomeActivity : ComponentActivity() {
    private val container by lazy { (application as TvHomeApp).container }

    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                HomeViewModel(
                    installedApps = container.appRepository::installedApps,
                    appChanges = container.appRepository.changes,
                    prefs = container.prefs,
                    clock = container.clock,
                    launcher = ::launchApp,
                    epg = TvProviderEpgSource(applicationContext),
                    nextWallpaper = container.nextWallpaper,
                    weather = container.weather,
                    tvInputs = { passthroughInputs(applicationContext) },
                    resumePrograms = { usage, exclude ->
                        com.vantaflow.kairo.reco.resumePrograms(applicationContext, usage, exclude, System.currentTimeMillis())
                    },
                )
            }
        }
    }

    private val appsViewModel: AppsViewModel by viewModels {
        viewModelFactory {
            initializer {
                AppsViewModel(container.appRepository::installedApps, container.appRepository.changes, container.prefs)
            }
        }
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        viewModelFactory { initializer { SettingsViewModel(
                        container.prefs,
                        container.clock,
                        TvProviderEpgSource(applicationContext),
                        container.nextWallpaper,
                    ) } }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = homeViewModel.syncClock()
    }

    /** Écran demandé par un appui sur Home : on revient toujours à l'accueil. */
    private var destination by mutableStateOf(Destination.Home)
    /** Écran laissé derrière le panneau des réglages (Météo, Radio…), retrouvé à leur fermeture. */
    private var settingsReturn by mutableStateOf(Destination.Home)

    /** Panneau des réglages par-dessus l'écran en cours, sans revenir à l'accueil. */
    private fun openSettingsPanel() {
        if (destination != Destination.Settings) settingsReturn = destination
        destination = Destination.Settings
    }
    /** Accueil animé plein écran (premier lancement d'une box pas encore configurée). */
    private var onboarding by mutableStateOf(false)
    /** Texte reçu du téléphone, pré-rempli dans la recherche. */
    private var phoneQuery by mutableStateOf("")
    /** Section des réglages ouverte d'emblée (touche Entrées de la télécommande). */
    private var settingsSection by mutableStateOf<Section?>(null)
    private var actionsFor by mutableStateOf<AppInfo?>(null)
    /** Choix du minuteur de mise en veille ouvert. */
    private var sleepDialog by mutableStateOf(false)
    /** Menu « Outils » de l'en-tête ouvert. */
    private var toolsMenu by mutableStateOf(false)
    /** L'écran Radio s'ouvre sur le lecteur (pastille « ♪ » de l'en-tête). */
    private var radioPlayer by mutableStateOf(false)
    /** Petit lecteur radio ouvert par la pastille « ♪ ». */
    private var miniPlayer by mutableStateOf(false)
    /** Favori en cours de déplacement sur l'accueil (← → puis OK), comme sur Google TV. */
    private var movingFavorite by mutableStateOf<String?>(null)
    private var searchWithVoice by mutableStateOf(false)
    /** Réglages déverrouillés par le PIN du profil enfant (reverrouillés au retour à l'accueil). */
    private var settingsUnlocked by mutableStateOf(false)
    private val homeFocus = HomeFocus()
    private val pinGuard = PinGuard()
    private val networkMonitor by lazy { NetworkMonitor(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opened = openFromIntent(intent)
        lifecycleScope.launch {
            if (!opened && !container.prefs.setupShown.first()) {
                container.prefs.setSetupShown()
                if (setupNeeded(this@HomeActivity)) onboarding = true
            }
        }
        // Touches Réglages / Entrées vues par le service pendant que Kairo est à l'écran.
        lifecycleScope.launch {
            KairoCommands.openSettings.collect { section ->
                onboarding = false
                settingsSection = Section.entries.firstOrNull { it.name == section }
                openSettingsPanel()
            }
        }
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback, Handler(mainLooper))
        setContent {
            DesignScale {
            // Rien n'est dessiné (fond uni) tant que le thème et l'affichage ne sont pas lus : pas de flash.
            val look by remember {
                combine(container.prefs.themeId, container.prefs.customTheme, container.prefs.homeLayout) { id, custom, layout ->
                    themeFor(id, custom) to layout
                }
            }.collectAsStateWithLifecycle(null)
            val current = look
            if (current == null) {
                Box(Modifier.fillMaxSize().background(Background))
            } else {
                TvHomeTheme(current.first) {
                    CompositionLocalProvider(LocalHomeLayout provides current.second) {
                        Box(Modifier.fillMaxSize()) {
                            Root()
                            if (onboarding) {
                                Onboarding(onFinish = {
                                    onboarding = false
                                    destination = Destination.Home
                                    homeFocus.reset()
                                })
                            }
                        }
                    }
                }
            }
            }
        }
    }

    /** `adb shell am start -n com.vantaflow.kairo/.HomeActivity --es tvhome.open Apps` ouvre un écran donné. */
    private fun openFromIntent(intent: Intent?): Boolean {
        val target = intent?.getStringExtra("tvhome.open") ?: return false
        // « Onboarding » : relance l'accueil animé (tests, captures).
        if (target == "Onboarding") {
            onboarding = true
            return true
        }
        val next = Destination.entries.firstOrNull { it.name == target } ?: return false
        if (next == Destination.Settings) openSettingsPanel() else destination = next
        // Texte tapé sur le téléphone : pré-rempli dans la recherche.
        phoneQuery = intent.getStringExtra("tvhome.query").orEmpty()
        settingsSection = Section.entries.firstOrNull { it.name == intent.getStringExtra("tvhome.section") }
        searchWithVoice = false
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (openFromIntent(intent)) return
        // Touche Home : retour à l'accueil, menu fermé, focus sur la 1re tuile.
        destination = Destination.Home
        actionsFor = null
        toolsMenu = false
        miniPlayer = false
        settingsUnlocked = false
        homeFocus.reset()
    }

    /** Touche Recherche (ou Assistant si la box la transmet) : écran de recherche. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val voice = keyCode == KeyEvent.KEYCODE_ASSIST || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST
        // Touche Réglages (roue dentée) : sur Google TV, c'est le launcher Google qui la gérait.
        if (keyCode == KeyEvent.KEYCODE_SETTINGS || keyCode == KeyEvent.KEYCODE_NOTIFICATION) {
            onboarding = false
            settingsSection = null
            openSettingsPanel()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_SEARCH || voice) {
            searchWithVoice = voice
            phoneQuery = ""
            destination = Destination.Search
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        // Retour sur l'accueil : l'appli quittée est notée, puis nettoyage si besoin (sans bloquer l'affichage).
        container.autoCleaner.onForeground(packageName)
        container.ensureAdBlock()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(3_000)
            runCatching { container.autoCleaner.check() }
        }
        homeViewModel.setForeground(true)
    }

    override fun onStop() {
        homeViewModel.setForeground(false)
        // Écran éteint ou autre appli : les réglages du profil enfant se reverrouillent.
        settingsUnlocked = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        container.ourApps.refresh()
        // Après l'attachement de la vue Compose (elle relit alors les services d'accessibilité).
        window.decorView.post { disableComposeAccessibilityTracking(window.decorView) }
        homeViewModel.refreshReco()
    }

    override fun onDestroy() {
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        super.onDestroy()
    }

    @Composable
    private fun Root() {
        val launch: (AppInfo) -> Unit = { homeViewModel.launch(it.packageName) }

        // Sur l'accueil, Retour ne doit jamais fermer le launcher.
        BackHandler {
            // Sur l'accueil, Retour remonte au premier favori (comme Google TV).
            when (destination) {
                Destination.Home -> homeFocus.reset()
                Destination.Settings -> destination = settingsReturn
                else -> destination = Destination.Home
            }
            settingsUnlocked = false
        }
        val profile by container.prefs.currentProfile.collectAsStateWithLifecycle(DefaultProfile)
        val programClick: (Program) -> Unit = { program ->
            launchProgram(program, container.appRepository::launchIntent) {
                startSafely(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        // Réglages ouverts : l'écran d'où ils ont été ouverts reste affiché derrière le panneau.
        when (if (destination == Destination.Settings) settingsReturn else destination) {
            Destination.Home, Destination.Settings -> {
                val state by homeViewModel.state.collectAsStateWithLifecycle()
                val apps by appsViewModel.apps.collectAsStateWithLifecycle()
                val net by networkMonitor.state.collectAsStateWithLifecycle(networkMonitor.current())
                val recent by container.prefs.recentApps.collectAsStateWithLifecycle(emptyList())
                val ourUpdates by container.ourApps.updates.collectAsStateWithLifecycle()
                val showRecent = LocalHomeLayout.current.showRecentApps && !profile.kids
                val tools = if (LocalHomeLayout.current.showTools) toolsFor(profile.kids) else emptyList()
                HomeScreen(
                    // Dernières applis ouvertes, hors favoris (déjà en tête) et applis masquées.
                    recentApps = if (!showRecent) emptyList() else remember(recent, apps, state.favorites) {
                        val favs = state.favorites.map { it.packageName }.toSet()
                        val byPkg = apps.associateBy { it.packageName }
                        recent.filterNot { it in favs }.mapNotNull { byPkg[it] }
                    },
                    // Profil enfant : seulement ses favoris, pas de rangée « Toutes les applis ».
                    otherApps = if (profile.kids) emptyList() else remember(apps, state.favorites) {
                        val favs = state.favorites.map { it.packageName }.toSet()
                        apps.filterNot { it.packageName in favs }
                    },
                    netState = net,
                    appLabels = remember(apps) { apps.associate { it.packageName to it.label } },
                    onProgramClick = programClick,
                    onOpenSearch = { searchWithVoice = false; phoneQuery = ""; destination = Destination.Search },
                    // Profil enfant : pas de recommandations (elles ouvrent n'importe quelle appli).
                    // Chaînes TV : seulement si la box en a, que le réglage les montre et hors profil enfant.
                    state = when {
                        profile.kids -> state.copy(watchNext = emptyList(), recoRows = emptyList(), liveChannels = emptyList(), tvInputs = emptyList())
                        !LocalHomeLayout.current.showLiveTv -> state.copy(liveChannels = emptyList(), tvInputs = emptyList())
                        else -> state
                    },
                    onOpenTv = ::openTv,
                    // Nos applis à proposer : recalculées quand la liste des applis change (installation).
                    ourApps = if (profile.kids || !LocalHomeLayout.current.showOurApps) emptyList() else remember(apps, ourUpdates) {
                        OurApps.filter { !container.ourApps.isInstalled(it) || it.id in ourUpdates }
                    },
                    ourAppUpdates = ourUpdates,
                    onOpenTools = { toolsMenu = true },
                    nowPlaying = com.vantaflow.kairo.radio.Radio.state.collectAsStateWithLifecycle().value
                        .let { r -> r.station?.name?.takeIf { r.active } },
                    onOpenNowPlaying = { miniPlayer = true },
                    tools = tools,
                    toolSubtitles = if (tools.isEmpty()) emptyMap() else rememberToolSubtitles(),
                    onOpenTool = ::openTool,
                    ourAppStates = container.ourApps.states.collectAsStateWithLifecycle().value,
                    onInstallOurApp = container.ourApps::install,
                    focus = homeFocus,
                    onLaunch = launch,
                    onFavoriteLongClick = { if (!profile.kids) actionsFor = it },
                    onOpenApps = { destination = Destination.Apps },
                    onOpenSettings = { openSettingsPanel() },
                    movingFavorite = movingFavorite,
                    onMoveFavorite = appsViewModel::moveFavoriteBy,
                    onMoveDone = { movingFavorite = null },
                )
            }
            Destination.Apps -> {
                val allApps by appsViewModel.apps.collectAsStateWithLifecycle()
                val showHidden by appsViewModel.showHidden.collectAsStateWithLifecycle()
                val hiddenApps by appsViewModel.hidden.collectAsStateWithLifecycle()
                val home by homeViewModel.state.collectAsStateWithLifecycle()
                // Profil enfant : seulement ses favoris.
                val apps = if (profile.kids) home.favorites else allApps
                AppsScreen(
                    apps = apps,
                    showHidden = showHidden,
                    onShowHiddenChange = { appsViewModel.showHidden.value = it },
                    hidden = hiddenApps,
                    onLaunch = launch,
                    onLongClick = { if (!profile.kids) actionsFor = it },
                    onOpenPlayStore = if (profile.kids) null else ({ openPlayStore(null) }),
                )
            }
            Destination.Search -> {
                val apps by appsViewModel.apps.collectAsStateWithLifecycle()
                val home by homeViewModel.state.collectAsStateWithLifecycle()
                SearchScreen(
                    apps = if (profile.kids) home.favorites else apps,
                    programs = if (profile.kids) emptyList() else home.watchNext + home.recoRows.flatMap { it.programs },
                    appLabels = remember(apps) { apps.associate { it.packageName to it.label } },
                    onLaunchApp = launch,
                    onProgramClick = programClick,
                    onSearchIn = { startSafely(it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    startWithVoice = searchWithVoice,
                    initialQuery = phoneQuery,
                )
            }
            Destination.Radio -> RadioScreen(startInPlayer = radioPlayer)
            Destination.Weather -> WeatherScreen()
            Destination.Cleaner -> {
                val apps by appsViewModel.apps.collectAsStateWithLifecycle()
                CleanerScreen(apps)
            }
        }

        if (miniPlayer) {
            com.vantaflow.kairo.radio.RadioMiniPlayer(
                onOpenPlayer = { miniPlayer = false; radioPlayer = true; destination = Destination.Radio },
                onDismiss = { miniPlayer = false; homeFocus.refocus() },
            )
        }

        if (toolsMenu) {
            ToolsMenu(
                tools = toolsFor(profile.kids),
                subtitles = rememberToolSubtitles(),
                onOpen = { toolsMenu = false; openTool(it) },
                onDismiss = { toolsMenu = false; homeFocus.refocus() },
            )
        }

        if (sleepDialog) {
            val remaining = remember(container.sleepTimer.endsAt.collectAsStateWithLifecycle().value) { container.sleepTimer.remainingMinutes() }
            SleepTimerDialog(
                remaining = remaining,
                serviceOn = KeyRemapService.instance != null,
                onPick = { min ->
                    if (min == null) container.sleepTimer.cancel() else container.sleepTimer.start(min)
                    sleepDialog = false
                },
                onDismiss = { sleepDialog = false },
            )
        }

        // Réglages : panneau latéral par-dessus l'accueil (les changements s'y voient en direct).
        if (destination == Destination.Settings) {
            val apps by appsViewModel.apps.collectAsStateWithLifecycle()
            val close = {
                destination = settingsReturn
                settingsUnlocked = false
                if (settingsReturn == Destination.Home) homeFocus.refocus()
            }
            if (profile.kids && !settingsUnlocked) {
                PinPrompt(profile, pinGuard, onUnlocked = { settingsUnlocked = true }, onCancel = close)
            } else {
                SettingsScreen(
                    settingsViewModel, apps, networkMonitor, networkMonitor.state,
                    onClose = { settingsSection = null; close() },
                    onReplayOnboarding = { onboarding = true },
                    initialSection = settingsSection,
                )
            }
        }

        actionsFor?.let { app ->
            val favorites by appsViewModel.favorites.collectAsStateWithLifecycle()
            val hidden by appsViewModel.hidden.collectAsStateWithLifecycle()
            AppActionsDialog(
                app = app,
                isFavorite = app.packageName in favorites,
                isHidden = app.packageName in hidden,
                onToggleFavorite = {
                    // Ajoutée aux favoris : elle quitte sa rangée, le focus la suit dans les favoris.
                    // Retirée : le focus passe au favori voisin.
                    if (destination == Destination.Home) {
                        val i = favorites.indexOf(app.packageName)
                        homeFocus.followFavorite = if (i < 0) app.packageName else favorites.getOrNull(i + 1) ?: favorites.getOrNull(i - 1)
                    }
                    appsViewModel.toggleFavorite(app.packageName)
                },
                onToggleHidden = { appsViewModel.toggleHidden(app.packageName) },
                onAppInfo = {
                    startSafely(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")),
                    )
                },
                onUninstall = {
                    startSafely(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                },
                onDismiss = { actionsFor = null },
                onPlayStore = { openPlayStore(app.packageName) },
                onMove = if (destination == Destination.Home) ({ movingFavorite = app.packageName }) else null,
            )
        }
    }

    private fun toolsFor(kids: Boolean) = if (kids) Tool.entries - Tool.Cleaner else Tool.entries

    private fun openTool(tool: Tool) {
        when (tool) {
            Tool.Radio -> { radioPlayer = Radio.state.value.active; destination = Destination.Radio }
            Tool.Weather -> destination = Destination.Weather
            Tool.Cleaner -> destination = Destination.Cleaner
            Tool.Sleep -> sleepDialog = true
        }
    }

    /** État de chaque outil, recalculé à chaque minute (l'heure de l'accueil change). */
    @Composable
    private fun rememberToolSubtitles(): Map<Tool, String> {
        val radio by com.vantaflow.kairo.radio.Radio.state.collectAsStateWithLifecycle()
        val sleepEnd by container.sleepTimer.endsAt.collectAsStateWithLifecycle()
        val home by homeViewModel.state.collectAsStateWithLifecycle()
        return remember(radio, sleepEnd, home.timeText, home.weatherText) {
            mapOf(
                Tool.Radio to (radio.station?.takeIf { radio.active }?.let { "♪ ${it.name}" } ?: "Radios en direct"),
                Tool.Weather to (home.weatherText ?: "Prévisions sur 7 jours"),
                Tool.Sleep to (container.sleepTimer.remainingMinutes()?.let { "Veille dans ${formatMinutes(it)}" } ?: "Éteindre plus tard"),
                Tool.Cleaner to "${formatMb(MemoryCleaner(this).state().availableBytes)} de mémoire libre",
            )
        }
    }

    /** Ouvre une chaîne ou une entrée dans l'appli TV du système ; à défaut, l'appli du tuner. */
    private fun openTv(channel: LiveChannel) {
        if (!startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(channel.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            launchApp(channel.packageName)
        }
    }

    private fun launchApp(pkg: String) {
        val intent = container.appRepository.launchIntent(pkg) ?: return
        startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        lifecycleScope.launch { container.prefs.recordLaunch(pkg) }
    }
}
