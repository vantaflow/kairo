package com.vantaflow.kairo.send

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SendApp : Application() {
    lateinit var store: Store
    lateinit var server: SendServer
    lateinit var token: String

    /** Écran de Kairo Send Files affiché : le serveur reste ouvert. */
    @Volatile var visible = false

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("send", Context.MODE_PRIVATE)
        token = prefs.getString("token", null) ?: newToken().also { prefs.edit().putString("token", it).apply() }
        store = Store(this)
        server = SendServer(this, store, token)
    }
}

val Context.sendApp: SendApp get() = applicationContext as SendApp

/**
 * Garde le serveur ouvert tant que l'écran est affiché, puis jusqu'à la fin des envois en cours
 * (la box ferme vite les applis en arrière-plan : un service de premier plan les protège).
 */
class SendService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("send", "Kairo Send Files", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, "send")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Kairo Send Files")
            .setContentText("Prêt à recevoir des fichiers")
            .setOngoing(true)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        sendApp.server.start()
        instance = this
        scope.launch {
            Hub.transfers.collect { stopIfIdle() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun stopIfIdle() {
        if (!sendApp.visible && !Hub.busy) stopSelf()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        sendApp.server.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: SendService? = null

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SendService::class.java))
        }

        /** L'écran est fermé : le service s'arrête dès qu'aucun envoi n'est en cours. */
        fun idleCheck() {
            instance?.stopIfIdle()
        }
    }
}
