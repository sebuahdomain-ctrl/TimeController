package com.timecontroller.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Memutar suara alarm secara berulang (loop) hingga dihentikan manual.
 * Menggunakan STREAM_ALARM, jadi volumenya mengikuti slider "Volume Alarm"
 * bawaan Android, bukan volume media/ringtone/notifikasi.
 */
object AlarmSoundPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    fun play(context: Context) {
        if (isPlaying) return
        try {
            val appContext = context.applicationContext
            val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(appContext, alarmUri)
                isLooping = true
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
            isPlaying = true
            startVibration(appContext)
        } catch (e: Exception) {
            isPlaying = false
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // aman diabaikan, mungkin sudah dihentikan
        }
        mediaPlayer = null
        isPlaying = false
        stopVibration()
    }

    private var vibrator: Vibrator? = null

    private fun startVibration(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, 0)
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    fun stopVibration() {
        vibrator?.cancel()
    }
}
