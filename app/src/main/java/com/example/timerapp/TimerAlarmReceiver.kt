package com.example.timerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Penerima alarm sistem (AlarmManager) yang berbunyi tepat saat hitung mundur
 * habis. Tugasnya kecil: menahan HP tetap bangun beberapa detik, lalu
 * meneruskan ACTION_TIMER_DONE ke TimerForegroundService. Logika "selesai"
 * (pindah ke FINISHED, alarm berbunyi) tetap ada di service.
 *
 * PENTING: receiver ini TIDAK boleh berhenti hanya karena service belum
 * hidup. Kalau sistem mematikan proses app saat layar mati, alarm ini justru
 * datang ke proses yang baru; service harus dihidupkan lagi (lihat
 * TimerForegroundService.handleTimerDone, yang memulihkan timer dari RunningStore).
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimerForegroundService.ACTION_TIMER_DONE) return

        // Sistem cuma menahan HP tetap bangun selama onReceive() berjalan,
        // padahal service baru mulai bekerja sesaat sesudahnya. Jadi kita
        // tahan sendiri beberapa detik (otomatis lepas), sampai AlarmPlayer
        // mulai memutar dan MediaPlayer memegang wake lock-nya sendiri.
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimerApp:alarmHandoff")
            lock.setReferenceCounted(false)
            lock.acquire(HANDOFF_WAKE_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Wake lock handoff gagal", e)
        }

        // startForegroundService (bukan startService): boleh dari latar belakang
        // untuk alarm exact/alarm jam, dan bisa menghidupkan service yang sudah mati.
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimerForegroundService::class.java)
                    .setAction(TimerForegroundService.ACTION_TIMER_DONE)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai service alarm", e)
        }
    }

    private companion object {
        const val TAG = "TimerAlarmReceiver"
        const val HANDOFF_WAKE_MS = 10_000L
    }
}
