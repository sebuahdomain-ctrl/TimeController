package com.example.timerapp

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var btnStart: TextView
    private lateinit var knob: KnobView
    private lateinit var statusDot: View
    private lateinit var stateNotif: TextView
    private lateinit var stateOverlay: TextView
    private lateinit var stateFullScreen: TextView
    private lateinit var stateBattery: TextView

    // Launcher untuk minta izin notifikasi (Android 13+)
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkOverlayThenStart()
        } else {
            Toast.makeText(this, "Izin notifikasi dibutuhkan supaya app bisa jalan", Toast.LENGTH_LONG).show()
        }
    }

    // Launcher untuk balik dari layar pengaturan izin overlay
    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            checkFullScreenIntentThenStart()
        } else {
            Toast.makeText(this, "Izin overlay belum diaktifkan, popup tidak akan muncul", Toast.LENGTH_LONG).show()
        }
    }

    // Launcher untuk balik dari layar pengaturan izin layar penuh (Android 14+)
    private val fullScreenSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!canUseFullScreenIntentCompat()) {
            Toast.makeText(
                this,
                "Izin layar penuh belum aktif, halaman Matikan tidak muncul saat layar mati",
                Toast.LENGTH_LONG
            ).show()
        }
        checkBatteryThenStart()
    }

    // Launcher untuk balik dari dialog pengecualian optimasi baterai
    private val batterySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startForegroundServiceTimer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Status bar & navigation bar menyatu dengan latar halaman
        window.statusBarColor = Color.parseColor("#0E0E10")
        window.navigationBarColor = Color.parseColor("#0E0E10")
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        txtStatus = findViewById(R.id.txtStatus)
        btnStart = findViewById(R.id.btnStart)
        knob = findViewById(R.id.knob)
        statusDot = findViewById(R.id.statusDot)
        stateNotif = findViewById(R.id.stateNotif)
        stateOverlay = findViewById(R.id.stateOverlay)
        stateFullScreen = findViewById(R.id.stateFullScreen)
        stateBattery = findViewById(R.id.stateBattery)

        val toggle = View.OnClickListener {
            if (TimerForegroundService.isRunning) {
                stopTimerService()
            } else {
                checkNotificationPermissionThenProceed()
            }
        }
        btnStart.setOnClickListener(toggle)
        knob.setOnClickListener(toggle)

        // Tap baris izin: langsung buka halaman pengaturannya
        findViewById<View>(R.id.rowNotif).setOnClickListener { openNotificationSettings() }
        findViewById<View>(R.id.rowOverlay).setOnClickListener { openOverlaySettings() }
        findViewById<View>(R.id.rowFullScreen).setOnClickListener { openFullScreenSettings() }
        findViewById<View>(R.id.rowBattery).setOnClickListener { openBatterySettings() }
    }

    override fun onResume() {
        super.onResume()
        // Sumber kebenaran status hidup/mati: flag statis di TimerForegroundService.
        // Berguna kalau service sempat mati sendiri (dibunuh sistem, dll) saat app
        // sedang di background.
        updateStatusUi()
    }

    private fun updateStatusUi() {
        renderActive(TimerForegroundService.isRunning)
        updatePermissionUi()
    }

    /** Knob, pill status, dan tombol Start/Stop mengikuti satu status aktif/nonaktif. */
    private fun renderActive(active: Boolean) {
        knob.setActive(active)
        btnStart.text = if (active) "Stop" else "Start"
        btnStart.setBackgroundResource(
            if (active) R.drawable.bg_main_btn_stop else R.drawable.bg_main_btn_start
        )
        btnStart.setTextColor(
            ContextCompat.getColor(this, if (active) R.color.alarm_red else R.color.alarm_text)
        )
        txtStatus.text = if (active) "Aktif, cek notifikasi" else "Nonaktif"
        (statusDot.background.mutate() as GradientDrawable).setColor(
            if (active) ContextCompat.getColor(this, R.color.alarm_red) else Color.parseColor("#8E8E93")
        )
    }

    private fun updatePermissionUi() {
        val pm = getSystemService(PowerManager::class.java)
        setPermState(stateNotif, NotificationManagerCompat.from(this).areNotificationsEnabled())
        setPermState(stateOverlay, Settings.canDrawOverlays(this))
        setPermState(stateFullScreen, canUseFullScreenIntentCompat())
        setPermState(stateBattery, pm.isIgnoringBatteryOptimizations(packageName))
    }

    private fun setPermState(view: TextView, ok: Boolean) {
        if (ok) {
            view.text = "Aktif"
            view.setTextColor(Color.parseColor("#8E8E93"))
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_perm_check, 0, 0, 0)
        } else {
            view.text = "Atur"
            view.setTextColor(ContextCompat.getColor(this, R.color.alarm_red))
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_perm_chevron, 0)
        }
    }

    // ---- Pintasan ke halaman pengaturan (dipanggil dari baris izin) ----

    private fun openSettingsSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Cadangan: halaman info app (ada di semua HP)
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun openNotificationSettings() {
        openSettingsSafely(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun openOverlaySettings() {
        openSettingsSafely(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            openSettingsSafely(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
            )
        } else {
            openNotificationSettings()
        }
    }

    /** Belum dikecualikan: tampilkan dialog izin langsung. Sudah: buka daftar optimasi baterai. */
    private fun openBatterySettings() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            openSettingsSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } else {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                openSettingsSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun checkNotificationPermissionThenProceed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                checkOverlayThenStart()
            } else {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            checkOverlayThenStart()
        }
    }

    private fun checkOverlayThenStart() {
        if (Settings.canDrawOverlays(this)) {
            checkFullScreenIntentThenStart()
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlaySettingsLauncher.launch(intent)
        }
    }

    /** Android 14+: izin "layar penuh" bisa dimatikan user. Tanpa itu, halaman Matikan tidak muncul di layar mati. */
    private fun canUseFullScreenIntentCompat(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    private fun checkFullScreenIntentThenStart() {
        if (canUseFullScreenIntentCompat()) {
            checkBatteryThenStart()
        } else {
            Toast.makeText(this, "Aktifkan izin notifikasi layar penuh untuk app ini", Toast.LENGTH_LONG).show()
            try {
                fullScreenSettingsLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                checkBatteryThenStart()
            }
        }
    }

    /**
     * Minta pengecualian optimasi baterai. Ditanyakan sekali saja (kalau ditolak
     * tidak dipaksa lagi tiap Start). Di ColorOS ini penting supaya app tidak
     * dibekukan saat layar mati.
     */
    private fun checkBatteryThenStart() {
        val pm = getSystemService(PowerManager::class.java)
        val prefs = getSharedPreferences("timer_prefs", MODE_PRIVATE)
        if (pm.isIgnoringBatteryOptimizations(packageName) || prefs.getBoolean("battery_asked", false)) {
            startForegroundServiceTimer()
            return
        }
        prefs.edit().putBoolean("battery_asked", true).apply()
        try {
            batterySettingsLauncher.launch(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            startForegroundServiceTimer()
        }
    }

    private fun startForegroundServiceTimer() {
        val serviceIntent = Intent(this, TimerForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        renderActive(true)
        Toast.makeText(this, "Notifikasi persisten aktif", Toast.LENGTH_SHORT).show()
    }

    private fun stopTimerService() {
        stopService(Intent(this, TimerForegroundService::class.java))
        stopService(Intent(this, OverlayPopupService::class.java))
        renderActive(false)
    }
}
