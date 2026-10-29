package com.tvhome.launcher.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import com.tvhome.launcher.ui.startSafely
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE = "https://api.github.com/repos/vantaflow/kairo/releases/latest"

/** Vérifie, télécharge et installe les nouvelles versions publiées sur GitHub (dépôt privé : jeton requis). */
class Updater(private val context: Context) {

    fun latest(token: String): Release? {
        val body = request(LATEST_RELEASE, token, accept = "application/vnd.github+json") { it.bufferedReader().readText() }
        return body?.let(::parseLatestRelease)
    }

    /** Télécharge l'APK puis lance l'installation (Android demande confirmation). */
    fun install(release: Release, token: String): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        val ok = installer.openSession(sessionId).use { session ->
            val written = request(release.apkAssetUrl, token, accept = "application/octet-stream") { input ->
                session.openWrite("tvhome.apk", 0, -1).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
                true
            } ?: false
            if (written) {
                val intent = Intent(context, UpdateReceiver::class.java)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                session.commit(pending.intentSender)
            } else {
                session.abandon()
            }
            written
        }
        return ok
    }

    /**
     * GET authentifié. Les assets GitHub redirigent vers un stockage qui refuse l'en-tête
     * Authorization : la redirection est donc suivie à la main, sans jeton.
     */
    private fun <T> request(url: String, token: String, accept: String, read: (InputStream) -> T): T? = runCatching {
        var target = url
        var withAuth = true
        repeat(5) {
            val conn = URL(target).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 60_000
                conn.setRequestProperty("Accept", accept)
                conn.setRequestProperty("User-Agent", "TVHome")
                if (withAuth && token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
                when (conn.responseCode) {
                    in 200..299 -> return@runCatching conn.inputStream.use(read)
                    in 300..399 -> {
                        target = conn.getHeaderField("Location")?.takeIf { it.startsWith("https://") } ?: return@runCatching null
                        withAuth = false
                    }
                    else -> return@runCatching null
                }
            } finally {
                conn.disconnect()
            }
        }
        null
    }.getOrNull()
}

/** Reçoit le résultat d'une installation (mise à jour ou APK envoyé du téléphone) ; affiche la confirmation d'Android si nécessaire. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startSafely(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "erreur inconnue"
                Toast.makeText(context, "Installation impossible : $reason", Toast.LENGTH_LONG).show()
            }
        }
    }
}
