package com.example.timerapp

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.Log

/**
 * Memutar beep pendek (res/raw/beep_interval.mp3) SEKALI saja, tiap pergantian
 * antar interval. Lewat stream ALARM, sama seperti AlarmPlayer, jadi tetap
 * terdengar walau HP di mode silent. Tidak memakai audio focus supaya musik
 * yang sedang diputar tidak ikut berhenti.
 *
 * Pemanggil (service) yang menentukan kapan bunyi dihentikan: stop() dipanggil
 * tepat saat jeda 1 detik selesai.
 */
class BeepPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    // Naik tiap start()/stop(). Callback dari percobaan lama dibatalkan kalau nomornya beda.
    private var generation = 0

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Mulai bunyi sekali. Aman dipanggil berulang (yang lama dihentikan dulu). */
    fun start() {
        stop()
        val gen = generation

        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attributes)
            // Jaga CPU tetap hidup selama beep bunyi (layar mati)
            mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)

            context.resources.openRawResourceFd(R.raw.beep_interval).use { fd ->
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            mp.isLooping = false
            mp.setOnPreparedListener { prepared ->
                if (gen != generation) {
                    // Sudah di-stop selagi menyiapkan nada
                    try {
                        prepared.release()
                    } catch (e: Exception) {
                        // diabaikan
                    }
                    return@setOnPreparedListener
                }
                try {
                    player = prepared
                    prepared.start()
                } catch (e: Exception) {
                    Log.e(TAG, "start gagal", e)
                }
            }
            mp.setOnCompletionListener { finished ->
                // Beep habis sendiri: lepaskan sumber dayanya
                if (player === finished) player = null
                try {
                    finished.release()
                } catch (e: Exception) {
                    // diabaikan
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyiapkan beep", e)
            try {
                mp.release()
            } catch (e2: Exception) {
                // diabaikan
            }
        }
    }

    /** Berhenti dan lepaskan semua sumber daya. */
    fun stop() {
        generation++
        val mp = player
        player = null
        if (mp != null) {
            try {
                mp.release()
            } catch (e: Exception) {
                Log.e(TAG, "release gagal", e)
            }
        }
    }

    private companion object {
        const val TAG = "BeepPlayer"
    }
}
