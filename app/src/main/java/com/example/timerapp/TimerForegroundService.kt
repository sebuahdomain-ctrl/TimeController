package com.example.timerapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class TimerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_persistent_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.example.timerapp.ACTION_PLAY_PAUSE"
        const val ACTION_RESET = "com.example.timerapp.ACTION_RESET"

        // Format angka waktu di notifikasi. Masih placeholder statis (belum dihitung),
        // sengaja dijadikan satu konstanta supaya gampang diganti nanti.
        private const val TIME_PLACEHOLDER = "00:00"

        // Dibaca MainActivity.onResume untuk menentukan teks tombol & status.
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
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
        // Tombol "Atur" -> HARUS PendingIntent.getActivity supaya notification shade
        // menutup otomatis. Activity-nya transparan & langsung finish(), lalu
        // memunculkan popup lewat OverlayPopupService (lihat PopupTrampolineActivity).
        val openPopupIntent = Intent(this, PopupTrampolineActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        val openPopupPending = PendingIntent.getActivity(
            this, 1, openPopupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tombol "Play" -> logika timer belum ada, sengaja no-op lewat broadcast biasa.
        // Shade memang TIDAK menutup untuk tombol ini, itu disengaja.
        val playPauseIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePending = PendingIntent.getBroadcast(
            this, 3, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tombol "Reset" -> sama, sengaja no-op untuk sekarang.
        val resetIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_RESET
        }
        val resetPending = PendingIntent.getBroadcast(
            this, 4, resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val customView = RemoteViews(packageName, R.layout.notification_timer).apply {
            setTextViewText(R.id.txtNotifTime, TIME_PLACEHOLDER)
            setOnClickPendingIntent(R.id.btnPlay, playPausePending)
            setOnClickPendingIntent(R.id.btnReset, resetPending)
            setOnClickPendingIntent(R.id.btnAtur, openPopupPending)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true) // membuat notifikasi tidak bisa di-swipe hilang
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(customView)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
