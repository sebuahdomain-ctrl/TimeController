package com.timecontroller.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class TimerForegroundService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private var remainingSeconds: Long = 0
    private var totalSeconds: Long = 0
    private var isPaused: Boolean = false

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(Const.PREFS_NAME, MODE_PRIVATE)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Const.ACTION_START_SERVICE -> {
                remainingSeconds = prefs.getLong(Const.PREF_REMAINING_SECONDS, 0)
                totalSeconds = prefs.getLong(Const.PREF_TOTAL_SECONDS, 0)
                isPaused = prefs.getBoolean(Const.PREF_IS_PAUSED, false)
                prefs.edit().putBoolean(Const.PREF_IS_RUNNING, true).apply()
                startForeground(Const.NOTIF_ID_PERSISTENT, buildPersistentNotification())
                if (!isPaused && totalSeconds > 0) {
                    startCountdown()
                }
            }
            Const.ACTION_SET_DURATION -> {
                val seconds = intent.getIntExtra(Const.EXTRA_SECONDS, 0).toLong()
                totalSeconds = seconds
                remainingSeconds = seconds
                isPaused = false
                persistState()
                startCountdown()
                updateNotification()
            }
            Const.ACTION_PAUSE_RESUME -> {
                if (isPaused) {
                    isPaused = false
                    persistState()
                    startCountdown()
                } else {
                    isPaused = true
                    countDownTimer?.cancel()
                    persistState()
                }
                updateNotification()
            }
            Const.ACTION_RESET -> {
                countDownTimer?.cancel()
                remainingSeconds = totalSeconds
                isPaused = if (totalSeconds > 0) true else false
                persistState()
                if (!isPaused && totalSeconds > 0) startCountdown()
                updateNotification()
            }
            Const.ACTION_STOP_SERVICE -> {
                countDownTimer?.cancel()
                prefs.edit()
                    .putBoolean(Const.PREF_IS_RUNNING, false)
                    .putLong(Const.PREF_REMAINING_SECONDS, 0)
                    .putLong(Const.PREF_TOTAL_SECONDS, 0)
                    .putBoolean(Const.PREF_IS_PAUSED, false)
                    .apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startCountdown() {
        countDownTimer?.cancel()
        if (remainingSeconds <= 0) return

        countDownTimer = object : CountDownTimer(remainingSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = millisUntilFinished / 1000
                persistState()
                updateNotification()
            }

            override fun onFinish() {
                remainingSeconds = 0
                persistState()
                updateNotification()
                triggerAlarm()
            }
        }.start()
    }

    private fun persistState() {
        prefs.edit()
            .putLong(Const.PREF_REMAINING_SECONDS, remainingSeconds)
            .putLong(Const.PREF_TOTAL_SECONDS, totalSeconds)
            .putBoolean(Const.PREF_IS_PAUSED, isPaused)
            .putBoolean(Const.PREF_IS_RUNNING, true)
            .apply()
    }

    private fun triggerAlarm() {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = Const.ACTION_TIMER_FINISHED
        }
        sendBroadcast(intent)
    }

    private fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun buildPersistentNotification(): Notification {
        val contentView = android.widget.RemoteViews(packageName, R.layout.notification_persistent)
        contentView.setTextViewText(R.id.notifTime, formatTime(remainingSeconds))
        contentView.setTextViewText(
            R.id.notifPauseButton,
            if (isPaused) getString(R.string.resume) else getString(R.string.pause)
        )

        contentView.setOnClickPendingIntent(R.id.notifPauseButton, actionPendingIntent(Const.ACTION_PAUSE_RESUME, 1))
        contentView.setOnClickPendingIntent(R.id.notifResetButton, actionPendingIntent(Const.ACTION_RESET, 2))
        contentView.setOnClickPendingIntent(R.id.notifAdjustButton, adjustPendingIntent())

        return NotificationCompat.Builder(this, Const.CHANNEL_PERSISTENT)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setCustomContentView(contentView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val manager = NotificationManagerCompat.from(this)
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            manager.notify(Const.NOTIF_ID_PERSISTENT, buildPersistentNotification())
        }
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TimerForegroundService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun adjustPendingIntent(): PendingIntent {
        val intent = Intent(this, TimerPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            this, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val persistentChannel = NotificationChannel(
                Const.CHANNEL_PERSISTENT,
                getString(R.string.notification_channel_persistent_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(persistentChannel)

            val alarmChannel = NotificationChannel(
                Const.CHANNEL_ALARM,
                getString(R.string.notification_channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(
                    android.media.RingtoneManager.getActualDefaultRingtoneUri(
                        this@TimerForegroundService, android.media.RingtoneManager.TYPE_ALARM
                    ),
                    audioAttributes
                )
                setBypassDnd(true)
            }
            manager.createNotificationChannel(alarmChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
