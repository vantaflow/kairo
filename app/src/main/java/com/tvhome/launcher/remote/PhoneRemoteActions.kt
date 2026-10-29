package com.tvhome.launcher.remote

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
import com.tvhome.launcher.HomeActivity
import com.tvhome.launcher.TvHomeApp
import com.tvhome.launcher.ui.startSafely
import com.tvhome.launcher.update.UpdateReceiver
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
        else -> HttpResponse.text(404, "Introuvable")
    }

    private fun page() = HttpResponse(
        200, "text/html; charset=utf-8",
        context.assets.open("remote/index.html").use { it.readBytes() },
    )

    private fun key(name: String): HttpResponse {
        val key = PhoneKey.parse(name) ?: return HttpResponse.text(400, "Touche inconnue")
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
                    ?: return HttpResponse.text(409, "Activez « Kairo » dans Accessibilité sur la box pour la navigation.")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && key.global!! >= AccessibilityService.GLOBAL_ACTION_DPAD_UP) {
                    return HttpResponse.text(409, "Navigation possible à partir d'Android 13.")
                }
                main.post { service.performGlobalAction(key.global!!) }
            }
        }
        return HttpResponse.json("{\"ok\":true}")
    }

    /** Dans le champ de saisie actif de l'appli ouverte ; sinon, recherche dans Kairo. */
    private fun text(value: String): HttpResponse {
        if (value.isBlank()) return HttpResponse.text(400, "Texte vide")
        if (KeyRemapService.instance?.typeText(value) == true) return HttpResponse.json("{\"ok\":true,\"where\":\"field\"}")
        val search = Intent(context, HomeActivity::class.java)
            .putExtra("tvhome.open", "Search")
            .putExtra("tvhome.query", value.take(200))
        context.startSafely(search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        return HttpResponse.json("{\"ok\":true,\"where\":\"search\"}")
    }

    private fun apps(): HttpResponse {
        val hidden = runBlocking { container.prefs.hidden.first() }
        val list = JSONArray()
        container.appRepository.installedApps()
            .filter { it.packageName !in hidden && it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
            .forEach { list.put(JSONObject().put("p", it.packageName).put("l", it.label)) }
        return HttpResponse.json(list.toString())
    }

    private fun icon(pkg: String): HttpResponse {
        val drawable = container.appRepository.icon(pkg) ?: return HttpResponse.text(404, "Pas d'icône")
        val out = ByteArrayOutputStream()
        drawable.toBitmap(96, 96).compress(Bitmap.CompressFormat.PNG, 100, out)
        return HttpResponse(200, "image/png", out.toByteArray())
    }

    private fun launch(pkg: String): HttpResponse {
        val intent = container.appRepository.launchIntent(pkg.trim()) ?: return HttpResponse.text(404, "Appli introuvable")
        context.startSafely(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        runBlocking { container.prefs.recordLaunch(pkg.trim()) }
        return HttpResponse.json("{\"ok\":true}")
    }

    /** APK : installation (Android demande confirmation sur la box). Autre fichier : Téléchargements/Kairo. */
    private fun upload(rawName: String, body: InputStream, length: Long): HttpResponse {
        val name = rawName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "fichier" }.take(120)
        if (length <= 0) return HttpResponse.text(400, "Fichier vide")
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
                return HttpResponse.text(400, "Transfert interrompu")
            }
            val intent = Intent(context, UpdateReceiver::class.java)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pending.intentSender)
        }
        toast("Installation de $name : confirmez sur la télé.")
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
            ?: return HttpResponse.text(500, "Impossible d'écrire dans Téléchargements")
        val copied = resolver.openOutputStream(uri)?.use { copy(body, it, length) } ?: 0L
        if (copied != length) {
            resolver.delete(uri, null, null)
            return HttpResponse.text(400, "Transfert interrompu")
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        toast("Fichier reçu : $name (Téléchargements › Kairo)")
        return HttpResponse.json("{\"ok\":true,\"kind\":\"file\"}")
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

    private fun toast(message: String) = main.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
}
