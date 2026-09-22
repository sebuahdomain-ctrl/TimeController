package com.timecontroller.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Menampilkan popup pengaturan sebagai overlay window sungguhan (TYPE_APPLICATION_OVERLAY),
 * bukan Activity biasa. Ini yang membuatnya benar-benar mengambang di atas aplikasi
 * apa pun tanpa memindahkan aplikasi lain itu ke background.
 *
 * Membutuhkan izin SYSTEM_ALERT_WINDOW yang harus disetujui user secara manual
 * lewat Settings (kebijakan Android, tidak bisa diminta langsung seperti izin biasa).
 */
class OverlayPopupService : Service() {

    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private var selectedMode = TimeService.Mode.TIMER

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (popupView != null) return // sudah tampil, jangan dobel

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.activity_settings_popup, null)
        popupView = view

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        setupPopupContent(view)

        windowManager?.addView(view, params)
    }

    private fun removeOverlay() {
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: IllegalArgumentException) {
                // view sudah tidak terpasang, aman diabaikan
            }
        }
        popupView = null
    }

    private fun setupPopupContent(root: View) {
        // Tap di luar kartu (area scrim) menutup overlay
        root.setOnClickListener { removeOverlay(); stopSelf() }

        val pageMain = root.findViewById<ViewGroup>(R.id.pageMain)
        val pagePreset = root.findViewById<ViewGroup>(R.id.pagePreset)
        val popupCard = root.findViewById<ViewGroup>(R.id.popupCard)

        val tabTimer = root.findViewById<TextView>(R.id.popupTabTimer)
        val tabStopwatch = root.findViewById<TextView>(R.id.popupTabStopwatch)
        val inputMinutes = root.findViewById<android.widget.EditText>(R.id.inputMinutes)
        val inputSeconds = root.findViewById<android.widget.EditText>(R.id.inputSeconds)

        // Cegah tap di mana pun dalam kartu (termasuk area padding) ikut
        // menembus ke listener root, yang akan menutup popup secara tidak sengaja.
        popupCard.setOnClickListener { /* konsumsi tap, jangan diteruskan */ }

        fun refreshTabs() {
            tabTimer.setBackgroundResource(if (selectedMode == TimeService.Mode.TIMER) R.drawable.bg_time_box else 0)
            tabStopwatch.setBackgroundResource(if (selectedMode == TimeService.Mode.STOPWATCH) R.drawable.bg_time_box else 0)
        }
        refreshTabs()

        tabTimer.setOnClickListener {
            selectedMode = TimeService.Mode.TIMER
            refreshTabs()
        }
        tabStopwatch.setOnClickListener {
            selectedMode = TimeService.Mode.STOPWATCH
            refreshTabs()
        }

        root.findViewById<TextView>(R.id.btnOpenPresets).setOnClickListener {
            populatePresetGrid(root)
            pageMain.visibility = ViewGroup.GONE
            pagePreset.visibility = ViewGroup.VISIBLE
        }

        root.findViewById<TextView>(R.id.btnBackToMain).setOnClickListener {
            pagePreset.visibility = ViewGroup.GONE
            pageMain.visibility = ViewGroup.VISIBLE
        }

        root.findViewById<TextView>(R.id.btnClosePopup).setOnClickListener {
            removeOverlay()
            stopSelf()
        }

        root.findViewById<android.widget.Button>(R.id.btnStart).setOnClickListener {
            val intent = Intent(this, TimeService::class.java)
            if (selectedMode == TimeService.Mode.TIMER) {
                val minutes = inputMinutes.text.toString().toIntOrNull() ?: 0
                val seconds = inputSeconds.text.toString().toIntOrNull() ?: 0
                intent.action = TimeService.ACTION_START_TIMER
                intent.putExtra(TimeService.EXTRA_MINUTES, minutes)
                intent.putExtra(TimeService.EXTRA_SECONDS, seconds)
            } else {
                intent.action = TimeService.ACTION_START_STOPWATCH
            }
            ContextCompat.startForegroundService(this, intent)
            removeOverlay()
            stopSelf()
        }

        populatePresetGrid(root)
    }

    private fun populatePresetGrid(root: View) {
        val grid = root.findViewById<GridLayout>(R.id.presetGrid)
        grid.removeAllViews()
        val presets = PresetStore.getPresets(this)
        val inflater = LayoutInflater.from(this)

        presets.forEach { preset ->
            val circle = inflater.inflate(R.layout.item_preset_circle, grid, false) as TextView
            circle.text = preset.label()
            circle.setOnClickListener {
                val minutes = preset.totalSeconds / 60
                val seconds = preset.totalSeconds % 60
                root.findViewById<android.widget.EditText>(R.id.inputMinutes).setText(String.format("%02d", minutes))
                root.findViewById<android.widget.EditText>(R.id.inputSeconds).setText(String.format("%02d", seconds))
                selectedMode = TimeService.Mode.TIMER
                root.findViewById<ViewGroup>(R.id.pagePreset).visibility = ViewGroup.GONE
                root.findViewById<ViewGroup>(R.id.pageMain).visibility = ViewGroup.VISIBLE
                root.findViewById<TextView>(R.id.popupTabTimer).setBackgroundResource(R.drawable.bg_time_box)
                root.findViewById<TextView>(R.id.popupTabStopwatch).setBackgroundResource(0)
            }
            grid.addView(circle)
        }
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CLOSE = "com.timecontroller.app.OVERLAY_CLOSE"
    }
}
