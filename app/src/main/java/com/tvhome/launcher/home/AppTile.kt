package com.tvhome.launcher.home

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tvhome.launcher.apps.AppInfo
import com.tvhome.launcher.apps.AppTileImage
import com.tvhome.launcher.remote.RemoteAction
import com.tvhome.launcher.remote.remoteActionFor
import com.tvhome.launcher.ui.theme.LocalLauncherTheme

/** Durée du zoom au focus (tuiles et cartes) : assez lente pour rester douce, comme Google TV. */
const val FOCUS_ZOOM_MS = 200

/**
 * Tuile d'appli 16:9 au style Google TV : zoom et contour au focus.
 * Volontairement légère (un seul zoom animé, pas d'ombre) : la box n'a qu'une petite carte graphique.
 */
@Composable
fun AppTile(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 192.dp,
    /** Appli masquée : image estompée (le contour de focus, lui, reste bien visible). */
    dimmed: Boolean = false,
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(theme.tileRadiusDp.dp)
    // État lu uniquement pendant le dessin (zoom, contour) : un changement de focus ne recompose rien.
    val focused = remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    // Lu seulement dans graphicsLayer : l'animation ne recompose pas la tuile.
    val scale = animateFloatAsState(if (focused.value) theme.focusScale else 1f, tween(FOCUS_ZOOM_MS, easing = FastOutSlowInEasing), label = "zoom")

    Box(
        modifier
            .size(width, width * 9 / 16)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .onFocusChanged { focused.value = it.isFocused }
            .onPreviewKeyEvent { e ->
                val code = e.key.nativeKeyCode
                when {
                    // Touche Menu (ou Info) de la télécommande = appui long.
                    remoteActionFor(code) == RemoteAction.OpenMenu -> {
                        if (e.type == KeyEventType.KeyUp) onLongClick()
                        true
                    }
                    code == NativeKeyEvent.KEYCODE_DPAD_CENTER || code == NativeKeyEvent.KEYCODE_ENTER ||
                        code == NativeKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        when (e.type) {
                            KeyEventType.KeyDown -> if (e.nativeKeyEvent.isLongPress && !longFired) {
                                longFired = true
                                onLongClick()
                            }
                            KeyEventType.KeyUp -> {
                                if (!longFired) onClick()
                                longFired = false
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            // Contour à l'extérieur de la tuile, séparé par un liseré sombre : visible même
            // sur les tuiles blanches (Navigateur, YouTube, Play Store…).
            .drawWithContent {
                drawContent()
                if (focused.value) drawFocusRing(Color(theme.focusBorder), theme.tileRadiusDp.dp.toPx())
            }
            .clip(shape)
            .background(Color(0xFF202329)),
    ) {
        // Un seul nœud d'accessibilité par tuile (voir ProgramCard).
        AppTileImage(
            app,
            Modifier
                .clearAndSetSemantics { contentDescription = app.label }
                .then(if (dimmed) Modifier.alpha(0.45f) else Modifier),
        )
    }
}

/** Anneau de focus autour de l'élément (hors de ses bords), avec un liseré sombre entre les deux. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFocusRing(color: Color, radiusPx: Float) {
    val gap = 2.dp.toPx()
    val stroke = 3.dp.toPx()
    val dark = gap + 0.5f
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(-dark / 2, -dark / 2),
        size = Size(size.width + dark, size.height + dark),
        cornerRadius = CornerRadius(radiusPx + dark / 2),
        style = Stroke(dark),
    )
    val o = gap + stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(-o, -o),
        size = Size(size.width + 2 * o, size.height + 2 * o),
        cornerRadius = CornerRadius(radiusPx + o),
        style = Stroke(stroke),
    )
}
