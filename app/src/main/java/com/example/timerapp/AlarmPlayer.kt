package com.example.timerapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.Log

/**
 * Memutar nada alarm bawaan app (res/raw/alarm_timer.mp3, dipilih sendiri oleh
 * user) secara berulang, lewat stream ALARM (ikut volume alarm HP, bukan
 * volume media). Nadanya selalu sama tiap alarm berbunyi -- tidak bergantung
 * pada nada alarm sistem yang bisa beda-beda tergantung pengaturan tiap HP.
 *
 * Nada disiapkan lewat prepareAsync() supaya main thread tidak tertahan.
 * Semua error ditangkap supaya tidak pernah crash.
 */
class AlarmPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    // Naik tiap start()/stop(). Callback dari percobaan lama dibatalkan kalau nomornya beda.
    private var generation = 0

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Mulai bunyi. Aman dipanggil berulang (yang lama dihentikan dulu). */
    fun start() {
        stop()
        val gen = generation
        requestFocus()

        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attributes)
            // Jaga CPU tetap hidup selama nada bunyi (layar mati)
            mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)

            context.resources.openRawResourceFd(R.raw.alarm_timer).use { fd ->
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            mp.isLooping = true
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
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyiapkan nada alarm", e)
            try {
                mp.release()
            } catch (e2: Exception) {
                // diabaikan
            }
            abandonFocus()
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
        abandonFocus()
    }

    private fun requestFocus() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .build()
            am.requestAudioFocus(request)
            focusRequest = request
        } catch (e: Exception) {
            // Tidak dapat focus pun alarm tetap dibunyikan
            Log.e(TAG, "requestFocus gagal", e)
        }
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.abandonAudioFocusRequest(request)
        } catch (e: Exception) {
            // diabaikan
        }
    }

    private companion object {
        const val TAG = "AlarmPlayer"
    }
}
