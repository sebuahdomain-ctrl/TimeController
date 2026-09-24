package com.timecontroller.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var mainButton: TextView
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val REQ_NOTIFICATIONS = 501
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(Const.PREFS_NAME, MODE_PRIVATE)
        mainButton = findViewById(R.id.mainButton)

        mainButton.setOnClickListener { onMainButtonClicked() }
        updateButtonState()
    }

    override fun onResume() {
        super.onResume()
        updateButtonState()
    }

    private fun updateButtonState() {
        val running = prefs.getBoolean(Const.PREF_IS_RUNNING, false)
        mainButton.text = if (running) getString(R.string.stop) else getString(R.string.start)
    }

    private fun onMainButtonClicked() {
        val running = prefs.getBoolean(Const.PREF_IS_RUNNING, false)

        if (running) {
            // Matikan notifikasi persisten
            val intent = Intent(this, TimerForegroundService::class.java).apply {
                action = Const.ACTION_STOP_SERVICE
            }
            startService(intent)
            mainButton.text = getString(R.string.start)
            return
        }

        // Belum berjalan: pastikan izin overlay dulu, baru izin notifikasi, baru start service
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(this, OverlayPermissionActivity::class.java))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATIONS
            )
            return
        }

        startTimerService()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) {
            startTimerService()
        }
    }

    private fun startTimerService() {
        val intent = Intent(this, TimerForegroundService::class.java).apply {
            action = Const.ACTION_START_SERVICE
        }
        ContextCompat.startForegroundService(this, intent)
        mainButton.text = getString(R.string.stop)
    }
}
