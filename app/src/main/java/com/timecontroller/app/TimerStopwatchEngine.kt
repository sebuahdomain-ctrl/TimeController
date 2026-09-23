package com.timecontroller.app

import android.os.Handler
import android.os.Looper

/**
 * Logic inti timer & stopwatch, dipisah dari UI supaya bisa dipakai
 * dari Service (berjalan terus di background) maupun dari popup overlay.
 * Perilakunya niru persis logic di desain HTML yang diberikan user.
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

    // ===== State Stopwatch =====
    var stopwatchElapsedMs: Long = 0
        private set
    var stopwatchIsRunning = false
        private set
    private var stopwatchStartUptimeMs: Long = 0
    var stopwatchLapCount = 0
        private set

    // ===== Listener untuk update UI =====
    interface Listener {
        fun onTimerTick() {}
        fun onTimerFinished() {}
        fun onTimerStateChanged() {}
        fun onStopwatchTick() {}
        fun onStopwatchStateChanged() {}
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

    fun adjustTimerMinute(delta: Int) {
        if (timerIsRunning) return
        var newMin = timerMinutes + delta
        if (newMin < 0) newMin = 99
        if (newMin > 99) newMin = 0
        setTimerDuration(newMin, timerSeconds)
    }

    fun adjustTimerSecond(delta: Int) {
        if (timerIsRunning) return
        var newSec = timerSeconds + delta
        if (newSec < 0) newSec = 55
        if (newSec >= 60) newSec = 0
        setTimerDuration(timerMinutes, newSec)
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

    // ================= STOPWATCH =================

    private val stopwatchRunnable = object : Runnable {
        override fun run() {
            if (!stopwatchIsRunning) return
            stopwatchElapsedMs = android.os.SystemClock.elapsedRealtime() - stopwatchStartUptimeMs
            listeners.forEach { it.onStopwatchTick() }
            handler.postDelayed(this, 30)
        }
    }

    fun toggleStopwatch() {
        if (!stopwatchIsRunning) startStopwatch() else pauseStopwatch()
    }

    fun startStopwatch() {
        stopwatchIsRunning = true
        stopwatchStartUptimeMs = android.os.SystemClock.elapsedRealtime() - stopwatchElapsedMs
        listeners.forEach { it.onStopwatchStateChanged() }
        handler.postDelayed(stopwatchRunnable, 30)
    }

    fun pauseStopwatch() {
        stopwatchIsRunning = false
        handler.removeCallbacks(stopwatchRunnable)
        listeners.forEach { it.onStopwatchStateChanged() }
    }

    fun resetStopwatch() {
        pauseStopwatch()
        stopwatchElapsedMs = 0
        stopwatchLapCount = 0
        listeners.forEach { it.onStopwatchStateChanged() }
    }

    fun recordLap() {
        if (!stopwatchIsRunning) return
        stopwatchLapCount++
        listeners.forEach { it.onStopwatchStateChanged() }
    }

    // ================= RESET SEMUA (dipanggil saat service stop) =================
    fun resetAll() {
        pauseTimer()
        pauseStopwatch()
        timerMinutes = 5
        timerSeconds = 0
        timerInitialTotalSec = 300
        timerRemainingSec = 300
        stopwatchElapsedMs = 0
        stopwatchLapCount = 0
    }

    fun formatTwoDigits(n: Int): String = n.toString().padStart(2, '0')
}
