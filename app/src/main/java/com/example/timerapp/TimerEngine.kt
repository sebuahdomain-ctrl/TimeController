package com.example.timerapp

import android.os.SystemClock
import java.util.Locale

/** Empat keadaan timer. */
enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }

/**
 * Otak timer: menyimpan keadaan dan menghitung sisa waktu.
 * Tidak ada Handler, suara, atau notifikasi di sini (itu tugas service).
 * Semua dipanggil dari main thread.
 */
class TimerEngine {

    companion object {
        // Durasi default. Nanti popup Atur tinggal mengubah properti durationMs.
        const val DEFAULT_DURATION_MS = 5_000L

        /** Ubah milidetik jadi "mm:ss", dibulatkan ke ATAS (ceil) per detik. */
        fun formatTime(remainingMs: Long): String {
            val totalSeconds = (maxOf(remainingMs, 0L) + 999L) / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** Durasi hitung mundur. Gampang diganti (misal dari popup Atur). */
    var durationMs: Long = DEFAULT_DURATION_MS

    var state: TimerState = TimerState.IDLE
        private set

    // Waktu berakhir berbasis elapsedRealtime (dipakai saat RUNNING)
    private var endTimeMs: Long = 0L

    // Sisa waktu yang dibekukan (dipakai saat PAUSED)
    private var pausedRemainingMs: Long = 0L

    /** Sisa waktu dalam milidetik menurut keadaan sekarang. */
    fun remainingMs(): Long = when (state) {
        TimerState.IDLE -> durationMs
        TimerState.RUNNING -> maxOf(endTimeMs - SystemClock.elapsedRealtime(), 0L)
        TimerState.PAUSED -> pausedRemainingMs
        TimerState.FINISHED -> 0L
    }

    /** Aksi tombol Play/Pause sesuai tabel keadaan. */
    fun playPause() {
        when (state) {
            TimerState.IDLE, TimerState.FINISHED -> {
                endTimeMs = SystemClock.elapsedRealtime() + durationMs
                state = TimerState.RUNNING
            }
            TimerState.RUNNING -> {
                pausedRemainingMs = remainingMs() // hitung dulu selagi masih RUNNING
                state = TimerState.PAUSED
            }
            TimerState.PAUSED -> {
                endTimeMs = SystemClock.elapsedRealtime() + pausedRemainingMs
                state = TimerState.RUNNING
            }
        }
    }

    /** Kembali ke IDLE (00:05). Kalau sudah IDLE, tidak ada yang berubah. */
    fun reset() {
        state = TimerState.IDLE
    }

    /** Kalau RUNNING dan waktunya habis, pindah ke FINISHED. Return true kalau baru pindah. */
    fun finishIfDue(): Boolean {
        if (state == TimerState.RUNNING && endTimeMs - SystemClock.elapsedRealtime() <= 0L) {
            state = TimerState.FINISHED
            return true
        }
        return false
    }

    /** Berapa ms lagi sampai angka detik yang tampil berganti (minimal 1 ms). */
    fun msUntilNextTick(): Long {
        val remaining = endTimeMs - SystemClock.elapsedRealtime()
        if (remaining <= 0L) return 1L
        val part = remaining % 1000L
        return if (part == 0L) 1000L else part
    }
}
