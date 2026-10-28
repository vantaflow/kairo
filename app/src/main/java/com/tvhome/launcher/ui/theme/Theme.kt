package com.tvhome.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Background = Color(0xFF101216)
val OnBackground = Color(0xFFE8EAED)
val OnBackgroundMuted = Color(0xFFC4C7C5)
val Accent = Color(0xFF8AB4F8)

@Composable
fun TvHomeTheme(theme: LauncherTheme = Themes.first(), content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(theme.accent),
        background = Background,
        surface = Background,
        onBackground = OnBackground,
        onSurface = OnBackground,
        onSurfaceVariant = OnBackgroundMuted,
    )
    CompositionLocalProvider(LocalLauncherTheme provides theme) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
