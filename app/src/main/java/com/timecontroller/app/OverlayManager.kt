package com.timecontroller.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.TextView

/**
 * Mengelola dua jenis overlay window yang tampil di atas aplikasi lain:
 * 1. Popup pengaturan (Timer/Stopwatch) - dibuka dari tombol di notifikasi
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

    // ================= POPUP PENGATURAN =================

    fun showPopup(context: Context) {
        if (!Settings.canDrawOverlays(context)) return
        if (popupView != null) return

        val appContext = context.applicationContext
        val inflater = LayoutInflater.from(appContext)
        val view = inflater.inflate(R.layout.popup_overlay, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.x = 0
        params.y = 0

        setupPopupInteractions(appContext, view, params)

        wm(appContext).addView(view, params)
        popupView = view
        refreshPopupUI()
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

    private fun setupPopupInteractions(context: Context, view: View, params: WindowManager.LayoutParams) {
        // Supaya popup bisa menerima sentuhan (perlu FLAG_NOT_FOCUSABLE dilepas saat interaksi teks,
        // tapi untuk tombol biasa cukup begini karena kita pakai FLAG_NOT_FOCUSABLE agar tidak
        // mencuri fokus dari aplikasi lain di belakangnya)
        view.findViewById<View>(R.id.btnClosePopup).setOnClickListener {
            hidePopup(context)
        }

        val tabTimer = view.findViewById<TextView>(R.id.tabTimerBtn)
        val tabStopwatch = view.findViewById<TextView>(R.id.tabStopwatchBtn)
        val timerSection = view.findViewById<View>(R.id.timerSection)
        val stopwatchSection = view.findViewById<View>(R.id.stopwatchSection)

        tabTimer.setOnClickListener {
            tabTimer.setBackgroundResource(R.drawable.bg_tab_active)
            tabTimer.setTextColor(context.getColor(R.color.text_white))
            tabStopwatch.background = null
            tabStopwatch.setTextColor(context.getColor(R.color.text_gray))
            timerSection.visibility = View.VISIBLE
            stopwatchSection.visibility = View.GONE
        }
        tabStopwatch.setOnClickListener {
            tabStopwatch.setBackgroundResource(R.drawable.bg_tab_active)
            tabStopwatch.setTextColor(context.getColor(R.color.text_white))
            tabTimer.background = null
            tabTimer.setTextColor(context.getColor(R.color.text_gray))
            stopwatchSection.visibility = View.VISIBLE
            timerSection.visibility = View.GONE
        }

        // ===== Timer main view =====
        val timerMainView = view.findViewById<View>(R.id.timerMainView)
        val timerPresetView = view.findViewById<View>(R.id.timerPresetView)

        view.findViewById<View>(R.id.boxMinute).setOnClickListener {
            TimerStopwatchEngine.adjustTimerMinute(1)
        }
        view.findViewById<View>(R.id.boxSecond).setOnClickListener {
            TimerStopwatchEngine.adjustTimerSecond(5)
        }
        view.findViewById<View>(R.id.primaryTimerBtn).setOnClickListener {
            TimerStopwatchEngine.toggleTimer()
        }
        view.findViewById<View>(R.id.resetTimerBtn).setOnClickListener {
            TimerStopwatchEngine.resetTimer()
        }
        view.findViewById<View>(R.id.btnOpenPreset).setOnClickListener {
            timerMainView.visibility = View.GONE
            timerPresetView.visibility = View.VISIBLE
        }
        view.findViewById<View>(R.id.btnBackFromPreset).setOnClickListener {
            timerPresetView.visibility = View.GONE
            timerMainView.visibility = View.VISIBLE
        }

        buildPresetGrid(context, view)

        // ===== Stopwatch =====
        view.findViewById<View>(R.id.swStartBtn).setOnClickListener {
            TimerStopwatchEngine.toggleStopwatch()
        }
        view.findViewById<View>(R.id.swLapBtn).setOnClickListener {
            TimerStopwatchEngine.recordLap()
        }
        view.findViewById<View>(R.id.swResetBtn).setOnClickListener {
            TimerStopwatchEngine.resetStopwatch()
        }
    }

    private val presets = listOf(1 to 0, 3 to 0, 5 to 0, 10 to 0, 15 to 0, 20 to 0, 25 to 0, 30 to 0)

    private fun buildPresetGrid(context: Context, view: View) {
        val grid = view.findViewById<GridLayout>(R.id.presetGrid)
        grid.removeAllViews()
        val inflater = LayoutInflater.from(context)
        for ((min, sec) in presets) {
            val btn = inflater.inflate(R.layout.item_preset, grid, false) as TextView
            val label = "${TimerStopwatchEngine.formatTwoDigits(min)}:${TimerStopwatchEngine.formatTwoDigits(sec)}"
            btn.text = label
            val isActive = TimerStopwatchEngine.timerInitialTotalSec == (min * 60 + sec)
            btn.setBackgroundResource(if (isActive) R.drawable.bg_preset_btn_active else R.drawable.bg_preset_btn)
            btn.setOnClickListener {
                TimerStopwatchEngine.setTimerDuration(min, sec)
                view.findViewById<View>(R.id.timerPresetView).visibility = View.GONE
                view.findViewById<View>(R.id.timerMainView).visibility = View.VISIBLE
                refreshPopupUI()
            }
            grid.addView(btn)
        }
    }

    private fun refreshPopupUI() {
        val view = popupView ?: return
        view.findViewById<TextView>(R.id.minuteDisplay).text =
            TimerStopwatchEngine.formatTwoDigits(TimerStopwatchEngine.timerMinutes)
        view.findViewById<TextView>(R.id.secondDisplay).text =
            TimerStopwatchEngine.formatTwoDigits(TimerStopwatchEngine.timerSeconds)

        val primaryBtn = view.findViewById<TextView>(R.id.primaryTimerBtn)
        primaryBtn.text = if (TimerStopwatchEngine.timerIsRunning) "JEDA" else "MULAI"

        // Refresh preset highlight
        buildPresetGrid(view.context, view)

        // Stopwatch
        val elapsed = TimerStopwatchEngine.stopwatchElapsedMs
        val hours = elapsed / 3600000
        val swMinutes = view.findViewById<TextView>(R.id.swMinutes)
        val swSeconds = view.findViewById<TextView>(R.id.swSeconds)
        val swMillis = view.findViewById<TextView>(R.id.swMillis)

        if (hours >= 1) {
            val minutes = (elapsed % 3600000) / 60000
            val seconds = (elapsed % 60000) / 1000
            swMinutes.text = TimerStopwatchEngine.formatTwoDigits(hours.toInt())
            swSeconds.text = TimerStopwatchEngine.formatTwoDigits(minutes.toInt())
            swMillis.text = TimerStopwatchEngine.formatTwoDigits(seconds.toInt())
        } else {
            val minutes = elapsed / 60000
            val seconds = (elapsed % 60000) / 1000
            val millis = (elapsed % 1000) / 10
            swMinutes.text = TimerStopwatchEngine.formatTwoDigits(minutes.toInt())
            swSeconds.text = TimerStopwatchEngine.formatTwoDigits(seconds.toInt())
            swMillis.text = TimerStopwatchEngine.formatTwoDigits(millis.toInt())
        }

        val swStartBtn = view.findViewById<TextView>(R.id.swStartBtn)
        swStartBtn.text = if (TimerStopwatchEngine.stopwatchIsRunning) "JEDA" else "MULAI"

        val swLapBtn = view.findViewById<TextView>(R.id.swLapBtn)
        swLapBtn.alpha = if (TimerStopwatchEngine.stopwatchIsRunning) 1.0f else 0.4f

        view.findViewById<TextView>(R.id.lapCountBadge).text =
            "${TimerStopwatchEngine.stopwatchLapCount} Lap"
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
        view.findViewById<TextView>(R.id.minuteDisplay).text =
            TimerStopwatchEngine.formatTwoDigits(TimerStopwatchEngine.timerMinutes)
        view.findViewById<TextView>(R.id.secondDisplay).text =
            TimerStopwatchEngine.formatTwoDigits(TimerStopwatchEngine.timerSeconds)
    }

    override fun onTimerFinished() {
        val view = popupView
        view?.let {
            it.findViewById<TextView>(R.id.primaryTimerBtn).text = "MULAI"
        }
        // Timer mencapai 00:00 -> bunyikan alarm & tampilkan heads-up,
        // lalu kembalikan durasi ke nilai awal (sesuai logic HTML timerCompleted()).
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

    override fun onStopwatchTick() {
        refreshPopupUI()
    }

    override fun onStopwatchStateChanged() {
        refreshPopupUI()
    }
}
