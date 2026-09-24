package com.example.timerapp

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.TextView

/**
 * Menampilkan popup "Atur durasi" sebagai jendela overlay di atas app lain
 * (WindowManager + TYPE_APPLICATION_OVERLAY), jadi app yang sedang
 * dibuka user tetap di depan.
 */
class OverlayPopupService : Service() {

    private var windowManager: WindowManager? = null
    private var popupView: View? = null

    // Flag sekali-pakai: mencegah ketukan ganda pada tombol Mulai
    private var startConsumed = false

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
        startConsumed = false
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Popup di-inflate dari context Service, jadi tidak otomatis ikut
        // tema DayNight seperti Activity. Bungkus context dengan tema
        // platform (Theme.Material / Theme.Material.Light) sesuai uiMode
        // sistem saat ini, supaya NumberPicker bawaan tampil dengan warna
        // yang benar (lihat Theme.Popup.Dark / Theme.Popup.Light di themes.xml).
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val themeRes = if (isDarkMode) R.style.Theme_Popup_Dark else R.style.Theme_Popup_Light
        val themedContext = ContextThemeWrapper(this, themeRes)

        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.overlay_popup, null)
        popupView = view

        setupNumberPickers(view)
        setupClicks(view)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY // wajib dari Android 8+
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            popupWidthPx(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            0,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        windowManager?.addView(view, params)
    }

    /** Lebar popup: 85% lebar layar, maksimal 300dp (kartu dibuat lebih ringkas). */
    private fun popupWidthPx(): Int {
        val metrics = resources.displayMetrics
        val maxWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300f, metrics)
        return minOf(metrics.widthPixels * 0.85f, maxWidthPx).toInt()
    }

    /** Roda menit (0..99) dan detik (0..59), berputar, angka selalu 2 digit. */
    private fun setupNumberPickers(view: View) {
        val npMinute: NumberPicker = view.findViewById(R.id.npMinute)
        val npSecond: NumberPicker = view.findViewById(R.id.npSecond)

        configurePicker(npMinute, maxValue = 99)
        configurePicker(npSecond, maxValue = 59)

        // Nilai awal kedua roda = durasi terpilih (pertama kali 00:05)
        val durationMs = DurationStore.get(this)
        npMinute.value = (durationMs / 60_000L).toInt().coerceIn(0, 99)
        npSecond.value = ((durationMs / 1000L) % 60L).toInt()
    }

    private fun configurePicker(picker: NumberPicker, maxValue: Int) {
        picker.minValue = 0
        picker.maxValue = maxValue
        // Angka 2 digit lewat displayedValues, BUKAN setFormatter (ada bug
        // nilai awal tidak terformat).
        picker.displayedValues = Array(maxValue + 1) { i -> String.format("%02d", i) }
        picker.wrapSelectorWheel = true
        // setTextSize/setTextColor cuma ada di API 29+; di bawahnya biarkan
        // default dari tema platform yang sudah dibungkus ContextThemeWrapper.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            picker.setTextColor(resources.getColor(R.color.popup_text, theme))
            picker.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 18f, resources.displayMetrics
            ))
        }
    }

    private fun setupClicks(view: View) {
        val npMinute: NumberPicker = view.findViewById(R.id.npMinute)
        val npSecond: NumberPicker = view.findViewById(R.id.npSecond)
        val btnClose: ImageButton = view.findViewById(R.id.btnClosePopup)
        val btnMulai: TextView = view.findViewById(R.id.btnMulai)
        val btnReset: TextView = view.findViewById(R.id.btnReset)

        // Mulai hanya boleh dipakai kalau roda bukan 00:00
        fun updateStartButton() {
            val enabled = npMinute.value != 0 || npSecond.value != 0
            btnMulai.isEnabled = enabled
            btnMulai.isClickable = enabled
            btnMulai.alpha = if (enabled) 1f else 0.4f
        }

        // Isi kedua roda sekaligus (tidak memicu listener roda, jadi tombol Mulai diperbarui manual)
        fun setWheels(minutes: Int, seconds: Int) {
            npMinute.value = minutes
            npSecond.value = seconds
            updateStartButton()
        }

        btnClose.setOnClickListener {
            removePopup()
            stopSelf()
        }

        // Tiap roda digeser, keadaan tombol Mulai ikut diperbarui
        npMinute.setOnValueChangedListener { _, _, _ -> updateStartButton() }
        npSecond.setOnValueChangedListener { _, _, _ -> updateStartButton() }

        // 6 pilihan cepat: hanya mengisi roda (menit = N, detik = 0),
        // TIDAK memulai timer dan popup tetap terbuka.
        val quickChoices = listOf(
            R.id.btnQuick1Min to 1,
            R.id.btnQuick2Min to 2,
            R.id.btnQuick5Min to 5,
            R.id.btnQuick10Min to 10,
            R.id.btnQuick20Min to 20,
            R.id.btnQuick30Min to 30
        )
        for ((buttonId, minutes) in quickChoices) {
            val chip: TextView = view.findViewById(buttonId)
            chip.setOnClickListener { setWheels(minutes, 0) }
        }

        // Reset: kedua roda kembali ke 00:00. Popup tetap terbuka dan timer
        // yang sedang berjalan tidak disentuh.
        btnReset.setOnClickListener { setWheels(0, 0) }

        // Mulai: simpan durasi, kirim ke service, lalu tutup popup
        btnMulai.setOnClickListener {
            if (startConsumed) return@setOnClickListener
            val durationMs = (npMinute.value * 60L + npSecond.value) * 1000L
            if (durationMs <= 0L) return@setOnClickListener
            startConsumed = true

            DurationStore.set(this, durationMs)

            // Kalau service timer sedang tidak hidup, jangan dihidupkan lewat
            // jalan ini (nanti jadi service tanpa notifikasi): cukup abaikan.
            if (TimerForegroundService.isRunning) {
                try {
                    startService(
                        Intent(this, TimerForegroundService::class.java)
                            .setAction(TimerForegroundService.ACTION_START_WITH_DURATION)
                            .putExtra(TimerForegroundService.EXTRA_DURATION_MS, durationMs)
                    )
                } catch (e: Exception) {
                    // Sistem menolak start service: abaikan
                }
            }

            removePopup()
            stopSelf()
        }

        updateStartButton()
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
