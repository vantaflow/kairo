package com.vantaflow.kairo.send

import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom

/** Port de Kairo Send Files (http://<box>:8766). Kairo Remote utilise déjà 8765. */
const val SEND_PORT = 8766

/** Requête lue sur la socket ; le corps reste dans le flux (envoi de gros fichiers). */
data class Request(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
) {
    val length: Long get() = headers["content-length"]?.toLongOrNull() ?: -1
}

private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

/** Première ligne et en-têtes (clés en minuscules) ; null si la requête est illisible. */
fun parseHead(lines: List<String>): Request? {
    val parts = lines.firstOrNull()?.split(' ') ?: return null
    if (parts.size < 2) return null
    val target = parts[1]
    val path = decode(target.substringBefore('?'))
    val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate {
        decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
    }
    val headers = lines.drop(1).mapNotNull { line ->
        val i = line.indexOf(':')
        if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
    }.toMap()
    return Request(parts[0].uppercase(), path, query, headers)
}

/** Ligne terminée par CRLF (ou LF), en ISO-8859-1 comme l'en-tête HTTP ; null en fin de flux. */
fun readLine(input: InputStream, max: Int = 8192): String? {
    val sb = StringBuilder()
    while (true) {
        val c = input.read()
        if (c < 0) return if (sb.isEmpty()) null else sb.toString()
        if (c == '\n'.code) return sb.toString().removeSuffix("\r")
        if (sb.length >= max) return null
        sb.append(c.toChar())
    }
}

/**
 * Nom de fichier sûr : sans dossier (« ../ »), sans caractères interdits, jamais vide.
 * Garde les accents et l'extension.
 */
fun safeFileName(raw: String?): String {
    val base = raw.orEmpty().replace('\\', '/').substringAfterLast('/')
        .filterNot { it.code < 32 || it in "<>:\"|?*" }
        .trim().trimStart('.')
    val name = base.ifBlank { "fichier" }
    return if (name.length <= 200) name else {
        val ext = name.substringAfterLast('.', "").take(12)
        name.take(200 - ext.length - 1) + if (ext.isNotEmpty()) ".$ext" else ""
    }
}

/** Chaîne JSON (guillemets, antislash, sauts de ligne et caractères de contrôle échappés). */
fun jsonString(s: String): String = buildString {
    append('"')
    for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c.code < 32 -> append("\\u%04x".format(c.code))
        else -> append(c)
    }
    append('"')
}

/** En-tête de téléchargement qui garde les accents (RFC 6266). */
fun contentDisposition(name: String): String {
    val ascii = name.map { if (it.code in 32..126 && it != '"' && it != '\\') it else '_' }.joinToString("")
    val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    return "attachment; filename=\"$ascii\"; filename*=UTF-8''$encoded"
}

/** Vrai pour un lien web, à ouvrir dans le navigateur de la box. */
fun isWebLink(text: String): Boolean {
    val t = text.trim()
    return (t.startsWith("http://") || t.startsWith("https://")) && t.none { it.isWhitespace() }
}

/** Jeton aléatoire qui protège la page : seul qui a scanné le QR code peut envoyer. */
fun newToken(random: SecureRandom = SecureRandom()): String {
    val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
    return (1..12).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
}
