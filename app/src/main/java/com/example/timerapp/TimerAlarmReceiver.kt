package com.example.timerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * Penerima alarm sistem (AlarmManager) yang berbunyi tepat saat hitung mundur
 * habis. Tugasnya kecil: menahan HP tetap bangun beberapa detik, lalu
 * meneruskan ACTION_TIMER_DONE ke TimerForegroundService. Logika "selesai"
 * (pindah ke FINISHED, alarm berbunyi) tetap ada di service.
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimerForegroundService.ACTION_TIMER_DONE) return

        // Service sudah mati? Abaikan saja, jangan crash.
        if (!TimerForegroundService.isRunning) return

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
            // Tanpa wake lock pun tetap coba teruskan ke service
        }

        try {
            context.startService(
                Intent(context, TimerForegroundService::class.java)
                    .setAction(TimerForegroundService.ACTION_TIMER_DONE)
            )
        } catch (e: Exception) {
            // Sistem menolak start service: abaikan
        }
    }

    private companion object {
        const val HANDOFF_WAKE_MS = 10_000L
    }
}
