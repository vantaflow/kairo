package com.tvhome.launcher.network

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
    data class Ethernet(val ip: String?) : NetState
    data class Wifi(val ssid: String?, val signalLevel: Int, val ip: String?) : NetState
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
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetState.Ethernet(ip)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                @Suppress("DEPRECATION")
                val info = wifi?.connectionInfo
                NetState.Wifi(cleanSsid(info?.ssid), signalLevel(info?.rssi ?: -100), ip)
            }
            else -> NetState.Offline
        }
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
