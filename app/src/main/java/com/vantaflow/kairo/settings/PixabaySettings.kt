package com.vantaflow.kairo.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import com.vantaflow.kairo.wallpaper.PixabayThemes

private val Intervals = listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 180 to "3 h", 1440 to "1 jour")

/** Fond Pixabay : thème, fréquence de changement et clé API. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PixabaySettings(vm: SettingsViewModel, current: WallpaperConfig) {
    val pixabay = current as? WallpaperConfig.Pixabay
    val savedKey by vm.pixabayKey.collectAsStateWithLifecycle()
    var keyText by remember(savedKey) { mutableStateOf(savedKey) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Images Pixabay", fontSize = 20.sp, color = OnBackground)
        ChoiceRow {
            PixabayThemes.forEach { (label, query) ->
                ChoiceChip(
                    selected = pixabay?.query == query,
                    onClick = { vm.setWallpaper(WallpaperConfig.Pixabay(query, pixabay?.intervalMin ?: 60)) },
                ) { Text(label) }
            }
        }
        if (pixabay != null) {
            ChoiceRow {
                Text("Changer toutes les", color = OnBackgroundMuted)
                Intervals.forEach { (minutes, label) ->
                    ChoiceChip(
                        selected = pixabay.intervalMin == minutes,
                        onClick = { vm.setWallpaper(pixabay.copy(intervalMin = minutes)) },
                    ) { Text(label) }
                }
            }
            OutlinedButton(onClick = vm::nextPixabayImage) { Text("Image suivante") }
        }
        FieldWithButton("Clé API Pixabay (vide = clé intégrée)", keyText, { keyText = it }, "Enregistrer", { vm.setPixabayKey(keyText) }, secret = true)
    }
}
