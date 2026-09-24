package com.example.timerapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Activity perantara untuk tombol Play/Pause di notifikasi.
 * Sama logikanya dengan PopupTrampolineActivity: karena tombol notifikasi
 * memicu ACTIVITY, notification shade menutup otomatis. Activity ini
 * transparan, langsung meneruskan aksi Play/Pause ke service, lalu finish().
 */
class PlayTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kalau service sudah mati, abaikan saja (jangan crash)
        if (TimerForegroundService.isRunning) {
            try {
                startService(
                    Intent(this, TimerForegroundService::class.java)
                        .setAction(TimerForegroundService.ACTION_PLAY_PAUSE)
                )
            } catch (e: Exception) {
                // diabaikan
            }
        }

        finish()
        // Pastikan tidak ada animasi transisi sama sekali
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
