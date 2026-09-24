package com.example.timerapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Menangani tombol "Stop" dari notifikasi persisten.
 * (Tombol "Buka" ditangani PopupTrampolineActivity, karena hanya tombol
 * yang memicu Activity yang bisa menutup notification shade otomatis.)
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
        }
    }
}
