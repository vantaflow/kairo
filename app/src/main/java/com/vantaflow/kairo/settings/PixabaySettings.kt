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
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R

private val Intervals = listOf(
    15 to R.string.settings_pixabay_15m, 30 to R.string.settings_pixabay_30m, 60 to R.string.settings_pixabay_1h,
    180 to R.string.settings_pixabay_3h, 1440 to R.string.settings_pixabay_1d,
)

/** Fond Pixabay : thème, fréquence de changement et clé API. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PixabaySettings(vm: SettingsViewModel, current: WallpaperConfig) {
    val pixabay = current as? WallpaperConfig.Pixabay
    val savedKey by vm.pixabayKey.collectAsStateWithLifecycle()
    var keyText by remember(savedKey) { mutableStateOf(savedKey) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_pixabay_title), fontSize = 20.sp, color = OnBackground)
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
                Text(stringResource(R.string.settings_pixabay_every), color = OnBackgroundMuted)
                Intervals.forEach { (minutes, label) ->
                    ChoiceChip(
                        selected = pixabay.intervalMin == minutes,
                        onClick = { vm.setWallpaper(pixabay.copy(intervalMin = minutes)) },
                    ) { Text(stringResource(label)) }
                }
            }
            OutlinedButton(onClick = vm::nextPixabayImage) { Text(stringResource(R.string.settings_pixabay_next)) }
        }
        FieldWithButton(stringResource(R.string.settings_pixabay_key), keyText, { keyText = it }, stringResource(R.string.settings_save), { vm.setPixabayKey(keyText) }, secret = true)
    }
}
