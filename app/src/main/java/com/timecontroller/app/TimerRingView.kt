package com.timecontroller.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Ring progress lingkaran untuk mode Timer, meniru tampilan SVG di mockup:
 * lingkaran track abu-abu di belakang, lingkaran progress merah di depan
 * yang mengecil searah jarum jam mulai dari jam 12.
 *
 * progress = 1f (penuh, waktu masih banyak) sampai 0f (habis).
 */
class TimerRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val strokeWidthPx = context.resources.displayMetrics.density * 7f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = ContextCompat.getColor(context, R.color.ring_track)
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = ContextCompat.getColor(context, R.color.accent)
        strokeCap = Paint.Cap.ROUND
    }

    private val bounds = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = strokeWidthPx / 2f
        bounds.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawOval(bounds, trackPaint)
        // Mulai dari jam 12 (-90 derajat), searah jarum jam, panjang busur = progress
        val sweep = 360f * progress
        canvas.drawArc(bounds, -90f, sweep, false, progressPaint)
    }
}
