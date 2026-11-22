package com.vantaflow.kairo.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.vantaflow.kairo.ui.theme.CUSTOM_THEME_ID
import com.vantaflow.kairo.ui.theme.HomeLayout
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R

private val Palette = listOf(
    0xFFFFFFFF, 0xFF8AB4F8, 0xFF7FB2FF, 0xFF81C995, 0xFFFDD663, 0xFFFFB74D,
    0xFFF28B82, 0xFFFF8BCB, 0xFFC58AF9, 0xFF78D9EC, 0xFFE8EAED, 0xFF9AA0A6,
)
private val Radii = listOf(0, 4, 8, 12, 16, 24)
private val Scales = listOf(
    1.0f to R.string.settings_zoom_none, 1.05f to R.string.settings_zoom_light, 1.1f to R.string.settings_zoom_normal,
    1.15f to R.string.settings_zoom_strong, 1.2f to R.string.settings_zoom_very_strong,
)
private val ClockSizes = listOf(
    48 to R.string.settings_size_small_f, 72 to R.string.settings_size_normal_f,
    96 to R.string.settings_size_large_f, 120 to R.string.settings_size_xlarge_f,
)
private val AnimationSpeeds = listOf(
    60 to R.string.settings_anim_fast, 100 to R.string.settings_anim_normal,
    150 to R.string.settings_anim_smooth, 200 to R.string.settings_anim_very_smooth,
)
private val HeroTransitions = listOf(
    "fade" to R.string.settings_hero_fade, "zoom" to R.string.settings_hero_zoom,
    "slide" to R.string.settings_hero_slide, "none" to R.string.settings_hero_none,
)
private val TileWidths = listOf(
    160 to R.string.settings_tiles_small, 192 to R.string.settings_tiles_normal,
    224 to R.string.settings_tiles_large, 256 to R.string.settings_tiles_xlarge,
)

