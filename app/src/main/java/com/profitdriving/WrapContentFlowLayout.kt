package com.profitdriving

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

class WrapContentFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams =
        MarginLayoutParams(p ?: LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var contentWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        if (contentWidth < 0) contentWidth = 0

        var totalHeight = paddingTop + paddingBottom
        var currentLineWidth = 0
        var currentLineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val lp = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + lp.leftMargin + lp.rightMargin
            val childHeight = child.measuredHeight + lp.topMargin + lp.bottomMargin

            if (currentLineWidth > 0 && currentLineWidth + childWidth > contentWidth) {
                totalHeight += currentLineHeight
                currentLineWidth = childWidth
                currentLineHeight = childHeight
            } else {
                currentLineWidth += childWidth
                currentLineHeight = maxOf(currentLineHeight, childHeight)
            }
        }

        if (currentLineHeight > 0) {
            totalHeight += currentLineHeight
        } else if (childCount == 0) {
            totalHeight = paddingTop + paddingBottom
        }

        val measuredWidth = resolveSize(contentWidth + paddingLeft + paddingRight, widthMeasureSpec)
        setMeasuredDimension(measuredWidth, totalHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = measuredWidth - paddingLeft - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            val lp = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + lp.leftMargin + lp.rightMargin
            val childHeight = child.measuredHeight + lp.topMargin + lp.bottomMargin

            if (x > paddingLeft && x + childWidth > width + paddingLeft) {
                x = paddingLeft
                y += if (lineHeight > 0) lineHeight else childHeight
                lineHeight = 0
            }

            val cx = x + lp.leftMargin
            val cy = y + lp.topMargin
            child.layout(cx, cy, cx + child.measuredWidth, cy + child.measuredHeight)

            x += childWidth
            lineHeight = maxOf(lineHeight, childHeight)
        }
    }
}
