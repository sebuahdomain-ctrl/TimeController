package com.example.timerapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class TimerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_persistent_channel"
        const val NOTIFICATION_ID = 1001

        // Channel & notifikasi terpisah untuk alarm (melayang / heads-up)
        const val ALARM_CHANNEL_ID = "timer_alarm_channel"
        const val ALARM_NOTIFICATION_ID = 1002

        const val ACTION_PLAY_PAUSE = "com.example.timerapp.ACTION_PLAY_PAUSE"
        const val ACTION_RESET = "com.example.timerapp.ACTION_RESET"
        const val ACTION_STOP_ALARM = "com.example.timerapp.ACTION_STOP_ALARM"

        // Alarm berhenti sendiri setelah 60 detik
        const val ALARM_MAX_MS = 60_000L

        // Dibaca MainActivity.onResume untuk menentukan teks tombol & status.
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val engine = TimerEngine()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var alarmPlayer: AlarmPlayer
    private var wakeLock: PowerManager.WakeLock? = null

    // Isi notifikasi terakhir yang ditampilkan (supaya tidak update kalau tidak berubah)
    private var lastText: String? = null
    private var lastShowPause: Boolean = false

    // Dipanggil tiap pergantian detik
    private val tickRunnable = Runnable { onTick() }

    // Alarm berhenti sendiri setelah ALARM_MAX_MS
    private val alarmTimeoutRunnable = Runnable { stopAlarm() }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        alarmPlayer = AlarmPlayer(applicationContext)
        createNotificationChannel()
    }

    override fun onDestroy() {
        // Hentikan semuanya: hitungan, alarm, wake lock, notifikasi alarm
        handler.removeCallbacksAndMessages(null)
        alarmPlayer.stop()
        notificationManager().cancel(ALARM_NOTIFICATION_ID)
        releaseWakeLock()
        isRunning = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: kalau service dibunuh sistem karena low memory,
        // Android akan mencoba menghidupkannya lagi otomatis (intent = null).
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> handlePlayPause()
            ACTION_RESET -> handleReset()
            ACTION_STOP_ALARM -> handleStopAlarm()
            // Tanpa action (Start dari app / restart sistem): mulai bersih di IDLE
            else -> startClean()
        }
        return START_STICKY
    }

    // ---------------------------------------------------------------
    // Logika tombol
    // ---------------------------------------------------------------

    private fun startClean() {
        resetToIdle()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun handlePlayPause() {
        // Kalau sedang FINISHED, alarm dihentikan dulu lalu langsung mulai baru
        if (engine.state == TimerState.FINISHED) {
            stopAlarm()
        }
        engine.playPause()
        if (engine.state == TimerState.RUNNING) {
            acquireWakeLock()
            scheduleTick()
        } else {
            // Jeda: bekukan hitungan
            handler.removeCallbacks(tickRunnable)
            releaseWakeLock()
        }
        refreshNotification()
    }

    private fun handleReset() {
        if (engine.state == TimerState.IDLE) return // tidak ada perubahan
        resetToIdle()
        refreshNotification()
    }

    // Tombol "Matikan" di notifikasi alarm: sama seperti Reset
    private fun handleStopAlarm() {
        if (engine.state == TimerState.FINISHED) {
            resetToIdle()
            refreshNotification()
        } else {
            stopAlarm() // jaga-jaga kalau notifikasi alarm masih nyangkut
        }
    }

    /** Berhenti total (hitungan, alarm, wake lock) dan kembali ke IDLE. */
    private fun resetToIdle() {
        handler.removeCallbacks(tickRunnable)
        stopAlarm()
        releaseWakeLock()
        engine.reset()
    }

    // ---------------------------------------------------------------
    // Hitung mundur
    // ---------------------------------------------------------------

    private fun scheduleTick() {
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, engine.msUntilNextTick())
    }

    private fun onTick() {
        if (engine.state != TimerState.RUNNING) return
        if (engine.finishIfDue()) {
            onFinished()
            return
        }
        refreshNotification()
        scheduleTick()
    }

    private fun onFinished() {
        alarmPlayer.start()
        releaseWakeLock() // MediaPlayer memegang wake lock sendiri selama bunyi
        refreshNotification()
        showAlarmNotification()
        handler.removeCallbacks(alarmTimeoutRunnable)
        handler.postDelayed(alarmTimeoutRunnable, ALARM_MAX_MS)
    }

    /** Hentikan bunyi alarm dan hapus notifikasi alarm. Aman dipanggil kapan saja. */
    private fun stopAlarm() {
        handler.removeCallbacks(alarmTimeoutRunnable)
        alarmPlayer.stop()
        notificationManager().cancel(ALARM_NOTIFICATION_ID)
    }

    // ---------------------------------------------------------------
    // Wake lock
    // ---------------------------------------------------------------

    private fun acquireWakeLock() {
        releaseWakeLock()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimerApp:countdown")
        lock.setReferenceCounted(false)
        lock.acquire(engine.remainingMs() + 5_000L) // timeout = sisa waktu + 5 detik
        wakeLock = lock
    }

    private fun releaseWakeLock() {
        val lock = wakeLock
        wakeLock = null
        if (lock != null && lock.isHeld) {
            lock.release()
        }
    }

    // ---------------------------------------------------------------
    // Notifikasi
    // ---------------------------------------------------------------

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notifikasi Persisten",
                NotificationManager.IMPORTANCE_LOW // LOW supaya tidak bunyi tiap update, tapi tetap selalu tampil
            ).apply {
                description = "Notifikasi yang selalu aktif selama service berjalan"
                setShowBadge(false)
            }

            // Channel alarm: HIGH supaya muncul melayang. Sengaja tanpa suara &
            // getar karena bunyinya dari AlarmPlayer (stream ALARM).
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Alarm Timer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi melayang saat timer berakhir"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }

            val manager = notificationManager()
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(alarmChannel)
        }
    }

    /** Kirim ulang notifikasi utama, tapi hanya kalau angka atau ikonnya berubah. */
    private fun refreshNotification() {
        val text = TimerEngine.formatTime(engine.remainingMs())
        val showPause = engine.state == TimerState.RUNNING
        if (text == lastText && showPause == lastShowPause) return
        notificationManager().notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val timeText = TimerEngine.formatTime(engine.remainingMs())
        val showPause = engine.state == TimerState.RUNNING
        lastText = timeText
        lastShowPause = showPause

        // Tombol "Atur" -> HARUS PendingIntent.getActivity supaya notification shade
        // menutup otomatis. Activity-nya transparan & langsung finish(), lalu
        // memunculkan popup lewat OverlayPopupService (lihat PopupTrampolineActivity).
        val openPopupIntent = Intent(this, PopupTrampolineActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        val openPopupPending = PendingIntent.getActivity(
            this, 1, openPopupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tombol "Play/Pause" -> HARUS PendingIntent.getActivity supaya notification
        // shade menutup otomatis (sama seperti tombol Atur). Activity-nya transparan,
        // meneruskan aksi ke service, lalu langsung finish() (lihat PlayTrampolineActivity).
        val playPauseIntent = Intent(this, PlayTrampolineActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        val playPausePending = PendingIntent.getActivity(
            this, 6, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tombol "Reset" -> sama.
        val resetIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_RESET
        }
        val resetPending = PendingIntent.getBroadcast(
            this, 4, resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val customView = RemoteViews(packageName, R.layout.notification_timer).apply {
            setTextViewText(R.id.txtNotifTime, timeText)
            setImageViewResource(R.id.btnPlay, if (showPause) R.drawable.ic_pause else R.drawable.ic_play)
            setContentDescription(R.id.btnPlay, if (showPause) "Pause" else "Play")
            setOnClickPendingIntent(R.id.btnPlay, playPausePending)
            setOnClickPendingIntent(R.id.btnReset, resetPending)
            setOnClickPendingIntent(R.id.btnAtur, openPopupPending)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true) // membuat notifikasi tidak bisa di-swipe hilang
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(customView)
            .build()
    }

    /**
     * Notifikasi melayang: "Timer berakhir" di kiri + tombol "Matikan" di kanan.
     * Angka di sampingnya berjalan sendiri (chronometer) menghitung sudah
     * berapa lama alarm bunyi, jadi tidak perlu di-update manual.
     */
    private fun showAlarmNotification() {
        val stopIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPending = PendingIntent.getBroadcast(
            this, 5, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tampilan custom: "Timer berakhir" di kiri, tombol "Matikan" di kanan
        val alarmView = RemoteViews(packageName, R.layout.notification_alarm).apply {
            setOnClickPendingIntent(R.id.btnMatikan, stopPending)
        }

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Timer berakhir")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(alarmView)
            .setCustomHeadsUpContentView(alarmView)
            .build()

        notificationManager().notify(ALARM_NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
