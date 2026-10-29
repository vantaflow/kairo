package com.tvhome.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icônes au trait de Kairo (24 × 24, trait de 2, extrémités arrondies), dessinées dans
 * Claude Design (planche « Icônes et identité ») : mêmes tracés SVG que la maquette.
 */
enum class TvIcon(val path: String) {
    Search("M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z M20 20l-4.2-4.2"),
    Apps("M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z"),
    Settings("M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z M12 2v3 M12 19v3 M4.9 4.9l2.1 2.1 M17 17l2.1 2.1 M2 12h3 M19 12h3 M4.9 19.1l2.1-2.1 M17 7l2.1-2.1"),
    Favorites("M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3 6.4 20.2l1.1-6.2L3 9.6l6.2-.9z"),
    Wallpaper("M3 5h18v14H3z M3 16l5-5 4 4 3-3 6 6 M17 9a1.5 1.5 0 1 1-3 0a1.5 1.5 0 1 1 3 0z"),
    Theme("M12 3a9 9 0 1 0 0 18c1.1 0 1.5-.8 1.5-1.5 0-1-.8-1.4-.8-2.3 0-.9.7-1.6 1.6-1.6H17a4 4 0 0 0 4-4c0-4.7-4-8.6-9-8.6z M7.5 11.5h.01 M10 7.5h.01 M14.5 7.5h.01"),
    Layout("M3 4h18v6H3z M3 14h8v6H3z M15 14h6v6h-6z"),
    Weather("M7 18h10a4 4 0 0 0 .5-8 6 6 0 0 0-11.5 1.5A3.3 3.3 0 0 0 7 18z"),
    Reco("M4 5h16v14H4z M10 9l5 3-5 3z"),
    Clock("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z M12 7v5l3 2"),
    Remote("M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z M14.5 7.5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z M10 14h4 M10 17h4"),
    Profiles("M13 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0z M2 21c0-3.9 3.1-7 7-7s7 3.1 7 7 M17 4a3.5 3.5 0 0 1 0 7 M22 21c0-3-2-5.5-4.5-6.5"),
    Backup("M5 3h11l3 3v15H5z M8 3v5h7V3 M8 21v-7h8v7"),
    Update("M12 3v12 M7 10l5 5 5-5 M4 20h16"),
    Home("M3 11l9-8 9 8 M5 9.5V21h14V9.5 M10 21v-6h4v6"),
    Network("M2 8.5a15 15 0 0 1 20 0 M5 12a10 10 0 0 1 14 0 M8.5 15.5a5 5 0 0 1 7 0 M12 19h.01"),
    Memory("M7 7h10v10H7z M10 10h4v4h-4z M9 3v4 M15 3v4 M9 17v4 M15 17v4 M3 9h4 M3 15h4 M17 9h4 M17 15h4"),
    Android("M4 6h10 M18 6h2 M16 4v4 M4 12h2 M10 12h10 M8 10v4 M4 18h12 M20 18h.01 M18 16v4"),
    Mic("M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z M5 11a7 7 0 0 0 14 0 M12 18v3"),
    Store("M5 8h14l-1 13H6z M9 8a3 3 0 0 1 6 0"),
    Check("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z M8 12.5l2.8 2.8L16 10");

    val vector: ImageVector by lazy {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
    }
}

@Composable
fun TvIconImage(icon: TvIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val painter = rememberVectorPainter(icon.vector)
    val filter = remember(tint) { ColorFilter.tint(tint) }
    Image(painter, contentDescription = null, colorFilter = filter, modifier = modifier.size(size))
}
