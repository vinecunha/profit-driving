package com.profitdriving

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.profitdriving.accessibility.RideAccessibilityServiceV2
import com.profitdriving.ui.permissions.PermissionsActivity
import com.profitdriving.ui.subscription.SubscriptionActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        preferenceManager = PreferenceManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, 1500)
    }

    private fun navigateToNextScreen() {
        val onboardingCompleted = preferenceManager.isOnboardingCompleted()
        val hasSeenPermissions = preferenceManager.hasSeenPermissionsScreen()
        val subscriptionActive = preferenceManager.isSubscriptionActive()
        val isServiceEnabled = isAccessibilityServiceEnabled()

        android.util.Log.d("Splash", "onboarding: $onboardingCompleted")
        android.util.Log.d("Splash", "permissions: $hasSeenPermissions")
        android.util.Log.d("Splash", "subscription: $subscriptionActive")
        android.util.Log.d("Splash", "service: $isServiceEnabled")

        when {
            !onboardingCompleted -> {
                android.util.Log.d("Splash", "→ Onboarding")
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
            }
            !hasSeenPermissions || !isServiceEnabled -> {
                android.util.Log.d("Splash", "→ Permissions")
                startActivity(Intent(this, PermissionsActivity::class.java))
                finish()
            }
            !subscriptionActive -> {
                android.util.Log.d("Splash", "→ Subscription")
                startActivity(Intent(this, SubscriptionActivity::class.java))
                finish()
            }
            else -> {
                android.util.Log.d("Splash", "→ Main")
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = ComponentName(this, RideAccessibilityServiceV2::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service.flattenToString()) == true
    }
}
