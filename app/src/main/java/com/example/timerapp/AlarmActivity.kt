package com.example.timerapp

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.KeyguardManager
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Layar alarm penuh: muncul di atas kunci layar saat timer berakhir dan HP
 * terkunci / layar mati. Isinya durasi timer besar dengan lingkaran berdenyut
 * dan slider "Geser untuk matikan", jadi alarm bisa dimatikan tanpa buka kunci
 * dan tidak mati karena tersentuh tidak sengaja.
 *
 * Dibuka oleh sistem lewat full-screen intent milik notifikasi alarm (lihat
 * TimerForegroundService.showAlarmNotification). Menutup diri sendiri kalau
 * alarm berhenti dari jalur lain (notifikasi, batas 10 detik, timer baru).
 */
class AlarmActivity : Activity() {

    private var receiverRegistered = false
    private val pulseAnimators = mutableListOf<ObjectAnimator>()

    // Service mengirim ini tiap bunyi alarm berhenti
    private val alarmStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // HP sedang dipakai (layar nyala dan tidak terkunci): halaman layar penuh tidak
        // perlu. Cukup notifikasi melayang (yang punya tombol Matikan). Cek ini SEBELUM
        // showOverLockScreen() supaya layar tidak sempat dinyalakan ulang.
        if (isPhoneInUse()) {
            finish()
            return
        }

        showOverLockScreen()
        setContentView(R.layout.activity_alarm)

        bindDuration()
        startPulse()
        findViewById<SlideToStopView>(R.id.slideStop).onSlideComplete = { stopAlarmAndClose() }

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

    /** Isi angka jam: durasi timer yang baru selesai (mode Interval: tulisan "Selesai"). */
    private fun bindDuration() {
        val txtTime: TextView = findViewById(R.id.txtAlarmTime)
        val txtCaption: TextView = findViewById(R.id.txtAlarmCaption)
        if (IntervalStore.isIntervalMode(this)) {
            txtTime.text = "Selesai"
            txtCaption.text = "Interval selesai"
        } else {
            txtTime.text = TimerEngine.formatTime(DurationStore.get(this))
            txtCaption.text = "Durasi timer selesai"
        }
    }

    /** Dua lingkaran merah membesar dan memudar bergantian, tiap 2 detik. */
    private fun startPulse() {
        listOf(R.id.alarmRing1 to 0L, R.id.alarmRing2 to 1000L).forEach { (id, delay) ->
            val ring: View = findViewById(id)
            ring.alpha = 0f
            val anim = ObjectAnimator.ofPropertyValuesHolder(
                ring,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.72f, 1.15f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.72f, 1.15f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 0f)
            ).apply {
                duration = 2000L
                startDelay = delay
                interpolator = LinearInterpolator()
                repeatCount = ObjectAnimator.INFINITE
            }
            anim.start()
            pulseAnimators.add(anim)
        }
    }

    /** True kalau layar sedang nyala DAN kunci layar tidak tampil (user sedang memakai HP). */
    private fun isPhoneInUse(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return pm.isInteractive && !km.isKeyguardLocked
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
        pulseAnimators.forEach { it.cancel() }
        pulseAnimators.clear()
        if (receiverRegistered) {
            receiverRegistered = false
            unregisterReceiver(alarmStoppedReceiver)
        }
        super.onDestroy()
    }
}
