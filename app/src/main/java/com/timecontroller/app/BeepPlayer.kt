package com.timecontroller.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Padanan native dari fungsi playBeep() Web Audio API di desain HTML.
 * Meniru persis: frekuensi, durasi, tipe gelombang (sine/triangle/sawtooth),
 * dan envelope gain (mulai 0.08, meluruh eksponensial ke ~0).
 * Dijalankan di STREAM_MUSIC, generate PCM 16-bit sendiri lewat AudioTrack.
 */
object BeepPlayer {

    enum class Wave { SINE, TRIANGLE, SAWTOOTH }

    private const val SAMPLE_RATE = 44100

    fun beep(freq: Double = 880.0, durationSec: Double = 0.12, type: Wave = Wave.SINE) {
        thread {
            try {
                val numSamples = (SAMPLE_RATE * durationSec).toInt()
                if (numSamples <= 0) return@thread
                val buffer = ShortArray(numSamples)
                val startGain = 0.08
                // Meniru gain.exponentialRampToValueAtTime(0.0001, ...): peluruhan eksponensial
                val endGain = 0.0001
                val decayRate = Math.log(endGain / startGain)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val phase = (freq * t) % 1.0
                    val raw = when (type) {
                        Wave.SINE -> sin(2.0 * PI * freq * t)
                        Wave.TRIANGLE -> 2.0 * Math.abs(2.0 * phase - 1.0) - 1.0
                        Wave.SAWTOOTH -> 2.0 * phase - 1.0
                    }
                    val gain = startGain * Math.exp(decayRate * (t / durationSec))
                    val sample = (raw * gain).coerceIn(-1.0, 1.0)
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()

                Thread.sleep((durationSec * 1000).toLong() + 60)
                track.stop()
                track.release()
            } catch (e: Exception) {
                // Gagal main suara tidak boleh mengganggu fungsi utama - aman diabaikan
            }
        }
    }
}
