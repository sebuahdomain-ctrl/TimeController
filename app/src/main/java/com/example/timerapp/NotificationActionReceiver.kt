package com.example.timerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Menangani tombol Reset dan Matikan (alarm) dari notifikasi, lewat broadcast.
 * Semuanya cuma diteruskan ke TimerForegroundService, logikanya ada di sana.
 * (Tombol "Atur" dan "Play/Pause" lewat Activity perantara, karena hanya tombol
 * yang memicu Activity yang bisa menutup notification shade otomatis.
 * ACTION_PLAY_PAUSE di sini dibiarkan sebagai cadangan.)
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val dikenal = action == TimerForegroundService.ACTION_PLAY_PAUSE ||
            action == TimerForegroundService.ACTION_RESET ||
            action == TimerForegroundService.ACTION_STOP_ALARM
        if (!dikenal) return

        // Service sudah mati? Abaikan saja, jangan crash.
        if (!TimerForegroundService.isRunning) return

        try {
            context.startService(
                Intent(context, TimerForegroundService::class.java).setAction(action)
            )
        } catch (e: Exception) {
            // Sistem menolak start service: abaikan
        }
    }
}
