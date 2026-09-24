package com.example.timerapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TimerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_persistent_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "com.example.timerapp.ACTION_STOP"
        const val ACTION_OPEN_POPUP = "com.example.timerapp.ACTION_OPEN_POPUP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: kalau service dibunuh sistem karena low memory,
        // Android akan mencoba menghidupkannya lagi otomatis.
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notifikasi Persisten",
                NotificationManager.IMPORTANCE_LOW // LOW supaya tidak bunyi tiap update, tapi tetap selalu tampil
            ).apply {
                description = "Notifikasi yang selalu aktif selama service berjalan"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // Tombol "Buka" -> minta OverlayPopupService menampilkan popup
        val openPopupIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_OPEN_POPUP
        }
        val openPopupPending = PendingIntent.getBroadcast(
            this, 1, openPopupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tombol "Stop" -> hentikan foreground service
        val stopIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getBroadcast(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timer App aktif")
            .setContentText("Notifikasi ini akan terus muncul")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true) // membuat notifikasi tidak bisa di-swipe hilang
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Buka", openPopupPending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
