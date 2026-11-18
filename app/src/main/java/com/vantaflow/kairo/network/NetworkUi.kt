package com.vantaflow.kairo.network

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiNetworkSuggestion
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vantaflow.kairo.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.vantaflow.kairo.system.SettingsShortcut
import com.vantaflow.kairo.system.openSettings
import com.vantaflow.kairo.ui.startSafely
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
    ).joinToString(" · ")
    is NetState.Wifi -> listOfNotNull(
        state.ssid?.let { context.getString(R.string.network_wifi_connected_to, it) } ?: context.getString(R.string.network_wifi_connected),
        context.getString(R.string.network_signal, state.signalLevel),
        state.ip?.let { context.getString(R.string.network_ip, it) },
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
    var message by remember { mutableStateOf<String?>(null) }

    fun scan() {
        if (!monitor.locationEnabled()) {
            message = context.getString(R.string.network_location_off)
        }
        scope.launch { networks = monitor.scanAndWait() }
    }
    // L'écran système d'ajout de réseau doit être ouvert « pour résultat », sinon il se ferme aussitôt.
    val addNetwork = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        message = if (result.resultCode == Activity.RESULT_OK) {
            context.getString(R.string.network_added)
        } else {
            context.startSafely(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            context.getString(R.string.network_add_unconfirmed)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scan() else message = context.getString(R.string.network_location_denied)
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stateText(context, state), color = OnBackground, fontSize = 20.sp) }
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
                        BasicTextField(
                            value = password,
                            onValueChange = { password = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = TextStyle(color = OnBackground, fontSize = 16.sp),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .focusRequester(passwordFocus)
                                .width(360.dp)
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            when (val request = wifiAddRequest(selected, password)) {
                                is WifiAddRequest.Ready -> runCatching { addNetwork.launch(request.intent) }.onFailure {
                                    context.startSafely(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    message = context.getString(R.string.network_direct_add_unsupported)
                                }
                                is WifiAddRequest.Invalid -> message = context.getString(request.reason)
                            }
                            target = null
                            password = ""
                        }) { Text(stringResource(R.string.network_connect)) }
                        OutlinedButton(onClick = { target = null }) { Text(stringResource(R.string.network_cancel)) }
                    }
                }
            }
        }
        networks?.let { list ->
            if (list.isEmpty()) item { Text(stringResource(R.string.network_none_found), color = OnBackgroundMuted) }
            items(list, key = { it.ssid }) { net ->
                ListItem(
                    selected = false,
                    onClick = { target = net; password = "" },
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
        item { Text(stringResource(R.string.network_advanced), color = OnBackgroundMuted, modifier = Modifier.padding(top = 8.dp)) }
        items(NetworkShortcuts, key = { it.title }) { s ->
            ListItem(selected = false, onClick = { context.openSettings(s) }, headlineContent = { Text(stringResource(s.title)) })
        }
    }
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) scan()
    }
}

private sealed interface WifiAddRequest {
    data class Ready(val intent: Intent) : WifiAddRequest
    data class Invalid(@StringRes val reason: Int) : WifiAddRequest
}

/** Demande d'ajout du réseau à Android (écran de confirmation système, API 30). */
private fun wifiAddRequest(net: WifiNetwork, password: String): WifiAddRequest {
    val builder = WifiNetworkSuggestion.Builder().setSsid(net.ssid)
    when (net.security) {
        WifiSecurity.Wpa2 -> builder.setWpa2Passphrase(password)
        WifiSecurity.Wpa3 -> builder.setWpa3Passphrase(password)
        WifiSecurity.Open -> Unit
        WifiSecurity.Unsupported -> return WifiAddRequest.Invalid(R.string.network_unsupported_type)
    }
    val suggestion = runCatching { builder.build() }.getOrElse {
        return WifiAddRequest.Invalid(R.string.network_bad_password)
    }
    return WifiAddRequest.Ready(
        Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
            .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion)),
    )
}
