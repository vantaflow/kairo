package com.vantaflow.kairo.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R
import com.vantaflow.kairo.tools.formatMinutes
import java.util.Locale
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

/** « 20 min », « 1 h », [never] (« Jamais ») dans la langue de l'appli. */
fun timeoutLabel(ms: Long, never: String, locale: Locale = Locale.getDefault()): String =
    if (ms <= 0) never else formatMinutes((ms / MINUTE).toInt(), locale)

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
    val prefs = remember { (context.applicationContext as com.vantaflow.kairo.TvHomeApp).container.prefs }
    val guard by prefs.wakeGuard.collectAsState(initial = true)
    val scope = rememberCoroutineScope()

    fun write(block: () -> Unit): Boolean {
        if (!allowed) return false.also { refused = true }
        return runCatching(block).isSuccess.also { refused = !it }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column {
                SettingLabel(stringResource(R.string.power_idle_title))
                SettingHint(stringResource(R.string.power_idle_hint))
            }
        }
        item {
            ChoiceRow {
                IdleChoices.forEach { ms ->
                    ChoiceChip(idle == ms, {
                        if (write { Settings.Secure.putLong(context.contentResolver, "sleep_timeout", ms) }) idle = ms
                    }) { Text(timeoutLabel(ms, stringResource(R.string.duration_never))) }
                }
            }
        }
        item {
            Column {
                SettingLabel(stringResource(R.string.power_watching_title))
                SettingHint(stringResource(R.string.power_watching_hint))
            }
        }
        item {
            ChoiceRow {
                WatchingChoices.forEach { ms ->
                    val selected = if (ms <= 0) watching <= 0 else watching == ms
                    ChoiceChip(selected, {
                        if (write { Settings.Secure.putLong(context.contentResolver, "attentive_timeout", ms) }) watching = ms
                    }) { Text(timeoutLabel(ms, stringResource(R.string.duration_never))) }
                }
            }
        }
        item {
            SwitchRow(stringResource(R.string.power_wake_guard), guard, { on -> scope.launch { prefs.setWakeGuard(on) } },
                stringResource(R.string.power_wake_guard_hint))
        }
        item { SettingLabel("HDMI-CEC") }
        item {
            SwitchRow(stringResource(R.string.power_cec), cec, { on ->
                if (write { Settings.Global.putInt(context.contentResolver, "hdmi_control_enabled", if (on) 1 else 0) }) cec = on
            }, stringResource(R.string.power_cec_hint))
        }
        item {
            SwitchRow(stringResource(R.string.power_volume), volume, { on ->
                if (write { Settings.Global.putInt(context.contentResolver, "hdmi_control_volume_control_enabled", if (on) 1 else 0) }) volume = on
            }, stringResource(R.string.power_volume_hint))
        }
        item {
            ListItem(
                selected = false,
                onClick = {
                    context.openSettings(
                        SettingsShortcut(0, listOf("android.settings.HDMI_CEC_SETTINGS", "com.android.tv.settings.HDMI_CEC_SETTINGS", Settings.ACTION_SETTINGS)),
                    )
                },
                headlineContent = { Text(stringResource(R.string.power_cec_all)) },
            )
        }
        if (refused) {
            item { Column {
                SettingHint(stringResource(if (allowed) R.string.system_change_refused else R.string.system_permission_adb))
                if (!allowed) Text(PRIVATE_DNS_ADB, color = OnBackground, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            } }
        }
    }
}
