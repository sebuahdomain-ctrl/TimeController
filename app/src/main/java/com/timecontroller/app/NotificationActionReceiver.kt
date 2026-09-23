package com.timecontroller.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Menangani tombol "Buka" pada notifikasi persistent di status bar,
 * yang memunculkan popup pengaturan Timer/Stopwatch sebagai overlay.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TimeControllerService.ACTION_OPEN_POPUP -> {
                OverlayManager.togglePopup(context)
            }
        }
    }
}
