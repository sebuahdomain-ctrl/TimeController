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
        val btnStart: Button = findViewById(R.id.btnStart)

        btnStart.setOnClickListener {
            checkNotificationPermissionThenProceed()
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
        txtStatus.text = "Aktif — cek notifikasi"
        Toast.makeText(this, "Notifikasi persisten aktif", Toast.LENGTH_SHORT).show()
    }
}
