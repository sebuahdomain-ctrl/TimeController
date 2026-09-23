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

    private val density = context.resources.displayMetrics.density

    // Warna gerigi dibuat lebih terang (dial_tick / dial_tick_major) dibanding
    // sebelumnya (ring_track), supaya tetap jelas kelihatan dari jarak jauh.
    private val tickMinorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.dial_tick)
        strokeWidth = density * 1.6f
        strokeCap = Paint.Cap.ROUND
    }

    private val tickMajorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.dial_tick_major)
        strokeWidth = density * 2.6f
        strokeCap = Paint.Cap.ROUND
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
        val tickRadius = radius * 0.90f
        val dotOrbitRadius = radius * 0.94f
        val dotRadius = density * 4.5f

        // 60 tick, tebal & terang tiap kelipatan 5 (menandai detik ke-5, 10, 15, ...)
        for (i in 0 until 60) {
            val angle = Math.toRadians((i * 6 - 90).toDouble())
            val isMajor = i % 5 == 0
            val len = if (isMajor) radius * 0.11f else radius * 0.05f
            val x1 = cx + (tickRadius - len) * cos(angle).toFloat()
            val y1 = cy + (tickRadius - len) * sin(angle).toFloat()
            val x2 = cx + tickRadius * cos(angle).toFloat()
            val y2 = cy + tickRadius * sin(angle).toFloat()
            val paint = if (isMajor) tickMajorPaint else tickMinorPaint
            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        // Titik merah berputar: 1 putaran penuh = 60 detik
        val secondsInMinute = elapsedSeconds % 60f
        val dotAngle = Math.toRadians((secondsInMinute * 6 - 90).toDouble())
        val dotX = cx + dotOrbitRadius * cos(dotAngle).toFloat()
        val dotY = cy + dotOrbitRadius * sin(dotAngle).toFloat()
        canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
    }
}
