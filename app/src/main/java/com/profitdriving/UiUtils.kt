package com.profitdriving

import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import kotlin.math.roundToInt

fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).roundToInt()

object UiUtils {

    fun createPillBackground(color: Int, radiusDp: Int = 14): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = radiusDp.dpToPx().toFloat()
            setColor(color)
        }
    }

    fun createBadgeBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 12.dpToPx().toFloat()
            setColor(color)
        }
    }

    fun setPillStyle(textView: TextView, selected: Boolean) {
        textView.setBackgroundResource(
            if (selected) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        textView.setTextColor(
            if (selected) AppColors.textInverse else AppColors.textSecondary
        )
    }

    fun showSnackbar(view: View, message: String, actionText: String? = null, actionListener: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        if (actionText != null && actionListener != null) {
            snackbar.setAction(actionText) { actionListener() }
                .setActionTextColor(AppColors.overlayWarning)
        }
        snackbar.show()
    }
}
