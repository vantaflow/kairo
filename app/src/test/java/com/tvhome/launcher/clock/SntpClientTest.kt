package com.tvhome.launcher.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

/** Fabrique de réponses NTP pour les tests. */
object NtpPackets {
    private const val NTP_EPOCH_OFFSET_S = 2_208_988_800L

    private fun putTimestamp(buf: ByteBuffer, index: Int, ms: Long) {
        val seconds = ms / 1000 + NTP_EPOCH_OFFSET_S
        val fraction = (ms % 1000) * 0x1_0000_0000L / 1000
        buf.putInt(index, seconds.toInt())
        buf.putInt(index + 4, fraction.toInt())
    }

    /** Réponse serveur : le serveur est en avance de [serverAheadMs] sur l'horloge locale [localMs]. */
    fun response(localMs: Long, serverAheadMs: Long, mode: Int = 4, stratum: Int = 2): ByteArray {
        val buf = ByteBuffer.allocate(48)
        buf.put(0, ((4 shl 3) or mode).toByte())
        buf.put(1, stratum.toByte())
        putTimestamp(buf, 32, localMs + serverAheadMs) // réception serveur
        putTimestamp(buf, 40, localMs + serverAheadMs) // émission serveur
        return buf.array()
    }
}

class SntpClientTest {
    private val local = 1_790_000_000_000L

    private fun client(response: ByteArray?) = SntpClient { _, request, _ ->
        assertEquals(48, request.size)
        response
    }

    @Test
    fun offsetComputedFromServerTimestamps() {
        val offset = client(NtpPackets.response(local, 5_000)).queryOffsetMs("h") { local }
        assertNotNull(offset)
        assertEquals(5_000.0, offset!!.toDouble(), 5.0)
    }

    @Test
    fun invalidModeReturnsNull() {
        assertNull(client(NtpPackets.response(local, 5_000, mode = 3)).queryOffsetMs("h") { local })
    }

    @Test
    fun stratumZeroReturnsNull() {
        assertNull(client(NtpPackets.response(local, 5_000, stratum = 0)).queryOffsetMs("h") { local })
    }

    @Test
    fun timeoutReturnsNull() {
        assertNull(client(null).queryOffsetMs("h") { local })
    }

    @Test
    fun shortPacketReturnsNull() {
        assertNull(client(ByteArray(10)).queryOffsetMs("h") { local })
    }
}
