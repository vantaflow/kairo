package com.vantaflow.kairo.remote

import com.vantaflow.kairo.R
import android.accessibilityservice.AccessibilityService
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import com.vantaflow.kairo.HomeActivity
import com.vantaflow.kairo.TvHomeApp
import com.vantaflow.kairo.ui.startSafely
import com.vantaflow.kairo.update.UpdateReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Touche de la page web → action globale (navigation) ou touche multimédia. */
enum class PhoneKey(val global: Int?, val media: Int?) {
    Up(AccessibilityService.GLOBAL_ACTION_DPAD_UP, null),
    Down(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN, null),
    Left(AccessibilityService.GLOBAL_ACTION_DPAD_LEFT, null),
    Right(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT, null),
    Ok(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER, null),
    Back(AccessibilityService.GLOBAL_ACTION_BACK, null),
    Home(AccessibilityService.GLOBAL_ACTION_HOME, null),
    PlayPause(null, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
    Next(null, KeyEvent.KEYCODE_MEDIA_NEXT),
    Previous(null, KeyEvent.KEYCODE_MEDIA_PREVIOUS),
    VolumeUp(null, null),
    VolumeDown(null, null),
    Mute(null, null),
    ;

    companion object {
        fun parse(name: String): PhoneKey? = entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    }
}

private const val PLAYER_PACKAGE = "com.vantaflow.kairo.player"

/** Ce que la page du téléphone peut demander à la box. */
class PhoneRemoteActions(private val context: Context) {
    private val container get() = (context.applicationContext as TvHomeApp).container
    private val main = Handler(Looper.getMainLooper())

    fun route(request: HttpRequest): HttpResponse = when ("${request.method} ${request.path}") {
        "GET /", "GET /index.html" -> page()
        "GET /api/state" -> HttpResponse.json(
            JSONObject().put("navigation", KeyRemapService.instance != null).put("model", Build.MODEL).toString(),
        )
        "POST /api/key" -> key(request.bodyText())
        "POST /api/text" -> text(request.bodyText())
        "GET /api/apps" -> apps()
        "GET /api/icon" -> icon(request.query["p"].orEmpty())
        "POST /api/launch" -> launch(request.bodyText())
        "POST /api/upload" -> upload(request.query["name"].orEmpty(), request.body, request.length)
        else -> HttpResponse.text(404, str(R.string.phoneremote_not_found))
    }

    private fun page() = HttpResponse(
        200, "text/html; charset=utf-8",
        context.assets.open("remote/index.html").use { it.readBytes() },
    )

