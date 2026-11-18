package com.vantaflow.kairo.system

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.vantaflow.kairo.reco.passthroughInputs
import com.vantaflow.kairo.ui.startSafely
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted

/** Réglages de la sortie HDMI de la box (vers la télé). */
private val OutputShortcuts = listOf(
    SettingsShortcut(R.string.inputs_resolution, listOf(Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS)),
    SettingsShortcut(R.string.inputs_sound, listOf(Settings.ACTION_SOUND_SETTINGS, Settings.ACTION_SETTINGS)),
    SettingsShortcut(
        R.string.inputs_cec,
        listOf("android.settings.HDMI_CEC_SETTINGS", "com.android.tv.settings.HDMI_CEC_SETTINGS", Settings.ACTION_SETTINGS),
    ),
)

/**
 * Entrées et HDMI : ce qu'affichait le panneau du launcher Google pour la touche Entrées.
 * Entrées HDMI de la box s'il y en a, puis les réglages de la sortie HDMI.
 */
@Composable
fun InputsSettings() {
    val context = LocalContext.current
    val inputs = remember { passthroughInputs(context) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text(stringResource(R.string.inputs_title), color = OnBackground) }
        if (inputs.isEmpty()) {
            item { Text(stringResource(R.string.inputs_none), color = OnBackgroundMuted) }
        }
        items(inputs, key = { it.key }) { input ->
            ListItem(
                selected = false,
                onClick = { context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(input.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                headlineContent = { Text(input.name) },
            )
        }
        item { Text(stringResource(R.string.inputs_output), color = OnBackground) }
        items(OutputShortcuts, key = { it.title }) { s ->
            ListItem(selected = false, onClick = { context.openSettings(s) }, headlineContent = { Text(stringResource(s.title)) })
        }
    }
}
