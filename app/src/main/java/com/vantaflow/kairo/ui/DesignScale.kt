package com.vantaflow.kairo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Largeur de la grille des maquettes Claude Design (1280 × 720). */
const val DESIGN_WIDTH_DP = 1280f

/**
 * Cale l'interface sur la grille des maquettes : l'écran fait toujours 1280 unités de large,
 * quelle que soit la densité de la box (la KM7 Plus n'en donne que 960, tout paraissait 33 % trop grand).
 */
@Composable
fun DesignScale(content: @Composable () -> Unit) {
    val current = LocalDensity.current
    val widthPx = LocalConfiguration.current.screenWidthDp * current.density
    val density = remember(widthPx, current.fontScale) { Density(widthPx / DESIGN_WIDTH_DP, current.fontScale) }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