    private fun key(name: String): HttpResponse {
        KeyRemapService.instance?.userActive()
        val key = PhoneKey.parse(name) ?: return HttpResponse.text(400, str(R.string.phoneremote_unknown_key))
        val audio = context.getSystemService(AudioManager::class.java)
        when {
            key == PhoneKey.VolumeUp -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            key == PhoneKey.VolumeDown -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            key == PhoneKey.Mute -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            key.media != null -> {
                val now = SystemClock.uptimeMillis()
                audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key.media, 0))
                audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key.media, 0))
            }
            else -> {
                // Navigation dans toutes les applis : actions globales du service d'accessibilité (Android 13+).
                val service = KeyRemapService.instance
                    ?: return HttpResponse.text(409, str(R.string.phoneremote_enable_accessibility))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && key.global!! >= AccessibilityService.GLOBAL_ACTION_DPAD_UP) {
                    return HttpResponse.text(409, str(R.string.phoneremote_needs_android13))
                }
                main.post { service.performGlobalAction(key.global!!) }
            }
        }
        return HttpResponse.json("{\"ok\":true}")
    }

    /** Dans le champ de saisie actif de l'appli ouverte ; sinon, recherche dans Kairo. */
    private fun text(value: String): HttpResponse {
        if (value.isBlank()) return HttpResponse.text(400, str(R.string.phoneremote_empty_text))
        if (KeyRemapService.instance?.typeText(value) == true) return HttpResponse.json("{\"ok\":true,\"where\":\"field\"}")
        val search = Intent(context, HomeActivity::class.java)
            .putExtra("tvhome.open", "Search")
            .putExtra("tvhome.query", value.take(200))
        context.startSafely(search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        return HttpResponse.json("{\"ok\":true,\"where\":\"search\"}")
    }

    /** Applis que le téléphone peut voir et lancer : non masquées ; en profil enfant, ses favoris seulement. */
    private fun allowedApps(): List<com.vantaflow.kairo.apps.AppInfo> {
        val (hidden, kidsFavorites) = runBlocking {
            container.prefs.hidden.first() to
                if (container.prefs.currentProfile.first().kids) container.prefs.favorites.first().toSet() else null
        }
        return container.appRepository.installedApps().filter {
            it.packageName !in hidden && it.packageName != context.packageName && (kidsFavorites == null || it.packageName in kidsFavorites)
        }
    }

    private fun apps(): HttpResponse {
        val list = JSONArray()
        allowedApps()
            .sortedBy { it.label.lowercase() }
            .forEach { list.put(JSONObject().put("p", it.packageName).put("l", it.label)) }
        return HttpResponse.json(list.toString())
    }

    private fun icon(pkg: String): HttpResponse {
        val drawable = container.appRepository.icon(pkg) ?: return HttpResponse.text(404, str(R.string.phoneremote_no_icon))
        val out = ByteArrayOutputStream()
        drawable.toBitmap(96, 96).compress(Bitmap.CompressFormat.PNG, 100, out)
        return HttpResponse(200, "image/png", out.toByteArray())
    }

    private fun launch(pkg: String): HttpResponse {
        if (allowedApps().none { it.packageName == pkg.trim() }) return HttpResponse.text(404, str(R.string.phoneremote_app_not_found))
        val intent = container.appRepository.launchIntent(pkg.trim()) ?: return HttpResponse.text(404, str(R.string.phoneremote_app_not_found))
        context.startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        runBlocking { container.prefs.recordLaunch(pkg.trim()) }
        return HttpResponse.json("{\"ok\":true}")
    }

    /** APK : installation (Android demande confirmation sur la box). Autre fichier : Téléchargements/Kairo. */
    private fun upload(rawName: String, body: InputStream, length: Long): HttpResponse {
        val name = rawName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "fichier" }.take(120)
        if (length <= 0) return HttpResponse.text(400, str(R.string.phoneremote_empty_file))
        return if (name.endsWith(".apk", ignoreCase = true)) installApk(name, body, length) else saveFile(name, body, length)
    }

    private fun installApk(name: String, body: InputStream, length: Long): HttpResponse {
        val installer = context.packageManager.packageInstaller
        val sessionId = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        installer.openSession(sessionId).use { session ->
            val copied = session.openWrite("upload.apk", 0, length).use { out ->
                copy(body, out, length).also { session.fsync(out) }
            }
            if (copied != length) {
                session.abandon()
                return HttpResponse.text(400, str(R.string.phoneremote_transfer_interrupted))
            }
            val intent = Intent(context, UpdateReceiver::class.java)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pending.intentSender)
        }
        toast(str(R.string.phoneremote_apk_confirm, name))
        return HttpResponse.json("{\"ok\":true,\"kind\":\"apk\"}")
    }

    private fun saveFile(name: String, body: InputStream, length: Long): HttpResponse {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Kairo")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return HttpResponse.text(500, str(R.string.phoneremote_write_failed))
        val copied = resolver.openOutputStream(uri)?.use { copy(body, it, length) } ?: 0L
        if (copied != length) {
            resolver.delete(uri, null, null)
            return HttpResponse.text(400, str(R.string.phoneremote_transfer_interrupted))
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        // Vidéo ou musique : lecture directe dans Kairo Player s'il est installé.
        val mime = java.net.URLConnection.guessContentTypeFromName(name).orEmpty()
        if ((mime.startsWith("video/") || mime.startsWith("audio/")) && openInPlayer(uri, mime)) {
            toast(str(R.string.phoneremote_playing_in_player, name))
            return HttpResponse.json("{\"ok\":true,\"kind\":\"play\"}")
        }
        toast(str(R.string.phoneremote_file_received, name))
        return HttpResponse.json("{\"ok\":true,\"kind\":\"file\"}")
    }

    private fun openInPlayer(uri: android.net.Uri, mime: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).setPackage(PLAYER_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return context.packageManager.resolveActivity(intent, 0) != null && context.startSafely(intent)
    }

    private fun copy(input: InputStream, out: OutputStream, length: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (total < length) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), length - total).toInt())
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return total
    }

    /** Texte lu sur le téléphone ou la télé, dans la langue de l'appli. */
    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun toast(message: String) = main.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
}
