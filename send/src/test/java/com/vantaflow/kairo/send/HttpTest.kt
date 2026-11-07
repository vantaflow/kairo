package com.vantaflow.kairo.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTest {
    @Test
    fun parsesHeadAndQuery() {
        val r = parseHead(listOf("PUT /up?k=abc&name=Vid%C3%A9o%20vacances.mp4 HTTP/1.1", "Content-Length: 1234", "Host: box"))!!
        assertEquals("PUT", r.method)
        assertEquals("/up", r.path)
        assertEquals("Vidéo vacances.mp4", r.query["name"])
        assertEquals("abc", r.query["k"])
        assertEquals(1234L, r.length)
        assertNull(parseHead(emptyList()))
    }

    @Test
    fun readsCrlfLines() {
        val input = "GET / HTTP/1.1\r\nHost: x\r\n\r\n".byteInputStream()
        assertEquals("GET / HTTP/1.1", readLine(input))
        assertEquals("Host: x", readLine(input))
        assertEquals("", readLine(input))
        assertNull(readLine(input))
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("passwd", safeFileName("../../etc/passwd"))
        assertEquals("photo été.jpg", safeFileName("C:\\Users\\moi\\photo été.jpg"))
        assertEquals("fichier", safeFileName("  "))
        assertEquals("env", safeFileName(".env"))
        assertEquals("ab.txt", safeFileName("a<b>.txt"))
        val long = safeFileName("x".repeat(300) + ".mkv")
        assertTrue(long.length <= 200 && long.endsWith(".mkv"))
    }

    @Test
    fun jsonAndDisposition() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", jsonString("a\"b\\c\nd"))
        assertEquals("attachment; filename=\"_t_.txt\"; filename*=UTF-8''%C3%A9t%C3%A9.txt", contentDisposition("été.txt"))
    }

    @Test
    fun links() {
        assertTrue(isWebLink(" https://youtu.be/abc "))
        assertFalse(isWebLink("voir https://x.y"))
        assertFalse(isWebLink("bonjour"))
    }

    @Test
    fun tokens() {
        val t = newToken()
        assertEquals(12, t.length)
        assertTrue(t.all { it.isLetterOrDigit() })
    }
}
