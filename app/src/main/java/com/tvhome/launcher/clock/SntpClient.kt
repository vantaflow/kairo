package com.tvhome.launcher.clock

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

private const val NTP_PORT = 123
private const val NTP_PACKET_SIZE = 48
private const val NTP_MODE_CLIENT = 3
private const val NTP_MODE_SERVER = 4
private const val NTP_VERSION = 4
private const val NTP_EPOCH_OFFSET_S = 2_208_988_800L
private const val RECEIVE_TIME_OFFSET = 32
private const val TRANSMIT_TIME_OFFSET = 40

/** Envoie une requête UDP et renvoie la réponse, ou null en cas d'échec ou de délai dépassé. */
fun interface NtpTransport {
    fun exchange(host: String, request: ByteArray, timeoutMs: Int): ByteArray?
}

/** Transport réel : UDP port 123. */
val UdpNtpTransport = NtpTransport { host, request, timeoutMs ->
    runCatching {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val address = InetAddress.getByName(host)
            socket.send(DatagramPacket(request, request.size, address, NTP_PORT))
            val buffer = ByteArray(NTP_PACKET_SIZE)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            buffer.copyOf(response.length)
        }
    }.getOrNull()
}

/** Client SNTP minimal (RFC 4330) qui mesure l'écart entre l'horloge locale et un serveur. */
class SntpClient(private val transport: NtpTransport = UdpNtpTransport) {

    /** Écart serveur − local en millisecondes, ou null si la réponse est absente ou invalide. */
    fun queryOffsetMs(host: String, nowMs: () -> Long): Long? {
        val request = ByteArray(NTP_PACKET_SIZE)
        request[0] = ((NTP_VERSION shl 3) or NTP_MODE_CLIENT).toByte()
        val t0 = nowMs()
        val response = transport.exchange(host, request, TIMEOUT_MS) ?: return null
        val t3 = nowMs()
        if (response.size < NTP_PACKET_SIZE) return null
        val mode = response[0].toInt() and 0x7
        val stratum = response[1].toInt() and 0xff
        if (mode != NTP_MODE_SERVER || stratum == 0) return null
        val buf = ByteBuffer.wrap(response)
        val t1 = readTimestamp(buf, RECEIVE_TIME_OFFSET)
        val t2 = readTimestamp(buf, TRANSMIT_TIME_OFFSET)
        return ((t1 - t0) + (t2 - t3)) / 2
    }

    private fun readTimestamp(buf: ByteBuffer, index: Int): Long {
        val seconds = buf.getInt(index).toLong() and 0xffffffffL
        val fraction = buf.getInt(index + 4).toLong() and 0xffffffffL
        return (seconds - NTP_EPOCH_OFFSET_S) * 1000 + fraction * 1000 / 0x1_0000_0000L
    }

    companion object {
        const val TIMEOUT_MS = 3000
    }
}
