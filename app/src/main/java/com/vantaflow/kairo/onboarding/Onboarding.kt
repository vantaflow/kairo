package com.vantaflow.kairo.onboarding

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.vantaflow.kairo.R
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.reco.EPG_PERMISSIONS
import com.vantaflow.kairo.settings.GOOGLE_TV_LAUNCHER
import com.vantaflow.kairo.settings.dreamEnabled
import com.vantaflow.kairo.settings.isDefaultHome
import com.vantaflow.kairo.settings.keyServiceRunning
import com.vantaflow.kairo.settings.openHomeSettings
import com.vantaflow.kairo.settings.openSafely
import com.vantaflow.kairo.settings.recoAccessGranted
import com.vantaflow.kairo.ui.TvIcon
import com.vantaflow.kairo.ui.TvIconImage
import com.vantaflow.kairo.ui.theme.AccentColor
import com.vantaflow.kairo.ui.theme.Background
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import com.vantaflow.kairo.ui.theme.Themes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PKG = "com.vantaflow.kairo"

/** Une étape de configuration : [isDone] est relu à chaque retour dans Kairo. */
private class Step(
    val icon: TvIcon,
    val title: String,
    val text: String,
    val action: String,
    val optional: Boolean,
    val adb: String?,
    val isDone: () -> Boolean,
    val run: () -> Unit,
)

private sealed interface Page {
    data object Welcome : Page
    class Setup(val step: Step) : Page
    data object Theme : Page
    data object OurApps : Page
    data object Done : Page
}

