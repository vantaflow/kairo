package com.vantaflow.kairo.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.wallpaper.PresetGradients
import com.vantaflow.kairo.wallpaper.toBrush

private val PresetSolids = listOf(0xFF101216, 0xFF1C2A3A, 0xFF202124, 0xFF1E2A22, 0xFF2A1E2E, 0xFF000000)
    .map { WallpaperConfig.Solid(it) }

private val SwatchShape = RoundedCornerShape(12.dp)

@Composable
fun WallpaperSettings(vm: SettingsViewModel) {
    val current by vm.wallpaper.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PixabaySettings(vm, current)
        Text("Dégradés", fontSize = 20.sp, color = OnBackground)
        SwatchRow(PresetGradients, current, vm::setWallpaper)
        Text("Couleurs unies", fontSize = 20.sp, color = OnBackground)
        SwatchRow(PresetSolids, current, vm::setWallpaper)
    }
}

@Composable
private fun SwatchRow(
    configs: List<WallpaperConfig>,
    current: WallpaperConfig,
    onPick: (WallpaperConfig) -> Unit,
) {
    // Passe à la ligne au lieu de couper les dernières pastilles au bord de l'écran.
    ChoiceRow {
        configs.forEach { config ->
            Surface(
                onClick = { onPick(config) },
                modifier = Modifier.size(160.dp, 90.dp),
                shape = ClickableSurfaceDefaults.shape(shape = SwatchShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                border = ClickableSurfaceDefaults.border(
                    border = if (config == current) Border(BorderStroke(2.dp, Color(0xFF8AB4F8)), shape = SwatchShape) else Border.None,
                    focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = SwatchShape),
                ),
            ) {
                Canvas(Modifier.fillMaxSize()) { drawRect(config.toBrush(size.width, size.height)) }
            }
        }
    }
}
