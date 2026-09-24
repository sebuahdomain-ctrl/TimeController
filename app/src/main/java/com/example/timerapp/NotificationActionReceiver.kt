package com.example.timerapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Menangani tap tombol dari notifikasi persisten.
 * PENTING: ini BroadcastReceiver, bukan Activity — jadi tap tombol
 * TIDAK akan membawa user "masuk" ke dalam app. App yang sedang
 * dibuka user (WhatsApp, Chrome, dll) tetap di depan.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TimerForegroundService.ACTION_STOP -> {
                // Hentikan foreground service -> notifikasi otomatis hilang
                val stopServiceIntent = Intent(context, TimerForegroundService::class.java)
                context.stopService(stopServiceIntent)

                // Jika popup sedang terbuka, ikut ditutup juga
                val stopOverlayIntent = Intent(context, OverlayPopupService::class.java)
                context.stopService(stopOverlayIntent)
            }

            TimerForegroundService.ACTION_OPEN_POPUP -> {
                // Minta OverlayPopupService menampilkan jendela popup,
                // tanpa membuka Activity apa pun. Notification shade akan
                // otomatis tertutup karena popup ini dibuat focusable
                // (lihat catatan di OverlayPopupService).
                val overlayIntent = Intent(context, OverlayPopupService::class.java)
                context.startService(overlayIntent)
            }
        }
    }
}
