package com.vantaflow.kairo.adblock

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** DNS privé imposé par la box (mode « nom d'hôte »), sinon null. */
fun privateDnsHost(context: Context): String? {
    val cr = context.contentResolver
    if (android.provider.Settings.Global.getString(cr, "private_dns_mode") != "hostname") return null
    return android.provider.Settings.Global.getString(cr, "private_dns_specifier")?.takeIf { it.isNotBlank() }
}

/** DNS privé qui bloque déjà les pubs (AdGuard, NextDNS, Mullvad, Control D…). */
fun isAdBlockingDns(host: String): Boolean =
    listOf("adguard", "nextdns", "adblock.dns.mullvad", "controld", "dns.oszx", "blahdns").any { host.contains(it, ignoreCase = true) }

/** Serveurs filtrants AdGuard DNS (sans compte) : principal et secours. */
enum class AdFilter(val label: String, val servers: List<String>) {
    Ads("Pubs et pistage", listOf("94.140.14.14", "94.140.15.15")),
    Family("Pubs, pistage et contenus adultes", listOf("94.140.14.15", "94.140.15.16")),
}

/** État du bloqueur, partagé avec les réglages. */
object AdBlock {
    internal val mutableRunning = MutableStateFlow(false)
    val running: StateFlow<Boolean> = mutableRunning
    /** Requêtes depuis l'allumage : toutes, et celles bloquées (pubs, pistage…). */
    val queries = AtomicInteger()
    val blocked = AtomicInteger()
    /** Filtre utilisé (changé à chaud : profil enfant, réglage). */
    @Volatile var filter: AdFilter = AdFilter.Ads

    /** Démarre le bloqueur si Android l'a déjà autorisé (sinon il faut l'accord de l'utilisateur). */
    fun start(context: Context): Boolean {
        // DNS privé strict : Android l'impose aussi au VPN, qui couperait alors tout le DNS.
        if (privateDnsHost(context) != null || VpnService.prepare(context) != null) return false
        return runCatching { context.startService(Intent(context, AdBlockVpn::class.java)) }.isSuccess
    }

    fun stop(context: Context) {
        runCatching { context.startService(Intent(context, AdBlockVpn::class.java).setAction(AdBlockVpn.ACTION_STOP)) }
    }
}

/**
 * Bloqueur de pubs pour toute la box : un VPN local qui ne capte que le DNS (une adresse
 * factice), et transmet chaque requête à un serveur filtrant. Le reste du trafic ne passe
 * jamais par Kairo : aucun effet sur le débit des vidéos.
 */
class AdBlockVpn : VpnService() {
    @Volatile private var tunnel: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private val workers = Executors.newFixedThreadPool(4)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tunnel == null) establish()
        return START_STICKY
    }

    private fun establish() {
        val pfd = runCatching {
            Builder()
                .setSession("Kairo · bloqueur de pubs")
                .addAddress(TUN_ADDRESS, 32)
                .addDnsServer(DNS_ADDRESS)
                .addRoute(DNS_ADDRESS, 32)
                .setBlocking(true)
                .establish()
        }.getOrNull()
        if (pfd == null) {
            stopSelf()
            return
        }
        tunnel = pfd
        AdBlock.mutableRunning.value = true
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        reader = Thread({
            val buf = ByteArray(32767)
            while (tunnel != null) {
                val n = runCatching { input.read(buf) }.getOrDefault(-1)
                if (n < 0) break
                val q = parseDnsQuery(buf, n) ?: continue
                workers.execute { answer(q, output) }
            }
        }, "kairo-adblock").apply { start() }
    }

    /** Transmet la requête au serveur filtrant (principal, puis secours) et renvoie sa réponse. */
    private fun answer(q: DnsQuery, output: FileOutputStream) {
        AdBlock.queries.incrementAndGet()
        for (server in AdBlock.filter.servers) {
            val response = runCatching {
                DatagramSocket().use { s ->
                    // Le socket sort par le réseau normal, pas par notre tunnel.
                    protect(s)
                    s.soTimeout = 3_000
                    s.send(DatagramPacket(q.payload, q.payload.size, InetAddress.getByName(server), 53))
                    val back = DatagramPacket(ByteArray(4096), 4096)
                    s.receive(back)
                    back.data.copyOf(back.length)
                }
            }.getOrNull() ?: continue
            if (isBlockedResponse(response)) AdBlock.blocked.incrementAndGet()
            val packet = buildDnsReply(q, response)
            synchronized(output) { runCatching { output.write(packet) } }
            return
        }
    }

    private fun shutdown() {
        val t = tunnel
        tunnel = null
        runCatching { t?.close() }
        reader?.interrupt()
        AdBlock.mutableRunning.value = false
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        workers.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.vantaflow.kairo.adblock.STOP"
        private const val TUN_ADDRESS = "10.111.222.1"
        /** Adresse factice : seules les requêtes DNS entrent dans le tunnel. */
        private const val DNS_ADDRESS = "10.111.222.2"
    }
}
