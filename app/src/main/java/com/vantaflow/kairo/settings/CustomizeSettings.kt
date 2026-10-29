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

private val Palette = listOf(
    0xFFFFFFFF, 0xFF8AB4F8, 0xFF7FB2FF, 0xFF81C995, 0xFFFDD663, 0xFFFFB74D,
    0xFFF28B82, 0xFFFF8BCB, 0xFFC58AF9, 0xFF78D9EC, 0xFFE8EAED, 0xFF9AA0A6,
)
private val Radii = listOf(0, 4, 8, 12, 16, 24)
private val Scales = listOf(1.0f to "Aucun", 1.05f to "Léger", 1.1f to "Normal", 1.15f to "Fort", 1.2f to "Très fort")
private val ClockSizes = listOf(48 to "Petite", 72 to "Normale", 96 to "Grande", 120 to "Très grande")
private val AnimationSpeeds = listOf(60 to "Rapide", 100 to "Normale", 150 to "Douce", 200 to "Très douce")
private val HeroTransitions = listOf("fade" to "Fondu", "zoom" to "Zoom doux", "slide" to "Glissement", "none" to "Aucune")
private val TileWidths = listOf(160 to "Petites", 192 to "Normales", 224 to "Grandes", 256 to "Très grandes")

/** Réglages du thème personnalisé : couleurs, arrondi et zoom des tuiles. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CustomThemeEditor(vm: SettingsViewModel) {
    val custom by vm.customTheme.collectAsStateWithLifecycle()
    val themeId by vm.themeId.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingLabel(if (themeId == CUSTOM_THEME_ID) "Personnaliser (thème personnalisé actif)" else "Personnaliser")
        if (themeId != CUSTOM_THEME_ID) SettingHint("Modifier une option ci-dessous active le thème personnalisé.")
        Text("Couleur d'accent", color = OnBackgroundMuted)
        ColorRow(custom.accent) { vm.setCustomTheme(custom.copy(accent = it)) }
        Text("Contour de sélection", color = OnBackgroundMuted)
        ColorRow(custom.focusBorder) { vm.setCustomTheme(custom.copy(focusBorder = it)) }
        Text("Arrondi des tuiles", color = OnBackgroundMuted)
        ChoiceRow {
            Radii.forEach { r ->
                ChoiceChip(selected = custom.tileRadiusDp == r, onClick = { vm.setCustomTheme(custom.copy(tileRadiusDp = r)) }) {
                    Text("$r")
                }
            }
        }
        Text("Zoom au survol", color = OnBackgroundMuted)
        ChoiceRow {
            Scales.forEach { (s, label) ->
                ChoiceChip(selected = custom.focusScale == s, onClick = { vm.setCustomTheme(custom.copy(focusScale = s)) }) {
                    Text(label)
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

/** Éléments affichés sur l'accueil et leurs tailles. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LayoutSettings(vm: SettingsViewModel) {
    val layout by vm.homeLayout.collectAsStateWithLifecycle()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Toggle("Afficher l'heure", layout.showClock) { vm.setHomeLayout(layout.copy(showClock = it)) } }
        item { Toggle("Afficher la date et la météo", layout.showDate) { vm.setHomeLayout(layout.copy(showDate = it)) } }
        item { Toggle("Afficher le titre « Favoris »", layout.showFavoritesLabel) { vm.setHomeLayout(layout.copy(showFavoritesLabel = it)) } }
        item { Toggle("Afficher les titres des rangées", layout.showRowTitles) { vm.setHomeLayout(layout.copy(showRowTitles = it)) } }
        item { Toggle("Afficher les applis récentes", layout.showRecentApps) { vm.setHomeLayout(layout.copy(showRecentApps = it)) } }
        item { Toggle("Afficher les chaînes TV et entrées (box avec tuner ou HDMI)", layout.showLiveTv) { vm.setHomeLayout(layout.copy(showLiveTv = it)) } }
        item { Toggle("Proposer nos applications (Kairo Player, Ultra TV)", layout.showOurApps) { vm.setHomeLayout(layout.copy(showOurApps = it)) } }
        item {
            Choices("Taille de l'horloge", ClockSizes, layout.clockSizeSp) { vm.setHomeLayout(layout.copy(clockSizeSp = it)) }
        }
        item {
            Choices("Taille des tuiles", TileWidths, layout.tileWidthDp) { vm.setHomeLayout(layout.copy(tileWidthDp = it)) }
        }
        item {
            Choices("Vitesse des animations", AnimationSpeeds, layout.animationPercent) {
                vm.setHomeLayout(layout.copy(animationPercent = it))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Changement de l'image de fond", color = OnBackgroundMuted)
                ChoiceRow {
                    HeroTransitions.forEach { (id, label) ->
                        ChoiceChip(selected = layout.heroTransition == id, onClick = { vm.setHomeLayout(layout.copy(heroTransition = id)) }) {
                            Text(label)
                        }
                    }
                }
            }
        }
        item {
            ChoiceChip(selected = false, onClick = { vm.setHomeLayout(HomeLayout()) }) { Text("Rétablir l'affichage par défaut") }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SwitchRow(label, checked, onChange)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Choices(label: String, options: List<Pair<Int, String>>, selected: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = OnBackgroundMuted)
        ChoiceRow {
            options.forEach { (value, text) ->
                ChoiceChip(selected = value == selected, onClick = { onPick(value) }) { Text(text) }
            }
        }
    }
}