/**
 * Accueil animé du premier lancement, en plein écran : bienvenue, uniquement les étapes qui
 * manquent sur cette box (chacune se coche et passe seule à la suivante une fois faite), choix
 * du thème appliqué en direct, puis l'écran final. Retour revient à la page précédente.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Onboarding(onFinish: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    // Liste figée à l'ouverture : seules les étapes encore à faire sur cette box.
    val pages = remember {
        val steps = listOf(
            Step(
                TvIcon.Reco, "Vos contenus sur l'accueil",
                "Autorisez Kairo à afficher « À suivre » et les recommandations de Netflix, YouTube, TiviMate…",
                "Autoriser", optional = false,
                adb = "adb shell pm grant $PKG android.permission.READ_TV_LISTINGS",
                isDone = { recoAccessGranted(context) },
                run = { permission.launch(EPG_PERMISSIONS) },
            ),
            Step(
                TvIcon.Home, "Kairo comme écran d'accueil",
                "La touche Accueil de la télécommande ouvrira directement Kairo.",
                "Choisir l'écran d'accueil", optional = false,
                adb = "adb shell pm disable-user --user 0 $GOOGLE_TV_LAUNCHER",
                isDone = { isDefaultHome(context) },
                run = { openHomeSettings(context) },
            ),
            Step(
                TvIcon.Remote, "Les touches de votre télécommande",
                "Envoyez les touches YouTube, Netflix, Prime Video… vers l'appli de votre choix : activez « Kairo » dans Accessibilité.",
                "Ouvrir Accessibilité", optional = true, adb = null,
                isDone = { keyServiceRunning(context) },
                run = { openSafely(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            ),
            Step(
                TvIcon.Update, "Mises à jour en un clic",
                "Autorisez Kairo à installer ses nouvelles versions depuis Réglages › Mise à jour.",
                "Autoriser", optional = true,
                adb = "adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow",
                isDone = { context.packageManager.canRequestPackageInstalls() },
                run = { openSafely(context, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$PKG"))) },
            ),
            Step(
                TvIcon.Clock, "Un bel écran de veille",
                "Images, heure et météo quand la box reste inactive : choisissez « Kairo » comme écran de veille.",
                "Écran de veille", optional = true,
                adb = "adb shell settings put secure screensaver_components $PKG/.dream.TvHomeDream",
                isDone = { dreamEnabled(context) == true },
                run = { openSafely(context, Intent(Settings.ACTION_DREAM_SETTINGS)) },
            ),
        )
        val store = (context.applicationContext as TvHomeApp).container.ourApps
        val ourApps = if (com.vantaflow.kairo.apps.OurApps.any { !store.isInstalled(it) }) listOf(Page.OurApps) else emptyList()
        listOf(Page.Welcome) + steps.filterNot { it.isDone() }.map { Page.Setup(it) } + ourApps + Page.Theme + Page.Done
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    fun go(delta: Int) {
        forward = delta > 0
        index = (index + delta).coerceIn(0, pages.lastIndex)
    }
    BackHandler { if (index == 0) onFinish() else go(-1) }
    // L'accueil derrière pose aussi son focus au démarrage : tant que l'assistant est ouvert,
    // tout focus perdu revient sur le premier bouton de la page.
    val rootFocus = remember { FocusRequester() }
    var rootHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(rootHasFocus, index) {
        while (!rootHasFocus) {
            withFrameNanos { }
            runCatching { rootFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            // Le focus reste dans l'assistant : l'accueil derrière n'est pas atteignable.
            .focusRequester(rootFocus)
            .onFocusChanged { rootHasFocus = it.hasFocus }
            .focusGroup()
            .focusProperties { exit = { FocusRequester.Cancel } },
    ) {
        Backdrop()
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(560, easing = FastOutSlowInEasing)) { it / 5 * dir } + fadeIn(tween(420, delayMillis = 100)))
                    .togetherWith(slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { -it / 5 * dir } + fadeOut(tween(240)))
            },
            label = "page",
        ) { i ->
            when (val page = pages[i]) {
                Page.Welcome -> WelcomePage(onStart = { go(1) })
                is Page.Setup -> SetupPage(page.step, refresh, onNext = { go(1) })
                Page.Theme -> ThemePage(onNext = { go(1) })
                Page.OurApps -> OurAppsPage(refresh, onNext = { go(1) })
                Page.Done -> DonePage(onFinish)
            }
        }
        Dots(pages.size, index, Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp))
    }
}

/** Fond vivant : deux halos dessinés une fois, seul le calque glisse lentement (rien n'est redessiné). */
@Composable
private fun Backdrop() {
    val accent = AccentColor
    val drift by rememberInfiniteTransition(label = "fond").animateFloat(
        0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "derive",
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = (drift - 0.5f) * 140.dp.toPx()
                translationY = (0.5f - drift) * 70.dp.toPx()
                scaleX = 1.2f
                scaleY = 1.2f
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val first = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.22f), radius = size.width * 0.45f,
                )
                val second = Brush.radialGradient(
                    listOf(Color(0xFF7C4DFF).copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.85f), radius = size.width * 0.5f,
                )
                onDrawBehind {
                    drawRect(first)
                    drawRect(second)
                }
            },
    )
}

/** Apparition en cascade : chaque élément monte et se dévoile un peu après le précédent. */
@Composable
private fun Modifier.reveal(order: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(140L + order * 90L)
        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}

/** Donne le focus au bouton principal dès que la page est posée. */
@Composable
private fun rememberInitialFocus(key: Any?): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) {
        // Plusieurs essais : le bouton n'est attaché qu'après la transition de page.
        repeat(20) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    return requester
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    val focus = rememberInitialFocus(Unit)
    val logo = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { logo.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 120f)) }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Glow(220)
            Image(
                painterResource(R.drawable.kairo_logo),
                contentDescription = "Kairo",
                modifier = Modifier.size(150.dp).graphicsLayer {
                    scaleX = logo.value
                    scaleY = logo.value
                    alpha = ((logo.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
                },
            )
        }
        Spacer(Modifier.height(28.dp))
        Text("Bienvenue dans Kairo", fontSize = 46.sp, fontWeight = FontWeight.SemiBold, color = OnBackground, modifier = Modifier.reveal(1))
        Spacer(Modifier.height(10.dp))
        Text(
            "Un écran d'accueil rapide et complet, pensé pour votre box.",
            fontSize = 20.sp, color = OnBackgroundMuted, modifier = Modifier.reveal(2),
        )
        Spacer(Modifier.height(36.dp))
        Button(onClick = onStart, modifier = Modifier.reveal(3).focusRequester(focus)) { Text("Commencer", fontSize = 18.sp) }
        Spacer(Modifier.height(14.dp))
        Text("Quelques réglages, une minute à peine.", fontSize = 14.sp, color = OnBackgroundMuted, modifier = Modifier.reveal(4))
    }
}

