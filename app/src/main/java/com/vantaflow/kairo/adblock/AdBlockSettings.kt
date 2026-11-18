package com.vantaflow.kairo.adblock

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.settings.ChoiceChip
import com.vantaflow.kairo.settings.ChoiceRow
import com.vantaflow.kairo.settings.SettingLabel
import com.vantaflow.kairo.settings.SwitchRow
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Réglages du bloqueur de pubs : marche / arrêt, filtre, compteur. */
@Composable
fun AdBlockSettings() {
    val context = LocalContext.current
    val prefs = (context.applicationContext as TvHomeApp).container.prefs
    val scope = rememberCoroutineScope()
    val enabled by prefs.adBlockEnabled.collectAsStateWithLifecycle(false)
    val filter by prefs.adBlockFilter.collectAsStateWithLifecycle(AdFilter.Ads.name)
    val running by AdBlock.running.collectAsStateWithLifecycle()
    // Accord d'Android pour créer le VPN local (une seule fois).
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) scope.launch {
            prefs.setAdBlockEnabled(true)
            AdBlock.start(context)
        }
    }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            tick++
        }
    }
    val fmt = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

    val strictDns = remember { privateDnsHost(context) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // DNS privé strict : il remplace tout autre DNS, le bloqueur de Kairo ne peut pas tourner.
        if (strictDns != null) item {
            Text(
                stringResource(if (isAdBlockingDns(strictDns)) R.string.adblock_strict_ok else R.string.adblock_strict_conflict, strictDns),
                color = OnBackground, fontSize = 18.sp,
            )
        }
        // Toujours un élément sélectionnable : sinon le focus retombe sur l'accueil derrière.
        if (strictDns != null) item {
            com.vantaflow.kairo.settings.SmallButton(stringResource(R.string.adblock_network_settings), {
                com.vantaflow.kairo.settings.openSafely(context, android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
            })
        }
        if (strictDns == null) item {
            SwitchRow(
                stringResource(R.string.adblock_switch), enabled,
                { on ->
                    if (!on) scope.launch {
                        prefs.setAdBlockEnabled(false)
                        AdBlock.stop(context)
                    } else {
                        val ask = VpnService.prepare(context)
                        if (ask != null) consent.launch(ask) else scope.launch {
                            prefs.setAdBlockEnabled(true)
                            AdBlock.start(context)
                        }
                    }
                },
                stringResource(R.string.adblock_switch_hint),
            )
        }
        if (enabled && strictDns == null) {
            item {
                tick.let { }
                Text(
                    if (running) {
                        val blocked = AdBlock.blocked.get()
                        pluralStringResource(R.plurals.adblock_counter, blocked, fmt.format(blocked), fmt.format(AdBlock.queries.get()))
                    } else stringResource(R.string.adblock_starting),
                    color = OnBackground, fontSize = 18.sp,
                )
            }
            item {
                SettingLabel(stringResource(R.string.adblock_filter))
                ChoiceRow {
                    AdFilter.entries.forEach { f ->
                        ChoiceChip(filter == f.name, { scope.launch { prefs.setAdBlockFilter(f.name) } }) { Text(stringResource(f.label)) }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.adblock_footer),
                color = OnBackgroundMuted, fontSize = 15.sp,
            )
        }
    }
}
