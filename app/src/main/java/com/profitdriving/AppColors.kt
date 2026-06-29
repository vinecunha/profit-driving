package com.profitdriving

import android.content.Context
import androidx.core.content.ContextCompat

object AppColors {
    private var ctx: Context? = null

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    private fun c(id: Int) = ContextCompat.getColor(ctx!!, id)

    val textPrimary by lazy { c(R.color.text_primary) }
    val textSecondary by lazy { c(R.color.text_secondary) }
    val textTertiary by lazy { c(R.color.text_tertiary) }
    val textInverse by lazy { c(R.color.text_inverse) }
    val accent by lazy { c(R.color.accent) }
    val success by lazy { c(R.color.success) }
    val warning by lazy { c(R.color.warning) }
    val error by lazy { c(R.color.error) }
    val bgScreen by lazy { c(R.color.bg_screen) }
    val bgCard by lazy { c(R.color.bg_card) }
    val bgSurface by lazy { c(R.color.bg_surface) }
    val border by lazy { c(R.color.border) }
    val primaryDark by lazy { c(R.color.primary_dark) }
    val successBg by lazy { c(R.color.success_bg) }
    val successText by lazy { c(R.color.success_text) }
    val warningBg by lazy { c(R.color.warning_bg) }
    val warningText by lazy { c(R.color.warning_text) }
    val errorBg by lazy { c(R.color.error_bg) }
    val errorText by lazy { c(R.color.error_text) }
    val overlayWarning by lazy { c(R.color.overlay_warning) }
    val metricGood by lazy { c(R.color.metric_good) }
    val metricMedium by lazy { c(R.color.metric_medium) }
    val metricBad by lazy { c(R.color.metric_bad) }
    val metricAbsent by lazy { c(R.color.metric_absent) }
    val serviceUberx by lazy { c(R.color.service_uberx) }
    val overlayBg by lazy { c(R.color.overlay_bg) }
    val textDisabled by lazy { c(R.color.text_disabled) }
    val pillActiveText by lazy { c(R.color.pill_active_text) }
    val pillInactiveText by lazy { c(R.color.pill_inactive_text) }
}
