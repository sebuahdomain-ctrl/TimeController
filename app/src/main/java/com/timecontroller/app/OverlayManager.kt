package com.timecontroller.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.TextView

/**
 * Mengelola dua jenis overlay window yang tampil di atas aplikasi lain:
 * 1. Popup pengaturan (Timer/Stopwatch) - dibuka dari tombol di notifikasi
 * 2. Alarm heads-up - muncul otomatis saat timer selesai, mirip alarm bawaan Android
 *
 * Semua interaksi, urutan tampilan, dan efek suara di sini meniru persis
 * logic pada desain HTML popup terbaru (termasuk preset custom, mode hapus
 * preset, dan view tambah preset baru).
 */
object OverlayManager : TimerStopwatchEngine.Listener {

    private var popupView: View? = null
    private var alarmView: View? = null
    private var windowManager: WindowManager? = null

    // Sub-state navigasi tab Timer (main / preset / tambah-preset), independen dari engine
    private var inPresetView = false
    private var inAddPresetView = false
    private var newPresetMin = 0
    private var newPresetSec = 0
    private var activeTab = "timer" // "timer" | "stopwatch"

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

        // Reset navigasi sub-view setiap kali popup dibuka ulang
        inPresetView = false
        inAddPresetView = false
        activeTab = "timer"

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

    // ================= SETUP NUMBERPICKER (WHEEL) =================

