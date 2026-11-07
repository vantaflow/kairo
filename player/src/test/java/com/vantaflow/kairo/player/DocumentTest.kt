package com.vantaflow.kairo.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentTest {
    @Test
    fun utf8WithBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Été\r\nfin".toByteArray(Charsets.UTF_8)
        assertEquals("Été\nfin", decodeText(bytes))
    }

    @Test
    fun windowsTextFallsBackToCp1252() {
        val bytes = "Café à côté".toByteArray(charset("windows-1252"))
        assertEquals("Café à côté", decodeText(bytes))
    }
}
