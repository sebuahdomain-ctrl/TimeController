package com.example.timerapp

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Layar alarm penuh: muncul di atas kunci layar saat timer berakhir dan HP
 * terkunci / layar mati. Isinya cuma judul dan satu tombol besar "Matikan",
 * jadi alarm bisa dimatikan sekali tap tanpa buka kunci.
 *
 * Dibuka oleh sistem lewat full-screen intent milik notifikasi alarm (lihat
 * TimerForegroundService.showAlarmNotification). Menutup diri sendiri kalau
 * alarm berhenti dari jalur lain (notifikasi, batas 60 detik, timer baru).
 */
class AlarmActivity : Activity() {

    private var receiverRegistered = false

    // Service mengirim ini tiap bunyi alarm berhenti
    private val alarmStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showOverLockScreen()
        setContentView(R.layout.activity_alarm)

        val btnStop: TextView = findViewById(R.id.btnAlarmStop)
        btnStop.setOnClickListener { stopAlarmAndClose() }

        // Daftar dulu, baru cek keadaan alarm (supaya tidak ada celah waktu)
        ContextCompat.registerReceiver(
            this,
            alarmStoppedReceiver,
            IntentFilter(TimerForegroundService.ACTION_ALARM_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true

        // Alarm sudah berhenti sebelum layar ini sempat tampil: tidak ada gunanya, tutup
        if (!TimerForegroundService.isAlarmRinging) {
            finish()
        }
    }

    /** Tampil di atas kunci layar, menyalakan layar, dan menjaganya tetap menyala. */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Sama seperti tombol Matikan di notifikasi alarm: service kembali ke IDLE. */
    private fun stopAlarmAndClose() {
        if (TimerForegroundService.isRunning) {
            try {
                startService(
                    Intent(this, TimerForegroundService::class.java)
                        .setAction(TimerForegroundService.ACTION_STOP_ALARM)
                )
            } catch (e: Exception) {
                // Sistem menolak start service: abaikan
            }
        }
        finish()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            receiverRegistered = false
            unregisterReceiver(alarmStoppedReceiver)
        }
        super.onDestroy()
    }
}
