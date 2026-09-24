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
 * Menampilkan popup sebagai jendela overlay di atas app lain
 * (WindowManager + TYPE_APPLICATION_OVERLAY), jadi app yang sedang
 * dibuka user tetap di depan.
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
            0,
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
