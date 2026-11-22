package com.vantaflow.kairo

import com.vantaflow.kairo.catalog.CATALOG_SCHEME
import com.vantaflow.kairo.catalog.CatalogDetail
import com.vantaflow.kairo.catalog.CatalogItem
import com.vantaflow.kairo.catalog.CatalogRow
import com.vantaflow.kairo.catalog.MediaType
import com.vantaflow.kairo.catalog.catalogUri
import com.vantaflow.kairo.catalog.toChannel
import com.vantaflow.kairo.catalog.toProgram
import com.vantaflow.kairo.home.HomeTab
import com.vantaflow.kairo.apps.KairoSelf
import com.vantaflow.kairo.apps.OurApps
import com.vantaflow.kairo.radio.RadioScreen
import com.vantaflow.kairo.radio.Radio
import com.vantaflow.kairo.remote.KeyRemapService
import com.vantaflow.kairo.system.MemoryCleaner
import com.vantaflow.kairo.system.SafeHandler
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import com.vantaflow.kairo.system.orDefaultAfter
import com.vantaflow.kairo.system.orDefaultOnError
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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
import kotlinx.coroutines.flow.stateIn
import com.vantaflow.kairo.ui.theme.LocalHomeLayout
import com.vantaflow.kairo.ui.theme.themeFor
import com.vantaflow.kairo.ui.theme.themeById
import com.vantaflow.kairo.ui.theme.HomeLayout

/** Réglages ouverts au moment d'une recréation (changement de langue). */
private const val STATE_SETTINGS = "kairo.settings"

enum class Destination { Home, Apps, Settings, Search, Radio, Weather, Cleaner }

