package com.vantaflow.kairo.home

import androidx.compose.ui.graphics.Color
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import com.vantaflow.kairo.R
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.composed
import androidx.compose.runtime.key
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.vantaflow.kairo.reco.rememberRowState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.ExperimentalComposeUiApi
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.vantaflow.kairo.apps.AppInfo
import com.vantaflow.kairo.reco.HeroBackground
import com.vantaflow.kairo.reco.Program
import com.vantaflow.kairo.reco.RecoRow
import com.vantaflow.kairo.remote.HomeFocus
import com.vantaflow.kairo.ui.theme.LocalHomeLayout
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.wallpaper.WallpaperLayer

/**
 * Accueil « Épuré » (maquette C2) : heure, favoris, « À suivre » puis une rangée par chaîne.
 * Quand un contenu recommandé a le focus, son image remplit le fond (style Google TV).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeRowsScreen(
    data: HomeData,
    actions: HomeActions,
    focus: HomeFocus,
    /** Bannière en tête de la colonne défilante ; reçoit le premier élément focalisable (null = pas de bannière). */
    banner: (@Composable (firstElement: FocusRequester) -> Unit)? = null,
    /** Dessiné entre le fond « héros » et le contenu (null = rien). */
    backdrop: (@Composable () -> Unit)? = null,
    /** Contenu montré par la bannière focalisée (null sans focus) : un contenu À suivre remplit le fond « héros ». */
    bannerCurrent: State<BannerItem?>? = null,
) {
    val forYou = data.tab == null || data.tab == HomeTab.ForYou
    val tabRequesters = remember { HomeTab.entries.associateWith { FocusRequester() } }
    val showApps = forYou || data.tab == HomeTab.Apps
    // Premier élément focalisable de la bannière : il reçoit le focus à la place du 1er favori.
    val bannerFocus = remember { FocusRequester() }
    val bannerShown = banner != null
    // Bannière déjà affichée : si elle disparaît avec le focus, il peut revenir aux onglets ou à l'en-tête.
    val bannerSeen = remember { Memo(false) }
    SideEffect { if (bannerShown) bannerSeen.value = true }
    // En-tête : dernier recours pour ne jamais laisser l'accueil sans focus.
    val headerFocus = remember { FocusRequester() }
    // Accueil affiché derrière le panneau des réglages : il ne demande jamais le focus (lu dans les effets).
    val behindPanel by rememberUpdatedState(data.behindPanel)
    val requesters = remember(data.state.favorites.size) { List(data.state.favorites.size) { FocusRequester() } }
    val rowState = rememberRowState()
    val columnState = rememberScrollState()
    // Contenu sélectionné : lu seulement par le fond « héros », pour ne pas recomposer tout l'accueil.
    val heroProgram = remember { mutableStateOf<Program?>(null) }
    val layout = LocalHomeLayout.current
    // En arabe la rangée est en miroir : ← déplace le favori vers la fin de la liste.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val focusManager = LocalFocusManager.current

    // Défilement vertical piloté par rangée (titre compris), pas par carte : la rangée qui
    // reçoit le focus se cale juste sous l'en-tête, quel que soit l'écart titre / cartes.
    val scrollMs = layout.ms(SCROLL_MS)
    val rowMargin = with(LocalDensity.current) { 14.dp.toPx() }
    val scrollScope = rememberCoroutineScope()
    var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Rangée qui a le focus (null : focus dans l'en-tête ou ailleurs) et rangée des favoris.
    val focusedRow = remember { Memo<LayoutCoordinates?>(null) }
    val favoritesRow = remember { Memo<LayoutCoordinates?>(null) }
    // Défilement qui cale la rangée [r] juste sous l'en-tête (null : pas encore placée).
    fun rowTarget(r: LayoutCoordinates): Int? {
        val v = viewport ?: return null
        if (!r.isAttached || !v.isAttached) return null
        val delta = r.positionInRoot().y - v.positionInRoot().y - rowMargin
        return (columnState.value + delta).toInt().coerceIn(0, columnState.maxValue)
    }
    // La bannière ([isBanner]) se cale tout en haut, entière sous l'en-tête ; chaque rangée note
    // aussi si le focus est sur la bannière ou dans les rangées (pour le lui rendre au retour).
    fun Modifier.alignRowOnFocus(isBanner: Boolean = false): Modifier = composed {
        var row by remember { mutableStateOf<LayoutCoordinates?>(null) }
        onPlaced { row = it }.onFocusChanged { state ->
            val r = row
            if (state.hasFocus) {
                focus.onBanner = isBanner
                focusedRow.value = r
            } else if (r != null && focusedRow.value === r) {
                focusedRow.value = null
            }
            if (!state.hasFocus || r == null) return@onFocusChanged
            val target = if (isBanner) 0 else rowTarget(r) ?: return@onFocusChanged
            // Priorité haute : la demande de défilement automatique d'Android (qui arrive juste
            // après et ne bouge rien) n'interrompt plus l'animation.
            scrollScope.launch {
                val distance = (target - columnState.value).toFloat()
                if (distance == 0f) return@launch
                columnState.scroll(MutatePriority.PreventUserInput) {
                    var done = 0f
                    animate(0f, distance, animationSpec = scrollAnimation(scrollMs)) { value, _ ->
                        done += scrollBy(value - done)
                    }
                }
            }
        }
    }

    // Accueil (re)construit : aucune touche encore appuyée sur lui.
    LaunchedEffect(Unit) { focus.keyPressed = false }

    // Filet : la tuile sélectionnée a disparu (appli masquée ou désinstallée dans n'importe quelle rangée,
    // bannière vidée, dernier favori retiré) et plus rien n'a le focus : il revient sur la bannière si
    // elle l'avait, sinon sur le favori retenu, sinon sur la bannière, l'onglet affiché ou l'en-tête,
    // au lieu de rester perdu. Il n'agit que sans focus (jamais il ne le prend à la bannière) et
    // jamais derrière le panneau des réglages.
    var hasFocus by remember { mutableStateOf(true) }
    val lostFocusView = LocalView.current
    LaunchedEffect(data.state.favorites.size, data.otherApps.size, data.recentApps.size, bannerShown) {
        for (attempt in 1..120) {
            if (lostFocusView.hasWindowFocus()) break
            withFrameNanos { }
        }
        repeat(3) { withFrameNanos { } }
        if (hasFocus || behindPanel || focus.followFavorite != null) return@LaunchedEffect
        val favorite = requesters.getOrNull(focus.index.coerceIn(0, requesters.lastIndex.coerceAtLeast(0)))
        // Sans bannière vue (style Classique), seul le favori est essayé, comme avant.
        val candidates = if (!bannerSeen.value) listOfNotNull(favorite) else listOfNotNull(
            bannerFocus.takeIf { bannerShown && focus.onBanner },
            favorite?.takeIf { showApps },
            bannerFocus.takeIf { bannerShown },
            data.tab?.let { tabRequesters.getValue(it) },
            headerFocus,
        )
        for (target in candidates) {
            runCatching { target.requestFocus() }
            withFrameNanos { }
            if (hasFocus || behindPanel) return@LaunchedEffect
        }
        // Rien n'a pris : premier élément focalisable de l'écran.
        if (bannerSeen.value) focusManager.moveFocus(FocusDirection.Down)
    }

    // Bannière apparue après coup (catalogue ou sources lus en retard) : si l'utilisateur n'a encore
    // appuyé sur aucune touche, elle prend le focus (démarrage à froid) ; sinon le focus reste où il
    // est et sa rangée est recalée, pour ne pas être poussée hors de l'écran par la bannière.
    val bannerStarted = remember { Memo(false) }
    LaunchedEffect(bannerShown) {
        if (!bannerStarted.value) {
            bannerStarted.value = true
            return@LaunchedEffect
        }
        if (!bannerShown || behindPanel) return@LaunchedEffect
        withFrameNanos { }
        if (behindPanel) return@LaunchedEffect
        if (!focus.keyPressed) {
            columnState.scrollTo(0)
            runCatching { bannerFocus.requestFocus() }
        } else {
            focusedRow.value?.let(::rowTarget)?.let { columnState.scrollTo(it) }
        }
    }

    // Contenu À suivre montré par la bannière : fond « héros » flouté habituel ; contenu du catalogue :
    // son image nette est dessinée par le fond du style, le fond « héros » reprend le fond d'écran.
    // Bannière quittée (null) : la tuile qui reçoit le focus règle elle-même le fond.
    if (bannerCurrent != null) LaunchedEffect(bannerCurrent) {
        snapshotFlow { bannerCurrent.value }.collect { item ->
            if (item != null) heroProgram.value = item.program.takeIf { item.catalog == null }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onFocusChanged { hasFocus = it.hasFocus }
            // Touche appuyée sur l'accueil (non consommée) : une bannière tardive ne prend plus le focus.
            .onPreviewKeyEvent { focus.keyPressed = true; false },
    ) {
        HeroBackground(heroProgram, data.appLabels, layout.heroTransition) {
            // Couche à part : le fond n'est pas redessiné à chaque mouvement du focus.
            WallpaperLayer(data.state.wallpaper, Modifier.graphicsLayer(), imageUrl = data.state.wallpaperImageUrl)
        }
        backdrop?.invoke()
        Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
            HeaderBar(
                timeText = data.state.timeText,
                dateText = data.state.dateText,
                onOpenApps = actions.onOpenApps,
                onOpenSettings = actions.onOpenSettings,
                onOpenSearch = actions.onOpenSearch,
                onOpenTools = actions.onOpenTools,
                nowPlaying = data.nowPlaying,
                onOpenNowPlaying = actions.onOpenNowPlaying,
                // Sur l'en-tête (Applications, Réglages), le fond reprend le fond d'écran.
                modifier = Modifier
                    .padding(horizontal = 72.dp)
                    .onFocusChanged {
                        if (it.hasFocus) {
                            heroProgram.value = null
                            focusedRow.value = null
                        }
                    }
                    // Bas depuis l'en-tête : la bannière si elle est affichée, sinon retour sur le favori
                    // quitté, pas sur le plus proche à l'écran.
                    .focusProperties {
                        exit = { direction ->
                            when {
                                direction != FocusDirection.Down -> FocusRequester.Default
                                bannerShown -> bannerFocus
                                showApps && requesters.isNotEmpty() -> requesters[focus.index.coerceIn(0, requesters.lastIndex)]
                                else -> FocusRequester.Default
                            }
                        }
                    }
                    .focusRequester(headerFocus)
                    .focusGroup(),
                netState = data.netState,
                tabs = data.tab?.let { current ->
                    {
                        HomeTabs(
                            current, data.tabsShown, actions.onTab, tabRequesters,
                            Modifier.focusProperties { enter = { tabRequesters.getValue(current) } },
                        )
                    }
                },
            )
            // Rangée sélectionnée calée en haut de la liste (comme Google TV) : jamais à moitié
            // coupée sous l'en-tête. Les rangées horizontales gardent le défilement par défaut.
            val defaultSpec = LocalBringIntoViewSpec.current
            val rowSpec = remember(defaultSpec, scrollMs) { SmoothRowSpec(defaultSpec, scrollAnimation(scrollMs)) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoVerticalAutoScroll) {
            // Toutes les rangées sont construites une fois (Column, pas LazyColumn) : monter ou
            // descendre ne fait que déplacer l'image, sans construire 7 cartes pendant l'animation.
            Column(
                Modifier
                    .clipToBounds()
                    .onPlaced { viewport = it }
                    .verticalScroll(columnState)
                    // Peu de marge en bas : la dernière rangée remonte juste assez pour être entière, sans vide dessous.
                    .padding(top = 24.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (banner != null) key("banner") {
                    Box(
                        Modifier
                            .alignRowOnFocus(isBanner = true)
                            .focusProperties {
                                // Entrée par Haut depuis les rangées : toujours le bouton principal.
                                enter = { bannerFocus }
                                // Bas : le favori quitté, pas le plus proche à l'écran.
                                exit = { direction ->
                                    if (direction == FocusDirection.Down && showApps && requesters.isNotEmpty()) {
                                        requesters[focus.index.coerceIn(0, requesters.lastIndex)]
                                    } else {
                                        FocusRequester.Default
                                    }
                                }
                            }
                            .focusGroup(),
                    ) { banner(bannerFocus) }
                }
                if (showApps) key("favorites") {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                    Column(Modifier.onPlaced { favoritesRow.value = it }.alignRowOnFocus()) {
                        if (layout.showFavoritesLabel || data.movingFavorite != null) {
                            Text(
                                stringResource(if (data.movingFavorite != null) R.string.home_favorites_moving else R.string.home_favorites),
                                fontSize = 20.sp,
                                color = OnBackground,
                                modifier = Modifier.padding(start = 72.dp, bottom = 12.dp),
                            )
                        }
                        if (data.state.favorites.isEmpty() && data.state.loaded) {
                            Text(
                                stringResource(R.string.home_favorites_empty),
                                fontSize = 16.sp,
                                color = OnBackground.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 72.dp, end = 72.dp, top = 8.dp),
                            )
                        }
                        LazyRow(
                            state = rowState,
                            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.testTag("favorites"),
                        ) {
                            itemsIndexed(data.state.favorites, key = { _, app -> app.packageName }) { index, app ->
                                val moving = app.packageName == data.movingFavorite
                                Box(contentAlignment = Alignment.Center) {
                                    AppTile(
                                        app = app,
                                        onClick = { actions.onLaunch(app) },
                                        onLongClick = { actions.onFavoriteLongClick(app) },
                                        width = layout.tileWidthDp.dp,
                                        modifier = Modifier
                                            .testTag("favorite-$index")
                                            .focusRequester(requesters[index])
                                            // Haut depuis un favori : la bannière si elle est affichée.
                                            .focusProperties { if (bannerShown) up = bannerFocus }
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    focus.index = index
                                                    heroProgram.value = null
                                                }
                                            }
                                            .then(if (moving) Modifier.moveKeys(app.packageName, rtl, actions.onMoveFavorite, actions.onMoveDone) else Modifier),
                                    )
                                    if (moving) {
                                        // Chevrons tournés vers l'extérieur (côté début / côté fin, inversés en arabe).
                                        Text(stringResource(R.string.home_chevron_back), fontSize = 34.sp, color = OnBackground, modifier = Modifier.align(Alignment.CenterStart).offset(x = (-16).dp))
                                        Text(stringResource(R.string.chevron_forward), fontSize = 34.sp, color = OnBackground, modifier = Modifier.align(Alignment.CenterEnd).offset(x = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                    }
                }
                // Comme Google TV : « À suivre » sous les favoris, puis une rangée de recommandations,
                // une rangée d'applis, et ainsi de suite (les deux restent proches du haut).
                if (forYou && data.state.watchNext.isNotEmpty()) {
                    key("watch-next") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                            RecoRow(stringResource(R.string.home_watch_next), data.state.watchNext, data.appLabels, actions.onProgramClick, Modifier.alignRowOnFocus()) {
                                heroProgram.value = it
                            }
                        }
                    }
                }
                if (data.tab != HomeTab.Apps && data.myList.isNotEmpty()) {
                    key("my-list") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                            RecoRow(stringResource(R.string.home_my_list), data.myList, data.appLabels, actions.onProgramClick, Modifier.alignRowOnFocus()) { heroProgram.value = it }
                        }
                    }
                }
                val recoSlots: List<Pair<String, @Composable () -> Unit>> = (if (forYou) data.state.recoRows else emptyList()).map { channel ->
                    "channel-${channel.id}" to @Composable {
                        val app = data.appLabels[channel.packageName]
                        RecoRow(
                            title = if (app != null) "${channel.name} · $app" else channel.name,
                            programs = channel.programs,
                            appLabels = data.appLabels,
                            onClick = actions.onProgramClick,
                            modifier = Modifier.alignRowOnFocus(),
                        ) { heroProgram.value = it }
                    }
                } + (if (data.tab == HomeTab.Apps) emptyList() else data.catalog).map { row ->
                    "catalog-${row.id}" to @Composable {
                        RecoRow(row.name, row.programs, data.appLabels, actions.onProgramClick, Modifier.alignRowOnFocus()) { heroProgram.value = it }
                    }
                }
                val appSlots = if (!showApps) emptyList() else buildList<Pair<String, @Composable () -> Unit>> {
                    if (data.state.liveChannels.isNotEmpty()) add("live-tv" to @Composable {
                        LiveTvRow(stringResource(R.string.home_live_tv), data.state.liveChannels, actions.onOpenTv, Modifier.alignRowOnFocus()) { heroProgram.value = null }
                    })
                    if (data.state.tvInputs.isNotEmpty()) add("tv-inputs" to @Composable {
                        LiveTvRow(stringResource(R.string.home_inputs), data.state.tvInputs, actions.onOpenTv, Modifier.alignRowOnFocus()) { heroProgram.value = null }
                    })
                    if (data.recentApps.isNotEmpty()) add("recent-apps" to @Composable {
                        AppsRow(stringResource(R.string.home_recent), data.recentApps, actions.onLaunch, actions.onFavoriteLongClick, Modifier.alignRowOnFocus()) { heroProgram.value = null }
                    })
                    if (data.otherApps.isNotEmpty()) add("all-apps" to @Composable {
                        AppsRow(stringResource(R.string.home_all_apps), data.otherApps, actions.onLaunch, actions.onFavoriteLongClick, Modifier.alignRowOnFocus()) { heroProgram.value = null }
                    })
                    if (data.tools.isNotEmpty()) add("tools" to @Composable {
                        com.vantaflow.kairo.tools.ToolsRow(data.tools, data.toolSubtitles, actions.onOpenTool, Modifier.alignRowOnFocus()) { heroProgram.value = null }
                    })
                    if (data.ourApps.isNotEmpty()) add("our-apps" to @Composable {
                        OurAppsRow(data.ourApps, data.ourAppStates, data.ourAppUpdates, actions.onInstallOurApp, Modifier.alignRowOnFocus()) { heroProgram.value = null }
                    })
                }
                interleave(recoSlots, appSlots).forEach { (id, row) ->
                    key(id) {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) { row() }
                    }
                }
            }
            }
        }
    }

    // Favori déplacé : il garde le focus et reste visible pendant qu'il change de place.
    LaunchedEffect(data.movingFavorite, data.state.favorites) {
        val index = data.state.favorites.indexOfFirst { it.packageName == data.movingFavorite }
        if (index < 0 || index > requesters.lastIndex) return@LaunchedEffect
        rowState.animateScrollToItem((index - 1).coerceAtLeast(0))
        runCatching { requesters[index].requestFocus() }
    }

    // Carte focalisée disparue (retirée de Ma liste) : le focus revient sur l'onglet affiché.
    LaunchedEffect(focus.tabsToken) {
        if (focus.tabsToken == 0 || data.tab == null) return@LaunchedEffect
        withFrameNanos { }
        runCatching { tabRequesters.getValue(data.tab).requestFocus() }
    }

    // Appli ajoutée aux favoris depuis une autre rangée : le focus la suit.
    val view = LocalView.current
    LaunchedEffect(focus.followFavorite, data.state.favorites, requesters) {
        val pkg = focus.followFavorite ?: return@LaunchedEffect
        val index = data.state.favorites.indexOfFirst { it.packageName == pkg }
        if (index < 0 || index > requesters.lastIndex) return@LaunchedEffect
        focus.index = index
        // Le menu de l'appui long doit être fermé (fenêtre de l'accueil de nouveau active), et la
        // tuile, souvent hors écran, construite : la rangée défile d'abord, puis le focus est demandé.
        for (attempt in 1..60) {
            if (view.hasWindowFocus()) break
            withFrameNanos { }
        }
        columnState.scrollTo(0)
        rowState.scrollToItem((index - 1).coerceAtLeast(0))
        for (attempt in 1..10) {
            withFrameNanos { }
            if (runCatching { requesters[index].requestFocus() }.isSuccess) break
        }
        // Effacé à la fin seulement : c'est une clé de l'effet, l'effacer plus tôt l'annulerait.
        focus.followFavorite = null
    }

    // Focus restauré à l'arrivée sur l'accueil, et ramené sur la 1re tuile par la touche Home.
    // Pas quand le nombre de favoris change (ajout / retrait par l'appui long) : le focus reste
    // sur l'appli choisie au lieu de remonter aux favoris. Bannière affichée : elle le reçoit à
    // l'arrivée et après la touche Home, et le retrouve si elle l'avait (ou si les favoris sont
    // absents). Jamais derrière le panneau des réglages : sa fermeture (refocus) relance l'effet.
    LaunchedEffect(requesters.isNotEmpty(), focus.resetToken) {
        if (behindPanel) return@LaunchedEffect
        val toBanner = bannerShown && (focus.onBanner || !showApps || requesters.isEmpty())
        if (requesters.isEmpty() && !toBanner) return@LaunchedEffect
        val index = focus.index.coerceIn(0, requesters.lastIndex.coerceAtLeast(0))
        if (requesters.isNotEmpty()) rowState.scrollToItem(index)
        // Favori sous une bannière : sa rangée est recalée sous l'en-tête (même s'il a gardé le focus).
        if (toBanner || !bannerShown) columnState.scrollTo(0)
        withFrameNanos { }
        if (!toBanner && bannerShown) favoritesRow.value?.let(::rowTarget)?.let { columnState.scrollTo(it) }
        runCatching { if (toBanner) bannerFocus.requestFocus() else requesters[index].requestFocus() }
    }
}

