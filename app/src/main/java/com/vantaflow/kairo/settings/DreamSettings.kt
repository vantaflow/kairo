package com.vantaflow.kairo.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.dream.DreamConfig
import com.vantaflow.kairo.dream.DreamIntervals
import com.vantaflow.kairo.dream.DreamModes
import com.vantaflow.kairo.dream.DreamPreviewActivity
import com.vantaflow.kairo.dream.DreamTransitions
import com.vantaflow.kairo.dream.Palettes
import com.vantaflow.kairo.dream.localPhotos
import com.vantaflow.kairo.ui.startSafely
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import com.vantaflow.kairo.wallpaper.PixabayThemes
import kotlinx.coroutines.launch
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R

private val SwatchShape = RoundedCornerShape(12.dp)

/** Écran de veille : contenu (photos du web, mes photos, dégradés animés), rythme, transition, heure. */
@Composable
fun DreamSettings() {
    val context = LocalContext.current
    val prefs = (context.applicationContext as TvHomeApp).container.prefs
    val scope = rememberCoroutineScope()
    val config by prefs.dreamConfig.collectAsStateWithLifecycle(DreamConfig())
    fun save(c: DreamConfig) = scope.launch { prefs.setDreamConfig(c) }
    var photosGranted by remember {
        mutableIntStateOf(if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) 1 else 0)
    }
    // Compté hors du fil principal (index des médias de la box et des clés USB).
    val photoCount by produceState(-1, photosGranted, config.mode) {
        value = if (photosGranted == 1 && config.mode == "photos") withContext(Dispatchers.IO) { localPhotos(context).size } else -1
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) photosGranted = 1 }

    // Marge haut et bas : le 1er et le dernier bouton grossissent au focus.
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallButton(stringResource(R.string.settings_dream_preview), { context.startSafely(Intent(context, DreamPreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
                SmallButton(stringResource(R.string.settings_dream_choose), { openSafely(context, Intent(Settings.ACTION_DREAM_SETTINGS)) })
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingLabel(stringResource(R.string.settings_dream_content))
                ChoiceRow {
                    DreamModes.forEach { (id, label) ->
                        ChoiceChip(selected = config.mode == id, onClick = {
                            save(config.copy(mode = id))
                            if (id == "photos" && photosGranted == 0) permission.launch(Manifest.permission.READ_MEDIA_IMAGES)
                        }) { Text(stringResource(label)) }
                    }
                }
            }
        }
        when (config.mode) {
            "pixabay" -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingLabel(stringResource(R.string.settings_dream_photo_theme))
                    ChoiceRow {
                        PixabayThemes.forEach { (label, query) ->
                            ChoiceChip(selected = config.query == query, onClick = { save(config.copy(query = query)) }) { Text(label) }
                        }
                    }
                }
            }
            "photos" -> item {
                Text(
                    when {
                        photosGranted == 0 -> stringResource(R.string.settings_dream_photos_permission)
                        photoCount == 0 -> stringResource(R.string.settings_dream_no_photos)
                        else -> pluralStringResource(R.plurals.settings_dream_photo_count, photoCount, photoCount)
                    },
                    color = OnBackgroundMuted,
                )
            }
            "gradient" -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingLabel(stringResource(R.string.settings_dream_colors))
                    ChoiceRow {
                        Palettes.forEach { p ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    onClick = { save(config.copy(palette = p.id)) },
                                    modifier = Modifier.size(128.dp, 72.dp),
                                    shape = ClickableSurfaceDefaults.shape(shape = SwatchShape),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                                    border = ClickableSurfaceDefaults.border(
                                        border = if (config.palette == p.id) Border(BorderStroke(2.dp, Color(0xFF8AB4F8)), shape = SwatchShape) else Border.None,
                                        focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = SwatchShape),
                                    ),
                                ) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        drawRect(Brush.linearGradient(p.colors.map { Color(it) }, start = Offset.Zero, end = Offset(size.width, size.height)))
                                    }
                                }
                                Text(stringResource(p.name), color = OnBackgroundMuted)
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingLabel(stringResource(if (config.mode == "gradient") R.string.settings_dream_gradient_speed else R.string.settings_dream_image_duration))
                ChoiceRow {
                    DreamIntervals.forEach { (sec, label) ->
                        ChoiceChip(selected = config.intervalSec == sec, onClick = { save(config.copy(intervalSec = sec)) }) {
                            Text(stringResource(if (config.mode == "gradient") GradientSpeeds[sec] ?: label else label))
                        }
                    }
                }
            }
        }
        if (config.mode != "gradient") {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingLabel(stringResource(R.string.settings_dream_transition))
                    ChoiceRow {
                        DreamTransitions.forEach { (id, label) ->
                            ChoiceChip(selected = config.transition == id, onClick = { save(config.copy(transition = id)) }) { Text(stringResource(label)) }
                        }
                    }
                }
            }
        }
        item { SwitchRow(stringResource(R.string.settings_dream_show_clock), config.showClock, { save(config.copy(showClock = it)) }) }
        item { SwitchRow(stringResource(R.string.settings_dream_quote), config.showQuote, { save(config.copy(showQuote = it)) }, stringResource(R.string.settings_dream_quote_desc)) }
        item {
            Text(
                stringResource(R.string.settings_dream_footer),
                color = OnBackgroundMuted,
            )
        }
    }
}

/** Pour les dégradés, la durée devient une vitesse (cycle complet des couleurs). */
private val GradientSpeeds = mapOf(
    20 to R.string.settings_speed_fast, 45 to R.string.settings_speed_normal,
    120 to R.string.settings_speed_slow, 300 to R.string.settings_speed_very_slow,
)
