package com.example.timerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Menangani tombol Play dan Reset dari notifikasi custom.
 * (Tombol "Atur" ditangani PopupTrampolineActivity, karena hanya tombol
 * yang memicu Activity yang bisa menutup notification shade otomatis.
 * Play dan Reset sengaja lewat broadcast biasa, jadi shade TIDAK menutup
 * otomatis saat keduanya ditekan — ini disengaja, bukan bug.)
 *
 * Tombol "Stop" sekarang ditangani langsung di MainActivity, bukan di sini.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TimerForegroundService.ACTION_PLAY_PAUSE -> {
                // TODO: logika play/pause timer belum dibuat, ini baru kerangka tampilan.
            }
            TimerForegroundService.ACTION_RESET -> {
                // TODO: logika reset timer belum dibuat, ini baru kerangka tampilan.
            }
        }
    }
}
