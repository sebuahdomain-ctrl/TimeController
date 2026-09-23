package com.timecontroller.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dial bulat untuk mode Stopwatch: 60 tanda centang (tick) di tepi lingkaran
 * (mirip jam), dan satu titik merah yang berputar penuh setiap 60 detik,
 * meniru dial SVG di mockup.
 */
class StopwatchDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Detik berjalan (boleh pecahan), dipakai untuk memutar titik penanda. */
    var elapsedSeconds: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ring_track)
        strokeWidth = context.resources.displayMetrics.density * 1.5f
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) * 0.94f
        val tickRadius = radius * 0.92f
        val dotOrbitRadius = radius * 0.94f
        val dotRadius = context.resources.displayMetrics.density * 4f

        // 60 tick, tebal tiap kelipatan 5 (menandai detik ke-5, 10, 15, ...)
        for (i in 0 until 60) {
            val angle = Math.toRadians((i * 6 - 90).toDouble())
            val isMajor = i % 5 == 0
            val len = if (isMajor) radius * 0.09f else radius * 0.045f
            val x1 = cx + (tickRadius - len) * cos(angle).toFloat()
            val y1 = cy + (tickRadius - len) * sin(angle).toFloat()
            val x2 = cx + tickRadius * cos(angle).toFloat()
            val y2 = cy + tickRadius * sin(angle).toFloat()
            tickPaint.strokeWidth = if (isMajor) context.resources.displayMetrics.density * 2f
            else context.resources.displayMetrics.density * 1.2f
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // Titik merah berputar: 1 putaran penuh = 60 detik
        val secondsInMinute = elapsedSeconds % 60f
        val dotAngle = Math.toRadians((secondsInMinute * 6 - 90).toDouble())
        val dotX = cx + dotOrbitRadius * cos(dotAngle).toFloat()
        val dotY = cy + dotOrbitRadius * sin(dotAngle).toFloat()
        canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
    }
}
