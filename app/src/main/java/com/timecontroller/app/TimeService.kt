package com.timecontroller.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/**
 * Service yang jalan terus di background supaya notif timer/stopwatch
 * selalu tampil di status bar, 24 jam, walau aplikasi ditutup.
 */
class TimeService : Service() {

    enum class Mode { TIMER, STOPWATCH }
    enum class RunState { IDLE, RUNNING, PAUSED, FINISHED }

    private var mode = Mode.TIMER
    private var runState = RunState.IDLE

    // Timer
    private var timerTotalMillis: Long = 5 * 60 * 1000L
    private var timerRemainingMillis: Long = timerTotalMillis
    private var countDownTimer: CountDownTimer? = null

    // Stopwatch
    private var stopwatchElapsedMillis: Long = 0L
    private var stopwatchStartTick: Long = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopwatchTickRunnable = object : Runnable {
        override fun run() {
            if (runState == RunState.RUNNING && mode == Mode.STOPWATCH) {
                stopwatchElapsedMillis = SystemClock.elapsedRealtime() - stopwatchStartTick
                updateNotification()
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val binder = LocalBinder()
    inner class LocalBinder : android.os.Binder() {
        fun getService(): TimeService = this@TimeService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 5)
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 0)
                setMode(Mode.TIMER)
                startTimer((minutes * 60L + seconds) * 1000L)
            }
            ACTION_START_STOPWATCH -> {
                setMode(Mode.STOPWATCH)
                startStopwatch()
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_RESET -> reset()
            ACTION_SWITCH_MODE -> switchMode()
            else -> { /* service restarted by system (START_STICKY) */ }
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun setMode(newMode: Mode) {
        mode = newMode
    }

    private fun switchMode() {
        pause()
        mode = if (mode == Mode.TIMER) Mode.STOPWATCH else Mode.TIMER
        runState = RunState.IDLE
        updateNotification()
    }

    private fun startTimer(totalMillis: Long) {
        timerTotalMillis = totalMillis
        timerRemainingMillis = totalMillis
        countDownTimer?.cancel()
        runState = RunState.RUNNING
        countDownTimer = object : CountDownTimer(timerRemainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerRemainingMillis = millisUntilFinished
                updateNotification()
            }
            override fun onFinish() {
                timerRemainingMillis = 0
                runState = RunState.FINISHED
                updateNotification()
            }
        }.start()
    }

    private fun startStopwatch() {
        runState = RunState.RUNNING
        stopwatchStartTick = SystemClock.elapsedRealtime() - stopwatchElapsedMillis
        handler.post(stopwatchTickRunnable)
    }

    private fun pause() {
        if (mode == Mode.TIMER) {
            countDownTimer?.cancel()
        } else {
            handler.removeCallbacks(stopwatchTickRunnable)
        }
        if (runState == RunState.RUNNING) runState = RunState.PAUSED
        updateNotification()
    }

    private fun resume() {
        if (mode == Mode.TIMER) {
            startTimer(timerRemainingMillis)
        } else {
            startStopwatch()
        }
    }

    private fun reset() {
        countDownTimer?.cancel()
        handler.removeCallbacks(stopwatchTickRunnable)
        if (mode == Mode.TIMER) {
            timerRemainingMillis = timerTotalMillis
        } else {
            stopwatchElapsedMillis = 0L
        }
        runState = RunState.IDLE
        updateNotification()
    }

    private fun formatMillis(millis: Long): String {
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val displayTime = if (mode == Mode.TIMER) formatMillis(timerRemainingMillis)
        else formatMillis(stopwatchElapsedMillis)

        val modeLabel = if (mode == Mode.TIMER) "Timer" else "Stopwatch"
        val stateLabel = when (runState) {
            RunState.RUNNING -> "Berjalan"
            RunState.PAUSED -> "Dijeda"
            RunState.FINISHED -> "Selesai!"
            RunState.IDLE -> "Siap"
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val settingsIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, SettingsPopupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (runState == RunState.RUNNING) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Jeda",
                actionPendingIntent(ACTION_PAUSE, 2)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Mulai",
                actionPendingIntent(if (runState == RunState.PAUSED) ACTION_RESUME else ACTION_RESUME, 3)
            )
        }

        val resetAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_revert, "Reset",
            actionPendingIntent(ACTION_RESET, 4)
        )

        val settingsAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_manage, "Atur",
            settingsIntent
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$modeLabel · $displayTime")
            .setContentText(stateLabel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(playPauseAction)
            .addAction(resetAction)
            .addAction(settingsAction)
            .build()
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TimeService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Time Controller",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi timer dan stopwatch yang selalu aktif"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        handler.removeCallbacks(stopwatchTickRunnable)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "time_controller_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_START_TIMER = "com.timecontroller.app.START_TIMER"
        const val ACTION_START_STOPWATCH = "com.timecontroller.app.START_STOPWATCH"
        const val ACTION_PAUSE = "com.timecontroller.app.PAUSE"
        const val ACTION_RESUME = "com.timecontroller.app.RESUME"
        const val ACTION_RESET = "com.timecontroller.app.RESET"
        const val ACTION_SWITCH_MODE = "com.timecontroller.app.SWITCH_MODE"

        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_SECONDS = "extra_seconds"
    }
}