class HomeActivity : ComponentActivity() {
    private val container by lazy { (application as TvHomeApp).container }
    /** Mode sans échec (démarrages répétés) : accueil réduit, aucun travail de fond. */
    private val safeMode by lazy { (application as TvHomeApp).safeMode }
    /** Kairo retiré de l'accueil (son mode sans échec plantait) : l'activité ne fait rien. */
    private val withdrawn by lazy { (application as TvHomeApp).withdrawn }
    /** Porte de démarrage (null en mode sans échec). */
    private val gate get() = (application as TvHomeApp).gate
    /** Première image de l'accueil déjà signalée à la porte. */
    private var homeReported = false
    /** Premier lancement vérifié : l'assistant, s'il doit s'afficher, est déjà décidé. */
    private var setupChecked by mutableStateOf(false)

    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                HomeViewModel(
                    apps = container.appRepository.apps,
                    prefs = container.prefs,
                    clock = container.clock,
                    launcher = ::launchApp,
                    // Mode sans échec : ni TvProvider, ni réseau (fonds, météo, heure).
                    epg = if (safeMode) null else TvProviderEpgSource(applicationContext),
                    nextWallpaper = if (safeMode) null else container.nextWallpaper,
                    weather = if (safeMode) null else container.weather,
                    tvInputs = if (safeMode) null else ({ passthroughInputs(applicationContext) }),
                    resumePrograms = if (safeMode) null else ({ usage, exclude ->
                        com.vantaflow.kairo.reco.resumePrograms(applicationContext, usage, exclude, System.currentTimeMillis())
                    }),
                    background = !safeMode,
                )
            }
        }
    }

    private val appsViewModel: AppsViewModel by viewModels {
        viewModelFactory {
            initializer {
                AppsViewModel(container.appRepository.apps, container.prefs)
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
    /** Petit lecteur d'une autre appli (Spotify, Anghami…). */
    private var mediaPlayer by mutableStateOf(false)
    /** Onglet de l'accueil, rangées du catalogue (TMDB) et fiche ouverte. */
    private var homeTab by mutableStateOf(HomeTab.ForYou)
    private var catalogRows by mutableStateOf<List<CatalogRow>>(emptyList())
    private var catalogDetail by mutableStateOf<CatalogItem?>(null)

    /** Lecture du catalogue en cours : une seule à la fois, même après plusieurs retours sur l'accueil. */
    private var catalogJob: kotlinx.coroutines.Job? = null

    /** Catalogue relu (fichier de la box) ou téléchargé s'il date de plus d'un jour, sans bloquer l'affichage. */
    private fun refreshCatalog() {
        if (catalogJob?.isActive == true) return
        catalogJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO + SafeHandler) {
            // Inventaire partagé avec l'accueil : pas de nouveau parcours des applis à chaque retour.
            val installed = container.appRepository.apps.filterNotNull().first().associate { it.packageName to it.label }
            val rows = runCatching { container.catalog.rows(installed) }.getOrDefault(emptyList())
            // État Compose : écrit sur le fil principal.
            if (rows.isNotEmpty()) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { catalogRows = rows }
        }
    }
    /**
     * Thème et affichage, partagés pour toute la vie de l'activité : la valeur par défaut (après
     * [com.vantaflow.kairo.home.LOOK_TIMEOUT_MS]) n'est donnée que si rien n'a encore été lu ; un
     * retour sur l'accueil ne relance pas ce minuteur.
     */
    private val lookFlow by lazy {
        combine(container.prefs.themeId, container.prefs.customTheme, container.prefs.homeLayout) { id, custom, layout ->
            themeFor(id, custom) to layout
        }
            // Préférences illisibles : thème et affichage par défaut tout de suite, puis nouvel essai.
            .orDefaultOnError(themeById(null) to HomeLayout(), "thème")
            .orDefaultAfter(com.vantaflow.kairo.home.LOOK_TIMEOUT_MS, themeById(null) to HomeLayout())
            .stateIn(kotlinx.coroutines.CoroutineScope(lifecycleScope.coroutineContext + SafeHandler), kotlinx.coroutines.flow.SharingStarted.Eagerly, null)
    }

    /** Favori en cours de déplacement sur l'accueil (← → puis OK), comme sur Google TV. */
    private var movingFavorite by mutableStateOf<String?>(null)
    private var searchWithVoice by mutableStateOf(false)
    /** Réglages déverrouillés par le PIN du profil enfant (reverrouillés au retour à l'accueil). */
    private var settingsUnlocked by mutableStateOf(false)
    private val homeFocus = HomeFocus()
    private val pinGuard = PinGuard()
    private val networkMonitor by lazy { NetworkMonitor(applicationContext) }

    /** Langue choisie dans Kairo (Réglages › Langue), appliquée avant toute ressource. */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase)
        com.vantaflow.kairo.i18n.AppLocale.applyTo(this, newBase)
    }

    /** Recréation (changement de langue) : les réglages restent ouverts sur la même section. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (destination == Destination.Settings) outState.putString(STATE_SETTINGS, settingsSection?.name ?: "")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kairo vient de se retirer de l'accueil (lancement déjà en route) : pas de conteneur, on s'efface.
        // L'accueil est désactivé à nouveau : un état de composant perdu à une coupure le relancerait sans fin.
        if (withdrawn) {
            val outcome = com.vantaflow.kairo.system.HomeFallback(this).repairAtStartup()
            finish()
            // Kairo a repris l'accueil (plus d'autre accueil) : nouveau process, avec son conteneur.
            if (outcome != com.vantaflow.kairo.system.StartupOutcome.Withdrawn) Runtime.getRuntime().exit(0)
            return
        }
        // Écran recréé par un changement de langue : le ViewModel survit, sa météo est encore dans l'ancienne.
        if (savedInstanceState != null) homeViewModel.reloadWeather()
        val restoredSettings = savedInstanceState?.getString(STATE_SETTINGS)
        val opened = if (restoredSettings != null) {
            settingsSection = Section.entries.firstOrNull { it.name == restoredSettings }
            openSettingsPanel()
            true
        } else openFromIntent(intent)
        lifecycleScope.launch(SafeHandler) {
            // Mode sans échec : pas d'accueil animé par-dessus le bandeau.
            if (!opened && !safeMode && !container.prefs.setupShown.first()) {
                container.prefs.setSetupShown()
                if (setupNeeded(this@HomeActivity)) onboarding = true
            }
            setupChecked = true
        }
        // Touches Réglages / Entrées vues par le service pendant que Kairo est à l'écran.
        lifecycleScope.launch(SafeHandler) {
            KairoCommands.openSettings.collect { section ->
                onboarding = false
                settingsSection = Section.entries.firstOrNull { it.name == section }
                openSettingsPanel()
            }
        }
        // Réseau retrouvé : heure resynchronisée (pas en mode sans échec, sans réseau).
        if (!safeMode) getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback, Handler(mainLooper))
        setContent {
            DesignScale {
            // Rien n'est dessiné (fond uni) tant que le thème et l'affichage ne sont pas lus : pas de flash.
            // Lecture lente (disque encore occupé par le démarrage de la box) : thème et affichage par
            // défaut après 700 ms, puis les vrais dès qu'ils arrivent ; jamais d'écran noir prolongé.
            val look by lookFlow.collectAsStateWithLifecycle()
            val current = look
            if (current == null) {
                Box(Modifier.fillMaxSize().background(Background))
            } else {
                TvHomeTheme(current.first) {
                    CompositionLocalProvider(LocalHomeLayout provides current.second) {
                        Box(Modifier.fillMaxSize()) {
                            Root()
                            // Accueil réellement dessiné (pas l'assistant ni un autre écran) : la porte de démarrage compte à partir d'ici.
                            if (!safeMode && !homeReported && setupChecked && !onboarding && destination == Destination.Home) {
                                LaunchedEffect(Unit) {
                                    withFrameNanos { }
                                    homeReported = true
                                    gate?.homeShown()
                                }
                            }
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
        mediaPlayer = false
        catalogDetail = null
        homeTab = HomeTab.ForYou
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
        if (!safeMode) {
            // Avant l'ouverture de la porte de démarrage, c'est elle qui lance ces fonctions.
            if (gate?.open?.value == true) {
                container.ensureAdBlock()
                com.vantaflow.kairo.media.NowPlaying.start(this)
                container.ultraTv.refresh()
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO + SafeHandler) {
                    kotlinx.coroutines.delay(3_000)
                    runCatching { container.autoCleaner.check() }
                }
            }
            refreshCatalog()
        }
        homeViewModel.setForeground(true)
        container.ourApps.homeVisible = true
    }

    override fun onStop() {
        homeViewModel.setForeground(false)
        // Écran éteint ou autre appli : les réglages du profil enfant se reverrouillent.
        settingsUnlocked = false
        super.onStop()
        container.ourApps.homeVisible = false
    }

    override fun onResume() {
        super.onResume()
        // Mode sans échec : pas de contrôle des mises à jour (réseau).
        if (!safeMode) container.ourApps.refresh(check = gate?.open?.value == true)
        // Après l'attachement de la vue Compose (elle relit alors les services d'accessibilité).
        window.decorView.post { disableComposeAccessibilityTracking(window.decorView) }
        homeViewModel.refreshReco()
    }

    override fun onDestroy() {
        if (!safeMode && !withdrawn) getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
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
        val myList by container.prefs.myList.collectAsStateWithLifecycle(emptyList())
        val programClick: (Program) -> Unit = click@{ program ->
            // Carte du catalogue : fiche dans Kairo (où le regarder, Ma liste).
            program.intentUri?.takeIf { it.startsWith(CATALOG_SCHEME) }?.let { uri ->
                catalogDetail = (catalogRows.flatMap { it.items } + myList).firstOrNull { catalogUri(it) == uri }
                return@click
            }
            launchProgram(program, container.appRepository::launchIntent) {
                startSafely(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        // Réglages ouverts : l'écran d'où ils ont été ouverts reste affiché derrière le panneau.
        when (if (destination == Destination.Settings) settingsReturn else destination) {
            Destination.Home, Destination.Settings -> if (safeMode) SafeModeHome(launch, profile.kids) else {
                val state by homeViewModel.state.collectAsStateWithLifecycle()
                val apps by appsViewModel.visibleApps.collectAsStateWithLifecycle()
                val net by networkMonitor.state.collectAsStateWithLifecycle(networkMonitor.current())
                val recent by container.prefs.recentApps.collectAsStateWithLifecycle(emptyList())
                val ourUpdates by container.ourApps.updates.collectAsStateWithLifecycle()
                val showRecent = LocalHomeLayout.current.showRecentApps && !profile.kids
                val tools = if (LocalHomeLayout.current.showTools) toolsFor(profile.kids) else emptyList()
                val tabsShown = LocalHomeLayout.current.let { l ->
                    remember(l.showTabMovies, l.showTabShows, l.showTabApps) { com.vantaflow.kairo.home.shownTabs(l.showTabMovies, l.showTabShows, l.showTabApps) }
                }
                if (homeTab !in tabsShown) homeTab = HomeTab.ForYou
                // Pastille « ♪ » : la radio de Kairo, sinon ce que joue une autre appli.
                val radioName = com.vantaflow.kairo.radio.Radio.state.collectAsStateWithLifecycle().value
                    .let { r -> r.station?.name?.takeIf { r.active } }
                val media by com.vantaflow.kairo.media.NowPlaying.state.collectAsStateWithLifecycle()
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
                    // Kairo en tête quand une version plus récente est publiée (réglable à part).
                    ourApps = run {
                        val layout = LocalHomeLayout.current
                        if (profile.kids) emptyList() else remember(apps, ourUpdates, layout.showOurApps, layout.showKairoUpdate) {
                            val self = if (layout.showKairoUpdate && KairoSelf.id in ourUpdates) listOf(KairoSelf) else emptyList()
                            val others = if (layout.showOurApps) OurApps.filter { !container.ourApps.isInstalled(it) || it.id in ourUpdates } else emptyList()
                            self + others
                        }
                    },
                    ourAppUpdates = ourUpdates,
                    onOpenTools = { toolsMenu = true },
                    nowPlaying = radioName ?: media?.title,
                    onOpenNowPlaying = { if (radioName != null) miniPlayer = true else mediaPlayer = true },
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
                    // Un onglet décoché dans les réglages disparaît ; s'il ne reste que « Pour vous », plus de barre d'onglets.
                    tab = homeTab.takeIf { catalogRows.isNotEmpty() && !profile.kids && tabsShown.size > 1 },
                    tabsShown = tabsShown,
                    onTab = { homeTab = it },
                    catalog = if (profile.kids) emptyList() else remember(catalogRows, homeTab) { catalogFor(homeTab, catalogRows) },
                    myList = if (profile.kids) emptyList() else remember(myList, homeTab) {
                        myList.filter { homeTab.mediaType().let { t -> t == null || it.type == t } }.map { it.toProgram() }
                    },
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
                val apps by appsViewModel.visibleApps.collectAsStateWithLifecycle()
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

        catalogDetail?.let { item ->
            // Titre déjà dans Ma liste à l'ouverture de la fiche : sa carte de Ma liste peut disparaître.
            val openedInList = remember(item) { myList.any { it.type == item.type && it.id == item.id } }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            // Fenêtre à part : à sa fermeture, le focus revient sur la carte d'où la fiche a été ouverte.
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { catalogDetail = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                // Même mise à l'échelle que l'écran principal (la fenêtre de la fiche est à part).
                DesignScale {
                    CatalogDetail(
                        item = item,
                        repository = container.catalog,
                        inMyList = myList.any { it.type == item.type && it.id == item.id },
                        onToggleMyList = { scope.launch { container.prefs.toggleMyList(item) } },
                        onDismiss = {
                            // Retiré de Ma liste depuis sa carte de Ma liste : la carte disparaît, le focus va aux onglets.
                            if (openedInList && myList.none { it.type == item.type && it.id == item.id }) homeFocus.tabsToken++
                            catalogDetail = null
                        },
                    )
                }
            }
        }

        if (mediaPlayer) {
            com.vantaflow.kairo.media.MediaMiniPlayer(onDismiss = { mediaPlayer = false; homeFocus.refocus() })
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
                        // Voisin pris parmi les favoris affichés (la liste enregistrée garde les applis masquées).
                        val shown = homeViewModel.state.value.favorites.map { it.packageName }
                        val i = shown.indexOf(app.packageName)
                        homeFocus.followFavorite =
                            if (app.packageName !in favorites) app.packageName else shown.getOrNull(i + 1) ?: shown.getOrNull(i - 1)
                    }
                    appsViewModel.toggleFavorite(app.packageName)
                },
                onToggleHidden = {
                    // Favori masqué : sa tuile disparaît, le focus passe au favori voisin.
                    if (destination == Destination.Home && app.packageName !in hidden) {
                        val shown = homeViewModel.state.value.favorites.map { it.packageName }
                        val i = shown.indexOf(app.packageName)
                        if (i >= 0) homeFocus.followFavorite = shown.getOrNull(i + 1) ?: shown.getOrNull(i - 1)
                    }
                    appsViewModel.toggleHidden(app.packageName)
                },
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

    /** Accueil du mode sans échec (voir [com.vantaflow.kairo.home.SafeModeHomeScreen]). */
    @Composable
    private fun SafeModeHome(launch: (AppInfo) -> Unit, kids: Boolean) {
        val state by homeViewModel.state.collectAsStateWithLifecycle()
        val apps by appsViewModel.visibleApps.collectAsStateWithLifecycle()
        com.vantaflow.kairo.home.SafeModeHomeScreen(
            state = state,
            focus = homeFocus,
            onLaunch = launch,
            onFavoriteLongClick = { if (!kids) actionsFor = it },
            onOpenApps = { destination = Destination.Apps },
            onOpenSettings = { openSettingsPanel() },
            onRestart = ::restartNormally,
            onAndroidSettings = { startSafely(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            otherApps = if (kids) emptyList() else remember(apps, state.favorites) {
                val favs = state.favorites.map { it.packageName }.toSet()
                apps.filterNot { it.packageName in favs }
            },
            appLabels = remember(apps) { apps.associate { it.packageName to it.label } },
            movingFavorite = movingFavorite,
            onMoveFavorite = appsViewModel::moveFavoriteBy,
            onMoveDone = { movingFavorite = null },
        )
    }

    /**
     * « Redémarrer Kairo normalement » : historique des démarrages effacé, puis nouveau process (le mode
     * sans échec est fixé pour toute la vie du process, une simple recréation de l'activité ne suffit pas).
     */
    private fun restartNormally() {
        (application as TvHomeApp).crashGuard.reset()
        com.vantaflow.kairo.system.HomeFallback(this).resetCrashes()
        // Intention d'accueil (pas LAUNCHER) : l'activité singleTask est reprise, pas ouverte dans une seconde tâche.
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(android.content.ComponentName(this, HomeActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Runtime.getRuntime().exit(0)
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
                Tool.Radio to (radio.station?.takeIf { radio.active }?.let { "♪ ${it.name}" } ?: getString(R.string.home_tool_radio)),
                Tool.Weather to (home.weatherText ?: getString(R.string.home_tool_weather)),
                Tool.Sleep to (container.sleepTimer.remainingMinutes()?.let { getString(R.string.home_tool_sleep_in, formatMinutes(it)) } ?: getString(R.string.home_tool_sleep)),
                Tool.Cleaner to getString(R.string.home_tool_cleaner, formatMb(MemoryCleaner(this).state().availableBytes)),
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
        lifecycleScope.launch(SafeHandler) { container.prefs.recordLaunch(pkg) }
    }
}

private fun HomeTab.mediaType(): MediaType? = when (this) {
    HomeTab.Movies -> MediaType.Movie
    HomeTab.Shows -> MediaType.Tv
    else -> null
}

/** Rangées du catalogue d'un onglet : Pour vous = tendances et plateformes installées ; Films / Séries = leur type. */
private fun catalogFor(tab: HomeTab, rows: List<CatalogRow>) = rows.filter { r ->
    when (val type = tab.mediaType()) {
        null -> r.key == "trending" || r.key.startsWith("movies-") || r.key.startsWith("shows-")
        else -> r.type == type
    }
}.mapIndexed { i, r -> r.toChannel(i) }
