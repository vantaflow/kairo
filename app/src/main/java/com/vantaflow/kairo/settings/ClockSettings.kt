package com.vantaflow.kairo.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.vantaflow.kairo.clock.TrustedClock
import com.vantaflow.kairo.home.formatDate
import com.vantaflow.kairo.home.formatTime
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import java.time.ZoneId
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R

@Composable
fun ClockSettings(vm: SettingsViewModel) {
    val use24h by vm.use24h.collectAsStateWithLifecycle()
    val lastSync by vm.lastSyncMs.collectAsStateWithLifecycle()
    val offset by vm.offsetMs.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SwitchRow(stringResource(R.string.settings_clock_24h), use24h, vm::setUse24h, stringResource(R.string.settings_clock_24h_desc))
        Text(stringResource(R.string.settings_clock_servers, TrustedClock.DEFAULT_HOSTS.joinToString(", ")), color = OnBackgroundMuted)
        Text(
            lastSync?.let {
                stringResource(R.string.settings_clock_last_sync, formatDate(it, zone), formatTime(it, zone, use24h), offset)
            } ?: stringResource(R.string.settings_clock_not_synced),
            color = OnBackgroundMuted,
        )
        Button(onClick = vm::syncNow) { Text(stringResource(R.string.settings_clock_sync_now)) }
    }
}
