package com.vantaflow.kairo.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.vantaflow.kairo.R
import com.vantaflow.kairo.ui.theme.AccentColor
import com.vantaflow.kairo.ui.theme.BannerSource
import com.vantaflow.kairo.ui.theme.HomeStyle
import com.vantaflow.kairo.ui.theme.LocalLauncherTheme
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

/** Fond des petits dessins de style : un écran éteint, très sombre. */
private val SketchBackground = Color(0xFF0B0C10)
private val SketchBand = Color.White.copy(alpha = 0.22f)

@get:StringRes
private val HomeStyle.title: Int
    get() = when (this) {
        HomeStyle.Classic -> R.string.home_style_classic
        HomeStyle.Cinema -> R.string.home_style_cinema
    }

@get:StringRes
private val HomeStyle.description: Int
    get() = when (this) {
        HomeStyle.Classic -> R.string.home_style_classic_desc
        HomeStyle.Cinema -> R.string.home_style_cinema_desc
    }

@get:StringRes
private val BannerSource.label: Int
    get() = when (this) {
        BannerSource.MyList -> R.string.home_my_list
        BannerSource.WatchNext -> R.string.banner_source_watch_next
        BannerSource.Catalog -> R.string.banner_source_catalog
    }

/**
 * Choix du style d'accueil : une carte par style (dessin schématique, nom, description).
 * Le style actif porte la couleur d'accent et ✓ ; la carte qui a le focus, le contour du thème.
 * Les cartes ne dépendent pas du style actif : changer de style laisse le focus où il est.
 */
@Composable
fun HomeStylePicker(current: HomeStyle, onPick: (HomeStyle) -> Unit, modifier: Modifier = Modifier) {
    val accent = AccentColor
    val focusBorder = Color(LocalLauncherTheme.current.focusBorder)
    Row(modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        HomeStyle.entries.forEach { style ->
            val selected = style == current
            Surface(
                onClick = { onPick(style) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("style-${style.id}")
                    .semantics { this.selected = selected },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.06f),
                    contentColor = OnBackground,
                    focusedContainerColor = Color.White.copy(alpha = 0.12f),
                    focusedContentColor = OnBackground,
                ),
                border = ClickableSurfaceDefaults.border(
                    border = if (selected) Border(BorderStroke(2.dp, accent), shape = RoundedCornerShape(14.dp)) else Border.None,
                    focusedBorder = Border(BorderStroke(3.dp, focusBorder), shape = RoundedCornerShape(14.dp)),
                ),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleSketch(style, accent)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (selected) Text("✓", fontSize = 15.sp, color = accent)
                        Text(stringResource(style.title), fontSize = 17.sp, color = OnBackground, maxLines = 1)
                    }
                    Text(
                        stringResource(style.description),
                        fontSize = 13.sp,
                        color = OnBackgroundMuted,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Dessin schématique d'un style, tracé en formes simples (aucune image). */
@Composable
private fun StyleSketch(style: HomeStyle, accent: Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(SketchBackground, RoundedCornerShape(8.dp)),
    ) {
        val pad = 8.dp.toPx()
        val gap = 6.dp.toPx()
        val width = size.width - 2 * pad
        val inner = size.height - 2 * pad
        when (style) {
            // Trois rangées.
            HomeStyle.Classic -> {
                val band = (inner - 2 * gap) / 3
                repeat(3) { i -> sketchBand(pad, pad + i * (band + gap), width, band, SketchBand) }
            }
            // Grande bannière en haut, une rangée dessous.
            HomeStyle.Cinema -> {
                val banner = inner * 0.62f
                sketchBand(pad, pad, width, banner, accent)
                sketchBand(pad, pad + banner + gap, width, inner - banner - gap, SketchBand)
            }
        }
    }
}

private fun DrawScope.sketchBand(x: Float, y: Float, width: Float, height: Float, color: Color) {
    val radius = 3.dp.toPx()
    drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(radius, radius))
}

/** Origines des contenus de la bannière : seulement pour le style Cinéma, le seul qui en a une. */
@Composable
fun BannerSourcesSettings(vm: SettingsViewModel) {
    val style by vm.homeStyle.collectAsStateWithLifecycle()
    val sources by vm.bannerSources.collectAsStateWithLifecycle()
    if (style != HomeStyle.Cinema) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingLabel(stringResource(R.string.banner_sources_title))
        BannerSource.entries.forEach { source ->
            SwitchRow(stringResource(source.label), source in sources, { vm.setBannerSource(source, it) })
        }
    }
}
