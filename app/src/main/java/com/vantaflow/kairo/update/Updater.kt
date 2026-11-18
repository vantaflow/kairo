package com.vantaflow.kairo.update

import com.vantaflow.kairo.R
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import com.vantaflow.kairo.ui.startSafely
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
    fun install(release: Release, token: String): Boolean = installAsset(release.apkAssetUrl, context.packageName, token)

    /**
     * Télécharge un APK publié sur GitHub ([assetUrl] : URL API de l'asset) et lance son
     * installation pour [packageName]. [onProgress] reçoit les octets reçus.
     */
    fun installAsset(
        assetUrl: String,
        packageName: String,
        token: String = "",
        /** Mise à jour automatique : installée sans fenêtre de confirmation, ou pas du tout. */
        auto: AutoInstall? = null,
        onProgress: (Long) -> Unit = {},
    ): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply {
                setAppPackageName(packageName)
                if (auto != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
        val sessionId = installer.createSession(params)
        val ok = installer.openSession(sessionId).use { session ->
            val written = request(assetUrl, token, accept = "application/octet-stream") { input ->
                session.openWrite("app.apk", 0, -1).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        total += n
                        onProgress(total)
                    }
                    session.fsync(out)
                }
                true
            } ?: false
            if (written) {
                val intent = Intent(context, UpdateReceiver::class.java)
                if (auto != null) intent.putExtra(EXTRA_AUTO_APP, auto.appId).putExtra(EXTRA_AUTO_VERSION, auto.version)
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
    /** Corps d'une requête GET (JSON de l'API GitHub). */
    fun getText(url: String): String? = request(url, "", accept = "application/vnd.github+json") { it.bufferedReader().readText() }

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

private const val EXTRA_AUTO_APP = "auto_app"
private const val EXTRA_AUTO_VERSION = "auto_version"

/** Reçoit le résultat d'une installation (mise à jour ou APK envoyé du téléphone) ; affiche la confirmation d'Android si nécessaire. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val autoApp = intent.getStringExtra(EXTRA_AUTO_APP)
                if (autoApp != null) {
                    // Mise à jour automatique : jamais de fenêtre d'Android par-dessus un film. On abandonne
                    // et on retient la version : elle reste proposée sur l'accueil et dans les réglages.
                    AutoUpdateLog(context).refuse(autoApp, intent.getStringExtra(EXTRA_AUTO_VERSION).orEmpty())
                    val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
                    if (id != -1) runCatching { context.packageManager.packageInstaller.abandonSession(id) }
                    return
                }
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startSafely(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            // Mise à jour automatique en échec : silencieuse, on réessaiera à la prochaine vérification.
            else -> if (intent.hasExtra(EXTRA_AUTO_APP)) Unit else {
                val reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: context.getString(R.string.updater_unknown_error)
                Toast.makeText(context, context.getString(R.string.updater_install_failed, reason), Toast.LENGTH_LONG).show()
            }
        }
    }
}
