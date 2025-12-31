package com.valdigley.claudevs.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.valdigley.claudevs.MainActivity
import com.valdigley.claudevs.R
import com.valdigley.claudevs.util.CrashLogger

class SSHForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "ssh_connection_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.valdigley.claudevs.STOP_SERVICE"
    }

    private val binder = SSHBinder()
    val sshService = SSHService()

    private var isConnected = false
    private var connectionName: String? = null

    inner class SSHBinder : Binder() {
        fun getService(): SSHForegroundService = this@SSHForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogger.log("SSHForegroundService", "onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CrashLogger.log("SSHForegroundService", "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        CrashLogger.log("SSHForegroundService", "onBind")
        return binder
    }

    override fun onDestroy() {
        CrashLogger.log("SSHForegroundService", "onDestroy")
        sshService.disconnect()
        super.onDestroy()
    }

    fun updateConnectionState(connected: Boolean, name: String? = null) {
        isConnected = connected
        connectionName = name
        updateNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Conexão SSH",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém a conexão SSH ativa em segundo plano"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Intent to open app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop service
        val stopIntent = Intent(this, SSHForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isConnected && connectionName != null) {
            "Conectado: $connectionName"
        } else if (isConnected) {
            "SSH Conectado"
        } else {
            "ClaudeVS"
        }

        val text = if (isConnected) {
            "Conexão SSH ativa em segundo plano"
        } else {
            "Serviço em execução"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desconectar", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
}
