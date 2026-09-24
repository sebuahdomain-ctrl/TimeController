package com.example.timerapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Activity perantara: transparan, tanpa animasi, dan langsung finish().
 *
 * KENAPA ADA INI?
 * Notification shade (bar yang ditarik dari atas) hanya menutup otomatis kalau
 * tombol notifikasi yang di-tap memicu ACTIVITY (PendingIntent.getActivity).
 * Kalau tombolnya cuma BroadcastReceiver / Service, SystemUI membiarkan shade
 * tetap terbuka. Fokus jendela overlay TIDAK ada hubungannya dengan ini.
 *
 * Alurnya: tap "Buka" -> Activity ini start (shade menutup) -> minta
 * OverlayPopupService menampilkan popup -> Activity ini langsung finish(),
 * jadi app yang tadi dibuka user (WhatsApp, Chrome, dll) balik ke depan.
 */
class PopupTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Aman memanggil startService di sini: Activity ini sedang foreground.
        startService(Intent(this, OverlayPopupService::class.java))

        finish()
        // Pastikan tidak ada animasi transisi sama sekali
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
