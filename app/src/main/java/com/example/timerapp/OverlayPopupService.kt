package com.example.timerapp

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.TextView

/**
 * Menampilkan popup "Timer / Interval" sebagai jendela overlay di atas app lain
 * (WindowManager + TYPE_APPLICATION_OVERLAY), jadi app yang sedang
 * dibuka user tetap di depan.
 */
class OverlayPopupService : Service() {

    companion object {
        // Halaman yang sedang tampil di popup (dipilih lewat judul "Timer v")
        private const val PAGE_TIMER = 0
        private const val PAGE_INTERVAL = 1

        // Batas roda: menit 0..60 (60:00 paling besar), detik 0..59
        private const val MAX_MINUTE = 60
        private const val MAX_SECOND = 59
    }

    // Halaman aktif. Saat popup dibuka, mengikuti mode terakhir: Interval kalau
    // mode Interval, Timer kalau mode Timer biasa (lihat showPopup).
    private var currentPage = PAGE_TIMER

    // Halaman Interval: dua waktu dalam detik (00:00 pada Interval 2 = mati),
    // jumlah putaran, dan kotak mana yang sedang diedit rodanya (1 atau 2).
    // Nilai awal diisi loadIntervalState(): kalau sesi interval sedang jalan,
    // angka putaran = sisa putaran (jadi mundur tiap putaran selesai); kalau
    // tidak, memakai pengaturan terakhir. Disimpan hanya saat tombol Mulai.
    private var intervalA = 60
    private var intervalB = 120
    private var intervalRepeat = 4
    private var intervalActive = 1

    // Dipasang setupIntervalPage: Reset di halaman Interval mengosongkan kotak aktif
    private var resetInterval: (() -> Unit)? = null

    private var windowManager: WindowManager? = null
    private var popupView: View? = null

    // Flag sekali-pakai: mencegah ketukan ganda pada tombol Mulai
    private var startConsumed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Kalau popup sudah tampil, jangan dobel
        if (popupView != null) {
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            // Guard: harusnya sudah dicek dari MainActivity, tapi tetap dijaga di sini
            stopSelf()
            return START_NOT_STICKY
        }