@Composable
private fun SetupPage(step: Step, refresh: Int, onNext: () -> Unit) {
    val done = remember(refresh) { step.isDone() }
    val focus = rememberInitialFocus(done)
    // Étape faite (au retour des paramètres Android) : la coche s'affiche, puis on passe à la suite.
    LaunchedEffect(done) {
        if (done) {
            delay(1100)
            onNext()
        }
    }
    Row(
        Modifier.fillMaxSize().padding(horizontal = 110.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(70.dp),
    ) {
        StepBadge(step.icon, done, Modifier.reveal(0))
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (step.optional) {
                Text("FACULTATIF", fontSize = 13.sp, color = AccentColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.reveal(1))
            }
            Text(step.title, fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold, color = OnBackground, modifier = Modifier.reveal(1))
            Text(step.text, fontSize = 19.sp, lineHeight = 28.sp, color = OnBackgroundMuted, modifier = Modifier.reveal(2))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.reveal(3)) {
                if (done) {
                    Button(onClick = onNext, modifier = Modifier.focusRequester(focus)) { Text("Continuer", fontSize = 17.sp) }
                } else {
                    Button(onClick = step.run, modifier = Modifier.focusRequester(focus)) { Text(step.action, fontSize = 17.sp) }
                    OutlinedButton(onClick = onNext) { Text("Plus tard", fontSize = 17.sp) }
                }
            }
            if (!done && step.adb != null) {
                Column(Modifier.reveal(4).padding(top = 12.dp)) {
                    Text("Si la box refuse, depuis un PC :", fontSize = 13.sp, color = OnBackgroundMuted)
                    Text(step.adb, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = OnBackground)
                }
            }
        }
    }
}

/** Halo pulsant derrière un élément : seul le calque change d'échelle. */
@Composable
private fun Glow(sizeDp: Int) {
    val accent = AccentColor
    val pulse by rememberInfiniteTransition(label = "halo").animateFloat(
        0.88f, 1.08f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    Box(
        Modifier
            .size(sizeDp.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.38f), Color.Transparent)), CircleShape),
    )
}

/** Pastille de l'étape : l'icône se change en coche (avec rebond) quand l'étape est faite. */
@Composable
private fun StepBadge(icon: TvIcon, done: Boolean, modifier: Modifier = Modifier) {
    val accent = AccentColor
    val check by animateFloatAsState(if (done) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 420f), label = "coche")
    Box(modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Glow(300)
        Box(
            Modifier
                .size(176.dp)
                .background(
                    Brush.linearGradient(
                        if (done) listOf(Color(0xFF34A853), Color(0xFF1E7E3E)) else listOf(accent, accent.copy(alpha = 0.55f)),
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            TvIconImage(icon, Color.White, Modifier.graphicsLayer { alpha = 1f - check.coerceIn(0f, 1f) }, size = 84.dp)
            Text(
                "✓",
                fontSize = 96.sp,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    scaleX = check
                    scaleY = check
                    alpha = check.coerceIn(0f, 1f)
                },
            )
        }
    }
}

