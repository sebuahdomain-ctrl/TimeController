package com.example.timerapp

import android.app.AlarmManager
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
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class TimerForegroundService : Service() {

    companion object {
        // Nama channel diganti "_v2" karena importance sebuah NotificationChannel
        // tidak bisa diubah lagi begitu pernah dibuat di HP user (perubahan
        // IMPORTANCE_LOW -> DEFAULT di bawah tidak akan berlaku untuk yang sudah
        // update app kalau ID-nya tetap sama). Channel lama dihapus otomatis.
        const val CHANNEL_ID = "timer_persistent_channel_v2"
        private const val OLD_CHANNEL_ID = "timer_persistent_channel"
        const val NOTIFICATION_ID = 1001

        // Channel & notifikasi terpisah untuk alarm (melayang / heads-up)
        const val ALARM_CHANNEL_ID = "timer_alarm_channel"
        const val ALARM_NOTIFICATION_ID = 1002

        const val ACTION_PLAY_PAUSE = "com.example.timerapp.ACTION_PLAY_PAUSE"
        const val ACTION_RESET = "com.example.timerapp.ACTION_RESET"
        const val ACTION_STOP_ALARM = "com.example.timerapp.ACTION_STOP_ALARM"

        // Dari tombol Mulai di popup Atur: mulai hitung mundur baru dari durasi yang dipilih
        const val ACTION_START_WITH_DURATION = "com.example.timerapp.ACTION_START_WITH_DURATION"
        const val EXTRA_DURATION_MS = "com.example.timerapp.EXTRA_DURATION_MS"

        // Dari tombol Mulai di halaman Interval popup Atur: mulai sesi interval baru
        const val ACTION_START_INTERVAL = "com.example.timerapp.ACTION_START_INTERVAL"
        const val EXTRA_INTERVAL_A = "com.example.timerapp.EXTRA_INTERVAL_A"        // detik
        const val EXTRA_INTERVAL_B = "com.example.timerapp.EXTRA_INTERVAL_B"        // detik, 0 = mati
        const val EXTRA_INTERVAL_REPEAT = "com.example.timerapp.EXTRA_INTERVAL_REPEAT"

        // Jeda antar interval = lamanya beep. Cuma ini jedanya, setelah itu interval berikutnya langsung jalan.
        private const val BEEP_GAP_MS = 1_000L

        // Dikirim TimerAlarmReceiver saat alarm sistem (AlarmManager) berbunyi: waktu habis
        const val ACTION_TIMER_DONE = "com.example.timerapp.ACTION_TIMER_DONE"

        // Request code tetap untuk PendingIntent alarm sistem (1, 4, 5, 6 sudah dipakai tombol notifikasi)
        private const val ALARM_REQUEST_CODE = 7

        // Request code untuk layar alarm penuh (full-screen intent)
        private const val ALARM_SCREEN_REQUEST_CODE = 8

        // Request code untuk "showIntent" alarm jam (dibuka kalau ikon alarm di status bar di-tap)
        private const val ALARM_SHOW_REQUEST_CODE = 9

        private const val TAG = "TimerService"

        // Dikirim service saat bunyi alarm berhenti, supaya AlarmActivity menutup diri
        const val ACTION_ALARM_STOPPED = "com.example.timerapp.ACTION_ALARM_STOPPED"

        // Alarm berhenti sendiri setelah 10 detik
        const val ALARM_MAX_MS = 10_000L

        // Dibaca MainActivity.onResume untuk menentukan teks tombol & status.
        @Volatile
        var isRunning: Boolean = false
            private set

        // True selama alarm sedang berbunyi. Dibaca AlarmActivity supaya tidak
        // tampil kalau alarm sebenarnya sudah berhenti.
        @Volatile
        var isAlarmRinging: Boolean = false
            private set
    }

    private val engine = TimerEngine()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var alarmPlayer: AlarmPlayer
    // Hanya dipakai di jalur CADANGAN (kalau alarm exact tidak boleh dipasang)
    private var wakeLock: PowerManager.WakeLock? = null

    // Sudah pernah startForeground() sejak service ini dibuat?
    private var foregroundStarted = false

    // Isi notifikasi terakhir yang ditampilkan (supaya tidak update kalau tidak berubah)
    private var lastText: String? = null
    private var lastShowPause: Boolean = false

    // Dipanggil tiap pergantian detik
    private val tickRunnable = Runnable { onTick() }

    // Alarm berhenti sendiri setelah ALARM_MAX_MS. Sama seperti tombol Matikan:
    // bunyi berhenti DAN angka kembali ke durasi terpilih (bukan tertahan di 00:00).
    private val alarmTimeoutRunnable = Runnable { handleStopAlarm() }

    // ---- Mode Interval ----
    private lateinit var beepPlayer: BeepPlayer

    // Sesi interval sedang berjalan (RUNNING, PAUSED, atau jeda beep)?
    private var ivActive = false
    private var ivA = 0          // detik, Interval 1
    private var ivB = 0          // detik, Interval 2 (0 = mati)
    private var ivTotal = 1      // jumlah putaran
    private var ivRound = 1      // putaran sekarang (mulai dari 1)
    private var ivPhase = 1      // 1 = Interval 1, 2 = Interval 2

    // True selama jeda beep 1 detik antar interval
    private var inTransition = false
    private val transitionRunnable = Runnable { finishTransition() }

    // Label terakhir di notifikasi (supaya tidak update kalau tidak berubah)
    private var lastLabel: String? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        alarmPlayer = AlarmPlayer(applicationContext)
        beepPlayer = BeepPlayer(applicationContext)
        createNotificationChannel()
    }

    override fun onDestroy() {
        // Hentikan semuanya: hitungan, alarm sistem, bunyi alarm, wake lock, notifikasi alarm
        handler.removeCallbacksAndMessages(null)
        cancelFinishAlarm()
        alarmPlayer.stop()
        beepPlayer.stop()
        IntervalStore.clearSession(this)
        notificationManager().cancel(ALARM_NOTIFICATION_ID)
        notifyAlarmStopped()
        releaseWakeLock()
        isRunning = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service yang dinyalakan lewat startForegroundService() (misalnya oleh
        // TimerAlarmReceiver di proses yang baru hidup) WAJIB memanggil
        // startForeground() dalam beberapa detik, kalau tidak sistem mematikannya.
        // Jadi lakukan itu paling awal, sebelum apa pun.
        val fresh = !foregroundStarted
        if (fresh || intent?.action == ACTION_TIMER_DONE) {
            startForeground(NOTIFICATION_ID, buildNotification())
            foregroundStarted = true
        }

        if (intent == null) {
            // START_STICKY: proses dibunuh sistem lalu service dihidupkan lagi
            // (intent = null). Pulihkan timer yang tadinya jalan, jangan direset.
            restoreAfterSystemRestart()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_PLAY_PAUSE -> handlePlayPause()
            ACTION_RESET -> handleReset()
            ACTION_STOP_ALARM -> handleStopAlarm()
            ACTION_START_WITH_DURATION ->
                handleStartWithDuration(intent.getLongExtra(EXTRA_DURATION_MS, 0L))
            ACTION_START_INTERVAL -> handleStartInterval(
                intent.getIntExtra(EXTRA_INTERVAL_A, 0),
                intent.getIntExtra(EXTRA_INTERVAL_B, 0),
                intent.getIntExtra(EXTRA_INTERVAL_REPEAT, 0)
            )
            ACTION_TIMER_DONE -> handleTimerDone(fresh)
            // Tanpa action (Start dari app): mulai bersih di IDLE
            else -> startClean()
        }
        return START_STICKY
    }

    /**
     * Service dihidupkan ulang sistem tanpa perintah. Kalau sebelumnya ada
     * timer yang jalan (tersimpan di RunningStore), lanjutkan; kalau waktunya
     * sudah lewat, langsung bunyikan alarm. Kalau tidak ada, mulai bersih.
     */
    private fun restoreAfterSystemRestart() {
        val endWall = RunningStore.getEndWall(this)
        if (endWall <= 0L) {
            resetToIdle()
            refreshNotification()
            return
        }
        val remaining = endWall - System.currentTimeMillis()
        loadSession() // kalau tadinya mode Interval, pulihkan putaran dan interval ke berapa
        engine.restoreRunning(remaining)
        if (remaining <= 0L) {
            engine.finishIfDue()
            onCountdownEnd()
        } else {
            armCountdown()
            notificationManager().notify(NOTIFICATION_ID, buildNotification())
        }
    }

    // ---------------------------------------------------------------
    // Logika tombol
    // ---------------------------------------------------------------

    private fun startClean() {
        resetToIdle()
        notificationManager().notify(NOTIFICATION_ID, buildNotification())
    }

    private fun handlePlayPause() {
        // Jeda beep 1 detik antar interval: tombol ini = Pause. Beep dihentikan dan
        // hitungan tertahan di awal interval berikutnya (Play lagi = lanjut dari sana).
        if (inTransition) {
            cancelTransition()
            refreshNotification()
            return
        }

        // Durasi 00:00 (belum dipilih): tombol Play tidak berfungsi
        if (engine.state == TimerState.IDLE && engine.durationMs <= 0L) return

        // Kalau sedang FINISHED, alarm dihentikan dulu lalu langsung mulai baru
        if (engine.state == TimerState.FINISHED) {
            stopAlarm()
        }

        // Mode Interval: Play dari awal (IDLE) atau setelah selesai (FINISHED)
        // = mulai sesi interval baru dari putaran pertama.
        if (!ivActive && IntervalStore.isIntervalMode(this) &&
            (engine.state == TimerState.IDLE || engine.state == TimerState.FINISHED)
        ) {
            val c = IntervalStore.getConfig(this)
            if (c.a > 0) startIntervalSession(c.a, c.b, c.repeat)
            return
        }

        engine.playPause()
        if (engine.state == TimerState.RUNNING) {
            armCountdown() // mulai atau lanjut dari jeda
        } else {
            // Jeda: bekukan hitungan, batalkan alarm sistem
            handler.removeCallbacks(tickRunnable)
            cancelFinishAlarm()
            releaseWakeLock()
        }
        refreshNotification()
    }

    /**
     * Tombol Mulai di popup Atur. Apa pun keadaan sekarang (IDLE, RUNNING,
     * PAUSED, atau alarm sedang berbunyi): hentikan semuanya, simpan durasi
     * baru sebagai durasi terpilih, lalu langsung hitung mundur dari durasi itu.
     */
    private fun handleStartWithDuration(requestedMs: Long) {
        if (requestedMs <= 0L) return // data tidak masuk akal: abaikan
        handler.removeCallbacks(tickRunnable)
        cancelFinishAlarm()
        releaseWakeLock()
        stopAlarm()
        // Timer biasa menggantikan sesi interval (kalau ada)
        cancelTransition()
        endIntervalSession()
        IntervalStore.setIntervalMode(this, false)

        DurationStore.set(this, requestedMs)
        engine.startFresh(DurationStore.get(this))
        armCountdown()
        refreshNotification()
    }

    /**
     * Alarm sistem berbunyi: waktu habis. Idempotent: hanya bekerja kalau masih
     * RUNNING dan waktunya memang sudah habis, jadi tidak mungkin berbunyi dobel
     * dengan jalur tick Handler (yang memakai finishIfDue yang sama).
     *
     * Kalau service baru saja dihidupkan alarm ini (proses app sempat dimatikan
     * sistem), engine masih IDLE: pulihkan dulu dari RunningStore.
     */
    private fun handleTimerDone(fresh: Boolean) {
        if (engine.state != TimerState.RUNNING) {
            val endWall = RunningStore.getEndWall(this)
            if (endWall > 0L) {
                if (!ivActive) loadSession() // proses baru hidup: pulihkan sesi interval
                engine.restoreRunning(endWall - System.currentTimeMillis())
            } else {
                // Alarm nyasar (timer sudah dibatalkan): kalau service ini hidup
                // hanya gara-gara alarm tadi, matikan lagi.
                if (fresh) stopSelf()
                return
            }
        }
        if (engine.finishIfDue()) {
            handler.removeCallbacks(tickRunnable)
            onCountdownEnd()
        } else {
            // Alarm datang sedikit lebih awal dari waktu habis: pasang ulang
            scheduleTick()
            scheduleFinishAlarm()
        }
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

    /** Berhenti total (hitungan, alarm sistem, bunyi alarm, wake lock) dan kembali ke IDLE. */
    private fun resetToIdle() {
        handler.removeCallbacks(tickRunnable)
        cancelFinishAlarm()
        stopAlarm()
        releaseWakeLock()
        cancelTransition()
        endIntervalSession() // Interval: putaran kembali ke awal
        engine.reset()
        // IDLE menampilkan durasi terpilih user: Interval 1 kalau mode Interval,
        // kalau tidak durasi dari DurationStore
        engine.durationMs = if (IntervalStore.isIntervalMode(this)) {
            IntervalStore.getConfig(this).a * 1000L
        } else {
            DurationStore.get(this)
        }
    }

    // ---------------------------------------------------------------
    // Mode Interval
    // ---------------------------------------------------------------
    //
    // Satu putaran = Interval 1 lalu Interval 2. Kalau Interval 2 = 00:00 (mati),
    // satu putaran cuma Interval 1. Tiap interval berakhir: beep 1 detik (hitungan
    // tertahan selama beep), lalu interval berikutnya langsung jalan. Setelah
    // interval TERAKHIR di putaran TERAKHIR: alarm penuh seperti timer biasa.

    /** Tombol Mulai di halaman Interval popup Atur: hentikan semuanya, simpan, lalu mulai dari putaran pertama. */
    private fun handleStartInterval(a: Int, b: Int, total: Int) {
        if (a <= 0 || total <= 0) return // Interval 1 wajib terisi
        handler.removeCallbacks(tickRunnable)
        cancelFinishAlarm()
        releaseWakeLock()
        stopAlarm()
        IntervalStore.setConfig(this, a, b, total)
        startIntervalSession(a, b, total)
    }

    private fun startIntervalSession(a: Int, b: Int, total: Int) {
        cancelTransition()
        ivActive = true
        ivA = a
        ivB = b
        ivTotal = total
        ivRound = 1
        ivPhase = 1
        IntervalStore.setIntervalMode(this, true)
        persistSession()
        engine.startFresh(segmentMs())
        armCountdown()
        refreshNotification()
    }

    /** Lama interval yang sedang berjalan (atau yang berikutnya, saat jeda beep), dalam ms. */
    private fun segmentMs(): Long = (if (ivPhase == 1) ivA else ivB) * 1000L

    private fun persistSession() {
        IntervalStore.saveSession(this, ivA, ivB, ivTotal, ivRound, ivPhase)
    }

    /** Pulihkan sesi dari penyimpanan (dipakai setelah proses app sempat dimatikan sistem). */
    private fun loadSession() {
        val s = IntervalStore.getSession(this)
        if (s == null) {
            ivActive = false
            return
        }
        ivActive = true
        ivA = s.a
        ivB = s.b
        ivTotal = s.total
        ivRound = s.round
        ivPhase = s.phase
    }

    private fun endIntervalSession() {
        ivActive = false
        IntervalStore.clearSession(this)
    }

    /** Batalkan jeda beep (kalau sedang berlangsung) dan hentikan bunyinya. */
    private fun cancelTransition() {
        handler.removeCallbacks(transitionRunnable)
        beepPlayer.stop()
        inTransition = false
    }

    /**
     * Hitungan satu interval habis. Mode Interval dan masih ada interval berikutnya:
     * beep lalu lanjut. Selain itu (timer biasa, atau interval terakhir): alarm penuh.
     */
    private fun onCountdownEnd() {
        if (ivActive && prepareNextSegment()) return
        if (ivActive) endIntervalSession() // semua putaran selesai
        onFinished()
    }

    /**
     * Pindah ke interval berikutnya dan mulai jeda beep. Return false kalau tidak
     * ada interval berikutnya (semua putaran sudah selesai).
     */
    private fun prepareNextSegment(): Boolean {
        var round = ivRound
        var phase = ivPhase
        if (phase == 1 && ivB > 0) {
            phase = 2
        } else {
            if (round >= ivTotal) return false
            round++ // satu putaran selesai: sisa putaran berkurang satu
            phase = 1
        }
        ivRound = round
        ivPhase = phase

        // Selama beep, hitungan tertahan di awal interval berikutnya
        handler.removeCallbacks(tickRunnable)
        cancelFinishAlarm()
        engine.holdPaused(segmentMs())
        inTransition = true
        persistSession()
        beepPlayer.start()
        handler.removeCallbacks(transitionRunnable)
        handler.postDelayed(transitionRunnable, BEEP_GAP_MS)
        refreshNotification()
        return true
    }

    /** Beep 1 detik selesai: interval berikutnya langsung jalan. */
    private fun finishTransition() {
        if (!inTransition) return
        inTransition = false
        beepPlayer.stop() // potong tepat di 1 detik
        if (engine.state == TimerState.PAUSED) {
            engine.playPause() // PAUSED -> RUNNING dari durasi interval berikutnya
            armCountdown()
            refreshNotification()
        }
    }

    /** Teks kecil di notifikasi persisten, atau null kalau bukan mode Interval. */
    private fun intervalLabel(): String? {
        if (ivActive) {
            val left = (ivTotal - ivRound + 1).coerceAtLeast(1)
            return "Interval $ivPhase \u00B7 sisa $left putaran"
        }
        if (engine.state == TimerState.IDLE && IntervalStore.isIntervalMode(this)) {
            return "Interval 1 \u00B7 sisa ${IntervalStore.getConfig(this).repeat} putaran"
        }
        return null
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
            onCountdownEnd()
            return
        }
        refreshNotification()
        scheduleTick()
    }

    private fun onFinished() {
        cancelFinishAlarm() // alarm sistem sudah tidak diperlukan lagi
        // URUTAN PENTING: tandai alarm berbunyi dan tampilkan notifikasi (full-screen
        // intent) DULU, baru nyalakan suara. Menyiapkan nada bisa lambat setelah HP
        // tidur, dan itu tidak boleh menunda munculnya halaman Matikan.
        isAlarmRinging = true
        showAlarmNotification()
        refreshNotification()
        alarmPlayer.start()
        releaseWakeLock() // MediaPlayer memegang wake lock sendiri selama bunyi
        handler.removeCallbacks(alarmTimeoutRunnable)
        handler.postDelayed(alarmTimeoutRunnable, ALARM_MAX_MS)
    }

    /** Hentikan bunyi alarm dan hapus notifikasi alarm. Aman dipanggil kapan saja. */
    private fun stopAlarm() {
        handler.removeCallbacks(alarmTimeoutRunnable)
        alarmPlayer.stop()
        notificationManager().cancel(ALARM_NOTIFICATION_ID)
        notifyAlarmStopped()
    }

    /** Beri tahu AlarmActivity (kalau sedang tampil) bahwa alarm sudah berhenti, supaya menutup diri. */
    private fun notifyAlarmStopped() {
        val wasRinging = isAlarmRinging
        isAlarmRinging = false
        if (wasRinging) {
            sendBroadcast(Intent(ACTION_ALARM_STOPPED).setPackage(packageName))
        }
    }

    // ---------------------------------------------------------------
    // Alarm sistem (AlarmManager)
    // ---------------------------------------------------------------

    /** Dipanggil tiap masuk RUNNING (mulai baru atau lanjut dari jeda). */
    private fun armCountdown() {
        scheduleTick()
        scheduleFinishAlarm()
    }

    /**
     * KENAPA ALARMMANAGER? Durasi bisa sampai 99 menit 59 detik. Menahan CPU
     * tetap bangun selama itu (wake lock) boros baterai. Sebagai gantinya kita
     * titip "bangunkan aku tepat di detik X" ke sistem. HP boleh tidur, dan
     * sistem yang membangunkannya persis saat waktu habis.
     *
     * KENAPA setAlarmClock? Ini jenis alarm yang diperlakukan sistem sebagai
     * alarm jam sungguhan: tidak ditunda Doze, tidak dibatasi kuota, dan tidak
     * butuh izin exact alarm. (Efek sampingnya: ikon alarm muncul di status bar.)
     *
     * Kalau setAlarmClock gagal, jatuh ke CADANGAN: alarm biasa
     * (setAndAllowWhileIdle) ditambah wake lock, supaya tick Handler tetap
     * jalan dan alarm tetap bunyi.
     *
     * Waktu habis juga disimpan ke RunningStore supaya bisa dipulihkan kalau
     * proses app dimatikan sistem sebelum alarm berbunyi.
     */
    private fun scheduleFinishAlarm() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = finishAlarmPendingIntent()
        val triggerWall = System.currentTimeMillis() + engine.remainingMs()
        RunningStore.setEndWall(this, triggerWall)

        var ok = false
        try {
            val showPending = PendingIntent.getActivity(
                this, ALARM_SHOW_REQUEST_CODE, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerWall, showPending), pending)
            ok = true
        } catch (e: Exception) {
            Log.e(TAG, "setAlarmClock gagal, pakai cadangan", e)
        }

        if (ok) {
            releaseWakeLock() // jalur utama: tidak perlu menahan CPU
        } else {
            try {
                am.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, engine.endElapsedMs(), pending
                )
            } catch (e: Exception) {
                // Tanpa alarm sistem pun wake lock di bawah tetap menjaga tick Handler
                Log.e(TAG, "setAndAllowWhileIdle gagal", e)
            }
            acquireWakeLock()
        }
    }

    /** Batalkan alarm sistem. Aman dipanggil kapan saja (walau belum dijadwalkan). */
    private fun cancelFinishAlarm() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(finishAlarmPendingIntent())
        RunningStore.clear(this)
    }

    /** PendingIntent yang selalu sama (request code tetap), jadi bisa dijadwalkan ulang dan dibatalkan. */
    private fun finishAlarmPendingIntent(): PendingIntent {
        val intent = Intent(this, TimerAlarmReceiver::class.java).apply {
            action = ACTION_TIMER_DONE
        }
        return PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ---------------------------------------------------------------
    // Wake lock (hanya jalur cadangan, lihat scheduleFinishAlarm)
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
                // DEFAULT (bukan LOW) supaya notifikasi ini diranking sistem lebih
                // tinggi daripada notifikasi biasa (LOW/MIN) app lain, jadi lebih
                // sering ada di atas. Suara & getar tetap dimatikan manual di bawah
                // supaya tidak berbunyi tiap detik dia update.
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi yang selalu aktif selama service berjalan"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
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
            // Hapus channel lama (importance LOW) peninggalan versi sebelumnya,
            // supaya tidak nyangkut dua channel timer di pengaturan notifikasi.
            manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        }
    }

    /** Kirim ulang notifikasi utama, tapi hanya kalau angka atau ikonnya berubah. */
    private fun refreshNotification() {
        val text = TimerEngine.formatTime(engine.remainingMs())
        val showPause = engine.state == TimerState.RUNNING || inTransition
        val label = intervalLabel()
        if (text == lastText && showPause == lastShowPause && label == lastLabel) return
        notificationManager().notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val timeText = TimerEngine.formatTime(engine.remainingMs())
        val showPause = engine.state == TimerState.RUNNING || inTransition
        val label = intervalLabel()
        lastText = timeText
        lastShowPause = showPause
        lastLabel = label

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
            if (label != null) {
                setTextViewText(R.id.txtNotifLabel, label)
                setViewVisibility(R.id.txtNotifLabel, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.txtNotifLabel, android.view.View.GONE)
            }
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
            // DEFAULT (bukan LOW) + CATEGORY_SERVICE: keduanya sinyal ke sistem
            // supaya notifikasi foreground-service ini diranking di atas notifikasi
            // biasa. setPriority ini yang dipakai di Android lama (sebelum channel
            // ada); di Android 8+ yang menentukan urutan adalah importance channel
            // di atas, jadi keduanya sengaja disamakan.
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
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

        // Layar alarm penuh: saat HP terkunci / layar mati, sistem langsung
        // membuka AlarmActivity di atas kunci layar (tombol Matikan besar, tanpa
        // perlu buka kunci). Saat HP sedang dipakai, tetap tampil melayang seperti biasa.
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        val fullScreenPending = PendingIntent.getActivity(
            this, ALARM_SCREEN_REQUEST_CODE, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Timer berakhir")
            .setFullScreenIntent(fullScreenPending, true)
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
