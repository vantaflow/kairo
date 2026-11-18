package com.vantaflow.kairo.send

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
    private val resources = ConcurrentHashMap<String, Resources>()

    /** Textes de la langue choisie pour cette requête (navigateur, sinon box). */
    private fun resourcesFor(language: String): Resources = resources.getOrPut(language) {
        val locale = BoxLocale.locale(Locale.forLanguageTag(language))
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        context.createConfigurationContext(config).resources
    }

    private fun languageOf(r: Request) = pickLanguage(r.headers["accept-language"], BoxLocale.locale().language)

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
            val res = resourcesFor(languageOf(request))
            if (request.query["k"] != token) {
                text(out, 403, res.getString(R.string.server_forbidden))
            } else {
                route(request, res, input, out)
            }
            out.flush()
        }
    }

    private fun route(r: Request, res: Resources, input: InputStream, out: OutputStream) {
        when {
            r.method == "GET" && r.path == "/" -> send(out, 200, "text/html; charset=utf-8", page(res).toByteArray())
            r.method == "GET" && r.path == "/api/files" -> {
                val json = store.list().joinToString(",", "[", "]") {
                    "{\"id\":${it.id},\"name\":${jsonString(it.name)},\"size\":${it.size},\"added\":${it.added}}"
                }
                send(out, 200, "application/json; charset=utf-8", json.toByteArray())
            }
            r.method == "GET" && r.path.startsWith("/dl/") -> {
                val file = r.path.removePrefix("/dl/").toLongOrNull()?.let(store::find)
                val stream = file?.let(store::open)
                if (file == null || stream == null) return text(out, 404, res.getString(R.string.server_file_not_found))
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
                    ?: return text(out, 404, res.getString(R.string.server_file_not_found))
                if (!store.delete(file)) return text(out, 500, res.getString(R.string.delete_failed))
                Hub.changed()
                send(out, 200, "application/json", "{\"ok\":true}".toByteArray())
            }
            r.method == "PUT" && r.path == "/up" -> {
                val name = safeFileName(r.query["name"])
                if (r.length < 0) return text(out, 411, res.getString(R.string.server_length_required))
                val id = Hub.start(name, r.length)
                val ok = runCatching {
                    store.save(name, r.headers["content-type"]?.takeIf { it != "application/octet-stream" }, input, r.length) { Hub.progress(id, it) }
                }.isSuccess
                Hub.finish(id, ok)
                if (ok) send(out, 200, "application/json", "{\"ok\":true}".toByteArray())
                else text(out, 500, res.getString(R.string.server_upload_failed))
            }
            r.method == "POST" && r.path == "/text" -> {
                val max = 64 * 1024
                if (r.length !in 1..max) return text(out, 413, res.getString(R.string.server_text_too_long))
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
            else -> text(out, 404, res.getString(R.string.server_not_found))
        }
    }

    /**
     * Page d'envoi dans la langue choisie : `lang` et `dir` de la balise <html>, et textes de
     * strings.xml (size_…, web_…) passés à la page en JSON.
     */
    private fun page(res: Resources): String {
        val language = res.configuration.locales.get(0).language
        val texts = PAGE_TEXTS.associate { id -> res.getResourceEntryName(id) to res.getString(id) }
        return context.assets.open("index.html").use { it.readBytes() }.toString(Charsets.UTF_8)
            .replaceFirst("<html lang=\"fr\">", "<html lang=\"$language\" dir=\"${if (language == "ar") "rtl" else "ltr"}\">")
            .replaceFirst("/*I18N*/{}", i18nJson(texts))
    }

    private companion object {
        val PAGE_TEXTS = listOf(
            R.string.size_bytes, R.string.size_kb, R.string.size_mb, R.string.size_gb, R.string.size_tb,
            R.string.web_subtitle, R.string.web_send_title, R.string.web_pick, R.string.web_pick_hint,
            R.string.web_text_title, R.string.web_text_placeholder, R.string.web_send, R.string.web_box_title,
            R.string.web_loading, R.string.web_waiting, R.string.web_progress, R.string.web_sent, R.string.web_failed,
            R.string.web_error_status, R.string.web_connection_lost_wifi, R.string.web_shown_on_tv, R.string.web_failure,
            R.string.web_connection_lost, R.string.web_scan_qr, R.string.web_download, R.string.web_delete,
            R.string.web_no_files, R.string.web_delete_confirm, R.string.web_delete_failed,
        )
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