/** Valeur mémorisée hors état Compose : lue seulement dans les effets et les rappels, sans recomposition. */
private class Memo<T>(var value: T)

/** Onglets de l'accueil, comme Google TV. */
/** Onglets à afficher selon les réglages : « Pour vous » toujours, les autres au choix. */
fun shownTabs(movies: Boolean, shows: Boolean, apps: Boolean): List<HomeTab> = HomeTab.entries.filter {
    when (it) {
        HomeTab.ForYou -> true
        HomeTab.Movies -> movies
        HomeTab.Shows -> shows
        HomeTab.Apps -> apps
    }
}

enum class HomeTab(@StringRes val label: Int) {
    ForYou(R.string.home_tab_for_you), Movies(R.string.home_tab_movies), Shows(R.string.home_tab_shows), Apps(R.string.home_tab_apps),
}

/** Rangée d'onglets : l'onglet qui reçoit le focus s'affiche aussitôt (comme Google TV). */
@Composable
private fun HomeTabs(current: HomeTab, shown: List<HomeTab>, onTab: (HomeTab) -> Unit, requesters: Map<HomeTab, FocusRequester>, modifier: Modifier) {
    androidx.compose.foundation.layout.Row(
        // Texte du 1er onglet aligné sur le bord des tuiles (la pilule déborde à gauche, comme Google TV).
        modifier.offset(x = (-22).dp).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        shown.forEach { t ->
            val selected = t == current
            androidx.tv.material3.Surface(
                onClick = { onTab(t) },
                modifier = Modifier.focusRequester(requesters.getValue(t)).onFocusChanged { if (it.isFocused && !selected) onTab(t) },
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(50)),
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                    focusedContainerColor = Color.White,
                    contentColor = OnBackground,
                    focusedContentColor = Color(0xFF101216),
                ),
                scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Text(stringResource(t.label), fontSize = 19.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp))
            }
        }
    }
}