/** Nos autres applis, à installer en un clic (facultatif). */
@Composable
private fun OurAppsPage(refresh: Int, onNext: () -> Unit) {
    val store = (LocalContext.current.applicationContext as TvHomeApp).container.ourApps
    val states by store.states.collectAsStateWithLifecycle()
    val focus = rememberInitialFocus(Unit)
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("FACULTATIF", fontSize = 13.sp, color = AccentColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.reveal(0))
        Text("Nos applications", fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = OnBackground, modifier = Modifier.reveal(0))
        Spacer(Modifier.height(8.dp))
        Text("Installez-les en un clic : Android vous demandera de confirmer.", fontSize = 18.sp, color = OnBackgroundMuted, modifier = Modifier.reveal(1))
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.reveal(2)) {
            com.vantaflow.kairo.apps.OurApps.forEachIndexed { i, app ->
                val installed = remember(refresh, states[app.id]) { store.isInstalled(app) }
                Column(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = { if (!installed) store.install(app) },
                        modifier = Modifier.size(340.dp, 191.dp).then(if (i == 0) Modifier.focusRequester(focus) else Modifier),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White))),
                    ) {
                        Image(painterResource(app.banner), contentDescription = app.name, modifier = Modifier.fillMaxSize())
                    }
                    Text(app.name, fontSize = 20.sp, color = OnBackground, fontWeight = FontWeight.SemiBold)
                    Text(app.tagline, fontSize = 14.sp, color = OnBackgroundMuted)
                    Text(
                        if (installed) "✓ Installée" else com.vantaflow.kairo.home.installLabel(states[app.id]),
                        fontSize = 15.sp,
                        color = if (installed) Color(0xFF6DD58C) else AccentColor,
                    )
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        Button(onClick = onNext, modifier = Modifier.reveal(3)) { Text("Continuer", fontSize = 17.sp) }
    }
}

/** Choix du thème : appliqué en direct, l'assistant lui-même change de couleurs. */
@Composable
private fun ThemePage(onNext: () -> Unit) {
    val container = (LocalContext.current.applicationContext as TvHomeApp).container
    val scope = rememberCoroutineScope()
    val focus = rememberInitialFocus(Unit)
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Choisissez votre style", fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = OnBackground, modifier = Modifier.reveal(0))
        Spacer(Modifier.height(8.dp))
        Text(
            "Le thème s'applique tout de suite. Vous pourrez le changer dans Réglages › Thème.",
            fontSize = 18.sp, color = OnBackgroundMuted, modifier = Modifier.reveal(1),
        )
        Spacer(Modifier.height(40.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.reveal(2)) {
            Themes.forEachIndexed { i, theme ->
                Surface(
                    onClick = {
                        chosen = theme.id
                        scope.launch { container.prefs.setThemeId(theme.id) }
                    },
                    modifier = Modifier.size(150.dp, 110.dp).then(if (i == 0) Modifier.focusRequester(focus) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    border = ClickableSurfaceDefaults.border(
                        border = if (chosen == theme.id) Border(BorderStroke(2.dp, Color(theme.accent))) else Border.None,
                        focusedBorder = Border(BorderStroke(3.dp, Color.White)),
                    ),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(theme.surface), focusedContainerColor = Color(theme.surface)),
                ) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(theme.surface), Color(theme.background))))) {
                        Box(Modifier.padding(14.dp).size(34.dp).background(Color(theme.accent), CircleShape))
                        Text(
                            theme.name, fontSize = 15.sp, color = Color.White, textAlign = TextAlign.Start,
                            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(44.dp))
        Button(onClick = onNext, modifier = Modifier.reveal(3)) { Text("Continuer", fontSize = 17.sp) }
    }
}

@Composable
private fun DonePage(onFinish: () -> Unit) {
    val focus = rememberInitialFocus(Unit)
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StepBadge(TvIcon.Check, done = true, modifier = Modifier.reveal(0))
        Text("Tout est prêt !", fontSize = 46.sp, fontWeight = FontWeight.SemiBold, color = OnBackground, modifier = Modifier.reveal(1))
        Spacer(Modifier.height(10.dp))
        Text(
            "Appui long sur une appli pour l'ajouter aux favoris. Cet assistant reste dans Réglages.",
            fontSize = 19.sp, color = OnBackgroundMuted, modifier = Modifier.reveal(2),
        )
        Spacer(Modifier.height(36.dp))
        Button(onClick = onFinish, modifier = Modifier.reveal(3).focusRequester(focus)) { Text("Aller à l'accueil", fontSize = 18.sp) }
    }
}

/** Points de progression : le point actif s'allonge en douceur. */
@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val accent = AccentColor
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 30.dp else 8.dp, tween(380, easing = FastOutSlowInEasing), label = "point")
            Box(
                Modifier
                    .size(width, 8.dp)
                    .background(if (i == current) accent else Color.White.copy(alpha = 0.28f), CircleShape),
            )
        }
    }
}
