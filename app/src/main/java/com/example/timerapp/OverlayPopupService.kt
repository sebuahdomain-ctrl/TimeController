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
            // CATATAN: fokus jendela TIDAK berpengaruh ke notification shade.
            // Shade ditutup oleh PopupTrampolineActivity (lewat tombol "Buka").
            0,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        // Minta fokus secara eksplisit, bukan mengandalkan default sistem —
        // beberapa ROM custom tidak otomatis memberi fokus ke window baru.
        view.isFocusable = true
        view.isFocusableInTouchMode = true

        val btnClose: Button = view.findViewById(R.id.btnClosePopup)
        btnClose.setOnClickListener {
            removePopup()
            stopSelf()
        }

        windowManager?.addView(view, params)
        // PENTING: requestFocus() tidak bisa dipanggil langsung di baris berikutnya,
        // karena addView() bersifat async — view belum tentu sudah benar-benar
        // ter-attach ke window saat baris berikutnya dieksekusi. Kalau dipaksa
        // langsung, requestFocus() gagal diam-diam (return false, tanpa error),
        // sehingga kelihatan seperti "tidak ngaruh sama sekali".
        // view.post{} menunda pemanggilan sampai giliran layout berikutnya,
        // saat view sudah pasti siap menerima fokus.
        view.post {
            view.requestFocus()
        }
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
