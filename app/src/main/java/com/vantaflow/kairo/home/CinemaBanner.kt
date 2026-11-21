package com.vantaflow.kairo.home

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vantaflow.kairo.R
import com.vantaflow.kairo.catalog.CatalogItem
import com.vantaflow.kairo.catalog.MediaType
import com.vantaflow.kairo.reco.Program
import com.vantaflow.kairo.reco.ProgramCard
import com.vantaflow.kairo.ui.theme.AccentColor
import com.vantaflow.kairo.ui.theme.LocalHomeLayout
import com.vantaflow.kairo.ui.theme.LocalLauncherTheme
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import kotlinx.coroutines.delay

private val Scrim = Color(0xFF101216)
private const val BANNER_HEIGHT_DP = 400
private const val TEXT_WIDTH_DP = 560
private const val BACKDROP_FADE_MS = 450

/**
 * Bannière du style Cinéma : un contenu à la fois parmi [items] (titre, informations, résumé),
 * son bouton principal (fiche ou reprise) et, pour un contenu du catalogue, « Ma liste ».
 * L'image de fond est dessinée à part par [CinemaBackdrop] ; un contenu À suivre montre sa carte
 * côté fin. Les flèches changent de contenu (sens de lecture respecté) et la bannière avance seule
 * toutes les [BANNER_ADVANCE_MS] tant qu'elle a le focus, sans changement fait à la main.
 * [onCurrent] reçoit le contenu affiché quand la bannière a le focus, null quand elle le perd.
 * Le contenu affiché est retenu par son identité ([bannerKey]) : il reste le même quand la liste se
 * réordonne (ajout à Ma liste, catalogue relu). Une autre [listId] (l'onglet) ramène au premier.
 *
 * Sans contenu, rien n'est affiché : si la bannière avait le focus, il est perdu. L'appelant ne
 * l'affiche donc pas avec une liste vide, ou déplace le focus avant de vider [items].
 */