    private fun configureWheel(picker: NumberPicker) {
        picker.minValue = 0
        picker.maxValue = 59
        picker.setFormatter { v -> TimerStopwatchEngine.formatTwoDigits(v) }
        picker.wrapSelectorWheel = true
        picker.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun setupPickers(view: View) {
        val pickerMin = view.findViewById<NumberPicker>(R.id.pickerMinute)
        val pickerSec = view.findViewById<NumberPicker>(R.id.pickerSecond)
        val pickerNewMin = view.findViewById<NumberPicker>(R.id.pickerNewPresetMinute)
        val pickerNewSec = view.findViewById<NumberPicker>(R.id.pickerNewPresetSecond)

        listOf(pickerMin, pickerSec, pickerNewMin, pickerNewSec).forEach { configureWheel(it) }

        pickerMin.value = TimerStopwatchEngine.timerMinutes
        pickerSec.value = TimerStopwatchEngine.timerSeconds

        // Sesuai logic onWheelScrolled('min'/'sec'): update state + beep nada pendek saat berhenti scroll
        pickerMin.setOnValueChangedListener { _, _, newVal ->
            if (TimerStopwatchEngine.timerIsRunning) return@setOnValueChangedListener
            TimerStopwatchEngine.setWheelMinute(newVal)
            BeepPlayer.beep(950.0, 0.02, BeepPlayer.Wave.TRIANGLE)
        }
        pickerSec.setOnValueChangedListener { _, _, newVal ->
            if (TimerStopwatchEngine.timerIsRunning) return@setOnValueChangedListener
            TimerStopwatchEngine.setWheelSecond(newVal)
            BeepPlayer.beep(1150.0, 0.02, BeepPlayer.Wave.TRIANGLE)
        }

        pickerNewMin.setOnValueChangedListener { _, _, newVal ->
            newPresetMin = newVal
            BeepPlayer.beep(950.0, 0.02, BeepPlayer.Wave.TRIANGLE)
            updateNewPresetBadge(view)
        }
        pickerNewSec.setOnValueChangedListener { _, _, newVal ->
            newPresetSec = newVal
            BeepPlayer.beep(1150.0, 0.02, BeepPlayer.Wave.TRIANGLE)
            updateNewPresetBadge(view)
        }
    }

    private fun updateNewPresetBadge(view: View) {
        view.findViewById<TextView>(R.id.newPresetTimeBadge).text =
            "${TimerStopwatchEngine.formatTwoDigits(newPresetMin)}:${TimerStopwatchEngine.formatTwoDigits(newPresetSec)}"
    }

    // ================= INTERAKSI TOMBOL =================

    private fun setupPopupInteractions(context: Context, view: View) {
        view.findViewById<View>(R.id.btnClosePopup).setOnClickListener {
            BeepPlayer.beep(400.0, 0.05, BeepPlayer.Wave.SINE)
            hidePopup(context)
        }

        val tabTimer = view.findViewById<TextView>(R.id.tabTimerBtn)
        val tabStopwatch = view.findViewById<TextView>(R.id.tabStopwatchBtn)

        tabTimer.setOnClickListener { switchTab(context, view, "timer") }
        tabStopwatch.setOnClickListener { switchTab(context, view, "stopwatch") }

        // ===== Timer main view =====
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
        view.findViewById<View>(R.id.btnOpenPreset).setOnClickListener {
            openPresetView(view)
        }
        view.findViewById<View>(R.id.btnBackFromPreset).setOnClickListener {
            closePresetView(view, silent = false)
        }
        view.findViewById<View>(R.id.btnToggleDeletePreset).setOnClickListener {
            TimerStopwatchEngine.togglePresetDeleteMode()
            BeepPlayer.beep(
                if (TimerStopwatchEngine.isPresetDeleteMode) 850.0 else 650.0,
                0.04,
                BeepPlayer.Wave.SINE
            )
        }

        // ===== Tambah preset =====
        view.findViewById<View>(R.id.btnCancelAddPreset).setOnClickListener {
            closeAddPresetView(view)
        }
        view.findViewById<View>(R.id.btnSaveNewPreset).setOnClickListener {
            saveNewPreset(view)
        }

        // ===== Stopwatch =====
        view.findViewById<View>(R.id.swStartBtn).setOnClickListener {
            val wasRunning = TimerStopwatchEngine.stopwatchIsRunning
            TimerStopwatchEngine.toggleStopwatch()
            BeepPlayer.beep(if (!wasRunning) 950.0 else 650.0, 0.05, BeepPlayer.Wave.SINE)
        }
        view.findViewById<View>(R.id.swLapBtn).setOnClickListener {
            if (!TimerStopwatchEngine.stopwatchIsRunning) return@setOnClickListener
            TimerStopwatchEngine.recordLap()
            BeepPlayer.beep(1100.0, 0.03, BeepPlayer.Wave.SINE)
        }
        view.findViewById<View>(R.id.swResetBtn).setOnClickListener {
            TimerStopwatchEngine.resetStopwatch()
            BeepPlayer.beep(450.0, 0.06, BeepPlayer.Wave.SINE)
        }
        view.findViewById<View>(R.id.swFastForwardTap).setOnClickListener {
            TimerStopwatchEngine.testFastForwardStopwatch()
            BeepPlayer.beep(900.0, 0.04, BeepPlayer.Wave.SINE)
        }

        buildPresetGrid(context, view)
    }

    // ================= NAVIGASI TIMER: MAIN / PRESET / TAMBAH PRESET =================

    private fun openPresetView(view: View) {
        inPresetView = true
        if (TimerStopwatchEngine.isPresetDeleteMode) TimerStopwatchEngine.togglePresetDeleteMode()
        buildPresetGrid(view.context, view)
        view.findViewById<View>(R.id.timerMainView).visibility = View.GONE
        view.findViewById<View>(R.id.timerPresetView).visibility = View.VISIBLE
        BeepPlayer.beep(700.0, 0.03, BeepPlayer.Wave.SINE)
    }

    private fun closePresetView(view: View, silent: Boolean) {
        inPresetView = false
        if (TimerStopwatchEngine.isPresetDeleteMode) TimerStopwatchEngine.togglePresetDeleteMode()
        view.findViewById<View>(R.id.timerPresetView).visibility = View.GONE
        view.findViewById<View>(R.id.timerMainView).visibility = View.VISIBLE
        view.findViewById<NumberPicker>(R.id.pickerMinute).value = TimerStopwatchEngine.timerMinutes
        view.findViewById<NumberPicker>(R.id.pickerSecond).value = TimerStopwatchEngine.timerSeconds
        if (!silent) BeepPlayer.beep(550.0, 0.03, BeepPlayer.Wave.SINE)
    }

    private fun openAddPresetView(view: View) {
        inAddPresetView = true
        newPresetMin = 0
        newPresetSec = 0
        view.findViewById<NumberPicker>(R.id.pickerNewPresetMinute).value = 0
        view.findViewById<NumberPicker>(R.id.pickerNewPresetSecond).value = 0
        updateNewPresetBadge(view)

        view.findViewById<View>(R.id.timerPresetView).visibility = View.GONE
        view.findViewById<View>(R.id.timerAddPresetView).visibility = View.VISIBLE
        BeepPlayer.beep(750.0, 0.03, BeepPlayer.Wave.SINE)
    }

    private fun closeAddPresetView(view: View) {
        inAddPresetView = false
        view.findViewById<View>(R.id.timerAddPresetView).visibility = View.GONE
        view.findViewById<View>(R.id.timerPresetView).visibility = View.VISIBLE
        BeepPlayer.beep(550.0, 0.03, BeepPlayer.Wave.SINE)
    }

    private fun saveNewPreset(view: View) {
        if (TimerStopwatchEngine.presets.size >= 8) {
            closeAddPresetView(view)
            return
        }
        if (newPresetMin == 0 && newPresetSec == 0) {
            BeepPlayer.beep(300.0, 0.15, BeepPlayer.Wave.SAWTOOTH)
            val badge = view.findViewById<TextView>(R.id.newPresetTimeBadge)
            badge.setTextColor(android.graphics.Color.parseColor("#FCA5A5"))
            badge.postDelayed({
                badge.setTextColor(view.context.getColor(R.color.text_white))
            }, 400)
            return
        }
        val exists = TimerStopwatchEngine.presets.any { it.first == newPresetMin && it.second == newPresetSec }
        if (exists) {
            BeepPlayer.beep(400.0, 0.1, BeepPlayer.Wave.SAWTOOTH)
            closeAddPresetView(view)
            return
        }
        TimerStopwatchEngine.addPreset(newPresetMin, newPresetSec)
        closeAddPresetView(view)
        buildPresetGrid(view.context, view)
        BeepPlayer.beep(1000.0, 0.06, BeepPlayer.Wave.SINE)
    }

    private fun applyPreset(view: View, min: Int, sec: Int) {
        if (TimerStopwatchEngine.isPresetDeleteMode) return
        TimerStopwatchEngine.applyPreset(min, sec)
        closePresetView(view, silent = true)
        BeepPlayer.beep(900.0, 0.04, BeepPlayer.Wave.SINE)
    }

    // ================= TAB SWITCH =================

    private fun switchTab(context: Context, view: View, tabName: String) {
        activeTab = tabName
        val tabTimer = view.findViewById<TextView>(R.id.tabTimerBtn)
        val tabStopwatch = view.findViewById<TextView>(R.id.tabStopwatchBtn)
        val timerMainView = view.findViewById<View>(R.id.timerMainView)
        val timerPresetView = view.findViewById<View>(R.id.timerPresetView)
        val timerAddPresetView = view.findViewById<View>(R.id.timerAddPresetView)
        val stopwatchSection = view.findViewById<View>(R.id.stopwatchSection)

        if (tabName == "timer") {
            tabTimer.setBackgroundResource(R.drawable.bg_tab_active)
            tabTimer.setTextColor(context.getColor(R.color.text_white))
            tabStopwatch.background = null
            tabStopwatch.setTextColor(context.getColor(R.color.text_gray))
            stopwatchSection.visibility = View.GONE

            when {
                inAddPresetView -> {
                    timerAddPresetView.visibility = View.VISIBLE
                    timerPresetView.visibility = View.GONE
                    timerMainView.visibility = View.GONE
                }
                inPresetView -> {
                    timerPresetView.visibility = View.VISIBLE
                    timerMainView.visibility = View.GONE
                    timerAddPresetView.visibility = View.GONE
                }
                else -> {
                    timerMainView.visibility = View.VISIBLE
                    timerPresetView.visibility = View.GONE
                    timerAddPresetView.visibility = View.GONE
                }
            }
        } else {
            tabStopwatch.setBackgroundResource(R.drawable.bg_tab_active)
            tabStopwatch.setTextColor(context.getColor(R.color.text_white))
            tabTimer.background = null
            tabTimer.setTextColor(context.getColor(R.color.text_gray))
            stopwatchSection.visibility = View.VISIBLE
            timerMainView.visibility = View.GONE
            timerPresetView.visibility = View.GONE
            timerAddPresetView.visibility = View.GONE
        }
        BeepPlayer.beep(700.0, 0.03, BeepPlayer.Wave.SINE)
    }

    // ================= GRID PRESET =================

    private fun buildPresetGrid(context: Context, view: View) {
        val grid = view.findViewById<GridLayout>(R.id.presetGrid)
        grid.removeAllViews()
        val inflater = LayoutInflater.from(context)
        val presets = TimerStopwatchEngine.presets
        val deleteMode = TimerStopwatchEngine.isPresetDeleteMode

        if (presets.isEmpty() && deleteMode) {
            val empty = inflater.inflate(R.layout.preset_empty_state, grid, false)
            val emptyParams = GridLayout.LayoutParams()
            emptyParams.width = 0
            emptyParams.height = GridLayout.LayoutParams.MATCH_PARENT
            emptyParams.columnSpec = GridLayout.spec(0, 4, 1f)
            emptyParams.rowSpec = GridLayout.spec(0, 2, 1f)
            empty.layoutParams = emptyParams
            empty.findViewById<View>(R.id.btnRestoreDefaultPresets).setOnClickListener {
                TimerStopwatchEngine.restoreDefaultPresets()
                buildPresetGrid(context, view)
                BeepPlayer.beep(800.0, 0.05, BeepPlayer.Wave.SINE)
            }
            grid.addView(empty)
            return
        }

        presets.forEachIndexed { index, (min, sec) ->
            val item = inflater.inflate(R.layout.item_preset, grid, false) as FrameLayout
            val label = item.findViewById<TextView>(R.id.presetLabel)
            val badge = item.findViewById<TextView>(R.id.presetDeleteBadge)
            label.text = "${TimerStopwatchEngine.formatTwoDigits(min)}:${TimerStopwatchEngine.formatTwoDigits(sec)}"

            val isCurrent = TimerStopwatchEngine.timerMinutes == min && TimerStopwatchEngine.timerSeconds == sec
            label.setBackgroundResource(
                when {
                    deleteMode -> R.drawable.bg_preset_btn_delete_mode
                    isCurrent -> R.drawable.bg_preset_btn_active
                    else -> R.drawable.bg_preset_btn
                }
            )
            badge.visibility = if (deleteMode) View.VISIBLE else View.GONE

            item.setOnClickListener {
                if (deleteMode) {
                    TimerStopwatchEngine.deletePreset(index)
                    buildPresetGrid(context, view)
                    BeepPlayer.beep(420.0, 0.08, BeepPlayer.Wave.SAWTOOTH)
                } else {
                    applyPreset(view, min, sec)
                    buildPresetGrid(context, view)
                }
            }

            grid.addView(item)
        }

        // Tombol tambah (+): hanya saat bukan mode hapus & preset < 8
        if (!deleteMode && presets.size < 8) {
            val addItem = inflater.inflate(R.layout.item_preset_add, grid, false)
            addItem.setOnClickListener { openAddPresetView(view) }
            grid.addView(addItem)
        }

        updateDeleteButtonUI(view)
    }

    private fun updateDeleteButtonUI(view: View) {
        val btn = view.findViewById<View>(R.id.btnToggleDeletePreset)
        val text = view.findViewById<TextView>(R.id.deletePresetBtnText)
        if (TimerStopwatchEngine.isPresetDeleteMode) {
            btn.setBackgroundResource(R.drawable.bg_toggle_delete_active)
            text.text = "Selesai"
            text.setTextColor(view.context.getColor(R.color.red_300))
        } else {
            btn.setBackgroundResource(R.drawable.bg_toggle_delete)
            text.text = "Hapus"
            text.setTextColor(view.context.getColor(R.color.text_gray))
        }
    }

    // ================= REFRESH UI PENUH =================

    private fun refreshPopupUI() {
        val view = popupView ?: return

        val pickerMin = view.findViewById<NumberPicker>(R.id.pickerMinute)
        val pickerSec = view.findViewById<NumberPicker>(R.id.pickerSecond)
        if (pickerMin.value != TimerStopwatchEngine.timerMinutes) pickerMin.value = TimerStopwatchEngine.timerMinutes
        if (pickerSec.value != TimerStopwatchEngine.timerSeconds) pickerSec.value = TimerStopwatchEngine.timerSeconds

        val primaryText = view.findViewById<TextView>(R.id.primaryTimerText)
        val primaryIcon = view.findViewById<ImageView>(R.id.primaryTimerIcon)
        primaryText.text = if (TimerStopwatchEngine.timerIsRunning) "JEDA" else "MULAI"
        primaryIcon.setImageResource(if (TimerStopwatchEngine.timerIsRunning) R.drawable.ic_pause else R.drawable.ic_play)

        pickerMin.isEnabled = !TimerStopwatchEngine.timerIsRunning
        pickerSec.isEnabled = !TimerStopwatchEngine.timerIsRunning
        pickerMin.alpha = if (TimerStopwatchEngine.timerIsRunning) 0.85f else 1f
        pickerSec.alpha = if (TimerStopwatchEngine.timerIsRunning) 0.85f else 1f

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

        val swStartText = view.findViewById<TextView>(R.id.swStartText)
        val swStartIcon = view.findViewById<ImageView>(R.id.swStartIcon)
        swStartText.text = if (TimerStopwatchEngine.stopwatchIsRunning) "JEDA" else "MULAI"
        swStartIcon.setImageResource(if (TimerStopwatchEngine.stopwatchIsRunning) R.drawable.ic_pause else R.drawable.ic_play)

        val swLapBtn = view.findViewById<TextView>(R.id.swLapBtn)
        swLapBtn.alpha = if (TimerStopwatchEngine.stopwatchIsRunning) 1.0f else 0.4f
        swLapBtn.isEnabled = TimerStopwatchEngine.stopwatchIsRunning

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
        val pickerMin = view.findViewById<NumberPicker>(R.id.pickerMinute)
        val pickerSec = view.findViewById<NumberPicker>(R.id.pickerSecond)
        if (pickerMin.value != TimerStopwatchEngine.timerMinutes) pickerMin.value = TimerStopwatchEngine.timerMinutes
        if (pickerSec.value != TimerStopwatchEngine.timerSeconds) pickerSec.value = TimerStopwatchEngine.timerSeconds
    }

    override fun onTimerFinished() {
        val view = popupView
        // Sesuai logic timerCompleted() di HTML: 3 beep nada naik (880, 880, 1174)
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

    override fun onStopwatchTick() {
        refreshPopupUI()
    }

    override fun onStopwatchStateChanged() {
        refreshPopupUI()
    }

    override fun onPresetsChanged() {
        val view = popupView ?: return
        buildPresetGrid(view.context, view)
    }
}
