package com.vantaflow.kairo.adblock

import java.nio.ByteBuffer

/** Requête DNS (UDP sur IPv4) lue dans le tunnel. */
class DnsQuery(
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray,
)

/** Lit un paquet IPv4/UDP à destination du port 53 ; null pour tout le reste. */
fun parseDnsQuery(p: ByteArray, len: Int): DnsQuery? {
    if (len < 28 || (p[0].toInt() ushr 4) != 4 || p[9].toInt() != 17) return null
    val ihl = (p[0].toInt() and 0x0f) * 4
    if (len < ihl + 8) return null
    val srcPort = u16(p, ihl)
    val dstPort = u16(p, ihl + 2)
    if (dstPort != 53) return null
    return DnsQuery(p.copyOfRange(12, 16), p.copyOfRange(16, 20), srcPort, dstPort, p.copyOfRange(ihl + 8, len))
}

/** Paquet IPv4/UDP de réponse : adresses et ports inversés, somme de contrôle IP calculée. */
fun buildDnsReply(q: DnsQuery, dns: ByteArray): ByteArray {
    val total = 28 + dns.size
    val b = ByteBuffer.allocate(total)
    b.put(0x45).put(0).putShort(total.toShort()).putShort(0).putShort(0x4000).put(64).put(17).putShort(0)
    b.put(q.dstIp).put(q.srcIp)
    b.putShort(q.dstPort.toShort()).putShort(q.srcPort.toShort()).putShort((8 + dns.size).toShort()).putShort(0)
    b.put(dns)
    val out = b.array()
    val sum = ipChecksum(out, 20)
    out[10] = (sum ushr 8).toByte()
    out[11] = sum.toByte()
    return out
}

fun ipChecksum(b: ByteArray, len: Int): Int {
    var sum = 0
    var i = 0
    while (i < len) {
        sum += u16(b, i)
        i += 2
    }
    while (sum ushr 16 != 0) sum = (sum and 0xffff) + (sum ushr 16)
    return sum.inv() and 0xffff
}

/** Réponse bloquée par le serveur filtrant : adresse 0.0.0.0 (ou ::) dans les réponses. */
fun isBlockedResponse(dns: ByteArray): Boolean = runCatching {
    if (dns.size < 12) return false
    val qd = u16(dns, 4)
    val an = u16(dns, 6)
    var i = 12
    repeat(qd) { i = skipName(dns, i) + 4 }
    repeat(an) {
        i = skipName(dns, i)
        val type = u16(dns, i)
        val rdLen = u16(dns, i + 8)
        val data = i + 10
        if ((type == 1 && rdLen == 4) || (type == 28 && rdLen == 16)) {
            if ((data until data + rdLen).all { dns[it].toInt() == 0 }) return true
        }
        i = data + rdLen
    }
    false
}.getOrDefault(false)

private fun skipName(b: ByteArray, start: Int): Int {
    var i = start
    while (true) {
        val l = b[i].toInt() and 0xff
        when {
            l == 0 -> return i + 1
            l and 0xc0 == 0xc0 -> return i + 2
            else -> i += l + 1
        }
    }
}

private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
