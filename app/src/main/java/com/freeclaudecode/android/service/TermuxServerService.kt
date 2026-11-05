package com.freeclaudecode.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.freeclaudecode.android.FreeClaudeCodeApplication
import com.freeclaudecode.android.ui.main.MainActivity
import com.freeclaudecode.android.R
import com.freeclaudecode.android.settings.SettingsManager
import com.freeclaudecode.android.termux.TermuxManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TermuxServerService : Service() {
    companion object {
        const val CHANNEL_ID = "fcc_server_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.freeclaudecode.android.START_SERVER"
        const val ACTION_STOP = "com.freeclaudecode.android.STOP_SERVER"
        private const val TAG = "TermuxServerService"
    }

    private lateinit var settingsManager: SettingsManager
    private lateinit var termuxManager: TermuxManager
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        termuxManager = TermuxManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        if (isRunning) return
        isRunning = true

        val port = settingsManager.port
        val host = settingsManager.host

        FreeClaudeCodeApplication.instance.logLive("Starting FCC server on $host:$port")
        val notification = createNotification("Starting FCC Server...", "Starting on $host:$port", false)
        startForeground(NOTIFICATION_ID, notification)

        scope.launch {
            termuxManager.startService { result ->
                FreeClaudeCodeApplication.instance.logLive("Server start result: $result")
                Log.d(TAG, "Server start result: $result")
                // Update notification
                val notif = createNotification("FCC Server Running", "Port: $port", true)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notif)
            }
        }
    }

    private fun stopServer() {
        if (!isRunning) return
        isRunning = false

        FreeClaudeCodeApplication.instance.logLive("Stopping FCC server")
        scope.launch {
            termuxManager.stopService { result ->
                FreeClaudeCodeApplication.instance.logLive("Server stop result: $result")
                Log.d(TAG, "Server stop result: $result")
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private fun createNotification(title: String, text: String, isRunning: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, TermuxServerService::class.java)
        stopIntent.action = ACTION_STOP
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(isRunning)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (isRunning) {
            builder.addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FCC Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Free Claude Code server status"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning) {
            stopServer()
        }
    }
}