package com.example.timerapp

import android.content.Context

/**
 * Menyimpan semua data mode Interval ke SharedPreferences:
 *
 * 1. KONFIGURASI: Interval 1, Interval 2 (detik; 0 = mati) dan jumlah putaran
 *    yang terakhir dipakai lewat tombol Mulai di halaman Interval. Dipakai
 *    sebagai isi awal popup dan sebagai sumber tombol Play saat IDLE.
 * 2. MODE: apakah yang terakhir dipilih user itu Interval (true) atau Timer
 *    biasa (false). Menentukan apa yang dilakukan tombol Play dan Reset.
 * 3. SESI: sesi interval yang sedang berjalan (putaran ke berapa, interval
 *    ke berapa). Dibaca popup supaya angka putaran bisa mundur, dan dipakai
 *    service untuk pulih kalau proses app dimatikan sistem.
 */
object IntervalStore {

    private const val PREFS_NAME = "timer_prefs"

    private const val KEY_A = "iv_a"
    private const val KEY_B = "iv_b"
    private const val KEY_REPEAT = "iv_repeat"
    private const val KEY_MODE = "iv_mode"

    private const val KEY_S_ACTIVE = "iv_s_active"
    private const val KEY_S_A = "iv_s_a"
    private const val KEY_S_B = "iv_s_b"
    private const val KEY_S_TOTAL = "iv_s_total"
    private const val KEY_S_ROUND = "iv_s_round"
    private const val KEY_S_PHASE = "iv_s_phase"

    const val DEFAULT_A = 60
    const val DEFAULT_B = 120
    const val DEFAULT_REPEAT = 4
    const val MAX_SECONDS = 60 * 60
    const val MAX_REPEAT = 99

    /** a, b dalam detik (b = 0 artinya Interval 2 mati), repeat = jumlah putaran. */
    data class Config(val a: Int, val b: Int, val repeat: Int)

    /** Sesi yang sedang jalan. round dan phase dimulai dari 1. */
    data class Session(val a: Int, val b: Int, val total: Int, val round: Int, val phase: Int) {
        /** Sisa putaran, termasuk putaran yang sedang berjalan. */
        val roundsLeft: Int get() = (total - round + 1).coerceAtLeast(1)
    }

    fun getConfig(context: Context): Config {
        val p = prefs(context)
        return Config(
            a = p.getInt(KEY_A, DEFAULT_A).coerceIn(0, MAX_SECONDS),
            b = p.getInt(KEY_B, DEFAULT_B).coerceIn(0, MAX_SECONDS),
            repeat = p.getInt(KEY_REPEAT, DEFAULT_REPEAT).coerceIn(1, MAX_REPEAT)
        )
    }

    fun setConfig(context: Context, a: Int, b: Int, repeat: Int) {
        prefs(context).edit()
            .putInt(KEY_A, a.coerceIn(0, MAX_SECONDS))
            .putInt(KEY_B, b.coerceIn(0, MAX_SECONDS))
            .putInt(KEY_REPEAT, repeat.coerceIn(1, MAX_REPEAT))
            .apply()
    }

    fun isIntervalMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MODE, false)

    fun setIntervalMode(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_MODE, on).apply()
    }

    fun getSession(context: Context): Session? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_S_ACTIVE, false)) return null
        val a = p.getInt(KEY_S_A, 0)
        val total = p.getInt(KEY_S_TOTAL, 0)
        if (a <= 0 || total <= 0) return null
        return Session(
            a = a,
            b = p.getInt(KEY_S_B, 0),
            total = total,
            round = p.getInt(KEY_S_ROUND, 1).coerceIn(1, total),
            phase = p.getInt(KEY_S_PHASE, 1).coerceIn(1, 2)
        )
    }

    fun saveSession(context: Context, a: Int, b: Int, total: Int, round: Int, phase: Int) {
        prefs(context).edit()
            .putBoolean(KEY_S_ACTIVE, true)
            .putInt(KEY_S_A, a)
            .putInt(KEY_S_B, b)
            .putInt(KEY_S_TOTAL, total)
            .putInt(KEY_S_ROUND, round)
            .putInt(KEY_S_PHASE, phase)
            .apply()
    }

    fun clearSession(context: Context) {
        prefs(context).edit().putBoolean(KEY_S_ACTIVE, false).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
