package com.profitdriving

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import androidx.viewpager2.widget.ViewPager2
import com.profitdriving.ui.permissions.PermissionsActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var viewPager: ViewPager2
    private lateinit var buttonNext: Button
    private lateinit var dotsContainer: LinearLayout

    private val onboardingItems = listOf(
        OnboardingItem(
            imageRes = R.drawable.ic_onboarding_car,
            title = "Maximize seus ganhos no Uber",
            description = "Acompanhe automaticamente suas corridas e saiba exatamente quanto você está ganhando por km e por hora",
            backgroundColor = R.color.success
        ),
        OnboardingItem(
            imageRes = R.drawable.ic_accessibility,
            title = "Permissão necessária",
            description = "O app precisa da permissão de acessibilidade para ler os dados das corridas na tela do Uber. Seus dados são 100% privados e ficam apenas no seu dispositivo.",
            backgroundColor = R.color.accent,
            showPrivacyBadge = true
        ),
        OnboardingItem(
            imageRes = R.drawable.ic_chart,
            title = "Veja seu lucro em tempo real",
            description = "Calcule automaticamente:",
            backgroundColor = R.color.primary,
            showBenefits = true
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        preferenceManager = PreferenceManager(this)
        viewPager = findViewById(R.id.viewPager)
        buttonNext = findViewById(R.id.buttonNext)
        dotsContainer = findViewById(R.id.dotsContainer)

        L.d(TAG, "OnboardingActivity iniciada com ${onboardingItems.size} slides")

        setupViewPager()
        setupDots()
        updateButton(0)

        buttonNext.setOnClickListener {
            val current = viewPager.currentItem
            if (current == onboardingItems.size - 1) {
                finishOnboarding()
            } else {
                viewPager.currentItem = current + 1
            }
        }
    }

    private fun finishOnboarding() {
        L.d(TAG, "=== FINALIZANDO ONBOARDING ===")

        preferenceManager.setOnboardingCompleted(true)
        L.d(TAG, "onboarding_completed = true")

        L.d(TAG, "Abrindo PermissionsActivity...")
        startActivity(Intent(this, PermissionsActivity::class.java))

        L.d(TAG, "Finalizando OnboardingActivity")
        finish()
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(onboardingItems)
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateButton(position)
            }
        })
    }

    private fun setupDots() {
        for (i in onboardingItems.indices) {
            val dot = TextView(this).apply {
                text = "●"
                textSize = 18f
                setTextColor(
                    if (i == 0) AppColors.textInverse
                    else AppColors.textDisabled
                )
                setPadding(8, 0, 8, 0)
            }
            dotsContainer.addView(dot)
        }
    }

    private fun updateDots(position: Int) {
        for (i in 0 until dotsContainer.childCount) {
            val dot = dotsContainer.getChildAt(i) as TextView
            dot.setTextColor(
                if (i == position) AppColors.textInverse
                else AppColors.textDisabled
            )
        }
    }

    private fun updateButton(position: Int) {
        if (position == onboardingItems.size - 1) {
            buttonNext.text = "Começar"
        } else {
            buttonNext.text = "Próximo"
        }
    }

    companion object {
        private const val TAG = "OnboardingActivity"
    }
}
