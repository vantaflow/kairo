package com.tvhome.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Fond des écrans (Applications, Recherche…) : suit le thème choisi. */
val Background: Color
    @Composable @ReadOnlyComposable get() = Color(LocalLauncherTheme.current.background)

/** Surfaces (boutons de l'en-tête, panneau des réglages) : suivent le thème choisi. */
val SurfaceColor: Color
    @Composable @ReadOnlyComposable get() = Color(LocalLauncherTheme.current.surface)

/** Couleur d'accent du thème. */
val AccentColor: Color
    @Composable @ReadOnlyComposable get() = Color(LocalLauncherTheme.current.accent)
val OnBackground = Color(0xFFE8EAED)
val OnBackgroundMuted = Color(0xFFC4C7C5)
val Accent = Color(0xFF8AB4F8)

@Composable
fun TvHomeTheme(theme: LauncherTheme = Themes.first(), content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(theme.accent),
        background = Color(theme.background),
        surface = Color(theme.surface),
        surfaceVariant = Color(theme.surface),
        onBackground = OnBackground,
        onSurface = OnBackground,
        onSurfaceVariant = OnBackgroundMuted,
    )
    CompositionLocalProvider(LocalLauncherTheme provides theme) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
