package com.remoteadb.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.remoteadb.app.data.AppPreferences
import com.remoteadb.app.root.AdbManager
import com.remoteadb.app.tunnel.TunnelClient
import com.remoteadb.app.tunnel.TunnelState
import com.remoteadb.app.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class TunnelForegroundService : Service() {

    private var tunnelClient: TunnelClient? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var preferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP -> {
                stopTunnelService()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Starting Remote ADB..."))
                startTunnel()
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification("Remote ADB running"))
                startTunnel()
            }
        }

        return START_STICKY
    }

    private fun startTunnel() {
        serviceScope.launch {
            // Check if ADB needs auto-start
            if (preferences.autoStartAdb.value) {
                AdbManager.enableAdbTcp(preferences.localAdbPort.value)
            }

            tunnelClient?.disconnect()

            val host = if (preferences.tunnelMode.value == com.remoteadb.app.data.TunnelMode.PUBLIC_FREE) {
                "relay.remoteadb.org" // Fallback public free relay endpoint
            } else {
                preferences.serverHost.value
            }

            val client = TunnelClient(
                serverHost = host,
                serverPort = preferences.serverPort.value,
                deviceId = preferences.deviceId.value,
                pin = preferences.pin.value,
                localAdbPort = preferences.localAdbPort.value
            )
            tunnelClient = client

            launch {
                client.tunnelState.collectLatest { state ->
                    val notificationText = when (state) {
                        is TunnelState.Connected -> "Connected! Relay Port: ${state.assignedPort}"
                        is TunnelState.Connecting -> "Connecting to $host..."
                        is TunnelState.Disconnected -> "Disconnected"
                        is TunnelState.Error -> "Error: ${state.message}"
                    }
                    val notification = buildNotification(notificationText)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                }
            }

            client.connect()
        }
    }

    private fun stopTunnelService() {
        serviceScope.launch {
            tunnelClient?.disconnect()
            tunnelClient = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TunnelForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote ADB Service")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote ADB Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing status of Remote ADB Tunnel connection"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tunnelClient?.disconnect()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.remoteadb.app.action.START"
        const val ACTION_STOP = "com.remoteadb.app.action.STOP"
        private const val CHANNEL_ID = "remote_adb_service_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
