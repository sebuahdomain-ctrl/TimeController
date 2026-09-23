package com.timecontroller.app

import android.os.Handler
import android.os.Looper

/**
 * Logic inti timer, dipisah dari UI supaya bisa dipakai
 * dari Service (berjalan terus di background) maupun dari popup overlay.
 */
object TimerStopwatchEngine {

    private val handler = Handler(Looper.getMainLooper())

    // ===== State Timer =====
    var timerMinutes = 5
        private set
    var timerSeconds = 0
        private set
    var timerInitialTotalSec = 300
        private set
    var timerRemainingSec = 300
        private set
    var timerIsRunning = false
        private set

    // ===== Listener untuk update UI =====
    interface Listener {
        fun onTimerTick() {}
        fun onTimerFinished() {}
        fun onTimerStateChanged() {}
    }

    private val listeners = mutableListOf<Listener>()

    fun addListener(l: Listener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    // ================= TIMER =================

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!timerIsRunning) return
            if (timerRemainingSec > 0) {
                timerRemainingSec--
                timerMinutes = timerRemainingSec / 60
                timerSeconds = timerRemainingSec % 60
                listeners.forEach { it.onTimerTick() }
                handler.postDelayed(this, 1000)
            } else {
                timerIsRunning = false
                listeners.forEach { it.onTimerFinished() }
            }
        }
    }

    fun setTimerDuration(minutes: Int, seconds: Int) {
        if (timerIsRunning) return
        timerMinutes = minutes.coerceIn(0, 99)
        timerSeconds = seconds.coerceIn(0, 59)
        timerInitialTotalSec = timerMinutes * 60 + timerSeconds
        timerRemainingSec = timerInitialTotalSec
        listeners.forEach { it.onTimerStateChanged() }
    }

    /** Set menit langsung dari wheel picker (0-59), sesuai logic onWheelScrolled('min') di HTML. */
    fun setWheelMinute(min: Int) {
        if (timerIsRunning) return
        setTimerDuration(min.coerceIn(0, 59), timerSeconds)
    }

    /** Set detik langsung dari wheel picker (0-59), sesuai logic onWheelScrolled('sec') di HTML. */
    fun setWheelSecond(sec: Int) {
        if (timerIsRunning) return
        setTimerDuration(timerMinutes, sec.coerceIn(0, 59))
    }

    fun toggleTimer() {
        val total = timerMinutes * 60 + timerSeconds
        if (total <= 0 && !timerIsRunning) return
        if (!timerIsRunning) startTimer() else pauseTimer()
    }

    fun startTimer() {
        if (timerRemainingSec <= 0) return
        timerIsRunning = true
        listeners.forEach { it.onTimerStateChanged() }
        handler.postDelayed(timerRunnable, 1000)
    }

    fun pauseTimer() {
        timerIsRunning = false
        handler.removeCallbacks(timerRunnable)
        listeners.forEach { it.onTimerStateChanged() }
    }

    /** Reset: kembali ke durasi awal (initialTotalSec), sesuai logic HTML. */
    fun resetTimer() {
        if (timerIsRunning) pauseTimer()
        timerMinutes = timerInitialTotalSec / 60
        timerSeconds = timerInitialTotalSec % 60
        timerRemainingSec = timerInitialTotalSec
        listeners.forEach { it.onTimerStateChanged() }
    }

    // ================= RESET SEMUA (dipanggil saat service stop) =================
    fun resetAll() {
        pauseTimer()
        timerMinutes = 5
        timerSeconds = 0
        timerInitialTotalSec = 300
        timerRemainingSec = 300
    }

    fun formatTwoDigits(n: Int): String = n.toString().padStart(2, '0')
}
