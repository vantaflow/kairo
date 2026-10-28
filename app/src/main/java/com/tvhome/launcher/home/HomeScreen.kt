package com.tvhome.launcher.home

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tvhome.launcher.apps.AppInfo
import com.tvhome.launcher.network.NetState
import com.tvhome.launcher.reco.HeroBackground
import com.tvhome.launcher.reco.Program
import com.tvhome.launcher.reco.RecoRow
import com.tvhome.launcher.remote.HomeFocus
import com.tvhome.launcher.ui.theme.LocalHomeLayout
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.wallpaper.WallpaperLayer

/**
 * Accueil « Épuré » (maquette C2) : heure, favoris, « À suivre » puis une rangée par chaîne.
 * Quand un contenu recommandé a le focus, son image remplit le fond (style Google TV).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    focus: HomeFocus,
    onLaunch: (AppInfo) -> Unit,
    onFavoriteLongClick: (AppInfo) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    appLabels: Map<String, String> = emptyMap(),
    onProgramClick: (Program) -> Unit = {},
    netState: NetState? = null,
    onOpenSearch: () -> Unit = {},
    /** Applis hors favoris, pour la rangée « Toutes les applis » (vide = rangée masquée). */
    otherApps: List<AppInfo> = emptyList(),
    /** Favori en cours de déplacement : ← → le déplacent, OK ou Retour terminent. */
    movingFavorite: String? = null,
    onMoveFavorite: (String, Int) -> Unit = { _, _ -> },
    onMoveDone: () -> Unit = {},
) {
    val requesters = remember(state.favorites.size) { List(state.favorites.size) { FocusRequester() } }
    val rowState = rememberLazyListState()
    val columnState = rememberLazyListState()
    // Contenu sélectionné : lu seulement par le fond « héros », pour ne pas recomposer tout l'accueil.
    val heroProgram = remember { mutableStateOf<Program?>(null) }
    val layout = LocalHomeLayout.current

    Box(Modifier.fillMaxSize()) {
        HeroBackground(heroProgram, appLabels) {
            // Couche à part : le fond n'est pas redessiné à chaque mouvement du focus.
            WallpaperLayer(state.wallpaper, Modifier.graphicsLayer(), imageUrl = state.wallpaperImageUrl)
        }
        Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
            HeaderBar(
                timeText = state.timeText,
                dateText = state.dateText,
                onOpenApps = onOpenApps,
                onOpenSettings = onOpenSettings,
                onOpenSearch = onOpenSearch,
                // Sur l'en-tête (Applications, Réglages), le fond reprend le fond d'écran.
                modifier = Modifier
                    .padding(horizontal = 72.dp)
                    .onFocusChanged { if (it.hasFocus) heroProgram.value = null }
                    // Bas depuis l'en-tête : retour sur le favori quitté, pas sur le plus proche à l'écran.
                    .focusProperties {
                        exit = { direction ->
                            if (direction == FocusDirection.Down && requesters.isNotEmpty()) {
                                requesters[focus.index.coerceIn(0, requesters.lastIndex)]
                            } else {
                                FocusRequester.Default
                            }
                        }
                    }
                    .focusGroup(),
                netState = netState,
            )
            // Rangée sélectionnée calée en haut de la liste (comme Google TV) : jamais à moitié
            // coupée sous l'en-tête. Les rangées horizontales gardent le défilement par défaut.
            val rowSpec = LocalBringIntoViewSpec.current
            // La carte sélectionnée est calée sous le titre de sa rangée (titre ≈ 52 dp).
            val topGap = with(LocalDensity.current) { 76.dp.toPx() }
            val columnSpec = remember(topGap) { TopAlignedSpec(topGap) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides columnSpec) {
            LazyColumn(
                state = columnState,
                contentPadding = PaddingValues(top = 24.dp, bottom = 480.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.clipToBounds(),
            ) {
                item(key = "favorites") {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                    Column {
                        if (layout.showFavoritesLabel || movingFavorite != null) {
                            Text(
                                if (movingFavorite != null) "Favoris · ← → pour déplacer, OK pour terminer" else "Favoris",
                                fontSize = 20.sp,
                                color = OnBackground,
                                modifier = Modifier.padding(start = 72.dp, bottom = 12.dp),
                            )
                        }
                        if (state.favorites.isEmpty()) {
                            Text(
                                "Aucun favori pour l'instant : appui long sur une appli › Ajouter aux favoris" +
                                    " (profil enfant : un parent les ajoute dans Réglages › Favoris).",
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
                            itemsIndexed(state.favorites, key = { _, app -> app.packageName }) { index, app ->
                                val moving = app.packageName == movingFavorite
                                Box(contentAlignment = Alignment.Center) {
                                    AppTile(
                                        app = app,
                                        onClick = { onLaunch(app) },
                                        onLongClick = { onFavoriteLongClick(app) },
                                        width = layout.tileWidthDp.dp,
                                        modifier = Modifier
                                            .testTag("favorite-$index")
                                            .focusRequester(requesters[index])
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    focus.index = index
                                                    heroProgram.value = null
                                                }
                                            }
                                            .then(if (moving) Modifier.moveKeys(app.packageName, onMoveFavorite, onMoveDone) else Modifier),
                                    )
                                    if (moving) {
                                        Text("‹", fontSize = 34.sp, color = OnBackground, modifier = Modifier.align(Alignment.CenterStart).offset(x = (-16).dp))
                                        Text("›", fontSize = 34.sp, color = OnBackground, modifier = Modifier.align(Alignment.CenterEnd).offset(x = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                    }
                }
                if (otherApps.isNotEmpty()) {
                    item(key = "all-apps") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                        Column {
                            if (layout.showRowTitles) {
                                Text("Toutes les applis", fontSize = 20.sp, color = OnBackground, modifier = Modifier.padding(start = 72.dp, bottom = 12.dp))
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 72.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                items(otherApps, key = { "app-${it.packageName}" }) { app ->
                                    AppTile(
                                        app = app,
                                        onClick = { onLaunch(app) },
                                        onLongClick = { onFavoriteLongClick(app) },
                                        width = layout.tileWidthDp.dp,
                                        modifier = Modifier.onFocusChanged { if (it.isFocused) heroProgram.value = null },
                                    )
                                }
                            }
                        }
                        }
                    }
                }
                if (state.watchNext.isNotEmpty()) {
                    item(key = "watch-next") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                            RecoRow("À suivre", state.watchNext, appLabels, onProgramClick) { heroProgram.value = it }
                        }
                    }
                }
                items(state.recoRows, key = { "channel-${it.id}" }) { channel ->
                    val app = appLabels[channel.packageName]
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                        RecoRow(
                            title = if (app != null) "${channel.name} · $app" else channel.name,
                            programs = channel.programs,
                            appLabels = appLabels,
                            onClick = onProgramClick,
                        ) { heroProgram.value = it }
                    }
                }
            }
            }
        }
    }

    // Favori déplacé : il garde le focus et reste visible pendant qu'il change de place.
    LaunchedEffect(movingFavorite, state.favorites) {
        val index = state.favorites.indexOfFirst { it.packageName == movingFavorite }
        if (index < 0 || index > requesters.lastIndex) return@LaunchedEffect
        rowState.animateScrollToItem((index - 1).coerceAtLeast(0))
        runCatching { requesters[index].requestFocus() }
    }

    // Focus restauré à l'arrivée sur l'accueil, et ramené sur la 1re tuile par la touche Home.
    LaunchedEffect(requesters, focus.resetToken) {
        if (requesters.isEmpty()) return@LaunchedEffect
        val target = focus.index.coerceIn(0, requesters.lastIndex)
        columnState.scrollToItem(0)
        rowState.scrollToItem(target)
        withFrameNanos { }
        runCatching { requesters[target].requestFocus() }
    }
}

/** Fait défiler la liste pour que l'élément sélectionné commence à [gap] du haut. */
@OptIn(ExperimentalFoundationApi::class)
private class TopAlignedSpec(private val gap: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - gap
}

/** Touches du mode déplacement : ← → déplacent le favori, OK / Entrée / Retour terminent. */
private fun Modifier.moveKeys(pkg: String, onMove: (String, Int) -> Unit, onDone: () -> Unit) =
    onPreviewKeyEvent { e ->
        when (e.key.nativeKeyCode) {
            NativeKeyEvent.KEYCODE_DPAD_LEFT -> { if (e.type == KeyEventType.KeyDown) onMove(pkg, -1); true }
            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> { if (e.type == KeyEventType.KeyDown) onMove(pkg, +1); true }
            NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_ENTER, NativeKeyEvent.KEYCODE_NUMPAD_ENTER,
            NativeKeyEvent.KEYCODE_BACK -> { if (e.type == KeyEventType.KeyUp) onDone(); true }
            // Haut / bas bloqués : on reste sur le favori tant qu'on le déplace.
            NativeKeyEvent.KEYCODE_DPAD_UP, NativeKeyEvent.KEYCODE_DPAD_DOWN -> true
            else -> false
        }
    }
