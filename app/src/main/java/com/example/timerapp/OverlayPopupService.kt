package com.example.timerapp

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button

/**
 * Menampilkan popup sebagai jendela overlay ASLI di atas app lain,
 * menggunakan WindowManager + TYPE_APPLICATION_OVERLAY.
 *
 * INI KUNCINYA supaya app yang sedang dibuka user (WhatsApp, Chrome, dll)
 * TETAP di depan / tetap aktif. Tidak ada startActivity() sama sekali,
 * jadi tidak ada "perpindahan app" yang terjadi.
 */
class OverlayPopupService : Service() {

    private var windowManager: WindowManager? = null
    private var popupView: View? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Kalau popup sudah tampil, jangan dobel
        if (popupView != null) {
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            // Guard: harusnya sudah dicek dari MainActivity, tapi tetap dijaga di sini
            stopSelf()
            return START_NOT_STICKY
        }

        showPopup()
        return START_NOT_STICKY
    }

    private fun showPopup() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_popup, null)
        popupView = view

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY // wajib dari Android 8+
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            // FLAG_NOT_FOCUSABLE: popup tidak "merebut" fokus input dari app di belakangnya,
            // jadi app yang sedang dibuka user tetap bisa dianggap aktif/foreground.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val btnClose: Button = view.findViewById(R.id.btnClosePopup)
        btnClose.setOnClickListener {
            removePopup()
            stopSelf()
        }

        windowManager?.addView(view, params)
    }

    private fun removePopup() {
        popupView?.let {
            windowManager?.removeView(it)
        }
        popupView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removePopup()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
