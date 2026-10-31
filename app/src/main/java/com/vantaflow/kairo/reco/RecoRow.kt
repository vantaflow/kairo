package com.vantaflow.kairo.reco

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.runtime.key
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.Context
import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import com.vantaflow.kairo.home.FOCUS_ZOOM_MS
import com.vantaflow.kairo.home.drawFocusRing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.vantaflow.kairo.apps.fallbackColorFor
import com.vantaflow.kairo.ui.theme.LocalHomeLayout
import com.vantaflow.kairo.ui.theme.LocalLauncherTheme
import com.vantaflow.kairo.ui.theme.OnBackground

private val Scrim = Color(0xFF101216)
// Plus long que le défilement d'une rangée (350 ms) : le fond ne change qu'une fois la rangée posée.
private const val HERO_DEBOUNCE_MS = 450L
private const val HERO_LOAD_WAIT_MS = 600L
private const val POSTER_W = 640
private const val POSTER_H = 360
private const val HERO_WIDTH = 0.7f
private const val HERO_HEIGHT = 0.75f
private const val HERO_SAMPLE_W = 160
private const val HERO_SAMPLE_H = 90

/** Cartes préparées à l'avance quand la rangée est préchargée : toutes celles visibles à l'écran. */
private const val ROW_PREFETCH_ITEMS = 7

/**
 * État d'une rangée horizontale de l'accueil. Quand la liste verticale précharge la rangée
 * suivante (pendant les temps morts), toutes ses cartes visibles sont construites d'avance :
 * descendre ne construit plus 7 cartes d'un coup au milieu de l'animation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberRowState(): LazyListState =
    rememberLazyListState(prefetchStrategy = remember { LazyListPrefetchStrategy(ROW_PREFETCH_ITEMS) })

/** Rangée de recommandations (« À suivre » ou une chaîne d'appli). */
@Composable
fun RecoRow(
    title: String,
    programs: List<Program>,
    appLabels: Map<String, String>,
    onClick: (Program) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Program) -> Unit,
) {
    Column(modifier) {
        if (LocalHomeLayout.current.showRowTitles) {
            Text(title, fontSize = 20.sp, color = OnBackground, modifier = Modifier.padding(start = 72.dp, bottom = 12.dp))
        }
        val state = rememberRowState()
        // Nouvelle carte en tête (ex. « Reprendre » de l'appli quittée) : la rangée revient au début
        // au lieu de garder la carte précédente en premier et de cacher la nouvelle à gauche.
        val first = programs.firstOrNull()?.let { "${it.packageName}:${it.id}" }
        LaunchedEffect(first) { if (state.firstVisibleItemIndex != 0) state.scrollToItem(0) }
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 72.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(programs, key = { "${it.packageName}:${it.id}" }) { program ->
                ProgramCard(
                    program = program,
                    appLabel = appLabels[program.packageName],
                    onClick = { onClick(program) },
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused(program) },
                )
            }
        }
    }
}

