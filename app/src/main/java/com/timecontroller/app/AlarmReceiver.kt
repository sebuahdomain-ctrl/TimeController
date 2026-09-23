package com.timecontroller.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Dipicu saat timer mencapai 00:00. Memutar suara alarm (loop, mengikuti
 * volume Alarm HP) dan menampilkan popup heads-up di atas layar dengan
 * tombol Matikan, mirip alarm bawaan Android.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmSoundPlayer.play(context)
        OverlayManager.showAlarm(context)
    }
}
