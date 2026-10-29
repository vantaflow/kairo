package com.vantaflow.kairo.dream

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.home.formatDate
import com.vantaflow.kairo.home.formatTime
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

/** Photos de la box et des clés USB (index des médias), dans un ordre aléatoire. */
fun localPhotos(context: Context): List<String> = runCatching {
    MediaStore.getExternalVolumeNames(context).flatMap { volume ->
        val uri = MediaStore.Images.Media.getContentUri(volume)
        context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(ContentUris.withAppendedId(uri, c.getLong(0)).toString()) }
        }.orEmpty()
    }.shuffled()
}.getOrDefault(emptyList())

/**
 * Écran de veille : photos du web (Pixabay), photos de la box ou dégradés animés, avec l'heure,
 * la date et la météo. Utilisé par le service d'écran de veille et par l'aperçu des réglages.
 * [override] : réglages à prévisualiser (sinon ceux enregistrés).
 */
@Composable
fun DreamScreen(override: DreamConfig? = null) {
    val context = LocalContext.current
    val container = (context.applicationContext as TvHomeApp).container
    var saved by remember { mutableStateOf<DreamConfig?>(null) }
    if (override == null) LaunchedEffect(Unit) { saved = container.prefs.dreamConfig.first() }
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var weather by remember { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()

    LaunchedEffect(Unit) {
        val city = container.prefs.weatherCity.first()
        val fahrenheit = container.prefs.useFahrenheit.first()
        weather = if (container.prefs.weatherEnabled.first()) {
            withContext(Dispatchers.IO) { runCatching { container.weather(city, fahrenheit) }.getOrNull() }?.headerText()
        } else null
    }
    LaunchedEffect(Unit) {
        while (true) {
            val now = container.clock.now()
            time = formatTime(now, zone, container.prefs.use24h.first())
            date = formatDate(now, zone)
            delay(10_000)
        }
    }

    val c = override ?: saved
    if (c == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (c.mode) {
            "gradient" -> AnimatedGradient(paletteById(c.palette), c.intervalSec)
            else -> Slideshow(c)
        }
        if (c.showClock || c.showQuote) {
            // Voile en bas : l'heure et la citation restent lisibles sur une photo claire.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
            Column(Modifier.align(Alignment.BottomStart).padding(64.dp)) {
                if (c.showClock) {
                    Text(time, fontSize = 96.sp, fontWeight = FontWeight.Light, color = OnBackground)
                    Text(listOfNotNull(date, weather).joinToString(" · "), fontSize = 26.sp, color = OnBackgroundMuted)
                }
                // Citation du jour sous l'heure ; largeur limitée pour laisser la place à la carte radio.
                if (c.showQuote) DreamQuote(Modifier.padding(top = if (c.showClock) 20.dp else 0.dp).widthIn(max = 760.dp))
            }
        }
        // Au-dessus du voile de l'heure : la carte radio reste nette.
        DreamRadio(Modifier.align(Alignment.BottomEnd).padding(48.dp))
    }
}

/** Diaporama : une image toutes les [DreamConfig.intervalSec] secondes, avec la transition choisie. */
@Composable
private fun Slideshow(c: DreamConfig) {
    val context = LocalContext.current
    val container = (context.applicationContext as TvHomeApp).container
    var image by remember(c.mode, c.query) { mutableStateOf<String?>(null) }
    LaunchedEffect(c.mode, c.query, c.intervalSec) {
        if (c.mode == "photos") {
            val photos = withContext(Dispatchers.IO) { localPhotos(context) }
            if (photos.isEmpty()) return@LaunchedEffect
            var i = 0
            while (true) {
                image = photos[i % photos.size]
                i++
                delay(c.intervalSec * 1000L)
            }
        } else {
            while (true) {
                withContext(Dispatchers.IO) { container.nextWallpaper(c.query) }?.let { image = it }
                delay(c.intervalSec * 1000L)
            }
        }
    }
    val fadeMs = if (c.transition == "fade") 1_500 else 4_000
    Crossfade(targetState = image, animationSpec = tween(fadeMs), label = "veille") { url ->
        if (url != null) {
            // Zoom lent : l'image avance doucement pendant tout son temps d'affichage.
            val zoom = remember(url) { Animatable(1f) }
            if (c.transition == "kenburns") {
                LaunchedEffect(url) { zoom.animateTo(1.12f, tween(c.intervalSec * 1000 + fadeMs, easing = LinearEasing)) }
            }
            AsyncImage(
                model = remember(url) { ImageRequest.Builder(context).data(url).size(1920, 1080).allowRgb565(true).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                },
            )
        }
    }
}

/**
 * Dégradé animé : deux couleurs de la palette qui glissent l'une vers l'autre et un angle qui
 * tourne lentement. Seul le dessin change (un rectangle), rien n'est recomposé.
 */
@Composable
private fun AnimatedGradient(palette: Palette, intervalSec: Int) {
    val colors = palette.colors.map { Color(it) }
    val cycleMs = (intervalSec * 1000).coerceAtLeast(20_000) * colors.size
    val transition = rememberInfiniteTransition(label = "dégradé")
    val phase by transition.animateFloat(0f, colors.size.toFloat(), infiniteRepeatable(tween(cycleMs, easing = LinearEasing)), label = "phase")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(cycleMs * 2, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Box(
        Modifier.fillMaxSize().drawBehind {
            fun at(p: Float): Color {
                val i = p.toInt() % colors.size
                return lerp(colors[i], colors[(i + 1) % colors.size], p - p.toInt())
            }
            val a = Math.toRadians(angle.toDouble())
            val r = maxOf(size.width, size.height) * 0.75f
            val center = Offset(size.width / 2, size.height / 2)
            val dir = Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat())
            drawRect(Brush.linearGradient(listOf(at(phase), at(phase + 1.3f), at(phase + 2.1f)), start = center - dir, end = center + dir))
            // Halo doux qui donne du relief.
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), center = center + dir * 0.4f, radius = r))
        },
    )
}

/** Radio en cours pendant l'écran de veille : logo, nom, égaliseur et touches utiles. */
@Composable
private fun DreamRadio(modifier: Modifier) {
    val now by com.vantaflow.kairo.radio.Radio.state.collectAsStateWithLifecycle()
    val station = now.station
    if (!now.active || station == null) return
    androidx.compose.foundation.layout.Row(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
    ) {
        com.vantaflow.kairo.radio.StationLogo(station, 72.dp)
        Column {
            Text(station.name, fontSize = 24.sp, color = OnBackground, maxLines = 1)
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(vertical = 4.dp)) {
                if (now.status == com.vantaflow.kairo.radio.RadioStatus.Playing) com.vantaflow.kairo.radio.Equalizer()
                else Text("Connexion…", fontSize = 16.sp, color = OnBackgroundMuted)
            }
            Text("← → changer de radio · OK arrêter", fontSize = 15.sp, color = OnBackgroundMuted)
        }
    }
}

/** Citation du jour, sous l'heure, alignée à gauche. */
@Composable
private fun DreamQuote(modifier: Modifier) {
    val quote = remember { quoteOfDay(java.time.LocalDate.now()) }
    Column(modifier) {
        Text(
            "« ${quote.text} »",
            fontSize = 22.sp, color = OnBackground, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 30.sp,
        )
        Text("— ${quote.author}", fontSize = 17.sp, color = OnBackgroundMuted, modifier = Modifier.padding(top = 6.dp))
    }
}
