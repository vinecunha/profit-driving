package com.profitdriving

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView


import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat


enum class Screen {
    HOME, PARAMS, MY_DAY, COSTS, ANALYSIS
}

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeMode()
        super.onCreate(savedInstanceState)
    }

    private fun applyThemeMode() {
        val mode = SecurePreferences.get(this).getInt(KEY_THEME_MODE, THEME_MODE_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    protected fun setupToolbar(
        title: String? = null,
        showBack: Boolean = false,
        showLogo: Boolean = false,
        actionText: String? = null,
        actionListener: (() -> Unit)? = null,
        backListener: (() -> Unit)? = null,
        accessibilityBadgeText: String? = null
    ) {
        val btnBack = findViewById<TextView>(R.id.btnBack)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val layoutLogo = findViewById<View>(R.id.layoutLogo)
        val btnAction = findViewById<TextView>(R.id.btnAction)
        val tvAccessibilityBadge = findViewById<TextView>(R.id.tvAccessibilityBadge)

        if (showLogo) {
            layoutLogo?.visibility = View.VISIBLE
            btnBack?.visibility = View.GONE
        } else {
            layoutLogo?.visibility = View.GONE
            btnBack?.visibility = if (showBack) View.VISIBLE else View.GONE
            btnBack?.setOnClickListener {
                (backListener ?: { finish() }).invoke()
            }
        }

        if (title != null) {
            tvTitle?.text = title
            tvTitle?.visibility = View.VISIBLE
        } else {
            tvTitle?.visibility = View.GONE
        }

        if (actionText != null && actionListener != null) {
            btnAction?.text = actionText
            btnAction?.visibility = View.VISIBLE
            btnAction?.setOnClickListener { actionListener() }
        } else {
            btnAction?.visibility = View.GONE
        }

        if (accessibilityBadgeText != null) {
            tvAccessibilityBadge?.text = accessibilityBadgeText
            tvAccessibilityBadge?.visibility = View.VISIBLE
        } else {
            tvAccessibilityBadge?.visibility = View.GONE
        }
    }

    protected fun setupBottomNav(currentScreen: Screen) {
        val btnHome = findViewById<LinearLayout>(R.id.btnNavHome) ?: return
        val btnParams = findViewById<LinearLayout>(R.id.btnNavParams) ?: return
        val btnMyDay = findViewById<LinearLayout>(R.id.btnNavMyDay) ?: return
        val btnCosts = findViewById<LinearLayout>(R.id.btnNavCosts) ?: return
        val btnAnalysis = findViewById<LinearLayout>(R.id.btnNavAnalysis) ?: return

        val iconHome = findViewById<TextView>(R.id.tvNavHomeIcon)
        val labelHome = findViewById<TextView>(R.id.tvNavHomeLabel)
        val iconParams = findViewById<TextView>(R.id.tvNavParamsIcon)
        val labelParams = findViewById<TextView>(R.id.tvNavParamsLabel)
        val iconMyDay = findViewById<TextView>(R.id.tvNavMyDayIcon)
        val labelMyDay = findViewById<TextView>(R.id.tvNavMyDayLabel)
        val iconCosts = findViewById<TextView>(R.id.tvNavCostsIcon)
        val labelCosts = findViewById<TextView>(R.id.tvNavCostsLabel)
        val iconAnalysis = findViewById<TextView>(R.id.tvNavAnalysisIcon)
        val labelAnalysis = findViewById<TextView>(R.id.tvNavAnalysisLabel)

        fun resetAll() {
            val secondary = ctxColor(R.color.text_secondary)
            listOf(labelHome, labelParams, labelMyDay, labelCosts, labelAnalysis).forEach {
                it?.setTextColor(secondary)
            }
            listOf(iconHome, iconParams, iconMyDay, iconCosts, iconAnalysis).forEach {
                it?.alpha = 0.5f
            }
            listOf(btnHome, btnParams, btnMyDay, btnCosts, btnAnalysis).forEach {
                it?.setBackgroundResource(android.R.color.transparent)
            }
        }

        resetAll()

        val accent = ctxColor(R.color.accent)

        when (currentScreen) {
            Screen.HOME -> {
                iconHome?.alpha = 1.0f
                iconHome?.setTextColor(accent)
                labelHome?.setTextColor(accent)
            }
            Screen.PARAMS -> {
                iconParams?.alpha = 1.0f
                iconParams?.setTextColor(accent)
                labelParams?.setTextColor(accent)
            }
            Screen.MY_DAY -> {
                iconMyDay?.alpha = 1.0f
                iconMyDay?.setTextColor(accent)
                labelMyDay?.setTextColor(accent)
            }
            Screen.COSTS -> {
                iconCosts?.alpha = 1.0f
                iconCosts?.setTextColor(accent)
                labelCosts?.setTextColor(accent)
            }
            Screen.ANALYSIS -> {
                iconAnalysis?.alpha = 1.0f
                iconAnalysis?.setTextColor(accent)
                labelAnalysis?.setTextColor(accent)
            }
        }

        btnHome.setOnClickListener {
            if (currentScreen != Screen.HOME) {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            }
        }

        btnParams.setOnClickListener {
            if (currentScreen != Screen.PARAMS) {
                startActivity(Intent(this, SettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            }
        }

        btnMyDay.setOnClickListener {
            if (currentScreen != Screen.MY_DAY) {
                startActivity(Intent(this, MyDayActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            }
        }

        btnCosts.setOnClickListener {
            if (currentScreen != Screen.COSTS) {
                startActivity(Intent(this, CostsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            }
        }

        btnAnalysis.setOnClickListener {
            if (currentScreen != Screen.ANALYSIS) {
                startActivity(Intent(this, AnalysisActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                })
            }
        }
    }

    protected fun spFromRes(dimenRes: Int): Float =
        resources.getDimension(dimenRes) / resources.displayMetrics.scaledDensity

    protected fun dimenPx(dimenRes: Int): Float =
        resources.getDimensionPixelSize(dimenRes).toFloat()

    protected fun ctxColor(id: Int) = ContextCompat.getColor(this, id)

    protected fun ctxColor(id: Int, alpha: Float) = run {
        val color = ContextCompat.getColor(this, id)
        (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
    }

    companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val THEME_MODE_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        const val THEME_MODE_LIGHT = AppCompatDelegate.MODE_NIGHT_NO
        const val THEME_MODE_DARK = AppCompatDelegate.MODE_NIGHT_YES
    }
}
