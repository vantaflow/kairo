package com.tvhome.launcher.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvhome.launcher.TvHomeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FallbackColors = listOf(
    0xFF1565C0, 0xFF00695C, 0xFF6A1B9A, 0xFFB85C00, 0xFF37474F, 0xFF283593, 0xFF4E342E, 0xFF1B7A52,
).map { Color(it) }

/** Couleur de fond stable pour une appli sans bannière. */
fun fallbackColorFor(packageName: String): Color =
    FallbackColors[Math.floorMod(packageName.hashCode(), FallbackColors.size)]

/** Image chargée hors du fil principal ; isBanner = vraie bannière 16:9 (sinon icône). */
class TileImage(val bitmap: ImageBitmap, val isBanner: Boolean)

/**
 * Image d'une tuile : la bannière si l'appli en déclare une et qu'elle se charge, sinon l'icône.
 * Renvoie l'image et true si c'est une bannière.
 */
fun <T : Any> chooseTileImage(hasBanner: Boolean, banner: () -> T?, icon: () -> T?): Pair<T, Boolean>? {
    if (hasBanner) banner()?.let { return it to true }
    return icon()?.let { it to false }
}

/** Bannière TV de l'appli si elle en a une, sinon icône centrée sur fond de couleur. */
@Composable
fun AppTileImage(app: AppInfo, modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as TvHomeApp).container
    val image by produceState<TileImage?>(null, app) {
        value = withContext(Dispatchers.IO) {
            container.tileImages.getOrLoad("${app.packageName}:${app.hasBanner}") {
                val repo = container.appRepository
                chooseTileImage(app.hasBanner, { repo.banner(app.packageName) }, { repo.icon(app.packageName) })
                    ?.let { (drawable, isBanner) ->
                        // 192 dp ≈ 384 px en xhdpi : on rend la bannière à cette taille.
                        val (w, h) = if (isBanner) 384 to 216 else 128 to 128
                        TileImage(drawable.toBitmap(w, h).asImageBitmap(), isBanner)
                    }
            }
        }
    }
    val current = image
    if (current != null && current.isBanner) {
        Image(current.bitmap, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = modifier.fillMaxSize())
        return
    }
    Box(modifier.fillMaxSize().background(fallbackColorFor(app.packageName)), contentAlignment = Alignment.Center) {
        if (current != null) {
            Image(current.bitmap, contentDescription = app.label, modifier = Modifier.size(56.dp))
        } else {
            Text(
                app.label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }
    }
}