@Composable
fun ProgramCard(program: Program, appLabel: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val cardShape = RoundedCornerShape(theme.tileRadiusDp.dp)
    // Même principe que AppTile : focus et zoom lus seulement au dessin, rien n'est recomposé.
    val focused = remember { mutableStateOf(false) }
    val zoomMs = LocalHomeLayout.current.ms(FOCUS_ZOOM_MS)
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .size(256.dp, 144.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .onFocusChanged {
                if (focused.value == it.isFocused) return@onFocusChanged
                focused.value = it.isFocused
                scope.launch { scale.animateTo(if (it.isFocused) theme.focusScale else 1f, tween(zoomMs, easing = FastOutSlowInEasing)) }
            }
            .onKeyEvent { e ->
                val code = e.key.nativeKeyCode
                val enter = code == NativeKeyEvent.KEYCODE_DPAD_CENTER || code == NativeKeyEvent.KEYCODE_ENTER ||
                    code == NativeKeyEvent.KEYCODE_NUMPAD_ENTER
                if (enter && e.type == KeyEventType.KeyUp) onClick()
                enter
            }
            .focusable()
            // Contour de focus hors de la carte (visible même sur une affiche blanche).
            .drawWithContent {
                drawContent()
                if (focused.value) drawFocusRing(Color(theme.focusBorder), theme.tileRadiusDp.dp.toPx())
            }
            .clip(cardShape)
            .background(Color(0xFF202329))
            .drawWithCache {
                // Barre de progression préparée une fois par taille, dessinée par-dessus le texte.
                val barHeight = 4.dp.toPx()
                onDrawWithContent {
                    drawContent()
                    program.progressPercent?.let { pct ->
                        val top = size.height - barHeight
                        drawRect(Color.White.copy(alpha = 0.25f), Offset(0f, top), Size(size.width, barHeight))
                        drawRect(Color.White, Offset(0f, top), Size(size.width * pct / 100f, barHeight))
                    }
                }
            }
            // Un seul nœud d'accessibilité par carte : la box a des services d'accessibilité actifs
            // (touches de la télécommande) et Compose reparcourt tout l'arbre à chaque défilement.
            .clearAndSetSemantics { contentDescription = program.title },
    ) {
        AsyncImage(
            model = remember(program.posterUri) { posterRequest(context, program.posterUri) },
            contentDescription = program.title,
            contentScale = ContentScale.Crop,
            // Dégradé sous le texte (et non par-dessus) pour que le titre reste blanc.
            modifier = Modifier.fillMaxSize().drawWithCache {
                val scrim = Brush.verticalGradient(0.45f to Color.Transparent, 1f to Scrim.copy(alpha = 0.9f))
                onDrawWithContent {
                    drawContent()
                    drawRect(scrim)
                }
            },
        )
        appLabel?.let {
            Text(
                it,
                fontSize = 11.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(fallbackColorFor(program.packageName), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(
            listOfNotNull(program.title, program.subtitle).joinToString(" · "),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }
}

/**
 * Une seule taille d'affiche pour la carte et le fond « héros » : l'image décodée pour la carte
 * est reprise telle quelle du cache mémoire, sans second décodage plein écran.
 */
private fun posterRequest(context: Context, uri: String?) =
    ImageRequest.Builder(context).data(uri).size(POSTER_W, POSTER_H).allowRgb565(true).build()

/** Affiche décodée en toute petite taille puis agrandie : un flou doux qui ne coûte rien au GPU. */
private fun blurredRequest(context: Context, uri: String?) =
    ImageRequest.Builder(context).data(uri).size(HERO_SAMPLE_W, HERO_SAMPLE_H).allowRgb565(true).build()

/**
 * Fond « héros » à la Google TV : l'image du contenu sélectionné, floue et fondue vers le fond,
 * en haut à droite. Sans contenu sélectionné, [fallback] (le fond d'écran) est affiché.
 *
 * Deux calques : l'image précédente et la nouvelle. La transition ([transition] : fade, zoom,
 * slide ou none) ne démarre qu'une fois la nouvelle image chargée, et n'anime que des propriétés
 * de calque (opacité, zoom, décalage) : ni recomposition ni redessin pendant l'animation.
 */
@Composable
fun HeroBackground(
    focused: State<Program?>,
    appLabels: Map<String, String>,
    transition: String = "fade",
    fallback: @Composable () -> Unit,
) {
    // On attend que la sélection se pose (D-pad maintenu) avant de charger une image plein écran.
    var program by remember { mutableStateOf<Program?>(null) }
    LaunchedEffect(focused) {
        snapshotFlow { focused.value }.collectLatest {
            if (it != null) delay(HERO_DEBOUNCE_MS)
            program = it
        }
    }
    var current by remember { mutableStateOf<Program?>(null) }
    var previous by remember { mutableStateOf<Program?>(null) }
    var loadedUri by remember { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(1f) }
    val target = program?.takeIf { it.posterUri != null }
    val heroMs = LocalHomeLayout.current.ms(if (transition == "zoom") 650 else 450)
    LaunchedEffect(target?.posterUri) {
        if (target?.posterUri == current?.posterUri) return@LaunchedEffect
        previous = current
        current = target
        progress.snapTo(0f)
        // Nouvelle image prête (ou 0,6 s au plus) avant de lancer la transition.
        if (target != null) withTimeoutOrNull(HERO_LOAD_WAIT_MS) { snapshotFlow { loadedUri }.first { it == target.posterUri } }
        if (transition == "none") progress.snapTo(1f)
        else progress.animateTo(1f, tween(heroMs, easing = FastOutSlowInEasing))
        previous = null
    }
    val slidePx = with(LocalDensity.current) { 80.dp.toPx() }
    // Part du fond d'écran encore visible (1 = pas de héros) : caché sous un héros, il n'est plus
    // dessiné du tout. Une seule couche plein écran par image au lieu de deux : la box tient 60 i/s.
    fun wallpaperAlpha(): Float {
        val t = progress.value
        return when {
            current == null && previous == null -> 1f
            current == null -> t
            previous == null -> 1f - t
            else -> 0f
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Fond d'écran figé dans une texture : rien n'est redessiné pendant la navigation.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = wallpaperAlpha()
                compositingStrategy = CompositingStrategy.Offscreen
            },
        ) { fallback() }
        // Fond uni sous le héros (simple remplissage, pas de texture).
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Scrim, alpha = 1f - wallpaperAlpha()) })
        previous?.let { p ->
            key(p.posterUri) {
                HeroLayer(p) {
                    val t = progress.value
                    alpha = 1f - t
                    if (transition == "slide") translationX = -slidePx / 2 * t
                }
            }
        }
        current?.let { p ->
            key(p.posterUri) {
                HeroLayer(p, onLoaded = { loadedUri = p.posterUri }) {
                    val t = progress.value
                    alpha = t
                    when (transition) {
                        "zoom" -> { val z = 1.08f - 0.08f * t; scaleX = z; scaleY = z }
                        "slide" -> translationX = slidePx * (1f - t)
                    }
                }
            }
        }
    }
}

/** Un calque du fond héros : image floue en haut à droite, fondue vers le fond sur ses bords. */
@Composable
private fun HeroLayer(program: Program, onLoaded: () -> Unit = {}, animate: GraphicsLayerScope.() -> Unit) {
    val context = LocalContext.current
    // Calque limité à l'image (haut à droite) : le fond uni est dessiné une fois, en dessous.
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = remember(program.posterUri) { blurredRequest(context, program.posterUri) },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { onLoaded() },
            onError = { onLoaded() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(HERO_WIDTH)
                .fillMaxHeight(HERO_HEIGHT)
                .graphicsLayer {
                    animate()
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithCache {
                    val left = Brush.horizontalGradient(0f to Scrim, 0.5f to Scrim.copy(alpha = 0.35f), 0.8f to Color.Transparent)
                    val bottom = Brush.verticalGradient(0.45f to Color.Transparent, 1f to Scrim)
                    // Haut assombri : l'heure et les boutons restent lisibles.
                    val top = Brush.verticalGradient(0f to Scrim.copy(alpha = 0.75f), 0.3f to Color.Transparent)
                    onDrawWithContent {
                        drawContent()
                        drawRect(left)
                        drawRect(bottom)
                        drawRect(top)
                    }
                },
        )
    }
}
