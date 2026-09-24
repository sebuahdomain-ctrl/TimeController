package com.example.timerapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.util.Log

/**
 * Memutar nada alarm berulang lewat stream ALARM (ikut volume alarm HP,
 * bukan volume media). Semua error ditangkap supaya tidak pernah crash.
 *
 * Nada dicoba berurutan: alarm milik user -> alarm default -> notifikasi ->
 * ringtone -> nada bawaan app (res/raw/alarm_beep.wav). Nada terakhir selalu
 * ada di dalam app, jadi alarm tidak mungkin bisu hanya karena nada sistem
 * tidak bisa dibaca (izin, file hilang, dsb).
 *
 * Nada disiapkan lewat prepareAsync() supaya main thread tidak tertahan.
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
        playFrom(candidates(), 0, gen)
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

    /** null = pakai nada bawaan app di res/raw. */
    private fun candidates(): List<Uri?> {
        val list = ArrayList<Uri?>()
        fun add(block: () -> Uri?) {
            try {
                block()?.let { list.add(it) }
            } catch (e: Exception) {
                Log.e(TAG, "gagal ambil nada", e)
            }
        }
        add { RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) }
        add { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) }
        add { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }
        add { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) }
        list.add(null)
        return list
    }

    private fun playFrom(sources: List<Uri?>, index: Int, gen: Int) {
        if (gen != generation) return
        if (index >= sources.size) {
            Log.e(TAG, "Tidak ada nada yang bisa diputar")
            abandonFocus()
            return
        }

        val mp = MediaPlayer()
        var handled = false

        // Coba sumber berikutnya (hanya sekali per percobaan)
        fun next() {
            if (handled) return
            handled = true
            if (player === mp) player = null
            try {
                mp.release()
            } catch (e: Exception) {
                // diabaikan
            }
            playFrom(sources, index + 1, gen)
        }

        try {
            mp.setAudioAttributes(attributes)
            // Jaga CPU tetap hidup selama nada bunyi (layar mati)
            mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)

            val uri = sources[index]
            if (uri != null) {
                mp.setDataSource(context, uri)
            } else {
                context.resources.openRawResourceFd(R.raw.alarm_beep).use { fd ->
                    mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
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
                    next()
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra (sumber #$index)")
                next()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Sumber #$index gagal", e)
            next()
        }
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
