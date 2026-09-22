package com.timecontroller.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var currentMode = TimeService.Mode.TIMER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()

        val tabTimer = findViewById<TextView>(R.id.tabTimer)
        val tabStopwatch = findViewById<TextView>(R.id.tabStopwatch)

        fun refreshTabs() {
            tabTimer.setBackgroundResource(if (currentMode == TimeService.Mode.TIMER) R.drawable.bg_time_box else 0)
            tabStopwatch.setBackgroundResource(if (currentMode == TimeService.Mode.STOPWATCH) R.drawable.bg_time_box else 0)
        }
        refreshTabs()

        tabTimer.setOnClickListener { currentMode = TimeService.Mode.TIMER; refreshTabs() }
        tabStopwatch.setOnClickListener { currentMode = TimeService.Mode.STOPWATCH; refreshTabs() }

        findViewById<android.widget.Button>(R.id.btnPlayPause).setOnClickListener {
            val intent = Intent(this, TimeService::class.java)
            intent.action = if (currentMode == TimeService.Mode.TIMER) {
                TimeService.ACTION_START_TIMER
            } else {
                TimeService.ACTION_START_STOPWATCH
            }
            ContextCompat.startForegroundService(this, intent)
        }

        findViewById<android.widget.Button>(R.id.btnReset).setOnClickListener {
            val intent = Intent(this, TimeService::class.java)
            intent.action = TimeService.ACTION_RESET
            ContextCompat.startForegroundService(this, intent)
        }

        findViewById<android.widget.Button>(R.id.btnAddPreset).setOnClickListener {
            showAddPresetDialog()
        }

        loadPresetList()
    }

    override fun onResume() {
        super.onResume()
        loadPresetList()
    }

    /** Menampilkan dialog kecil untuk membuat preset kustom baru (tetap di dalam app). */
    private fun showAddPresetDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 24, 48, 24)
        }
        val minutesInput = android.widget.EditText(this).apply {
            hint = "Menit"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val secondsInput = android.widget.EditText(this).apply {
            hint = "Detik"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        container.addView(minutesInput)
        container.addView(secondsInput)

        AlertDialog.Builder(this)
            .setTitle("Preset baru")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val minutes = minutesInput.text.toString().toIntOrNull() ?: 0
                val seconds = secondsInput.text.toString().toIntOrNull() ?: 0
                val total = minutes * 60 + seconds
                if (total > 0) {
                    PresetStore.addPreset(this, total)
                    loadPresetList()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun loadPresetList() {
        val container = findViewById<LinearLayout>(R.id.customPresetList)
        container.removeAllViews()
        val presets = PresetStore.getPresets(this)

        presets.forEach { preset ->
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_custom_preset_row, container, false)
            row.findViewById<TextView>(R.id.presetRowLabel).text = preset.label()
            row.findViewById<TextView>(R.id.presetRowDelete).setOnClickListener {
                PresetStore.removePreset(this, preset.totalSeconds)
                loadPresetList()
            }
            container.addView(row)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
                )
            }
        }
    }
}