@Composable
fun CinemaBanner(
    items: List<BannerItem>,
    inMyList: (CatalogItem) -> Boolean,
    onOpen: (Program) -> Unit,
    onToggleMyList: (CatalogItem) -> Unit,
    onCurrent: (BannerItem?) -> Unit,
    primaryFocus: FocusRequester,
    modifier: Modifier = Modifier,
    appLabels: Map<String, String> = emptyMap(),
    listId: Any? = null,
) {
    if (items.isEmpty()) return
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Contenu affiché (identité) et sa dernière position ; autre liste : le premier contenu.
    var shownKey by rememberSaveable(listId) { mutableStateOf<String?>(null) }
    var index by rememberSaveable(listId) { mutableIntStateOf(0) }
    // Contenu retrouvé là où il est passé ; disparu, la même position ramenée dans la liste (le
    // suivant), dès cette composition, sans plantage.
    val current = items.indexOfFirst { bannerKey(it) == shownKey }.takeIf { it >= 0 } ?: index.coerceIn(0, items.lastIndex)
    val item = items[current]
    val itemKey = bannerKey(item)
    LaunchedEffect(current, itemKey) {
        index = current
        shownKey = itemKey
    }

    var focused by remember { mutableStateOf(false) }
    // Changement de contenu fait à la main depuis que la bannière a reçu le focus : plus d'avance seule.
    var manual by remember { mutableStateOf(false) }
    var myListFocused by remember { mutableStateOf(false) }
    // Contenu du catalogue dont le bouton Ma liste a reçu le focus (null sans focus).
    var myListCatalog by remember { mutableStateOf<CatalogItem?>(null) }
    val myListFocus = remember { FocusRequester() }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()

    // Changement de contenu : le focus passe d'abord au bouton principal, présent pour tout contenu,
    // pour ne pas le perdre avec un bouton Ma liste qui disparaît.
    fun show(delta: Int) {
        if (items.size <= 1) return
        runCatching { primaryFocus.requestFocus() }
        val next = stepBanner(current, items.size, delta)
        index = next
        shownKey = bannerKey(items[next])
    }

    // Pas d'avance non plus sur Ma liste : un appui sur OK ne doit pas viser le contenu suivant.
    LaunchedEffect(focused, manual, myListFocused, lifecycleState, items, current) {
        if (!focused || manual || myListFocused || !lifecycleState.isAtLeast(Lifecycle.State.RESUMED) || items.size <= 1) return@LaunchedEffect
        delay(BANNER_ADVANCE_MS)
        show(1)
    }

    val notify by rememberUpdatedState(onCurrent)
    LaunchedEffect(focused, item) { notify(if (focused) item else null) }
    DisposableEffect(Unit) { onDispose { if (focused) notify(null) } }

    // Contenu changé de l'extérieur (liste raccourcie) alors que Ma liste a le focus : le bouton
    // reste le temps de passer le focus au bouton principal, au lieu de disparaître avec lui.
    val myListItem = item.catalog ?: myListCatalog?.takeIf { myListFocused }
    val orphanFocus = item.catalog == null && myListFocused
    // Autre contenu du catalogue sous le bouton Ma liste focalisé : le contenu retenu le suit.
    LaunchedEffect(item.catalog) { if (myListFocused && item.catalog != null) myListCatalog = item.catalog }
    LaunchedEffect(orphanFocus) { if (orphanFocus) runCatching { primaryFocus.requestFocus() } }

    Box(
        modifier
            .fillMaxWidth()
            .height(BANNER_HEIGHT_DP.dp)
            .testTag("banner")
            .onFocusChanged {
                if (it.hasFocus && !focused) manual = false
                focused = it.hasFocus
            }
            .focusGroup(),
    ) {
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 72.dp).width(TEXT_WIDTH_DP.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sourceLabel(item)?.let {
                Text(it.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AccentColor, letterSpacing = 1.5.sp)
            }
            Text(
                item.program.title,
                fontSize = 46.sp, fontWeight = FontWeight.Bold, color = OnBackground,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 52.sp,
                modifier = Modifier.testTag("banner-title"),
            )
            val info = listOfNotNull(item.program.subtitle, appLabels[item.program.packageName]).joinToString("  ·  ")
            if (info.isNotBlank()) Text(info, fontSize = 18.sp, color = OnBackgroundMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.program.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 16.sp, color = OnBackground, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 23.sp)
            }
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BannerButton(
                    stringResource(if (item.catalog != null) R.string.banner_open_details else R.string.banner_resume),
                    primary = true,
                    modifier = Modifier
                        .testTag("banner-primary")
                        .focusRequester(primaryFocus)
                        .bannerKeys(
                            rtl,
                            onStart = { manual = true; show(-1) },
                            onEnd = if (myListItem != null) {
                                { runCatching { myListFocus.requestFocus() } }
                            } else {
                                { manual = true; show(1) }
                            },
                        ),
                ) { onOpen(item.program) }
                myListItem?.let { catalog ->
                    BannerButton(
                        if (inMyList(catalog)) stringResource(R.string.banner_remove_my_list)
                        else "+ " + stringResource(R.string.home_my_list),
                        primary = false,
                        modifier = Modifier
                            .testTag("banner-mylist")
                            .focusRequester(myListFocus)
                            .onFocusChanged {
                                myListFocused = it.isFocused
                                myListCatalog = if (it.isFocused) item.catalog ?: myListCatalog else null
                            }
                            .bannerKeys(
                                rtl,
                                onStart = { runCatching { primaryFocus.requestFocus() } },
                                onEnd = { manual = true; show(1) },
                            ),
                    ) { item.catalog?.let(onToggleMyList) }
                }
                if (items.size > 1) Dots(items.size, current, Modifier.padding(start = 10.dp))
            }
        }
        // Contenu À suivre (sans image de fond) : sa carte côté fin, simple illustration non focalisable.
        if (item.backdropUrl == null && item.catalog == null) {
            ProgramCard(
                program = item.program,
                appLabel = appLabels[item.program.packageName],
                onClick = { onOpen(item.program) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 120.dp)
                    .focusProperties { canFocus = false },
            )
        }
    }
}

