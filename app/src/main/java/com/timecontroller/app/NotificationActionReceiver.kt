package com.timecontroller.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reserved for future notification action handling.
 * Saat ini semua aksi tombol notif dikirim langsung ke TimeService.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Tidak digunakan untuk saat ini
    }
}
