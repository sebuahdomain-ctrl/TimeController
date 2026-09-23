package com.timecontroller.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service yang tetap hidup selama user belum menekan tombol Stop
 * di halaman utama - terlepas dari apakah timer/stopwatch sedang diset atau tidak.
 */
class TimeControllerService : Service() {

    companion object {
        const val ACTION_START = "com.timecontroller.app.action.START"
        const val ACTION_STOP = "com.timecontroller.app.action.STOP"
        const val ACTION_OPEN_POPUP = "com.timecontroller.app.action.OPEN_POPUP"

        const val CHANNEL_ID_SERVICE = "tc_service_channel"
        const val NOTIF_ID_SERVICE = 1001

        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createServiceChannel()
        OverlayManager.appContextRef = applicationContext
        TimerStopwatchEngine.addListener(OverlayManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfService()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIF_ID_SERVICE, buildServiceNotification())
                isRunning = true
            }
        }
        // START_STICKY: kalau service dibunuh sistem (bukan oleh user), Android akan
        // mencoba menjalankannya lagi sesegera mungkin.
        return START_STICKY
    }

    private fun stopSelfService() {
        isRunning = false
        TimerStopwatchEngine.resetAll()
        OverlayManager.hidePopup(this)
        OverlayManager.hideAlarm(this)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_SERVICE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildServiceNotification(): Notification {
        val openIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_OPEN_POPUP
        }
        val openPendingIntent = PendingIntent.getBroadcast(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(getString(R.string.notif_service_text))
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, getString(R.string.notif_action_open), openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                getString(R.string.notif_channel_service),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
