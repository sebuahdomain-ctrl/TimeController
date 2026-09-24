package com.timecontroller.app

import android.content.Intent
import android.os.Bundle
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TimerPopupActivity : AppCompatActivity() {

    private lateinit var minutePicker: NumberPicker
    private lateinit var secondPicker: NumberPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Supaya popup ini bisa tampil walau app sedang di background / di atas app lain
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_timer_popup)

        val prefs = getSharedPreferences(Const.PREFS_NAME, MODE_PRIVATE)
        val currentTotal = prefs.getLong(Const.PREF_TOTAL_SECONDS, 0)

        minutePicker = findViewById(R.id.minutePicker)
        secondPicker = findViewById(R.id.secondPicker)

        minutePicker.minValue = 0
        minutePicker.maxValue = 59
        minutePicker.value = (currentTotal / 60).toInt()

        secondPicker.minValue = 0
        secondPicker.maxValue = 59
        secondPicker.value = (currentTotal % 60).toInt()

        findViewById<TextView>(R.id.closeButton).setOnClickListener { finish() }

        findViewById<TextView>(R.id.resetButton).setOnClickListener {
            minutePicker.value = 0
            secondPicker.value = 0
        }

        findViewById<TextView>(R.id.startButton).setOnClickListener {
            val totalSeconds = minutePicker.value * 60 + secondPicker.value
            val intent = Intent(this, TimerForegroundService::class.java).apply {
                action = Const.ACTION_SET_DURATION
                putExtra(Const.EXTRA_SECONDS, totalSeconds)
            }
            startService(intent)
            finish()
        }
    }
}
