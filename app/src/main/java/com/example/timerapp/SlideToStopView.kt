package com.example.timerapp

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/**
 * Slider "Geser untuk matikan". Isi (diatur di activity_alarm.xml):
 * - @id/slideLabel : teks petunjuk, memudar saat tombol digeser
 * - @id/slideThumb : tombol bulat yang digeser
 *
 * Tombol harus digeser sampai [COMPLETE_FRACTION] jalur baru dianggap selesai;
 * kalau dilepas sebelum itu, tombol kembali ke awal. Jadi alarm tidak mati
 * karena tersentuh tidak sengaja (HP di saku / masih setengah tidur).
 */
class SlideToStopView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Dipanggil sekali saat tombol digeser sampai ujung. */
    var onSlideComplete: (() -> Unit)? = null

    private var thumb: View? = null
    private var label: View? = null
    private var startTouchX = 0f
    private var startTranslation = 0f
    private var dragging = false
    private var completed = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        thumb = findViewById(R.id.slideThumb)
        label = findViewById(R.id.slideLabel)
        thumb?.setOnTouchListener { v, event -> handleTouch(v, event) }
    }

    /** Jarak geser maksimum: lebar jalur dikurangi tombol dan padding. */
    private fun maxTravel(): Float {
        val t = thumb ?: return 0f
        return (width - paddingLeft - paddingRight - t.width).coerceAtLeast(0).toFloat()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(v: View, event: MotionEvent): Boolean {
        if (completed) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                startTouchX = event.rawX
                startTranslation = v.translationX
                v.animate().cancel()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                moveTo(startTranslation + (event.rawX - startTouchX))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                val max = maxTravel()
                if (max > 0f && v.translationX >= max * COMPLETE_FRACTION) {
                    completed = true
                    v.animate().translationX(max).setDuration(80).start()
                    label?.alpha = 0f
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onSlideComplete?.invoke()
                } else {
                    v.animate().translationX(0f).setDuration(180).start()
                    label?.animate()?.alpha(1f)?.setDuration(180)?.start()
                }
            }
        }
        return true
    }

    private fun moveTo(x: Float) {
        val max = maxTravel()
        val clamped = x.coerceIn(0f, max)
        thumb?.translationX = clamped
        label?.alpha = if (max > 0f) 1f - clamped / max else 1f
    }

    companion object {
        private const val COMPLETE_FRACTION = 0.85f
    }
}
