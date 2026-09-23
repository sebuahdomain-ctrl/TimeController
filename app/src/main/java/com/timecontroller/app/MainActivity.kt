package com.timecontroller.app

import android.Manifest
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.DragEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var currentMode = TimeService.Mode.TIMER
    private var boundService: TimeService? = null
    private var isBound = false
    private var editMode = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as TimeService.LocalBinder
            boundService = localBinder.getService()
            isBound = true
            // Sinkronkan tab & mode yang lagi aktif di service saat pertama connect
            currentMode = boundService?.getCurrentMode() ?: TimeService.Mode.TIMER
            refreshTabsExternal?.invoke()
            boundService?.onUpdate = { runOnUiThread { refreshTimeDisplay() } }
            refreshTimeDisplay()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService?.onUpdate = null
            boundService = null
            isBound = false
        }
    }

    // Dipakai supaya callback bind bisa memicu refreshTabs() yang didefinisikan di onCreate()
    private var refreshTabsExternal: (() -> Unit)? = null

    private fun refreshTimeDisplay() {
        val service = boundService ?: return

        val mainTimeDisplay = findViewById<TextView>(R.id.mainTimeDisplay)
        val swTimeDisplay = findViewById<TextView>(R.id.swTimeDisplay)
        val timerSubLabel = findViewById<TextView>(R.id.timerSubLabel)
        val statusLabel = findViewById<TextView>(R.id.statusLabel)
        val ringView = findViewById<TimerRingView>(R.id.timerRingView)
        val dialView = findViewById<StopwatchDialView>(R.id.stopwatchDialView)
        val playPauseText = findViewById<TextView>(R.id.btnPlayPause)
        val swPrimaryText = findViewById<TextView>(R.id.btnSwPrimary)

        val runState = service.getCurrentRunState()
        val isRunning = runState == TimeService.RunState.RUNNING

        statusLabel.text = when (runState) {
            TimeService.RunState.RUNNING -> "Berjalan"
            TimeService.RunState.PAUSED -> "Jeda"
            TimeService.RunState.FINISHED -> "Selesai"
            TimeService.RunState.IDLE -> "Siap"
        }

        if (currentMode == TimeService.Mode.TIMER) {
            val remaining = service.getDisplayMillis()
            val total = service.getTimerTotalMillis().coerceAtLeast(1L)
            mainTimeDisplay.text = service.formatCurrentTime()
            timerSubLabel.text = "dari " + service.formatMillisPublic(total)
            ringView.progress = remaining.toFloat() / total.toFloat()
            playPauseText.text = if (isRunning) "\u2759\u2759" else "\u25B6"
        } else {
            val elapsedMillis = service.getDisplayMillis()
            swTimeDisplay.text = service.formatCurrentTime()
            dialView.elapsedSeconds = elapsedMillis / 1000f
            swPrimaryText.text = if (isRunning) "Jeda" else "Mulai"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()
        checkOverlayPermission()

        val tabTimer = findViewById<TextView>(R.id.tabTimer)
        val tabStopwatch = findViewById<TextView>(R.id.tabStopwatch)

        val presetSection = findViewById<LinearLayout>(R.id.presetSection)
        val stopwatchHint = findViewById<TextView>(R.id.stopwatchHint)
        val ringWrap = findViewById<FrameLayout>(R.id.ringWrap)
        val dialWrap = findViewById<FrameLayout>(R.id.dialWrap)
        val timerControls = findViewById<LinearLayout>(R.id.timerControls)
        val stopwatchControls = findViewById<LinearLayout>(R.id.stopwatchControls)
        val settingsPanel = findViewById<LinearLayout>(R.id.settingsPanel)

        fun refreshTabs() {
            tabTimer.setBackgroundResource(if (currentMode == TimeService.Mode.TIMER) R.drawable.bg_time_box else 0)
            tabTimer.setTextColor(ContextCompat.getColor(this, if (currentMode == TimeService.Mode.TIMER) R.color.text_primary else R.color.text_secondary))
            tabStopwatch.setBackgroundResource(if (currentMode == TimeService.Mode.STOPWATCH) R.drawable.bg_time_box else 0)
            tabStopwatch.setTextColor(ContextCompat.getColor(this, if (currentMode == TimeService.Mode.STOPWATCH) R.color.text_primary else R.color.text_secondary))

            val isTimer = currentMode == TimeService.Mode.TIMER
            ringWrap.visibility = if (isTimer) View.VISIBLE else View.GONE
            dialWrap.visibility = if (isTimer) View.GONE else View.VISIBLE
            timerControls.visibility = if (isTimer) View.VISIBLE else View.GONE
            stopwatchControls.visibility = if (isTimer) View.GONE else View.VISIBLE
            // Preset hanya relevan untuk mode Timer; sembunyikan otomatis saat Stopwatch aktif.
            presetSection.visibility = if (isTimer) View.VISIBLE else View.GONE
            stopwatchHint.visibility = if (isTimer) View.GONE else View.VISIBLE
            if (!isTimer) settingsPanel.visibility = View.GONE

            refreshTimeDisplay()
        }
        refreshTabs()
        refreshTabsExternal = { refreshTabs() }

        tabTimer.setOnClickListener {
            currentMode = TimeService.Mode.TIMER
            refreshTabs()
            val intent = Intent(this, TimeService::class.java)
            intent.action = TimeService.ACTION_SWITCH_MODE
            ContextCompat.startForegroundService(this, intent)
        }
        tabStopwatch.setOnClickListener {
            currentMode = TimeService.Mode.STOPWATCH
            refreshTabs()
            val intent = Intent(this, TimeService::class.java)
            intent.action = TimeService.ACTION_SWITCH_MODE
            ContextCompat.startForegroundService(this, intent)
        }

        findViewById<TextView>(R.id.btnPlayPause).setOnClickListener {
            val isRunning = boundService?.getCurrentRunState() == TimeService.RunState.RUNNING
            val intent = Intent(this, TimeService::class.java)
            intent.action = if (isRunning) {
                TimeService.ACTION_PAUSE
            } else if (boundService?.getCurrentRunState() == TimeService.RunState.PAUSED) {
                TimeService.ACTION_RESUME
            } else {
                TimeService.ACTION_START_TIMER
            }
            ContextCompat.startForegroundService(this, intent)
            refreshTimeDisplay()
        }

        findViewById<TextView>(R.id.btnReset).setOnClickListener {
            val intent = Intent(this, TimeService::class.java)
            intent.action = TimeService.ACTION_RESET
            ContextCompat.startForegroundService(this, intent)
            refreshTimeDisplay()
        }

        // Tombol utama Stopwatch: mulai / jeda
        findViewById<TextView>(R.id.btnSwPrimary).setOnClickListener {
            val isRunning = boundService?.getCurrentRunState() == TimeService.RunState.RUNNING
            val intent = Intent(this, TimeService::class.java)
            intent.action = if (isRunning) {
                TimeService.ACTION_PAUSE
            } else if (boundService?.getCurrentRunState() == TimeService.RunState.PAUSED) {
                TimeService.ACTION_RESUME
            } else {
                TimeService.ACTION_START_STOPWATCH
            }
            ContextCompat.startForegroundService(this, intent)
            refreshTimeDisplay()
        }

        // Tombol sekunder Stopwatch: reset
        findViewById<TextView>(R.id.btnSwSecondary).setOnClickListener {
            val intent = Intent(this, TimeService::class.java)
            intent.action = TimeService.ACTION_RESET
            ContextCompat.startForegroundService(this, intent)
            refreshTimeDisplay()
        }

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            settingsPanel.visibility = if (settingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        val editToggle = findViewById<TextView>(R.id.editToggle)
        editToggle.setOnClickListener {
            editMode = !editMode
            editToggle.text = if (editMode) "Selesai" else "Edit"
            editToggle.setTextColor(
                ContextCompat.getColor(this, if (editMode) R.color.text_primary else R.color.accent)
            )
            findViewById<TextView>(R.id.presetHint).visibility = if (editMode) View.VISIBLE else View.GONE
            loadPresetList()
        }

        findViewById<TextView>(R.id.btnAddPreset).setOnClickListener {
            openAddPresetForm()
        }
        findViewById<TextView>(R.id.btnAddCancel).setOnClickListener {
            closeAddPresetForm()
        }
        findViewById<TextView>(R.id.btnAddConfirm).setOnClickListener {
            confirmAddPreset()
        }
        setupTimeInputBox(findViewById(R.id.addHours))
        setupTimeInputBox(findViewById(R.id.addMinutes))
        setupTimeInputBox(findViewById(R.id.addSeconds))

        loadPresetList()
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, TimeService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            boundService?.onUpdate = null
            unbindService(connection)
            isBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        loadPresetList()
    }

    // ---------- Form tambah preset (3 kotak jam:menit:detik) ----------

    private fun openAddPresetForm() {
        findViewById<LinearLayout>(R.id.addPresetForm).visibility = View.VISIBLE
        findViewById<TextView>(R.id.addPresetError).text = ""
    }

    private fun closeAddPresetForm() {
        findViewById<LinearLayout>(R.id.addPresetForm).visibility = View.GONE
        findViewById<TextView>(R.id.addPresetError).text = ""
        findViewById<EditText>(R.id.addHours).setText("00")
        findViewById<EditText>(R.id.addMinutes).setText("00")
        findViewById<EditText>(R.id.addSeconds).setText("00")
    }

    private fun confirmAddPreset() {
        val h = findViewById<EditText>(R.id.addHours).text.toString().toIntOrNull() ?: 0
        val m = findViewById<EditText>(R.id.addMinutes).text.toString().toIntOrNull() ?: 0
        val s = findViewById<EditText>(R.id.addSeconds).text.toString().toIntOrNull() ?: 0
        val total = h * 3600 + m * 60 + s

        if (total <= 0) {
            findViewById<TextView>(R.id.addPresetError).text = "Atur waktu lebih dari 0 detik"
            return
        }
        PresetStore.addPreset(this, total)
        closeAddPresetForm()
        loadPresetList()
    }

    /** Menjaga input hanya angka dan maksimal 2 digit, meniru kotak jam/menit/detik di mockup. */
    private fun setupTimeInputBox(editText: EditText) {
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = editText.text.toString()
                editText.setText(
                    when {
                        value.isEmpty() -> "00"
                        value.length == 1 -> "0$value"
                        else -> value.take(2)
                    }
                )
            }
        }
    }

    // ---------- Daftar preset: render, mode edit, drag & drop reorder ----------

    private fun loadPresetList() {
        val container = findViewById<FlowRowLayout>(R.id.customPresetList)
        container.removeAllViews()
        val presets = PresetStore.getPresets(this)

        presets.forEach { preset ->
            val itemRoot = layoutInflater.inflate(R.layout.item_preset_circle, container, false)
            val label = itemRoot.findViewById<TextView>(R.id.presetCircleLabel)
            val deleteBadge = itemRoot.findViewById<TextView>(R.id.presetDeleteBadge)

            label.text = preset.label()
            deleteBadge.visibility = if (editMode) View.VISIBLE else View.GONE
            itemRoot.tag = preset.totalSeconds

            // Tap lingkaran (bukan mode edit) langsung pakai preset itu untuk mulai timer.
            label.setOnClickListener {
                if (editMode) return@setOnClickListener
                currentMode = TimeService.Mode.TIMER
                refreshTabsExternal?.invoke()
                val intent = Intent(this, TimeService::class.java)
                intent.action = TimeService.ACTION_START_TIMER
                intent.putExtra(TimeService.EXTRA_MINUTES, preset.totalSeconds / 60)
                intent.putExtra(TimeService.EXTRA_SECONDS, preset.totalSeconds % 60)
                ContextCompat.startForegroundService(this, intent)
                refreshTimeDisplay()
            }

            // Tekan-tahan untuk mulai drag & drop reorder (hanya aktif di mode edit).
            label.setOnLongClickListener { view ->
                if (!editMode) return@setOnLongClickListener false
                val clipData = ClipData.newPlainText("", "")
                val shadow = View.DragShadowBuilder(itemRoot)
                view.startDragAndDrop(clipData, shadow, itemRoot, 0)
                itemRoot.alpha = 0.4f
                true
            }

            deleteBadge.setOnClickListener {
                PresetStore.removePreset(this, preset.totalSeconds)
                loadPresetList()
            }

            // Target drop: saat item lain dilepas di atasnya, tukar posisi keduanya.
            itemRoot.setOnDragListener { targetView, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DROP -> {
                        val draggedView = event.localState as? View
                        if (draggedView != null && draggedView !== targetView) {
                            val draggedSeconds = draggedView.tag as? Int
                            val targetSeconds = targetView.tag as? Int
                            if (draggedSeconds != null && targetSeconds != null) {
                                swapPresetOrder(draggedSeconds, targetSeconds)
                            }
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        (event.localState as? View)?.alpha = 1f
                        true
                    }
                    else -> true
                }
            }

            container.addView(itemRoot)
        }
    }

    /** Menukar posisi dua preset (berdasarkan durasi detiknya) di daftar, lalu simpan urutan baru. */
    private fun swapPresetOrder(secondsA: Int, secondsB: Int) {
        val current = PresetStore.getPresets(this).map { it.totalSeconds }.toMutableList()
        val indexA = current.indexOf(secondsA)
        val indexB = current.indexOf(secondsB)
        if (indexA == -1 || indexB == -1) return
        current[indexA] = secondsB
        current[indexB] = secondsA
        PresetStore.reorderPresets(this, current)
        loadPresetList()
    }

    /**
     * Popup pengaturan butuh izin "Tampil di atas aplikasi lain" (SYSTEM_ALERT_WINDOW)
     * supaya bisa benar-benar mengambang di atas aplikasi apa pun. Android mewajibkan
     * izin ini diaktifkan manual lewat halaman Settings khusus, tidak bisa lewat
     * dialog izin biasa.
     */
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Aktifkan izin \"Tampil di atas aplikasi lain\" agar popup pengaturan bisa mengambang",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
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
