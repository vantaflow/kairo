package com.vantaflow.kairo.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.vantaflow.kairo.network.PRIVATE_DNS_ADB
import com.vantaflow.kairo.settings.ChoiceChip
import com.vantaflow.kairo.settings.ChoiceRow
import com.vantaflow.kairo.settings.SettingHint
import com.vantaflow.kairo.settings.SettingLabel
import com.vantaflow.kairo.settings.SwitchRow
import com.vantaflow.kairo.ui.theme.OnBackground

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

/** Délai sans touche pressée avant la veille (Google TV : Système › Minuteur d'arrêt › En cas d'inactivité). */
val IdleChoices = listOf(20 * MINUTE, 30 * MINUTE, HOUR, 3 * HOUR, 4 * HOUR, 8 * HOUR, 12 * HOUR, 24 * HOUR)

/** Délai pendant une vidéo sans touche pressée (Google TV : « Lors du visionnage ») ; -1 = jamais. */
val WatchingChoices = listOf(3 * HOUR, 4 * HOUR, 6 * HOUR, 8 * HOUR, -1L)

/** Délai par défaut d'Android TV quand rien n'est réglé. */
private const val DEFAULT_TIMEOUT = 3 * HOUR

/** « 20 min », « 1 h », « Jamais ». */
fun timeoutLabel(ms: Long): String = when {
    ms <= 0 -> "Jamais"
    ms < HOUR -> "${ms / MINUTE} min"
    else -> "${ms / HOUR} h"
}

private fun canWriteSystem(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

private fun readSecure(context: Context, key: String): Long =
    runCatching { Settings.Secure.getLong(context.contentResolver, key) }.getOrDefault(DEFAULT_TIMEOUT)

private fun readGlobal(context: Context, key: String, default: Int): Boolean =
    Settings.Global.getInt(context.contentResolver, key, default) == 1

/**
 * Alimentation et HDMI-CEC, comme les Paramètres Google TV : mise en veille sans touche pressée
 * (l'écran de veille s'affiche d'abord, puis la box et la télé s'éteignent), délai pendant une vidéo,
 * et HDMI-CEC. Les mêmes réglages Android sont modifiés : ce qui est choisi ici apparaît aussi là-bas.
 */
@Composable
fun PowerSettings() {
    val context = LocalContext.current
    val allowed = remember { canWriteSystem(context) }
    var idle by remember { mutableStateOf(readSecure(context, "sleep_timeout")) }
    var watching by remember { mutableStateOf(readSecure(context, "attentive_timeout")) }
    var cec by remember { mutableStateOf(readGlobal(context, "hdmi_control_enabled", 1)) }
    var volume by remember { mutableStateOf(readGlobal(context, "hdmi_control_volume_control_enabled", 1)) }
    var refused by remember { mutableStateOf(false) }

    fun write(block: () -> Unit): Boolean {
        if (!allowed) return false.also { refused = true }
        return runCatching(block).isSuccess.also { refused = !it }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column {
                SettingLabel("Mise en veille en cas d'inactivité")
                SettingHint("Sans touche pressée, l'écran de veille s'affiche puis la box s'éteint, et la télé avec elle.")
            }
        }
        item {
            ChoiceRow {
                IdleChoices.forEach { ms ->
                    ChoiceChip(idle == ms, {
                        if (write { Settings.Secure.putLong(context.contentResolver, "sleep_timeout", ms) }) idle = ms
                    }) { Text(timeoutLabel(ms)) }
                }
            }
        }
        item {
            Column {
                SettingLabel("Mise en veille pendant une vidéo")
                SettingHint("Pendant un film sans toucher la télécommande, Android demande si vous regardez encore, puis s'éteint.")
            }
        }
        item {
            ChoiceRow {
                WatchingChoices.forEach { ms ->
                    val selected = if (ms <= 0) watching <= 0 else watching == ms
                    ChoiceChip(selected, {
                        if (write { Settings.Secure.putLong(context.contentResolver, "attentive_timeout", ms) }) watching = ms
                    }) { Text(timeoutLabel(ms)) }
                }
            }
        }
        item { SettingLabel("HDMI-CEC") }
        item {
            SwitchRow("Contrôle HDMI-CEC", cec, { on ->
                if (write { Settings.Global.putInt(context.contentResolver, "hdmi_control_enabled", if (on) 1 else 0) }) cec = on
            }, "Allume et éteint la télé avec la box, et la box avec la télé.")
        }
        item {
            SwitchRow("Contrôle du volume", volume, { on ->
                if (write { Settings.Global.putInt(context.contentResolver, "hdmi_control_volume_control_enabled", if (on) 1 else 0) }) volume = on
            }, "La télécommande de la box règle le volume de la télé.")
        }
        item {
            ListItem(
                selected = false,
                onClick = {
                    context.openSettings(
                        SettingsShortcut("", listOf("android.settings.HDMI_CEC_SETTINGS", "com.android.tv.settings.HDMI_CEC_SETTINGS", Settings.ACTION_SETTINGS)),
                    )
                },
                headlineContent = { Text("Tous les réglages HDMI-CEC d'Android") },
            )
        }
        if (refused) {
            item { Column {
                SettingHint(if (allowed) "La box a refusé le changement." else "Autorisation à donner une seule fois depuis un ordinateur :")
                if (!allowed) Text(PRIVATE_DNS_ADB, color = OnBackground, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            } }
        }
    }
}
