package com.example.timerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Menangani tombol dari notifikasi: Play/Pause, Reset, dan Matikan (alarm).
 * Semuanya cuma diteruskan ke TimerForegroundService, logikanya ada di sana.
 * (Tombol "Atur" ditangani PopupTrampolineActivity, karena hanya tombol
 * yang memicu Activity yang bisa menutup notification shade otomatis.)
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
