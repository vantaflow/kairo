package com.vantaflow.kairo.adblock

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketTest {
    /** Requête DNS « ads.example » (type A). */
    private val dnsQuery = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0) +
        byteArrayOf(3) + "ads".toByteArray() + byteArrayOf(7) + "example".toByteArray() + byteArrayOf(0, 0, 1, 0, 1)

    private fun ipUdp(payload: ByteArray, dstPort: Int = 53): ByteArray {
        val q = DnsQuery(byteArrayOf(10, 111, 222.toByte(), 1), byteArrayOf(10, 111, 222.toByte(), 2), 40000, dstPort, payload)
        // Paquet aller : même format que la réponse, adresses et ports dans l'autre sens.
        return buildDnsReply(DnsQuery(q.dstIp, q.srcIp, q.dstPort, q.srcPort, payload), payload)
    }

    private fun answer(ip: ByteArray): ByteArray {
        val header = dnsQuery.copyOf().also { it[2] = 0x81.toByte(); it[3] = 0x80.toByte(); it[7] = 1 }
        return header + byteArrayOf(0xc0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4) + ip
    }

    @Test
    fun parsesQueryAndBuildsValidReply() {
        val packet = ipUdp(dnsQuery)
        val q = parseDnsQuery(packet, packet.size)!!
        assertEquals(53, q.dstPort)
        assertEquals(40000, q.srcPort)
        assertArrayEquals(dnsQuery, q.payload)
        val reply = buildDnsReply(q, answer(byteArrayOf(1, 2, 3, 4)))
        // Somme de contrôle IP correcte : recalculée sur l'en-tête complet, elle vaut 0.
        assertEquals(0, ipChecksum(reply, 20))
        assertArrayEquals(q.dstIp, reply.copyOfRange(12, 16))
    }

    @Test
    fun ignoresNonDnsTraffic() {
        val packet = ipUdp(dnsQuery, dstPort = 443)
        assertNull(parseDnsQuery(packet, packet.size))
    }

    @Test
    fun detectsBlockedAnswers() {
        assertTrue(isBlockedResponse(answer(byteArrayOf(0, 0, 0, 0))))
        assertFalse(isBlockedResponse(answer(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))))
        assertFalse(isBlockedResponse(byteArrayOf(1, 2)))
    }
}
