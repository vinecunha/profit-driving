package com.profitdriving

import android.content.Context

class PreferenceManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(PREF_ONBOARDING_DONE, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(PREF_ONBOARDING_DONE, completed).apply()
    }

    fun setAccessibilityGranted(granted: Boolean) {
        prefs.edit().putBoolean(PREF_ACCESSIBILITY_GRANTED, granted).apply()
    }

    fun isAccessibilityGranted(): Boolean {
        return prefs.getBoolean(PREF_ACCESSIBILITY_GRANTED, false)
    }

    fun setHasSeenPermissionsScreen(seen: Boolean) {
        prefs.edit().putBoolean(PREF_HAS_SEEN_PERMISSIONS, seen).apply()
    }

    fun hasSeenPermissionsScreen(): Boolean {
        return prefs.getBoolean(PREF_HAS_SEEN_PERMISSIONS, false)
    }

    fun setSubscriptionActive(active: Boolean) {
        prefs.edit().putBoolean(PREF_SUBSCRIPTION_ACTIVE, active).apply()
        if (!active) {
            prefs.edit().remove(PREF_SUBSCRIPTION_EXPIRY).apply()
        }
    }

    fun isSubscriptionActive(): Boolean {
        return prefs.getBoolean(PREF_SUBSCRIPTION_ACTIVE, false)
    }

    fun getSubscriptionPlan(): String? {
        return prefs.getString(PREF_SUBSCRIPTION_PLAN, null)
    }

    fun setSubscriptionPlan(plan: String) {
        prefs.edit().putString(PREF_SUBSCRIPTION_PLAN, plan).apply()
    }

    fun isDebugSubscriptionEnabled(): Boolean {
        return BuildConfig.DEBUG && prefs.getBoolean(PREF_SUBSCRIPTION_DEBUG, false)
    }

    fun enableDebugSubscription() {
        if (BuildConfig.DEBUG) {
            prefs.edit().putBoolean(PREF_SUBSCRIPTION_DEBUG, true).apply()
        }
    }

    fun disableDebugSubscription() {
        if (BuildConfig.DEBUG) {
            prefs.edit().putBoolean(PREF_SUBSCRIPTION_DEBUG, false).apply()
        }
    }

    fun startFreeTrial(days: Int = 30) {
        val expiry = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
        prefs.edit().putLong(PREF_SUBSCRIPTION_EXPIRY, expiry).apply()
        setSubscriptionActive(true)
    }

    fun getRemainingTrialDays(): Int {
        val expiry = prefs.getLong(PREF_SUBSCRIPTION_EXPIRY, 0)
        if (expiry == 0L) return 0
        val remaining = (expiry - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)
        return remaining.toInt().coerceAtLeast(0)
    }

    fun getCardAnimation(): String {
        return prefs.getString(KEY_CARD_ANIMATION, AnimationConstants.ANIMATION_FADE_SLIDE) ?: AnimationConstants.ANIMATION_FADE_SLIDE
    }

    fun setCardAnimation(animation: String) {
        prefs.edit().putString(KEY_CARD_ANIMATION, animation).apply()
    }

    // ── Notificações de Eventos Próximos ──

    fun isEventNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_EVENT_NOTIFICATIONS_ENABLED, true)
    }

    fun setEventNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EVENT_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getEventNotificationsThreshold(): Int {
        return prefs.getInt(KEY_EVENT_NOTIFICATIONS_THRESHOLD, 5)
    }

    fun setEventNotificationsThreshold(threshold: Int) {
        prefs.edit().putInt(KEY_EVENT_NOTIFICATIONS_THRESHOLD, threshold).apply()
    }

    // ── Capacidade do Veículo ──

    fun getTankCapacity(): Int {
        return prefs.getInt(KEY_TANK_CAPACITY, 0)
    }

    fun setTankCapacity(capacity: Int) {
        prefs.edit().putInt(KEY_TANK_CAPACITY, capacity).apply()
    }

    fun getCylinderCapacity(): Int {
        return prefs.getInt(KEY_CYLINDER_CAPACITY, 0)
    }

    fun setCylinderCapacity(capacity: Int) {
        prefs.edit().putInt(KEY_CYLINDER_CAPACITY, capacity).apply()
    }

    fun getCylinderPressure(): Int {
        return prefs.getInt(KEY_CYLINDER_PRESSURE, 200)
    }

    fun setCylinderPressure(pressure: Int) {
        prefs.edit().putInt(KEY_CYLINDER_PRESSURE, pressure).apply()
    }

    // ── Configuração dos Sistemas do Veículo ──

    fun isTankEnabled(): Boolean {
        return prefs.getBoolean(KEY_TANK_ENABLED, true)
    }

    fun setTankEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TANK_ENABLED, enabled).apply()
    }

    fun isCylinderEnabled(): Boolean {
        return prefs.getBoolean(KEY_CYLINDER_ENABLED, false)
    }

    fun setCylinderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CYLINDER_ENABLED, enabled).apply()
    }

    fun isBatteryEnabled(): Boolean {
        return prefs.getBoolean(KEY_BATTERY_ENABLED, false)
    }

    fun setBatteryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_ENABLED, enabled).apply()
    }

    fun getBatteryCapacity(): Int {
        return prefs.getInt(KEY_BATTERY_CAPACITY, 0)
    }

    fun setBatteryCapacity(capacity: Int) {
        prefs.edit().putInt(KEY_BATTERY_CAPACITY, capacity).apply()
    }

    companion object {
        private const val PREF_NAME = "profit_driving_prefs"
        private const val PREF_ONBOARDING_DONE = "onboarding_complete"
        private const val PREF_ACCESSIBILITY_GRANTED = "accessibility_granted"
        private const val PREF_HAS_SEEN_PERMISSIONS = "has_seen_permissions"
        private const val PREF_SUBSCRIPTION_ACTIVE = "subscription_active"
        private const val PREF_SUBSCRIPTION_DEBUG = "subscription_debug"
        private const val PREF_SUBSCRIPTION_EXPIRY = "subscription_expiry"
        private const val PREF_SUBSCRIPTION_PLAN = "subscription_plan"
        private const val KEY_CARD_ANIMATION = "card_animation"
        private const val KEY_EVENT_NOTIFICATIONS_ENABLED = "event_notifications_enabled"
        private const val KEY_EVENT_NOTIFICATIONS_THRESHOLD = "event_notifications_threshold"
        private const val KEY_TANK_CAPACITY = "tank_capacity"
        private const val KEY_CYLINDER_CAPACITY = "cylinder_capacity"
        private const val KEY_CYLINDER_PRESSURE = "cylinder_pressure"
        private const val KEY_TANK_ENABLED = "tank_enabled"
        private const val KEY_CYLINDER_ENABLED = "cylinder_enabled"
        private const val KEY_BATTERY_ENABLED = "battery_enabled"
        private const val KEY_BATTERY_CAPACITY = "battery_capacity"
    }
}
