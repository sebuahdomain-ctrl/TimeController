package com.example.timerapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager

/**
 * Memutar nada alarm berulang lewat stream ALARM (ikut volume alarm HP,
 * bukan volume media). Semua error ditangkap supaya tidak pernah crash.
 */
class AlarmPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Mulai bunyi. Aman dipanggil berulang (yang lama dihentikan dulu). */
    fun start() {
        stop()
        try {
            requestFocus()
            // Urutan nada: alarm milik user -> alarm default -> notifikasi -> ringtone
            val candidates = listOfNotNull(
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            )
            for (uri in candidates) {
                if (tryPlay(uri)) return
            }
            // Tidak ada nada yang bisa diputar
            abandonFocus()
        } catch (e: Exception) {
            stop()
        }
    }

    /** Berhenti dan lepaskan semua sumber daya. */
    fun stop() {
        val mp = player
        player = null
        if (mp != null) {
            try {
                mp.release()
            } catch (e: Exception) {
                // diabaikan
            }
        }
        abandonFocus()
    }

    private fun tryPlay(uri: Uri): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(attributes)
            // Jaga CPU tetap hidup selama nada bunyi (layar mati)
            mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(context, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
            true
        } catch (e: Exception) {
            try {
                mp.release()
            } catch (e2: Exception) {
                // diabaikan
            }
            false
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
}