/** Une rangée de [first], une de [second], et ainsi de suite ; le reste de la plus longue à la fin. */
fun <T> interleave(first: List<T>, second: List<T>): List<T> = buildList {
    for (i in 0 until maxOf(first.size, second.size)) {
        first.getOrNull(i)?.let(::add)
        second.getOrNull(i)?.let(::add)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private object NoVerticalAutoScroll : BringIntoViewSpec {
    // Le défilement vertical est fait par alignRowOnFocus (rangée entière, titre compris).
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/** Défilement des rangées : même position que par défaut, animation plus douce. */
@OptIn(ExperimentalFoundationApi::class)
private class SmoothRowSpec(
    private val base: BringIntoViewSpec,
    override val scrollAnimationSpec: AnimationSpec<Float>,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        base.calculateScrollDistance(offset, size, containerSize)
}

/** Rangée d'applis avec son titre (masqué si l'utilisateur a coupé les titres de rangées). */
@Composable
private fun AppsRow(
    title: String,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
) {
    val layout = LocalHomeLayout.current
    Column(modifier) {
        if (layout.showRowTitles) {
            Text(title, fontSize = 20.sp, color = OnBackground, modifier = Modifier.padding(start = 72.dp, bottom = 12.dp))
        }
        LazyRow(
            state = rememberRowState(),
            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(apps, key = { "app-${it.packageName}" }) { app ->
                AppTile(
                    app = app,
                    onClick = { onLaunch(app) },
                    onLongClick = { onLongClick(app) },
                    width = layout.tileWidthDp.dp,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() },
                )
            }
        }
    }
}

/** Durée normale du défilement (ajustée par la vitesse des animations). */
private const val SCROLL_MS = 350

/** Défilement doux (départ franc, arrivée en douceur), proche de Google TV. */
private fun scrollAnimation(ms: Int): AnimationSpec<Float> = tween(ms, easing = FastOutSlowInEasing)

/**
 * Touches du mode déplacement : ← → déplacent le favori, OK / Entrée / Retour terminent.
 * De droite à gauche ([rtl]), la rangée est en miroir : le sens est inversé pour que la tuile suive la flèche.
 */
private fun Modifier.moveKeys(pkg: String, rtl: Boolean, onMove: (String, Int) -> Unit, onDone: () -> Unit) =
    onPreviewKeyEvent { e ->
        val step = if (rtl) -1 else 1
        when (e.key.nativeKeyCode) {
            NativeKeyEvent.KEYCODE_DPAD_LEFT -> { if (e.type == KeyEventType.KeyDown) onMove(pkg, -step); true }
            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> { if (e.type == KeyEventType.KeyDown) onMove(pkg, step); true }
            NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_ENTER, NativeKeyEvent.KEYCODE_NUMPAD_ENTER,
            NativeKeyEvent.KEYCODE_BACK -> { if (e.type == KeyEventType.KeyUp) onDone(); true }
            // Haut / bas bloqués : on reste sur le favori tant qu'on le déplace.
            NativeKeyEvent.KEYCODE_DPAD_UP, NativeKeyEvent.KEYCODE_DPAD_DOWN -> true
            else -> false
        }
    }
