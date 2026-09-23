package com.timecontroller.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView

/**
 * Mengelola dua jenis overlay window yang tampil di atas aplikasi lain:
 * 1. Popup timer sederhana (angka, tombol mulai/jeda, tombol reset) - dibuka dari notifikasi
 * 2. Alarm heads-up - muncul otomatis saat timer selesai, mirip alarm bawaan Android
 */
object OverlayManager : TimerStopwatchEngine.Listener {

    private var popupView: View? = null
    private var alarmView: View? = null
    private var windowManager: WindowManager? = null

    private fun wm(context: Context): WindowManager {
        if (windowManager == null) {
            windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        return windowManager!!
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    fun isPopupShowing(): Boolean = popupView != null
    fun isAlarmShowing(): Boolean = alarmView != null

    // ================= POPUP TIMER =================

    fun showPopup(context: Context) {
        if (!Settings.canDrawOverlays(context)) return
        if (popupView != null) return

        val appContext = context.applicationContext
        val inflater = LayoutInflater.from(appContext)
        val view = inflater.inflate(R.layout.popup_overlay, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.x = 0
        params.y = 0

        setupPickers(view)
        setupPopupInteractions(appContext, view)

        wm(appContext).addView(view, params)
        popupView = view
        refreshPopupUI()
        BeepPlayer.beep(800.0, 0.05, BeepPlayer.Wave.SINE)
    }

    fun hidePopup(context: Context) {
        val view = popupView ?: return
        try {
            wm(context.applicationContext).removeView(view)
        } catch (e: Exception) {
            // view sudah tidak terpasang, aman diabaikan
        }
        popupView = null
    }

    fun togglePopup(context: Context) {
        if (popupView != null) hidePopup(context) else showPopup(context)
    }

    // ================= SETUP WHEEL PICKER =================

    private fun configureWheel(picker: WheelPicker) {
        picker.minValue = 0
        picker.maxValue = 59
    }

    private fun setupPickers(view: View) {
        val pickerMin = view.findViewById<WheelPicker>(R.id.pickerMinute)
        val pickerSec = view.findViewById<WheelPicker>(R.id.pickerSecond)

        listOf(pickerMin, pickerSec).forEach { configureWheel(it) }

        pickerMin.setValue(TimerStopwatchEngine.timerMinutes)
        pickerSec.setValue(TimerStopwatchEngine.timerSeconds)

        pickerMin.onValueChangeListener = { newVal ->
            if (TimerStopwatchEngine.timerIsRunning) {
                pickerMin.setValue(TimerStopwatchEngine.timerMinutes)
            } else {
                TimerStopwatchEngine.setWheelMinute(newVal)
                BeepPlayer.beep(950.0, 0.02, BeepPlayer.Wave.TRIANGLE)
            }
        }
        pickerSec.onValueChangeListener = { newVal ->
            if (TimerStopwatchEngine.timerIsRunning) {
                pickerSec.setValue(TimerStopwatchEngine.timerSeconds)
            } else {
                TimerStopwatchEngine.setWheelSecond(newVal)
                BeepPlayer.beep(1150.0, 0.02, BeepPlayer.Wave.TRIANGLE)
            }
        }
    }

    // ================= INTERAKSI TOMBOL =================

    private fun setupPopupInteractions(context: Context, view: View) {
        view.findViewById<View>(R.id.btnClosePopup).setOnClickListener {
            BeepPlayer.beep(400.0, 0.05, BeepPlayer.Wave.SINE)
            hidePopup(context)
        }

        view.findViewById<View>(R.id.primaryTimerBtn).setOnClickListener {
            val total = TimerStopwatchEngine.timerMinutes * 60 + TimerStopwatchEngine.timerSeconds
            if (total <= 0 && !TimerStopwatchEngine.timerIsRunning) {
                BeepPlayer.beep(300.0, 0.15, BeepPlayer.Wave.SAWTOOTH)
                return@setOnClickListener
            }
            val wasRunning = TimerStopwatchEngine.timerIsRunning
            TimerStopwatchEngine.toggleTimer()
            BeepPlayer.beep(
                if (!wasRunning) 1000.0 else 500.0,
                if (!wasRunning) 0.06 else 0.06,
                BeepPlayer.Wave.SINE
            )
        }
        view.findViewById<View>(R.id.resetTimerBtn).setOnClickListener {
            TimerStopwatchEngine.resetTimer()
            BeepPlayer.beep(450.0, 0.06, BeepPlayer.Wave.SINE)
        }
    }

    // ================= REFRESH UI =================

    private fun refreshPopupUI() {
        val view = popupView ?: return

        val pickerMin = view.findViewById<WheelPicker>(R.id.pickerMinute)
        val pickerSec = view.findViewById<WheelPicker>(R.id.pickerSecond)
        if (pickerMin.getValue() != TimerStopwatchEngine.timerMinutes) pickerMin.setValue(TimerStopwatchEngine.timerMinutes)
        if (pickerSec.getValue() != TimerStopwatchEngine.timerSeconds) pickerSec.setValue(TimerStopwatchEngine.timerSeconds)

        val primaryText = view.findViewById<TextView>(R.id.primaryTimerText)
        val primaryIcon = view.findViewById<ImageView>(R.id.primaryTimerIcon)
        primaryText.text = if (TimerStopwatchEngine.timerIsRunning) "JEDA" else "MULAI"
        primaryIcon.setImageResource(if (TimerStopwatchEngine.timerIsRunning) R.drawable.ic_pause else R.drawable.ic_play)

        pickerMin.isEnabled = !TimerStopwatchEngine.timerIsRunning
        pickerSec.isEnabled = !TimerStopwatchEngine.timerIsRunning
        pickerMin.alpha = if (TimerStopwatchEngine.timerIsRunning) 0.85f else 1f
        pickerSec.alpha = if (TimerStopwatchEngine.timerIsRunning) 0.85f else 1f
    }

    // ================= ALARM HEADS-UP =================

    fun showAlarm(context: Context) {
        if (alarmView != null) return
        if (!Settings.canDrawOverlays(context)) return

        val appContext = context.applicationContext
        val inflater = LayoutInflater.from(appContext)
        val view = inflater.inflate(R.layout.alarm_headsup, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        params.y = 60

        view.findViewById<View>(R.id.btnDismissAlarm).setOnClickListener {
            AlarmSoundPlayer.stop()
            hideAlarm(appContext)
        }

        wm(appContext).addView(view, params)
        alarmView = view
    }

    fun hideAlarm(context: Context) {
        val view = alarmView ?: return
        try {
            wm(context.applicationContext).removeView(view)
        } catch (e: Exception) {
            // sudah tidak terpasang, aman diabaikan
        }
        alarmView = null
    }

    // ================= LISTENER DARI ENGINE =================

    override fun onTimerTick() {
        val view = popupView ?: return
        val pickerMin = view.findViewById<WheelPicker>(R.id.pickerMinute)
        val pickerSec = view.findViewById<WheelPicker>(R.id.pickerSecond)
        if (pickerMin.getValue() != TimerStopwatchEngine.timerMinutes) pickerMin.setValue(TimerStopwatchEngine.timerMinutes)
        if (pickerSec.getValue() != TimerStopwatchEngine.timerSeconds) pickerSec.setValue(TimerStopwatchEngine.timerSeconds)
    }

    override fun onTimerFinished() {
        val view = popupView
        // 3 beep nada naik saat timer selesai
        BeepPlayer.beep(880.0, 0.15, BeepPlayer.Wave.SINE)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            BeepPlayer.beep(880.0, 0.15, BeepPlayer.Wave.SINE)
        }, 200)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            BeepPlayer.beep(1174.0, 0.35, BeepPlayer.Wave.SINE)
        }, 420)

        view?.let {
            it.findViewById<TextView>(R.id.primaryTimerText).text = "MULAI"
            it.findViewById<ImageView>(R.id.primaryTimerIcon).setImageResource(R.drawable.ic_play)
        }
        // Timer mencapai 00:00 -> tampilkan alarm heads-up, lalu kembalikan durasi ke nilai awal
        val context = view?.context ?: appContextRef
        context?.let {
            AlarmSoundPlayer.play(it)
            showAlarm(it)
        }
        TimerStopwatchEngine.resetTimer()
    }

    // Menyimpan referensi application context supaya alarm tetap bisa dipicu
    // walau popup sedang tidak terbuka.
    var appContextRef: Context? = null

    override fun onTimerStateChanged() {
        refreshPopupUI()
    }
}
