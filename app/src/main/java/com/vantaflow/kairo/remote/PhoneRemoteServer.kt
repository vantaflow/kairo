package com.vantaflow.kairo.remote

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Port de la télécommande sur téléphone (http://<box>:8765). */
const val PHONE_REMOTE_PORT = 8765

/** Requête HTTP lue sur la socket : le corps reste dans [body] (lu à la demande, [length] octets). */
class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: InputStream,
    val length: Long,
) {
    fun bodyText(max: Int = 64 * 1024): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var left = length.coerceAtMost(max.toLong())
        while (left > 0) {
            val n = body.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) break
            out.write(buffer, 0, n)
            left -= n
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

class HttpResponse(val status: Int, val type: String, val body: ByteArray) {
    companion object {
        fun text(status: Int, text: String) = HttpResponse(status, "text/plain; charset=utf-8", text.toByteArray())
        fun json(text: String) = HttpResponse(200, "application/json; charset=utf-8", text.toByteArray())
    }
}

/** Ligne de requête et en-têtes (« GET /api/apps?t=x HTTP/1.1 » …) ; null si illisible. */
fun parseRequestHead(lines: List<String>): Triple<String, String, Map<String, String>>? {
    val parts = lines.firstOrNull()?.split(' ') ?: return null
    if (parts.size < 2) return null
    val headers = lines.drop(1).mapNotNull { line ->
        val i = line.indexOf(':')
        if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
    }.toMap()
    return Triple(parts[0].uppercase(), parts[1], headers)
}

fun parseQuery(target: String): Pair<String, Map<String, String>> {
    val path = target.substringBefore('?')
    val raw = target.substringAfter('?', "")
    val query = raw.split('&').filter { it.isNotEmpty() }.associate { pair ->
        fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
        decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
    }
    return path to query
}

/** Comparaison à durée constante du jeton (« t » dans l'URL ou en-tête X-Kairo-Token). */
fun tokenMatches(request: HttpRequest, token: String): Boolean {
    val given = request.query["t"] ?: request.headers["x-kairo-token"] ?: return false
    return token.isNotEmpty() && MessageDigest.isEqual(given.toByteArray(), token.toByteArray())
}

/**
 * Petit serveur HTTP du réseau local, sans dépendance : une connexion = une requête.
 * Toute requête doit porter le jeton du QR code ; sans lui, rien n'est servi.
 */
class PhoneRemoteServer(
    private val token: () -> String,
    private val route: (HttpRequest) -> HttpResponse,
    private val port: Int = PHONE_REMOTE_PORT,
) {
    @Volatile private var socket: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(4)

    val running: Boolean get() = socket?.isClosed == false

    @Synchronized
    fun start(): Boolean {
        if (running) return true
        val server = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        }.getOrElse { return false }
        socket = server
        thread(name = "kairo-remote", isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                pool.execute { handle(client) }
            }
        }
        return true
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket) = client.use { socket ->
        runCatching {
            socket.soTimeout = 30_000
            val input = socket.getInputStream().buffered()
            val head = buildList {
                while (size < 100) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    add(line)
                }
            }
            val (method, target, headers) = parseRequestHead(head) ?: return@use
            val (path, query) = parseQuery(target)
            val request = HttpRequest(method, path, query, headers, input, headers["content-length"]?.toLongOrNull() ?: 0L)
            val response = if (!tokenMatches(request, token())) {
                HttpResponse.text(403, "Scannez le QR code affiché par Kairo (Réglages › Télécommande sur téléphone).")
            } else {
                runCatching { route(request) }.getOrElse { HttpResponse.text(500, it.message ?: "erreur") }
            }
            write(socket.getOutputStream(), response)
        }
    }

    private fun write(out: OutputStream, response: HttpResponse) {
        val head = "HTTP/1.1 ${response.status} ${if (response.status < 400) "OK" else "Error"}\r\n" +
            "Content-Type: ${response.type}\r\n" +
            "Content-Length: ${response.body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(response.body)
        out.flush()
    }

    /** Ligne terminée par CRLF (en-têtes HTTP, ASCII). */
    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (line.isEmpty()) null else line.toString()
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            if (line.length > 8192) return null
            line.append(c.toChar())
        }
    }
}
