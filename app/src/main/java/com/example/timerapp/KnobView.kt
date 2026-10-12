package com.example.timerapp

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

/**
 * Knob timer di halaman utama (sama dengan icon app): busur progres merah,
 * wajah jam dengan 12 tanda, dan jarum. Nonaktif = busur kosong + jarum abu
 * di posisi awal; aktif = busur 180 derajat merah + jarum putih.
 */
class KnobView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colorRed = ContextCompat.getColor(context, R.color.alarm_red)
    private val colorTrack = ContextCompat.getColor(context, R.color.alarm_track_stroke)
    private val colorFace = ContextCompat.getColor(context, R.color.alarm_track)
    private val colorTick = 0xFF6E6E76.toInt()
    private val colorIdle = 0xFF8E8E93.toInt()
    private val colorActive = 0xFFFFFFFF.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    // 0f = nonaktif, 1f = aktif
    private var progress = 0f
    private var animator: ValueAnimator? = null

    fun setActive(active: Boolean, animate: Boolean = true) {
        val target = if (active) 1f else 0f
        animator?.cancel()
        if (!animate || !isLaidOut) {
            progress = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = when {
            MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY -> w
            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY -> h
            else -> (240 * resources.displayMetrics.density).toInt()
        }
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val s = width / 200f
        val cx = width / 2f
        val cy = height / 2f

        // Busur jalur + progres (mulai kiri-bawah, 270 derajat searah jarum jam)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 12f * s
        val r = 80f * s
        rect.set(cx - r, cy - r, cx + r, cy + r)
        paint.color = colorTrack
        canvas.drawArc(rect, 135f, 270f, false, paint)
        paint.color = colorRed
        canvas.drawArc(rect, 135f, 0.1f + progress * 180f, false, paint)

        // Wajah knob
        paint.style = Paint.Style.FILL
        paint.color = colorFace
        canvas.drawCircle(cx, cy, 58f * s, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * s
        paint.color = colorTrack
        canvas.drawCircle(cx, cy, 58f * s, paint)

        // 12 tanda jam
        paint.strokeWidth = 3f * s
        paint.color = colorTick
        for (i in 0 until 12) {
            canvas.save()
            canvas.rotate(i * 30f, cx, cy)
            canvas.drawLine(cx, cy - 50f * s, cx, cy - 44f * s, paint)
            canvas.restore()
        }

        // Jarum: -135 derajat (nonaktif) ke 45 derajat (aktif)
        val handColor = ArgbEvaluator().evaluate(progress, colorIdle, colorActive) as Int
        paint.color = handColor
        paint.strokeWidth = 8f * s
        canvas.save()
        canvas.rotate(-135f + progress * 180f, cx, cy)
        canvas.drawLine(cx, cy, cx, cy - 34f * s, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 7f * s, paint)
    }
}
