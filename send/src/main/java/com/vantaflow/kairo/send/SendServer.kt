package com.vantaflow.kairo.send

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Petit serveur web de la box : page d'envoi pour le téléphone ou l'ordinateur, réception des fichiers
 * (corps brut d'un PUT, sans limite de taille) et téléchargement des fichiers proposés par la box.
 * Toutes les adresses exigent le jeton du QR code.
 */
class SendServer(private val context: Context, private val store: Store, private val token: String) {
    @Volatile private var socket: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(6)

    val running: Boolean get() = socket != null

    fun start(): Boolean {
        if (socket != null) return true
        val server = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(SEND_PORT))
            }
        }.getOrElse { return false }
        socket = server
        thread(name = "kairo-send", isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                pool.execute { handle(client) }
            }
        }
        return true
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket) = client.use { s ->
        runCatching {
            s.soTimeout = 60_000
            val input = s.getInputStream().buffered(64 * 1024)
            val out = s.getOutputStream().buffered(64 * 1024)
            val head = buildList {
                while (size < 100) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    add(line)
                }
            }
            val request = parseHead(head) ?: return@use
            if (request.query["k"] != token) {
                text(out, 403, "Scannez le QR code affiché par Kairo Send Files sur la TV.")
            } else {
                route(request, input, out)
            }
            out.flush()
        }
    }

    private fun route(r: Request, input: InputStream, out: OutputStream) {
        when {
            r.method == "GET" && r.path == "/" -> {
                val page = context.assets.open("index.html").use { it.readBytes() }
                send(out, 200, "text/html; charset=utf-8", page)
            }
            r.method == "GET" && r.path == "/api/files" -> {
                val json = store.list().joinToString(",", "[", "]") {
                    "{\"id\":${it.id},\"name\":${jsonString(it.name)},\"size\":${it.size},\"added\":${it.added}}"
                }
                send(out, 200, "application/json; charset=utf-8", json.toByteArray())
            }
            r.method == "GET" && r.path.startsWith("/dl/") -> {
                val file = r.path.removePrefix("/dl/").toLongOrNull()?.let(store::find)
                val stream = file?.let(store::open)
                if (file == null || stream == null) return text(out, 404, "Fichier introuvable")
                stream.use {
                    val headers = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n" +
                        "Content-Length: ${file.size}\r\nContent-Disposition: ${contentDisposition(file.name)}\r\n" +
                        "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
                    out.write(headers.toByteArray(Charsets.UTF_8))
                    it.copyTo(out, 256 * 1024)
                }
            }
            r.method == "DELETE" && r.path.startsWith("/api/files/") -> {
                val file = r.path.removePrefix("/api/files/").toLongOrNull()?.let(store::find)
                    ?: return text(out, 404, "Fichier introuvable")
                if (!store.delete(file)) return text(out, 500, "Suppression impossible")
                Hub.changed()
                send(out, 200, "application/json", "{\"ok\":true}".toByteArray())
            }
            r.method == "PUT" && r.path == "/up" -> {
                val name = safeFileName(r.query["name"])
                if (r.length < 0) return text(out, 411, "Taille du fichier manquante")
                val id = Hub.start(name, r.length)
                val ok = runCatching {
                    store.save(name, r.headers["content-type"]?.takeIf { it != "application/octet-stream" }, input, r.length) { Hub.progress(id, it) }
                }.isSuccess
                Hub.finish(id, ok)
                if (ok) send(out, 200, "application/json", "{\"ok\":true}".toByteArray())
                else text(out, 500, "Envoi interrompu ou stockage plein")
            }
            r.method == "POST" && r.path == "/text" -> {
                val max = 64 * 1024
                if (r.length !in 1..max) return text(out, 413, "Texte trop long")
                val bytes = ByteArray(r.length.toInt())
                var read = 0
                while (read < bytes.size) {
                    val n = input.read(bytes, read, bytes.size - read)
                    if (n < 0) break
                    read += n
                }
                val text = String(bytes, 0, read, Charsets.UTF_8).trim()
                if (text.isNotEmpty()) Hub.note(text)
                send(out, 200, "application/json", "{\"ok\":true}".toByteArray())
            }
            else -> text(out, 404, "Introuvable")
        }
    }

    private fun text(out: OutputStream, status: Int, message: String) =
        send(out, status, "text/plain; charset=utf-8", message.toByteArray())

    private fun send(out: OutputStream, status: Int, type: String, body: ByteArray) {
        val reason = if (status < 400) "OK" else "Error"
        out.write(
            ("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        out.write(body)
    }
}
