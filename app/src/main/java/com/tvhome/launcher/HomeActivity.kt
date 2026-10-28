package com.tvhome.launcher

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
import com.tvhome.launcher.ui.disableComposeAccessibilityTracking
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tvhome.launcher.apps.AppActionsDialog
import com.tvhome.launcher.apps.AppInfo
import com.tvhome.launcher.apps.AppsScreen
import com.tvhome.launcher.apps.AppsViewModel
import com.tvhome.launcher.home.HomeScreen
import com.tvhome.launcher.home.HomeViewModel
import com.tvhome.launcher.network.NetworkMonitor
import android.view.KeyEvent
import com.tvhome.launcher.reco.Program
import com.tvhome.launcher.remote.HomeFocus
import com.tvhome.launcher.search.SearchScreen
import com.tvhome.launcher.settings.DefaultProfile
import com.tvhome.launcher.settings.PinGuard
import com.tvhome.launcher.settings.PinPrompt
import com.tvhome.launcher.system.openPlayStore
import com.tvhome.launcher.reco.TvProviderEpgSource
import com.tvhome.launcher.reco.launchProgram
import com.tvhome.launcher.settings.SettingsScreen
import com.tvhome.launcher.settings.SettingsViewModel
import com.tvhome.launcher.ui.DesignScale
import com.tvhome.launcher.ui.startSafely
import com.tvhome.launcher.ui.theme.TvHomeTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tvhome.launcher.ui.theme.Background
import kotlinx.coroutines.flow.combine
import com.tvhome.launcher.ui.theme.LocalHomeLayout
import com.tvhome.launcher.ui.theme.themeFor

enum class Destination { Home, Apps, Settings, Search }

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
    private var actionsFor by mutableStateOf<AppInfo?>(null)
    private var searchWithVoice by mutableStateOf(false)
    /** Réglages déverrouillés par le PIN du profil enfant (reverrouillés au retour à l'accueil). */
    private var settingsUnlocked by mutableStateOf(false)
    private val homeFocus = HomeFocus()
    private val pinGuard = PinGuard()
    private val networkMonitor by lazy { NetworkMonitor(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openFromIntent(intent)
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
                    CompositionLocalProvider(LocalHomeLayout provides current.second) { Root() }
                }
            }
            }
        }
    }

    /** `adb shell am start -n com.tvhome.launcher/.HomeActivity --es tvhome.open Apps` ouvre un écran donné. */
    private fun openFromIntent(intent: Intent?): Boolean {
        val target = intent?.getStringExtra("tvhome.open") ?: return false
        destination = Destination.entries.firstOrNull { it.name == target } ?: return false
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (openFromIntent(intent)) return
        // Touche Home : retour à l'accueil, menu fermé, focus sur la 1re tuile.
        destination = Destination.Home
        actionsFor = null
        settingsUnlocked = false
        homeFocus.reset()
    }

    /** Touche Recherche (ou Assistant si la box la transmet) : écran de recherche. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val voice = keyCode == KeyEvent.KEYCODE_ASSIST || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST
        if (keyCode == KeyEvent.KEYCODE_SEARCH || voice) {
            searchWithVoice = voice
            destination = Destination.Search
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
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
            destination = Destination.Home
            settingsUnlocked = false
        }
        val profile by container.prefs.currentProfile.collectAsStateWithLifecycle(DefaultProfile)
        val programClick: (Program) -> Unit = { program ->
            launchProgram(program, container.appRepository::launchIntent) {
                startSafely(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        when (destination) {
            Destination.Home, Destination.Settings -> {
                val state by homeViewModel.state.collectAsStateWithLifecycle()
                val apps by appsViewModel.apps.collectAsStateWithLifecycle()
                val net by networkMonitor.state.collectAsStateWithLifecycle(networkMonitor.current())
                HomeScreen(
                    // Profil enfant : seulement ses favoris, pas de rangée « Toutes les applis ».
                    otherApps = if (profile.kids) emptyList() else remember(apps, state.favorites) {
                        val favs = state.favorites.map { it.packageName }.toSet()
                        apps.filterNot { it.packageName in favs }
                    },
                    netState = net,
                    appLabels = remember(apps) { apps.associate { it.packageName to it.label } },
                    onProgramClick = programClick,
                    onOpenSearch = { searchWithVoice = false; destination = Destination.Search },
                    // Profil enfant : pas de recommandations (elles ouvrent n'importe quelle appli).
                    state = if (profile.kids) state.copy(watchNext = emptyList(), recoRows = emptyList()) else state,
                    focus = homeFocus,
                    onLaunch = launch,
                    onFavoriteLongClick = { if (!profile.kids) actionsFor = it },
                    onOpenApps = { destination = Destination.Apps },
                    onOpenSettings = { destination = Destination.Settings },
                )
            }
            Destination.Apps -> {
                val allApps by appsViewModel.apps.collectAsStateWithLifecycle()
                val showHidden by appsViewModel.showHidden.collectAsStateWithLifecycle()
                val home by homeViewModel.state.collectAsStateWithLifecycle()
                // Profil enfant : seulement ses favoris.
                val apps = if (profile.kids) home.favorites else allApps
                AppsScreen(
                    apps = apps,
                    showHidden = showHidden,
                    onShowHiddenChange = { appsViewModel.showHidden.value = it },
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
                )
            }
        }

        // Réglages : panneau latéral par-dessus l'accueil (les changements s'y voient en direct).
        if (destination == Destination.Settings) {
            val apps by appsViewModel.apps.collectAsStateWithLifecycle()
            val close = {
                destination = Destination.Home
                settingsUnlocked = false
                homeFocus.refocus()
            }
            if (profile.kids && !settingsUnlocked) {
                PinPrompt(profile, pinGuard, onUnlocked = { settingsUnlocked = true }, onCancel = close)
            } else {
                SettingsScreen(settingsViewModel, apps, networkMonitor, networkMonitor.state, onClose = close)
            }
        }

        actionsFor?.let { app ->
            val favorites by appsViewModel.favorites.collectAsStateWithLifecycle()
            val hidden by appsViewModel.hidden.collectAsStateWithLifecycle()
            AppActionsDialog(
                app = app,
                isFavorite = app.packageName in favorites,
                isHidden = app.packageName in hidden,
                onToggleFavorite = { appsViewModel.toggleFavorite(app.packageName) },
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
            )
        }
    }

    private fun launchApp(pkg: String) {
        val intent = container.appRepository.launchIntent(pkg) ?: return
        startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
