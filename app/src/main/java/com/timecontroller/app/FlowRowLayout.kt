package com.timecontroller.app

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup

/**
 * ViewGroup sederhana yang menata anak-anaknya berjajar ke kanan dan otomatis
 * pindah baris kalau sudah tidak muat, meniru "display:flex; flex-wrap:wrap"
 * pada daftar preset di mockup HTML. Tidak ada layout bawaan Android View
 * (non-Compose) yang punya perilaku ini di luar library tambahan.
 */
class FlowRowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        var rowWidth = 0
        var rowHeight = 0
        var totalHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (rowWidth + childWidth > maxWidth && rowWidth > 0) {
                totalHeight += rowHeight
                rowWidth = childWidth
                rowHeight = childHeight
            } else {
                rowWidth += childWidth
                rowHeight = maxOf(rowHeight, childHeight)
            }
        }
        totalHeight += rowHeight

        setMeasuredDimension(maxWidth, totalHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l
        var x = 0
        var y = 0
        var rowHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (x + childWidth > maxWidth && x > 0) {
                x = 0
                y += rowHeight
                rowHeight = 0
            }

            child.layout(x, y, x + childWidth, y + childHeight)
            x += childWidth
            rowHeight = maxOf(rowHeight, childHeight)
        }
    }
}