        showPopup()
        return START_NOT_STICKY
    }

    private fun showPopup() {
        startConsumed = false
        currentPage = if (IntervalStore.isIntervalMode(this)) PAGE_INTERVAL else PAGE_TIMER
        loadIntervalState()
        intervalActive = 1
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Popup di-inflate dari context Service, jadi tidak otomatis ikut
        // tema DayNight seperti Activity. Bungkus context dengan tema
        // platform (Theme.Material / Theme.Material.Light) sesuai uiMode
        // sistem saat ini, supaya NumberPicker bawaan tampil dengan warna
        // yang benar (lihat Theme.Popup.Dark / Theme.Popup.Light di themes.xml).
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val themeRes = if (isDarkMode) R.style.Theme_Popup_Dark else R.style.Theme_Popup_Light
        val themedContext = ContextThemeWrapper(this, themeRes)

        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.overlay_popup, null)
        popupView = view

        setupNumberPickers(view)
        setupClicks(view)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY // wajib dari Android 8+
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            popupWidthPx(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            0,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        windowManager?.addView(view, params)
    }

    /** Isi nilai awal halaman Interval (lihat komentar di atas field intervalA). */
    private fun loadIntervalState() {
        val session = if (TimerForegroundService.isRunning) IntervalStore.getSession(this) else null
        if (session != null) {
            intervalA = session.a
            intervalB = session.b
            intervalRepeat = session.roundsLeft
        } else {
            val config = IntervalStore.getConfig(this)
            intervalA = config.a
            intervalB = config.b
            intervalRepeat = config.repeat
        }
    }

    /** Lebar popup: 85% lebar layar, maksimal 300dp (kartu dibuat lebih ringkas). */
    private fun popupWidthPx(): Int {
        val metrics = resources.displayMetrics
        val maxWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300f, metrics)
        return minOf(metrics.widthPixels * 0.85f, maxWidthPx).toInt()
    }

    /** Roda menit (0..60) dan detik (0..59), berputar, angka selalu 2 digit. */
    private fun setupNumberPickers(view: View) {
        val npMinute: NumberPicker = view.findViewById(R.id.npMinute)
        val npSecond: NumberPicker = view.findViewById(R.id.npSecond)

        configurePicker(npMinute, maxValue = MAX_MINUTE)
        configurePicker(npSecond, maxValue = MAX_SECOND)

        // Nilai awal kedua roda = durasi terpilih (pertama kali 00:00)
        val durationMs = DurationStore.get(this)
        npMinute.value = (durationMs / 60_000L).toInt().coerceIn(0, MAX_MINUTE)
        npSecond.value = ((durationMs / 1000L) % 60L).toInt()
    }

    private fun configurePicker(picker: NumberPicker, maxValue: Int) {
        picker.minValue = 0
        picker.maxValue = maxValue
        // Angka 2 digit lewat displayedValues, BUKAN setFormatter (ada bug
        // nilai awal tidak terformat).
        picker.displayedValues = Array(maxValue + 1) { i -> String.format("%02d", i) }
        picker.wrapSelectorWheel = true
        // setTextSize/setTextColor cuma ada di API 29+; di bawahnya biarkan
        // default dari tema platform yang sudah dibungkus ContextThemeWrapper.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            picker.setTextColor(resources.getColor(R.color.popup_text, theme))
            picker.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 18f, resources.displayMetrics
            ))
        }
    }

    private fun setupClicks(view: View) {
        val npMinute: NumberPicker = view.findViewById(R.id.npMinute)
        val npSecond: NumberPicker = view.findViewById(R.id.npSecond)
        val btnClose: ImageButton = view.findViewById(R.id.btnClosePopup)
        val btnMulai: TextView = view.findViewById(R.id.btnMulai)
        val btnReset: TextView = view.findViewById(R.id.btnReset)

        // Halaman Timer: Mulai aktif kalau roda bukan 00:00.
        // Halaman Interval: Mulai aktif kalau Interval 1 bukan 00:00 (Interval 2 boleh 00:00 = mati).
        fun updateStartButton() {
            val enabled = if (currentPage == PAGE_TIMER) {
                npMinute.value != 0 || npSecond.value != 0
            } else {
                intervalA > 0
            }
            btnMulai.isEnabled = enabled
            btnMulai.isClickable = enabled
            btnMulai.alpha = if (enabled) 1f else 0.4f
        }

        // Isi kedua roda sekaligus (tidak memicu listener roda, jadi tombol Mulai diperbarui manual)
        fun setWheels(minutes: Int, seconds: Int) {
            npMinute.value = minutes
            npSecond.value = seconds
            updateStartButton()
        }

        btnClose.setOnClickListener {
            removePopup()
            stopSelf()
        }

        // Tiap roda digeser: jaga batas 60:00, lalu perbarui tombol Mulai
        npMinute.setOnValueChangedListener { _, _, _ ->
            limitToSixty(npMinute, npSecond, minuteMoved = true)
            updateStartButton()
        }
        npSecond.setOnValueChangedListener { _, _, _ ->
            limitToSixty(npMinute, npSecond, minuteMoved = false)
            updateStartButton()
        }

        // 6 pilihan cepat: hanya mengisi roda (menit = N, detik = 0),
        // TIDAK memulai timer dan popup tetap terbuka.
        val quickChoices = listOf(
            R.id.btnQuick1Min to 1,
            R.id.btnQuick2Min to 2,
            R.id.btnQuick5Min to 5,
            R.id.btnQuick10Min to 10,
            R.id.btnQuick20Min to 20,
            R.id.btnQuick30Min to 30
        )
        for ((buttonId, minutes) in quickChoices) {
            val chip: TextView = view.findViewById(buttonId)
            chip.setOnClickListener { setWheels(minutes, 0) }
        }

        // Reset: di halaman Timer kedua roda kembali ke 00:00; di halaman Interval
        // kotak yang sedang dipilih jadi 00:00. Popup tetap terbuka dan timer
        // yang sedang berjalan tidak disentuh.
        btnReset.setOnClickListener {
            if (currentPage == PAGE_INTERVAL) resetInterval?.invoke() else setWheels(0, 0)
        }

        // Mulai: simpan durasi, kirim ke service, lalu tutup popup
        btnMulai.setOnClickListener {
            if (startConsumed) return@setOnClickListener
            if (currentPage == PAGE_INTERVAL) {
                startInterval()
                return@setOnClickListener
            }
            val durationMs = (npMinute.value * 60L + npSecond.value) * 1000L
            if (durationMs <= 0L) return@setOnClickListener
            startConsumed = true

            DurationStore.set(this, durationMs)

            // Kalau service timer sedang tidak hidup, jangan dihidupkan lewat
            // jalan ini (nanti jadi service tanpa notifikasi): cukup abaikan.
            if (TimerForegroundService.isRunning) {
                try {
                    startService(
                        Intent(this, TimerForegroundService::class.java)
                            .setAction(TimerForegroundService.ACTION_START_WITH_DURATION)
                            .putExtra(TimerForegroundService.EXTRA_DURATION_MS, durationMs)
                    )
                } catch (e: Exception) {
                    // Sistem menolak start service: abaikan
                }
            }

            removePopup()
            stopSelf()
        }

        setupIntervalPage(view) { updateStartButton() }
        setupPageSwitch(view) { updateStartButton() }
    }

    /**
     * Mulai di halaman Interval: simpan pengaturan, kirim ke service (mulai dari
     * putaran pertama), lalu tutup popup. Interval 1 wajib terisi.
     */
    private fun startInterval() {
        if (intervalA <= 0) return
        startConsumed = true

        IntervalStore.setConfig(this, intervalA, intervalB, intervalRepeat)

        // Sama seperti Mulai di halaman Timer: kalau service tidak hidup, jangan
        // dihidupkan lewat jalan ini (nanti jadi service tanpa notifikasi).
        if (TimerForegroundService.isRunning) {
            try {
                startService(
                    Intent(this, TimerForegroundService::class.java)
                        .setAction(TimerForegroundService.ACTION_START_INTERVAL)
                        .putExtra(TimerForegroundService.EXTRA_INTERVAL_A, intervalA)
                        .putExtra(TimerForegroundService.EXTRA_INTERVAL_B, intervalB)
                        .putExtra(TimerForegroundService.EXTRA_INTERVAL_REPEAT, intervalRepeat)
                )
            } catch (e: Exception) {
                // Sistem menolak start service: abaikan
            }
        }

        removePopup()
        stopSelf()
    }

    /**
     * Batas 60:00. Kalau menit sampai 60, detik dipaksa 00. Kalau detik digeser
     * saat menit masih 60, menit turun ke 59 (detiknya dipertahankan).
     */
    private fun limitToSixty(npMinute: NumberPicker, npSecond: NumberPicker, minuteMoved: Boolean) {
        if (npMinute.value == MAX_MINUTE && npSecond.value > 0) {
            if (minuteMoved) npSecond.value = 0 else npMinute.value = MAX_MINUTE - 1
        }
    }

    private fun mmss(totalSeconds: Int): String =
        String.format(java.util.Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)

    /**
     * Halaman Interval: ketuk Interval 1 / Interval 2 untuk memilih kotak yang
     * diedit, roda mengubah waktunya, baris Ulangi mengatur jumlah putaran.
     * Interval 2 di 00:00 tampil redup (artinya mati). Yang menjalankan adalah
     * tombol Mulai (lihat startInterval); [onChanged] dipanggil tiap nilai berubah
     * supaya tombol Mulai ikut diperbarui.
     */
    private fun setupIntervalPage(view: View, onChanged: () -> Unit) {
        val boxA: View = view.findViewById(R.id.boxInterval1)
        val boxB: View = view.findViewById(R.id.boxInterval2)
        val txtA: TextView = view.findViewById(R.id.txtInterval1)
        val txtB: TextView = view.findViewById(R.id.txtInterval2)
        val npMin: NumberPicker = view.findViewById(R.id.npIntMinute)
        val npSec: NumberPicker = view.findViewById(R.id.npIntSecond)
        val btnMinus: TextView = view.findViewById(R.id.btnRepeatMinus)
        val btnPlus: TextView = view.findViewById(R.id.btnRepeatPlus)
        val txtRepeat: TextView = view.findViewById(R.id.txtRepeat)

        configurePicker(npMin, maxValue = MAX_MINUTE)
        configurePicker(npSec, maxValue = MAX_SECOND)

        fun refresh() {
            txtA.text = mmss(intervalA)
            txtB.text = mmss(intervalB)
            boxA.isSelected = intervalActive == 1
            boxB.isSelected = intervalActive == 2
            // Interval 2 di 00:00 = mati: redup, kecuali sedang dipilih
            boxB.alpha = if (intervalB == 0 && intervalActive != 2) 0.45f else 1f
            txtRepeat.text = intervalRepeat.toString()
            onChanged()
        }

        // Isi roda dengan waktu kotak yang sedang dipilih
        fun loadWheels() {
            val v = if (intervalActive == 1) intervalA else intervalB
            npMin.value = v / 60
            npSec.value = v % 60
        }

        // Simpan nilai roda ke kotak yang sedang dipilih
        fun readWheels() {
            val v = npMin.value * 60 + npSec.value
            if (intervalActive == 1) intervalA = v else intervalB = v
            refresh()
        }

        npMin.setOnValueChangedListener { _, _, _ ->
            limitToSixty(npMin, npSec, minuteMoved = true)
            readWheels()
        }
        npSec.setOnValueChangedListener { _, _, _ ->
            limitToSixty(npMin, npSec, minuteMoved = false)
            readWheels()
        }

        boxA.setOnClickListener { intervalActive = 1; loadWheels(); refresh() }
        boxB.setOnClickListener { intervalActive = 2; loadWheels(); refresh() }

        btnMinus.setOnClickListener {
            intervalRepeat = maxOf(1, intervalRepeat - 1)
            refresh()
        }
        btnPlus.setOnClickListener {
            intervalRepeat = minOf(IntervalStore.MAX_REPEAT, intervalRepeat + 1)
            refresh()
        }

        resetInterval = {
            if (intervalActive == 1) intervalA = 0 else intervalB = 0
            loadWheels()
            refresh()
        }

        loadWheels()
        refresh()
    }

    /**
     * Judul "Timer v" / "Interval v" yang bisa diketuk: membuka menu mini
     * (di dalam kartu yang sama) untuk pindah halaman. Halaman yang tidak
     * aktif INVISIBLE (bukan GONE) supaya ukuran kartu tidak loncat.
     */
    private fun setupPageSwitch(view: View, onPageChanged: () -> Unit) {
        val btnSwitch: View = view.findViewById(R.id.btnPageSwitch)
        val txtTitle: TextView = view.findViewById(R.id.txtPopupTitle)
        val imgArrow: ImageView = view.findViewById(R.id.imgPageArrow)
        val menu: View = view.findViewById(R.id.menuPage)
        val itemTimer: TextView = view.findViewById(R.id.itemPageTimer)
        val itemInterval: TextView = view.findViewById(R.id.itemPageInterval)
        val pageTimer: View = view.findViewById(R.id.pageTimer)
        val pageInterval: View = view.findViewById(R.id.pageInterval)

        fun setMenuOpen(open: Boolean) {
            menu.visibility = if (open) View.VISIBLE else View.GONE
            imgArrow.animate().rotation(if (open) 180f else 0f).setDuration(150L).start()
        }

        fun showPage(page: Int) {
            currentPage = page
            val onTimer = page == PAGE_TIMER
            pageTimer.visibility = if (onTimer) View.VISIBLE else View.INVISIBLE
            pageInterval.visibility = if (onTimer) View.INVISIBLE else View.VISIBLE
            txtTitle.text = if (onTimer) "Timer" else "Interval"
            itemTimer.isSelected = onTimer
            itemInterval.isSelected = !onTimer
            onPageChanged()
        }

        btnSwitch.setOnClickListener { setMenuOpen(menu.visibility != View.VISIBLE) }
        itemTimer.setOnClickListener { showPage(PAGE_TIMER); setMenuOpen(false) }
        itemInterval.setOnClickListener { showPage(PAGE_INTERVAL); setMenuOpen(false) }

        showPage(currentPage)
    }

    private fun removePopup() {
        popupView?.let {
            windowManager?.removeView(it)
        }
        popupView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removePopup()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