/** Réglages du thème personnalisé : couleurs, arrondi et zoom des tuiles. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CustomThemeEditor(vm: SettingsViewModel) {
    val custom by vm.customTheme.collectAsStateWithLifecycle()
    val themeId by vm.themeId.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingLabel(stringResource(if (themeId == CUSTOM_THEME_ID) R.string.settings_custom_title_active else R.string.settings_custom_title))
        if (themeId != CUSTOM_THEME_ID) SettingHint(stringResource(R.string.settings_custom_hint))
        Text(stringResource(R.string.settings_custom_accent), color = OnBackgroundMuted)
        ColorRow(custom.accent) { vm.setCustomTheme(custom.copy(accent = it)) }
        Text(stringResource(R.string.settings_custom_focus_border), color = OnBackgroundMuted)
        ColorRow(custom.focusBorder) { vm.setCustomTheme(custom.copy(focusBorder = it)) }
        Text(stringResource(R.string.settings_custom_radius), color = OnBackgroundMuted)
        ChoiceRow {
            Radii.forEach { r ->
                ChoiceChip(selected = custom.tileRadiusDp == r, onClick = { vm.setCustomTheme(custom.copy(tileRadiusDp = r)) }) {
                    Text("$r")
                }
            }
        }
        Text(stringResource(R.string.settings_custom_zoom), color = OnBackgroundMuted)
        ChoiceRow {
            Scales.forEach { (s, label) ->
                ChoiceChip(selected = custom.focusScale == s, onClick = { vm.setCustomTheme(custom.copy(focusScale = s)) }) {
                    Text(stringResource(label))
                }
            }
        }
    }
}

@Composable
private fun ColorRow(selected: Long, onPick: (Long) -> Unit) {
    ChoiceRow {
        Palette.forEach { argb ->
            Surface(
                onClick = { onPick(argb) },
                modifier = Modifier.size(40.dp),
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                border = ClickableSurfaceDefaults.border(
                    border = if (argb == selected) Border(BorderStroke(3.dp, Color.White), shape = CircleShape) else Border.None,
                    focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CircleShape),
                ),
            ) {
                Box(Modifier.fillMaxSize().background(Color(argb)))
            }
        }
    }
}

/** Éléments affichés sur l'accueil et leurs tailles ; le réglage Ultra TV n'apparaît que si [ultraTvInstalled]. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LayoutSettings(vm: SettingsViewModel, ultraTvInstalled: Boolean = false) {
    val layout by vm.homeLayout.collectAsStateWithLifecycle()
    // Marge haut et bas : le 1er et le dernier bouton grossissent au focus.
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)) {
        item { Toggle(stringResource(R.string.settings_layout_show_clock), layout.showClock) { vm.setHomeLayout(layout.copy(showClock = it)) } }
        item { Toggle(stringResource(R.string.settings_layout_show_date), layout.showDate) { vm.setHomeLayout(layout.copy(showDate = it)) } }
        item { Toggle(stringResource(R.string.settings_layout_show_fav_label), layout.showFavoritesLabel) { vm.setHomeLayout(layout.copy(showFavoritesLabel = it)) } }
        item { Toggle(stringResource(R.string.settings_layout_show_row_titles), layout.showRowTitles) { vm.setHomeLayout(layout.copy(showRowTitles = it)) } }
        item { Toggle(stringResource(R.string.settings_layout_show_recent), layout.showRecentApps) { vm.setHomeLayout(layout.copy(showRecentApps = it)) } }
        item { Toggle(stringResource(R.string.settings_layout_show_live_tv), layout.showLiveTv) { vm.setHomeLayout(layout.copy(showLiveTv = it)) } }
        if (ultraTvInstalled) item { Toggle(stringResource(R.string.settings_layout_show_ultra_tv), layout.showUltraTv) { vm.setHomeLayout(layout.copy(showUltraTv = it)) } }
        item { Toggle(stringResource(R.string.settings_layout_tab_movies), layout.showTabMovies) { vm.setHomeLayout(layout.copy(showTabMovies = it)) } }
        item { Toggle(stringResource(R.string.settings_layout_tab_shows), layout.showTabShows) { vm.setHomeLayout(layout.copy(showTabShows = it)) } }
        item { Toggle(stringResource(R.string.settings_layout_tab_apps), layout.showTabApps) { vm.setHomeLayout(layout.copy(showTabApps = it)) } }
        item { Toggle(stringResource(R.string.settings_layout_show_tools), layout.showTools) { vm.setHomeLayout(layout.copy(showTools = it)) } }
        item { Toggle(stringResource(R.string.settings_layout_show_our_apps), layout.showOurApps) { vm.setHomeLayout(layout.copy(showOurApps = it)) } }
        item { Toggle(stringResource(R.string.settings_layout_show_update), layout.showKairoUpdate) { vm.setHomeLayout(layout.copy(showKairoUpdate = it)) } }
        item {
            Choices(stringResource(R.string.settings_layout_clock_size), ClockSizes, layout.clockSizeSp) { vm.setHomeLayout(layout.copy(clockSizeSp = it)) }
        }
        item {
            Choices(stringResource(R.string.settings_layout_tile_size), TileWidths, layout.tileWidthDp) { vm.setHomeLayout(layout.copy(tileWidthDp = it)) }
        }
        item {
            Choices(stringResource(R.string.settings_layout_anim_speed), AnimationSpeeds, layout.animationPercent) {
                vm.setHomeLayout(layout.copy(animationPercent = it))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_layout_hero_transition), color = OnBackgroundMuted)
                ChoiceRow {
                    HeroTransitions.forEach { (id, label) ->
                        ChoiceChip(selected = layout.heroTransition == id, onClick = { vm.setHomeLayout(layout.copy(heroTransition = id)) }) {
                            Text(stringResource(label))
                        }
                    }
                }
            }
        }
        item {
            ChoiceChip(selected = false, onClick = { vm.setHomeLayout(HomeLayout()) }) { Text(stringResource(R.string.settings_layout_reset)) }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SwitchRow(label, checked, onChange)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Choices(label: String, options: List<Pair<Int, Int>>, selected: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = OnBackgroundMuted)
        ChoiceRow {
            options.forEach { (value, text) ->
                ChoiceChip(selected = value == selected, onClick = { onPick(value) }) { Text(stringResource(text)) }
            }
        }
    }
}
