package com.vantaflow.kairo.network

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.vantaflow.kairo.settings.TvInput
import com.vantaflow.kairo.system.SettingsShortcut
import com.vantaflow.kairo.system.openSettings
import com.vantaflow.kairo.ui.theme.OnBackground
import com.vantaflow.kairo.ui.theme.OnBackgroundMuted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Petit indicateur de connexion pour l'en-tête de l'accueil. */
@Composable
fun NetworkIndicator(state: NetState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            NetState.Offline -> Text(stringResource(R.string.network_offline), color = Color(0xFFF28B82), fontSize = 16.sp)
            is NetState.Ethernet -> Text("Ethernet", color = OnBackground, fontSize = 16.sp)
            is NetState.Wifi -> SignalBars(state.signalLevel)
        }
    }
}

@Composable
private fun SignalBars(level: Int) {
    Canvas(Modifier.size(28.dp, 20.dp)) {
        val barW = size.width / 5
        for (i in 0 until 4) {
            val h = size.height * (i + 1) / 4
            drawRect(
                color = if (i < level) Color.White else Color.White.copy(alpha = 0.3f),
                topLeft = Offset(i * barW * 1.3f, size.height - h),
                size = Size(barW, h),
            )
        }
    }
}

private fun stateText(context: Context, state: NetState): String = when (state) {
    NetState.Offline -> context.getString(R.string.network_no_connection)
    is NetState.Ethernet -> listOfNotNull(
        context.getString(R.string.network_ethernet_connected),
        state.ip?.let { context.getString(R.string.network_ip, it) },
        state.gateway?.let { context.getString(R.string.network_gateway, it) },
        state.dns?.let { context.getString(R.string.network_dns, it) },
    ).joinToString(" · ")
    is NetState.Wifi -> listOfNotNull(
        state.ssid?.let { context.getString(R.string.network_wifi_connected_to, it) } ?: context.getString(R.string.network_wifi_connected),
        context.getString(R.string.network_signal, state.signalLevel),
        state.ip?.let { context.getString(R.string.network_ip, it) },
        state.gateway?.let { context.getString(R.string.network_gateway, it) },
        state.dns?.let { context.getString(R.string.network_dns, it) },
    ).joinToString(" · ")
}

private val NetworkShortcuts = listOf(
    SettingsShortcut(R.string.network_wifi_settings, listOf(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SETTINGS)),
    SettingsShortcut(
        R.string.network_ethernet_settings,
        listOf(Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SETTINGS),
    ),
    SettingsShortcut(R.string.network_tethering_vpn, listOf(Settings.ACTION_VPN_SETTINGS, Settings.ACTION_SETTINGS)),
)

/** Réseau : état, connexion à un Wi-Fi, raccourcis vers les options avancées. */
@Composable
fun NetworkSettings(monitor: NetworkMonitor, stateFlow: Flow<NetState>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by stateFlow.collectAsStateWithLifecycle(monitor.current())
    var networks by remember { mutableStateOf<List<WifiNetwork>?>(null) }
    var target by remember { mutableStateOf<WifiNetwork?>(null) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(monitor.savedByKairo()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun scan() {
        if (!monitor.locationEnabled()) {
            message = context.getString(R.string.network_location_off)
        }
        scope.launch { networks = monitor.scanAndWait() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scan() else message = context.getString(R.string.network_location_denied)
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stateText(context, state), color = OnBackground, fontSize = 20.sp) }
        if (state is NetState.Ethernet) {
            item { Text(stringResource(R.string.network_ethernet_note), color = OnBackgroundMuted) }
        }
        // Au-dessus de la liste Wi-Fi : le résultat de la recherche arrive après coup et ferait
        // sauter le DNS (et le focus) vers le bas.
        item(key = "dns") { PrivateDnsPicker() }
        message?.let { item { Text(it, color = OnBackgroundMuted) } }
        item {
            Button(onClick = {
                if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    scan()
                } else {
                    permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }) { Text(stringResource(R.string.network_scan)) }
        }
        val selected = target
        if (selected != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.network_connect_to, selected.ssid), color = OnBackground)
                    if (selected.security == WifiSecurity.Wpa2 || selected.security == WifiSecurity.Wpa3) {
                        val passwordFocus = remember { FocusRequester() }
                        LaunchedEffect(selected) { runCatching { passwordFocus.requestFocus() } }
                        // Champ pour télécommande : OK ouvre le clavier, « Valider » le referme et rend
                        // le focus, ce qui permet d'atteindre « Se connecter » (un champ nu gardait le focus).
                        TvInput(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.width(360.dp).focusRequester(passwordFocus),
                            secret = !showPassword,
                            fontSize = 16.sp,
                            keyboardType = KeyboardType.Password,
                        )
                        OutlinedButton(onClick = { showPassword = !showPassword }) {
                            Text(stringResource(if (showPassword) R.string.network_hide_password else R.string.network_show_password))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            val result = monitor.connect(selected, password)
                            message = context.getString(result.message, selected.ssid)
                            saved = monitor.savedByKairo()
                            // En cas d'erreur de saisie, le formulaire reste ouvert avec le mot de passe.
                            if (result != WifiConnectResult.BadPassword) {
                                target = null
                                password = ""
                                showPassword = false
                            }
                        }) { Text(stringResource(R.string.network_connect)) }
                        OutlinedButton(onClick = { target = null; password = ""; showPassword = false }) { Text(stringResource(R.string.network_cancel)) }
                    }
                }
            }
        }
        networks?.let { list ->
            if (list.isEmpty()) item { Text(stringResource(R.string.network_none_found), color = OnBackgroundMuted) }
            items(list, key = { it.ssid }) { net ->
                ListItem(
                    selected = false,
                    onClick = { target = net; password = ""; showPassword = false },
                    headlineContent = { Text(net.ssid) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.network_signal_item, net.signalLevel,
                                when (net.security) {
                                    WifiSecurity.Open -> stringResource(R.string.network_security_open)
                                    WifiSecurity.Wpa2 -> "WPA2"
                                    WifiSecurity.Wpa3 -> "WPA3"
                                    WifiSecurity.Unsupported -> stringResource(R.string.network_security_enterprise)
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (saved.isNotEmpty()) {
            item(key = "forget") {
                OutlinedButton(onClick = {
                    monitor.forgetSaved()
                    saved = monitor.savedByKairo()
                    message = context.getString(R.string.network_forgotten)
                }) { Text(stringResource(R.string.network_forget, saved.joinToString(", "))) }
            }
        }
        item { Text(stringResource(R.string.network_advanced), color = OnBackgroundMuted, modifier = Modifier.padding(top = 8.dp)) }
        items(NetworkShortcuts, key = { it.title }) { s ->
            ListItem(selected = false, onClick = { context.openSettings(s) }, headlineContent = { Text(stringResource(s.title)) })
        }
    }
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) scan()
    }
}
