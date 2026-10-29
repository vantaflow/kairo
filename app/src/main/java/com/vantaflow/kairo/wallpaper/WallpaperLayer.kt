package com.vantaflow.kairo.wallpaper

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.vantaflow.kairo.settings.WallpaperConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Dégradés sombres proposés dans les réglages. */
val PresetGradients: List<WallpaperConfig.Gradient> = listOf(
    WallpaperConfig.Gradient(0xFF1C2A3A, 0xFF101216, 135),
    WallpaperConfig.Gradient(0xFF2F5876, 0xFF141821, 160),
    WallpaperConfig.Gradient(0xFF3A2A55, 0xFF101216, 135),
    WallpaperConfig.Gradient(0xFF1E3A2F, 0xFF0E1412, 120),
    WallpaperConfig.Gradient(0xFF4A2E2A, 0xFF120E0E, 145),
    WallpaperConfig.Gradient(0xFF2B2F36, 0xFF0B0C0F, 90),
)

fun WallpaperConfig.colors(): List<Color> = when (this) {
    is WallpaperConfig.Solid -> listOf(Color(argb))
    is WallpaperConfig.Gradient -> listOf(Color(startArgb), Color(endArgb))
    is WallpaperConfig.Pixabay -> WallpaperConfig.Default.colors()
}

/**
 * Extrémités d'un dégradé linéaire couvrant tout le rectangle.
 * 0° = de gauche à droite, 90° = de haut en bas.
 */
fun gradientEndpoints(angleDeg: Int, width: Float, height: Float): Pair<Offset, Offset> {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val half = (abs(width * dx) + abs(height * dy)) / 2f
    val center = Offset(width / 2f, height / 2f)
    return Pair(center - Offset(dx * half, dy * half), center + Offset(dx * half, dy * half))
}

fun WallpaperConfig.toBrush(width: Float, height: Float): Brush = when (this) {
    is WallpaperConfig.Solid -> Brush.linearGradient(listOf(Color(argb), Color(argb)))
    is WallpaperConfig.Gradient -> {
        val (start, end) = gradientEndpoints(angleDeg, width, height)
        Brush.linearGradient(colors(), start = start, end = end)
    }
    // Avant le chargement de la première image : dégradé par défaut.
    is WallpaperConfig.Pixabay -> WallpaperConfig.Default.toBrush(width, height)
}

private val ScrimColor = Color(0xFF101216)

private fun scrimBrush(height: Float) = Brush.verticalGradient(
    listOf(ScrimColor.copy(alpha = 0f), ScrimColor.copy(alpha = 0.6f), ScrimColor.copy(alpha = 0.95f)),
    startY = height * (200f / 720f),
    endY = height,
)

/**
 * Fond de l'accueil + voile sombre en bas pour la lisibilité des rangées.
 * Le dégradé et le voile sont dessinés une fois dans une image (demi-résolution, lissée à l'affichage) :
 * la carte graphique de la box n'a ensuite qu'une image à afficher par trame.
 */
@Composable
fun WallpaperLayer(config: WallpaperConfig, modifier: Modifier = Modifier, imageUrl: String? = null) {
    val pixabayImage = imageUrl?.takeIf { config is WallpaperConfig.Pixabay }
    Box(modifier.fillMaxSize()) {
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val w = (size.width / 2).toInt().coerceAtLeast(1)
                val h = (size.height / 2).toInt().coerceAtLeast(1)
                val baked = ImageBitmap(w, h)
                CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(baked), Size(w.toFloat(), h.toFloat())) {
                    drawRect(config.toBrush(w.toFloat(), h.toFloat()))
                    if (pixabayImage == null) drawRect(scrimBrush(h.toFloat()))
                }
                onDrawBehind {
                    drawImage(baked, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
                }
            },
        )
        if (pixabayImage != null) {
            Crossfade(targetState = pixabayImage, animationSpec = tween(800), label = "wallpaper") { url ->
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            // Photo claire (ciel, mer…) : voile léger partout et plus marqué en haut,
            // pour que l'heure, la date et les boutons restent lisibles.
            Spacer(
                Modifier.fillMaxSize().drawWithCache {
                    val top = Brush.verticalGradient(
                        listOf(ScrimColor.copy(alpha = 0.7f), ScrimColor.copy(alpha = 0f)),
                        endY = size.height * (300f / 720f),
                    )
                    val bottom = scrimBrush(size.height)
                    onDrawBehind {
                        drawRect(ScrimColor.copy(alpha = 0.15f))
                        drawRect(top)
                        drawRect(bottom)
                    }
                },
            )
        }
    }
}
