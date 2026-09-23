package com.timecontroller.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Wheel picker angka (0-59) yang digambar & di-scroll manual sepenuhnya oleh kita,
 * bukan turunan dari android.widget.NumberPicker.
 *
 * Alasan dibuat dari nol: NumberPicker bawaan Android punya minimumWidth internal
 * yang di-hardcode oleh sistem OS (beda-beda tiap vendor/skin seperti MIUI/HyperOS)
 * dan tidak bisa ditekan lewat layout_width parent-nya. Akibatnya kotak yang di
 * desain HTML seharusnya persis 84dp x 56dp jadi ikut melebar/menyempit sendiri,
 * bikin proporsi popup keliatan beda (lebih "kurus tinggi") dibanding desain asli.
 *
 * View ini mengontrol penuh ukurannya sendiri lewat onMeasure(), jadi berapa pun
 * yang diminta layout_width/layout_height akan dipatuhi persis.
 */
class WheelPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var minValue: Int = 0
    var maxValue: Int = 59

    /** Dipanggil setiap kali nilai yang berhenti di tengah (setelah snap) berubah. */
    var onValueChangeListener: ((newValue: Int) -> Unit)? = null

    private var value: Int = 0
    private var itemHeightPx: Int = 0

    // Offset scroll saat ini dalam px, relatif terhadap posisi "value" di tengah.
    // 0 berarti `value` persis di tengah. Positif = geser ke bawah (nilai berkurang).
    private var scrollOffsetPx: Float = 0f

    private val textPaintCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        isFakeBoldText = true
    }
    private val textPaintSide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x669CA3AF // text_gray dengan alpha diredupkan, sesuai efek fade desain
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    private val fadeMaskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var fadeShader: LinearGradient? = null

    private val scroller = OverScroller(context)
    private var isSettling = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            scrollOffsetPx += distanceY
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (itemHeightPx <= 0) return false
            scroller.forceFinished(true)
            scroller.fling(
                0, scrollOffsetPx.roundToInt(),
                0, velocityY.roundToInt(),
                0, 0,
                Int.MIN_VALUE, Int.MAX_VALUE
            )
            isSettling = true
            postInvalidateOnAnimation()
            return true
        }
    })

    private var defaultWidthPx = 0
    private var defaultHeightPx = 0

    init {
        // Ukuran default kalau tidak di-override lewat layout XML: 84dp x 56dp,
        // persis seperti spesifikasi di desain HTML.
        val density = resources.displayMetrics.density
        defaultWidthPx = (84 * density).roundToInt()
        defaultHeightPx = (56 * density).roundToInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(defaultWidthPx, widthMeasureSpec)
        val height = resolveSize(defaultHeightPx, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Setiap slot angka setinggi 1/3 dari tinggi box: atas (redup), tengah (aktif), bawah (redup) —
        // sama seperti proporsi wheel-mask-compact di HTML (fade 0-14% dan 86-100%, solid di tengah).
        itemHeightPx = (h / 2.6f).roundToInt().coerceAtLeast(1)
        textPaintCenter.textSize = h * 0.34f
        textPaintSide.textSize = h * 0.30f
        fadeShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0x00000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000.toInt()),
            floatArrayOf(0f, 0.16f, 0.84f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    fun setValue(newValue: Int, animate: Boolean = false) {
        val clamped = newValue.coerceIn(minValue, maxValue)
        value = clamped
        scrollOffsetPx = 0f
        scroller.forceFinished(true)
        invalidate()
    }

    fun getValue(): Int = value

    private val range get() = maxValue - minValue + 1

    private fun wrap(v: Int): Int {
        val r = range
        var m = (v - minValue) % r
        if (m < 0) m += r
        return m + minValue
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            if (!scroller.isFinished) {
                // fling sedang jalan, biarkan computeScroll yang menuntaskan lalu snap
            } else {
                settleToNearest()
            }
        }
        return handled || true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffsetPx = scroller.currY.toFloat()
            invalidate()
        } else if (isSettling) {
            isSettling = false
            settleToNearest()
        }
    }

    private fun settleToNearest() {
        if (itemHeightPx <= 0) return
        val steps = -(scrollOffsetPx / itemHeightPx).roundToInt()
        if (steps != 0) {
            value = wrap(value - steps)
            onValueChangeListener?.invoke(value)
        }
        val target = 0
        scroller.forceFinished(true)
        scroller.startScroll(0, scrollOffsetPx.roundToInt(), 0, target - scrollOffsetPx.roundToInt(), 180)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        if (itemHeightPx <= 0) return
        val cx = width / 2f
        val cy = height / 2f

        val layerId = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // Baris tengah (aktif, solid & jernih) + baris atas/bawah (redup, sesuai efek fade desain)
        val centerRowOffset = scrollOffsetPx % itemHeightPx
        val centerIndexShift = -(scrollOffsetPx / itemHeightPx).let {
            if (it >= 0) kotlin.math.floor(it).toInt() else kotlin.math.ceil(it).toInt()
        }

        // Gambar cukup baris agar menutupi seluruh tinggi box + buffer
        val rowsAbove = (height / (2f * itemHeightPx)).roundToInt() + 2
        for (i in -rowsAbove..rowsAbove) {
            val rowValue = wrap(value - (centerIndexShift + i))
            val rowY = cy + i * itemHeightPx + centerRowOffset
            if (rowY < -itemHeightPx || rowY > height + itemHeightPx) continue

            val distanceFromCenter = abs(rowY - cy)
            val paint = if (distanceFromCenter < itemHeightPx * 0.35f) textPaintCenter else textPaintSide
            val fontMetrics = paint.fontMetrics
            val baseline = rowY - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(TimerStopwatchEngine.formatTwoDigits(rowValue), cx, baseline, paint)
        }

        fadeShader?.let {
            fadeMaskPaint.shader = it
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadeMaskPaint)
        }

        canvas.restoreToCount(layerId)
    }
}
