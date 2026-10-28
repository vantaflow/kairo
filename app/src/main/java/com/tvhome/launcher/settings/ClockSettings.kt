package com.tvhome.launcher.settings

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
import com.tvhome.launcher.clock.TrustedClock
import com.tvhome.launcher.home.formatDate
import com.tvhome.launcher.home.formatTime
import com.tvhome.launcher.ui.theme.OnBackground
import com.tvhome.launcher.ui.theme.OnBackgroundMuted
import java.time.ZoneId

@Composable
fun ClockSettings(vm: SettingsViewModel) {
    val use24h by vm.use24h.collectAsStateWithLifecycle()
    val lastSync by vm.lastSyncMs.collectAsStateWithLifecycle()
    val offset by vm.offsetMs.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SwitchRow("Format 24 heures", use24h, vm::setUse24h, "Sinon 12 heures avec AM/PM.")
        Text("Serveurs : ${TrustedClock.DEFAULT_HOSTS.joinToString(", ")}", color = OnBackgroundMuted)
        Text(
            lastSync?.let {
                "Dernière synchronisation : ${formatDate(it, zone)} à ${formatTime(it, zone, use24h)} (écart ${offset} ms)"
            } ?: "Pas encore synchronisé : l'heure de la box est affichée.",
            color = OnBackgroundMuted,
        )
        Button(onClick = vm::syncNow) { Text("Synchroniser maintenant") }
    }
}
