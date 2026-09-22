package com.timecontroller.app

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Popup transparan yang muncul di atas layar apa pun (trampoline activity),
 * dipicu dari tombol "Atur" di notifikasi. Dua halaman: utama & preset.
 */
class SettingsPopupActivity : AppCompatActivity() {

    private var selectedMode = TimeService.Mode.TIMER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_popup)

        val pageMain = findViewById<ViewGroup>(R.id.pageMain)
        val pagePreset = findViewById<ViewGroup>(R.id.pagePreset)

        val tabTimer = findViewById<TextView>(R.id.popupTabTimer)
        val tabStopwatch = findViewById<TextView>(R.id.popupTabStopwatch)
        val inputMinutes = findViewById<android.widget.EditText>(R.id.inputMinutes)
        val inputSeconds = findViewById<android.widget.EditText>(R.id.inputSeconds)

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

        // Buka halaman preset
        findViewById<TextView>(R.id.btnOpenPresets).setOnClickListener {
            populatePresetGrid()
            pageMain.visibility = ViewGroup.GONE
            pagePreset.visibility = ViewGroup.VISIBLE
        }

        // Kembali ke halaman utama
        findViewById<TextView>(R.id.btnBackToMain).setOnClickListener {
            pagePreset.visibility = ViewGroup.GONE
            pageMain.visibility = ViewGroup.VISIBLE
        }

        findViewById<TextView>(R.id.btnClosePopup).setOnClickListener { finish() }

        // Tombol Mulai: kirim perintah ke service lalu tutup popup
        findViewById<android.widget.Button>(R.id.btnStart).setOnClickListener {
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
            finish()
        }

        populatePresetGrid()
    }

    private fun populatePresetGrid() {
        val grid = findViewById<GridLayout>(R.id.presetGrid)
        grid.removeAllViews()
        val presets = PresetStore.getPresets(this)

        presets.forEach { preset ->
            val circle = layoutInflater.inflate(R.layout.item_preset_circle, grid, false) as TextView
            circle.text = preset.label()
            circle.setOnClickListener {
                val minutes = preset.totalSeconds / 60
                val seconds = preset.totalSeconds % 60
                findViewById<android.widget.EditText>(R.id.inputMinutes).setText(String.format("%02d", minutes))
                findViewById<android.widget.EditText>(R.id.inputSeconds).setText(String.format("%02d", seconds))
                selectedMode = TimeService.Mode.TIMER
                // Kembali ke halaman utama dengan durasi sudah terisi
                findViewById<ViewGroup>(R.id.pagePreset).visibility = ViewGroup.GONE
                findViewById<ViewGroup>(R.id.pageMain).visibility = ViewGroup.VISIBLE
                findViewById<TextView>(R.id.popupTabTimer).setBackgroundResource(R.drawable.bg_time_box)
                findViewById<TextView>(R.id.popupTabStopwatch).setBackgroundResource(0)
            }
            grid.addView(circle)
        }
    }
}
