package com.example.timerapp

import android.content.Context

/**
 * Menyimpan "timer sedang jalan dan habis jam berapa" ke SharedPreferences.
 *
 * KENAPA PERLU? Keadaan timer (TimerEngine) hidup di memori service. Kalau
 * sistem (misalnya ColorOS saat layar mati) mematikan proses app, memori itu
 * hilang. Waktu alarm sistem berbunyi, proses baru tidak tahu ada timer yang
 * sedang jalan, jadi alarm diam saja. Dengan waktu habis tersimpan di sini,
 * proses baru bisa memulihkan timer dan tetap membunyikan alarm.
 *
 * Disimpan sebagai jam dinding (System.currentTimeMillis), bukan
 * elapsedRealtime, supaya tetap bermakna walau prosesnya baru.
 */
object RunningStore {

    private const val PREFS_NAME = "timer_prefs"
    private const val KEY_END_WALL_MS = "running_end_wall_ms"

    /** Jam dinding (ms) saat timer habis, atau 0 kalau tidak ada timer yang jalan. */
    fun getEndWall(context: Context): Long =
        prefs(context).getLong(KEY_END_WALL_MS, 0L)

    fun setEndWall(context: Context, endWallMs: Long) {
        prefs(context).edit().putLong(KEY_END_WALL_MS, endWallMs).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_END_WALL_MS).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
