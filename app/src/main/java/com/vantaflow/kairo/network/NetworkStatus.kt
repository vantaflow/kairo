package com.vantaflow.kairo.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import androidx.annotation.StringRes
import com.vantaflow.kairo.R
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Connexion actuelle de la box. */
sealed interface NetState {
    data object Offline : NetState
    data class Ethernet(val ip: String?, val gateway: String? = null, val dns: String? = null) : NetState
    data class Wifi(
        val ssid: String?, val signalLevel: Int, val ip: String?,
        val gateway: String? = null, val dns: String? = null,
    ) : NetState
}

/** Sécurité d'un Wi-Fi ; Unsupported (WEP…) se configure dans les paramètres Android. */
enum class WifiSecurity { Open, Wpa2, Wpa3, Unsupported }

/** Réseau Wi-Fi trouvé par un scan. */
data class WifiNetwork(val ssid: String, val signalLevel: Int, val security: WifiSecurity)

/** Force du signal sur 0–4 à partir du RSSI (dBm). */
fun signalLevel(rssi: Int): Int = when {
    rssi >= -55 -> 4
    rssi >= -65 -> 3
    rssi >= -75 -> 2
    rssi >= -85 -> 1
    else -> 0
}

/** SSID lisible : sans guillemets ; null si inconnu (localisation non accordée). */
fun cleanSsid(raw: String?): String? {
    val s = raw?.removeSurrounding("\"")?.trim() ?: return null
    return s.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
}

fun securityOf(capabilities: String): WifiSecurity = when {
    "WEP" in capabilities || "EAP" in capabilities -> WifiSecurity.Unsupported
    // Mode mixte (PSK + SAE) : WPA2, accepté par le point d'accès.
    "SAE" in capabilities && "PSK" !in capabilities -> WifiSecurity.Wpa3
    "WPA" in capabilities || "RSN" in capabilities || "PSK" in capabilities -> WifiSecurity.Wpa2
    else -> WifiSecurity.Open
}

/** Issue d'une demande de connexion à un Wi-Fi. */
enum class WifiConnectResult(@StringRes val message: Int) {
    Saved(R.string.network_saved),
    WifiOff(R.string.network_wifi_off),
    NeedsApproval(R.string.network_needs_approval),
    BadPassword(R.string.network_bad_password),
    Unsupported(R.string.network_unsupported_type),
    Failed(R.string.network_connect_failed),
}

/** Un mot de passe WPA fait 8 à 63 caractères (ou 64 chiffres hexadécimaux). */
fun validWpaPassword(password: String): Boolean =
    password.length in 8..63 || (password.length == 64 && password.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })

/** Traduit le code renvoyé par Android à l'enregistrement d'un réseau. */
fun connectResultOf(status: Int, wifiEnabled: Boolean): WifiConnectResult = when {
    status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED -> WifiConnectResult.NeedsApproval
    status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> WifiConnectResult.Failed
    !wifiEnabled -> WifiConnectResult.WifiOff
    else -> WifiConnectResult.Saved
}

/** Suit la connexion par défaut (Ethernet ou Wi-Fi). */
class NetworkMonitor(private val context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    val state: Flow<NetState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(describe(caps, cm.getLinkProperties(network)))
            }

            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                cm.getNetworkCapabilities(network)?.let { trySend(describe(it, lp)) }
            }

            override fun onLost(network: Network) {
                trySend(NetState.Offline)
            }
        }
        trySend(current())
        cm.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun current(): NetState {
        val network = cm.activeNetwork ?: return NetState.Offline
        val caps = cm.getNetworkCapabilities(network) ?: return NetState.Offline
        return describe(caps, cm.getLinkProperties(network))
    }

    @SuppressLint("MissingPermission")
    private fun describe(caps: NetworkCapabilities, lp: LinkProperties?): NetState {
        val ip = lp?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
        val gateway = lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        val dns = lp?.dnsServers?.mapNotNull { it.hostAddress }?.takeIf { it.isNotEmpty() }?.joinToString(", ")
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetState.Ethernet(ip, gateway, dns)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                @Suppress("DEPRECATION")
                val info = wifi?.connectionInfo
                NetState.Wifi(cleanSsid(info?.ssid), signalLevel(info?.rssi ?: -100), ip, gateway, dns)
            }
            else -> NetState.Offline
        }
    }

    /**
     * Enregistre le réseau auprès d'Android, qui s'y connecte seul dès qu'il est à portée.
     * L'écran système d'ajout (ACTION_WIFI_ADD_NETWORKS) n'existe pas sur Android TV : il ouvre
     * les paramètres réseau et ignore le mot de passe.
     */
    @SuppressLint("MissingPermission")
    fun connect(net: WifiNetwork, password: String): WifiConnectResult {
        val manager = wifi ?: return WifiConnectResult.Failed
        val builder = WifiNetworkSuggestion.Builder().setSsid(net.ssid)
        when (net.security) {
            WifiSecurity.Open -> Unit
            WifiSecurity.Unsupported -> return WifiConnectResult.Unsupported
            WifiSecurity.Wpa2, WifiSecurity.Wpa3 -> {
                if (!validWpaPassword(password)) return WifiConnectResult.BadPassword
                runCatching {
                    if (net.security == WifiSecurity.Wpa3) builder.setWpa3Passphrase(password) else builder.setWpa2Passphrase(password)
                }.onFailure { return WifiConnectResult.BadPassword }
            }
        }
        val suggestion = runCatching { builder.build() }.getOrElse { return WifiConnectResult.BadPassword }
        return runCatching {
            // Un ancien mot de passe enregistré pour ce réseau est remplacé.
            manager.removeNetworkSuggestions(listOf(suggestion))
            connectResultOf(manager.addNetworkSuggestions(listOf(suggestion)), manager.isWifiEnabled)
        }.getOrDefault(WifiConnectResult.Failed)
    }

    /** Noms des réseaux que Kairo a enregistrés auprès d'Android. */
    fun savedByKairo(): List<String> =
        runCatching { wifi?.networkSuggestions.orEmpty().mapNotNull { it.ssid } }.getOrDefault(emptyList())

    /** Retire tous les réseaux enregistrés par Kairo (ceux des paramètres Android ne bougent pas). */
    fun forgetSaved() {
        runCatching { wifi?.removeNetworkSuggestions(emptyList()) }
    }

    fun locationEnabled(): Boolean =
        context.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: true

    /** Lance un scan et attend ses résultats (4 s au plus) ; sinon, derniers résultats connus. */
    suspend fun scanAndWait(): List<WifiNetwork> {
        val manager = wifi ?: return emptyList()
        val done = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                done.complete(Unit)
            }
        }
        context.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        try {
            @Suppress("DEPRECATION")
            val started = runCatching { manager.startScan() }.getOrDefault(false)
            if (started) withTimeoutOrNull(4_000) { done.await() }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
        return withContext(Dispatchers.IO) { latestResults() }
    }

    @SuppressLint("MissingPermission")
    private fun latestResults(): List<WifiNetwork> {
        val manager = wifi ?: return emptyList()
        return runCatching { manager.scanResults }.getOrDefault(emptyList())
            .mapNotNull { r ->
                @Suppress("DEPRECATION")
                val ssid = cleanSsid(r.SSID) ?: return@mapNotNull null
                WifiNetwork(ssid, signalLevel(r.level), securityOf(r.capabilities.orEmpty()))
            }
            .groupBy { it.ssid }
            .map { (_, same) -> same.maxBy { it.signalLevel } }
            .sortedByDescending { it.signalLevel }
    }
}
