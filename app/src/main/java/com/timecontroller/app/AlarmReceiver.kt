package com.timecontroller.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Const.ACTION_TIMER_FINISHED -> showAlarmNotification(context)
            Const.ACTION_DISMISS_ALARM -> {
                NotificationManagerCompat.from(context).cancel(Const.NOTIF_ID_ALARM)
            }
        }
    }

    private fun showAlarmNotification(context: Context) {
        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = Const.ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, 10, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Const.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.timer_finished_title))
            .setContentText(context.getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.turn_off), dismissPendingIntent)
            .build()

        val manager = NotificationManagerCompat.from(context)
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            manager.notify(Const.NOTIF_ID_ALARM, notification)
        }

        // Notifikasi persisten dimatikan karena timer sudah selesai
        val stopIntent = Intent(context, TimerForegroundService::class.java).apply {
            action = Const.ACTION_STOP_SERVICE
        }
        context.startService(stopIntent)
    }
}
