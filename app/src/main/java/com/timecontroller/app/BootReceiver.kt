package com.timecontroller.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Placeholder untuk BOOT_COMPLETED. Sengaja tidak auto-start service saat HP
 * reboot, karena start/stop service adalah keputusan eksplisit user lewat
 * tombol di halaman utama - bukan sesuatu yang harus otomatis menyala sendiri.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Sengaja dikosongkan.
    }
}