/** Étiquette de source : le type pour un contenu du catalogue, « À suivre » sinon. */
@Composable
private fun sourceLabel(item: BannerItem): String? = when (item.catalog?.type) {
    MediaType.Movie -> stringResource(R.string.catalog_movie)
    MediaType.Tv -> stringResource(R.string.catalog_show)
    null -> stringResource(R.string.home_watch_next)
}

/**
 * Flèches gauche / droite dans le sens de lecture : [onStart] vers le début (droite en arabe),
 * [onEnd] vers la fin. Premier appui seul pris en compte (pas les répétitions) ; la touche est
 * consommée dans les deux sens.
 */
private fun Modifier.bannerKeys(rtl: Boolean, onStart: () -> Unit, onEnd: () -> Unit) =
    onPreviewKeyEvent { e ->
        val toEnd = if (rtl) NativeKeyEvent.KEYCODE_DPAD_LEFT else NativeKeyEvent.KEYCODE_DPAD_RIGHT
        val toStart = if (rtl) NativeKeyEvent.KEYCODE_DPAD_RIGHT else NativeKeyEvent.KEYCODE_DPAD_LEFT
        val action = when (e.key.nativeKeyCode) {
            toEnd -> onEnd
            toStart -> onStart
            else -> return@onPreviewKeyEvent false
        }
        // Touche maintenue : une seule action, pas de défilement en boucle des contenus.
        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) action()
        true
    }

/** Bouton arrondi de la bannière, contour du thème au focus (détaché du bouton, visible sur fond clair). */
@Composable
private fun BannerButton(text: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color(theme.focusBorder)), inset = 3.dp, shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (primary) 0.24f else 0.12f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Scrim,
        ),
    ) {
        Text(
            text,
            fontSize = 19.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = if (primary) 30.dp else 22.dp, vertical = 12.dp),
        )
    }
}

/** Points de position : le contenu courant est un trait plus long. */
@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.testTag("banner-dots"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 22.dp else 8.dp, height = 8.dp)
                    .background(Color.White.copy(alpha = if (i == current) 0.95f else 0.4f), RoundedCornerShape(50)),
            )
        }
    }
}

/**
 * Fond du style Cinéma, plein écran, placé derrière l'accueil : l'image nette du contenu affiché
 * par la bannière, assombrie vers le bas et vers le côté du texte. Changement d'image en fondu ;
 * rien n'est dessiné sans image (contenu À suivre ou bannière sans focus).
 */
@Composable
fun CinemaBackdrop(current: State<BannerItem?>) {
    val context = LocalContext.current
    val url = current.value?.backdropUrl
    val fadeMs = LocalHomeLayout.current.ms(BACKDROP_FADE_MS)
    Crossfade(targetState = url, animationSpec = tween(fadeMs, easing = FastOutSlowInEasing), label = "fond cinéma") { shown ->
        if (shown == null) return@Crossfade
        AsyncImage(
            model = remember(shown) { ImageRequest.Builder(context).data(shown).size(1280, 720).allowRgb565(true).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().drawWithCache {
                // Côté texte (à droite en arabe) assombri pour que le titre reste lisible.
                val rtl = layoutDirection == LayoutDirection.Rtl
                val side = Brush.horizontalGradient(
                    0f to Scrim, 0.35f to Scrim.copy(alpha = 0.7f), 0.7f to Color.Transparent,
                    startX = if (rtl) Float.POSITIVE_INFINITY else 0f,
                    endX = if (rtl) 0f else Float.POSITIVE_INFINITY,
                )
                val bottom = Brush.verticalGradient(0.4f to Color.Transparent, 0.62f to Scrim.copy(alpha = 0.85f), 1f to Scrim)
                // Haut assombri : l'heure et les onglets restent lisibles.
                val top = Brush.verticalGradient(0f to Scrim.copy(alpha = 0.6f), 0.2f to Color.Transparent)
                onDrawWithContent {
                    drawContent()
                    drawRect(side)
                    drawRect(bottom)
                    drawRect(top)
                }
            },
        )
    }
}
