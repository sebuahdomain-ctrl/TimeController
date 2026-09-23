package com.timecontroller.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var statusText: TextView

    private val notifPermissionRequestCode = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        statusText = findViewById(R.id.statusText)

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener { onStopClicked() }

        updateButtonState(TimeControllerService.isRunning)
    }

    override fun onResume() {
        super.onResume()
        updateButtonState(TimeControllerService.isRunning)
    }

    private fun onStartClicked() {
        // 1. Cek izin overlay (tampil di atas app lain)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan \"Tampil di atas aplikasi lain\" untuk TimeController", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // 2. Cek izin notifikasi (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    notifPermissionRequestCode
                )
                return
            }
        }

        // 3. Cek izin exact alarm (Android 12+)
        val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(this, "Izinkan alarm presisi untuk TimeController", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            startActivity(intent)
            return
        }

        startTimeControllerService()
    }

    private fun startTimeControllerService() {
        val intent = Intent(this, TimeControllerService::class.java)
        intent.action = TimeControllerService.ACTION_START
        ContextCompat.startForegroundService(this, intent)
        updateButtonState(true)
    }

    private fun onStopClicked() {
        val intent = Intent(this, TimeControllerService::class.java)
        intent.action = TimeControllerService.ACTION_STOP
        startService(intent)
        updateButtonState(false)
    }

    private fun updateButtonState(running: Boolean) {
        btnStart.isEnabled = !running
        btnStop.isEnabled = running
        statusText.text = if (running) "Layanan aktif" else "Layanan tidak aktif"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == notifPermissionRequestCode) {
            onStartClicked()
        }
    }
}
