package com.example.timerapp

import android.content.Context

/**
 * Satu-satunya tempat menyimpan "durasi terpilih" (durasi yang dipilih user
 * lewat popup Atur). Disimpan di SharedPreferences, jadi tetap diingat
 * walau app ditutup atau HP dihidupkan ulang.
 *
 * Durasi terpilih dipakai untuk:
 * - nilai awal kedua roda saat popup dibuka,
 * - angka yang tampil saat timer siap (IDLE),
 * - tujuan kembali saat tombol Reset di notifikasi ditekan.
 */
object DurationStore {

    private const val PREFS_NAME = "timer_prefs"
    private const val KEY_DURATION_MS = "duration_ms"

    /** Batas atas: 99 menit 59 detik (sesuai roda menit 0..99 dan detik 0..59). */
    const val MAX_DURATION_MS = (99 * 60 + 59) * 1000L

    /** Ambil durasi terpilih. Kalau belum pernah disimpan: DEFAULT_DURATION_MS. */
    fun get(context: Context): Long {
        val saved = prefs(context).getLong(KEY_DURATION_MS, TimerEngine.DEFAULT_DURATION_MS)
        // Jaga-jaga kalau isinya aneh (0 atau kelewat besar)
        return if (saved in 1L..MAX_DURATION_MS) saved else TimerEngine.DEFAULT_DURATION_MS
    }

    /** Simpan durasi terpilih (dalam milidetik). */
    fun set(context: Context, ms: Long) {
        prefs(context).edit()
            .putLong(KEY_DURATION_MS, ms.coerceIn(1L, MAX_DURATION_MS))
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
