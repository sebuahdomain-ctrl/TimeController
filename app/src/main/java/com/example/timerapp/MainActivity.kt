package com.example.timerapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Button

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var btnStart: Button

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
            startForegroundServiceTimer()
        } else {
            Toast.makeText(this, "Izin overlay belum diaktifkan, popup tidak akan muncul", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        btnStart = findViewById(R.id.btnStart)

        btnStart.setOnClickListener {
            if (TimerForegroundService.isRunning) {
                stopTimerService()
            } else {
                checkNotificationPermissionThenProceed()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Sumber kebenaran status hidup/mati: flag statis di TimerForegroundService.
        // Berguna kalau service sempat mati sendiri (dibunuh sistem, dll) saat app
        // sedang di background.
        updateStatusUi()
    }

    private fun updateStatusUi() {
        if (TimerForegroundService.isRunning) {
            btnStart.text = "Stop"
            txtStatus.text = "Aktif, cek notifikasi"
        } else {
            btnStart.text = "Start"
            txtStatus.text = "Nonaktif"
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
            startForegroundServiceTimer()
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlaySettingsLauncher.launch(intent)
        }
    }

    private fun startForegroundServiceTimer() {
        val serviceIntent = Intent(this, TimerForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        btnStart.text = "Stop"
        txtStatus.text = "Aktif, cek notifikasi"
        Toast.makeText(this, "Notifikasi persisten aktif", Toast.LENGTH_SHORT).show()
    }

    private fun stopTimerService() {
        stopService(Intent(this, TimerForegroundService::class.java))
        stopService(Intent(this, OverlayPopupService::class.java))
        btnStart.text = "Start"
        txtStatus.text = "Nonaktif"
    }
}
