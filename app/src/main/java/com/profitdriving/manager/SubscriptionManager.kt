package com.profitdriving.manager

import android.content.Context
import com.profitdriving.BuildConfig
import com.profitdriving.PreferenceManager

class SubscriptionManager(private val context: Context) {

    private val prefs = PreferenceManager(context)

    fun isSubscriptionActive(): Boolean {
        if (BuildConfig.DEBUG && prefs.isDebugSubscriptionEnabled()) {
            return true
        }
        return prefs.isSubscriptionActive()
    }

    fun activateSubscription(plan: String = "monthly") {
        if (BuildConfig.DEBUG) {
            prefs.enableDebugSubscription()
            prefs.setSubscriptionActive(true)
            prefs.setSubscriptionPlan(plan)
        } else {
            throw IllegalStateException("Use Google Play Billing em producao")
        }
    }

    fun startFreeTrial(days: Int = 30) {
        if (BuildConfig.DEBUG) {
            prefs.startFreeTrial(days)
            prefs.setSubscriptionActive(true)
        }
    }

    fun deactivateSubscription() {
        if (BuildConfig.DEBUG) {
            prefs.disableDebugSubscription()
            prefs.setSubscriptionActive(false)
        }
    }

    fun isDebugMode(): Boolean {
        return BuildConfig.DEBUG
    }

    fun getStatusText(): String {
        return if (isSubscriptionActive()) {
            if (BuildConfig.DEBUG && prefs.isDebugSubscriptionEnabled()) {
                "Ativa (Debug)"
            } else {
                val days = prefs.getRemainingTrialDays()
                if (days > 0) "Ativa (teste: $days dias restantes)"
                else "Ativa"
            }
        } else {
            "Inativa"
        }
    }

    fun resetSubscription() {
        if (BuildConfig.DEBUG) {
            prefs.disableDebugSubscription()
            prefs.setSubscriptionActive(false)
        }
    }
}
