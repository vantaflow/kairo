package com.vantaflow.kairo.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRemoteTest {
    private fun request(target: String, headers: Map<String, String> = emptyMap()): HttpRequest {
        val (path, query) = parseQuery(target)
        return HttpRequest("GET", path, query, headers, "".byteInputStream(), 0)
    }

    @Test
    fun parsesRequestLineAndHeaders() {
        val (method, target, headers) = parseRequestHead(listOf("post /api/key?t=abc HTTP/1.1", "Content-Length: 2", "Host: box"))!!
        assertEquals("POST", method)
        assertEquals("/api/key?t=abc", target)
        assertEquals("2", headers["content-length"])
        assertNull(parseRequestHead(emptyList()))
    }

    @Test
    fun decodesQuery() {
        val (path, query) = parseQuery("/api/upload?name=Mon%20film.mkv&t=x1")
        assertEquals("/api/upload", path)
        assertEquals("Mon film.mkv", query["name"])
        assertEquals("x1", query["t"])
    }

    @Test
    fun tokenRequiredInQueryOrHeader() {
        assertTrue(tokenMatches(request("/?t=secret"), "secret"))
        assertTrue(tokenMatches(request("/", mapOf("x-kairo-token" to "secret")), "secret"))
        assertFalse(tokenMatches(request("/?t=autre"), "secret"))
        assertFalse(tokenMatches(request("/"), "secret"))
        assertFalse(tokenMatches(request("/?t="), ""))
    }

    @Test
    fun keyNamesFromThePage() {
        listOf("up", "down", "left", "right", "ok", "back", "home", "playpause", "next", "volumeup", "volumedown", "mute").forEach {
            assertTrue(it, PhoneKey.parse(it) != null)
        }
        assertNull(PhoneKey.parse("format"))
    }
}
