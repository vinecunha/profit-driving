package com.profitdriving

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(SettingsActivity.PREF_NAME, Context.MODE_PRIVATE)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val pkg = sbn.packageName
            val appMatch = matchApp(pkg)
            if (appMatch == null) {
                Log.d(TAG, "Ignorando pacote: $pkg")
                return
            }

            val extras: Bundle = sbn.notification.extras ?: return
            val title = extras.getString(Notification.EXTRA_TITLE, "") ?: ""
            val text = extras.getString(Notification.EXTRA_TEXT, "") ?: ""
            val bigText = extras.getString(Notification.EXTRA_BIG_TEXT, "") ?: ""
            val summary = extras.getString(Notification.EXTRA_SUMMARY_TEXT, "") ?: ""
            val fullText = "$title $text $bigText $summary"

            Log.d(TAG, "Notificação recebida de $pkg => $appMatch: $fullText")

            val rideData = parseRideData(fullText, appMatch)
            Log.d(TAG, "Dados extraídos: valor=${rideData.value} km=${rideData.distanceKm} " +
                    "tempo=${rideData.timeMin} nota=${rideData.rating}")

            runOnUi(rideData)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar notificação", e)
        }
    }

    private fun matchApp(pkg: String): String? {
        if (pkg.startsWith("com.ubercab")) return "Uber"
        if (pkg.startsWith("com.taxis99") || pkg.startsWith("br.com.taxis99")) return "99"
        return null
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    private fun parseRideData(text: String, appName: String): RideData {
        val value = extractValue(text)
        val distance = extractDistance(text)
        val time = extractTime(text)
        val rating = extractRating(text)
        return RideData(value, distance, time, rating, appName)
    }

    private fun extractValue(text: String): Double? {
        for (pat in VALUE_PATTERNS) {
            val m = pat.find(text)
            if (m != null) {
                val v = parseBrNumber(m.groupValues[1])
                if (v != null) return v
            }
        }
        return null
    }

    private fun extractDistance(text: String): Double? {
        for (pat in DISTANCE_PATTERNS) {
            val m = pat.find(text)
            if (m != null) {
                val v = parseBrNumber(m.groupValues[1])
                if (v != null && v > 0) return v
            }
        }
        return null
    }

    private fun extractTime(text: String): Int? {
        val hMatch = HOUR_REGEX.find(text)
        val mMatch = MINUTE_REGEX.find(text)
        val hours = hMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val mins = mMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = hours * 60 + mins
        return if (total > 0) total else null
    }

    private fun extractRating(text: String): Double? {
        val notaMatch = RATING_PREFIX_REGEX.find(text)
        if (notaMatch != null) {
            val v = parseBrNumber(notaMatch.groupValues[1])
            if (v != null && v >= 1.0 && v <= 5.1) return v
        }

        val valueMatch = VALUE_PATTERNS.firstNotNullOfOrNull { it.find(text) }
        val distMatch = DISTANCE_PATTERNS.firstNotNullOfOrNull { it.find(text) }
        val timeMatch = MINUTE_REGEX.find(text)

        val used = mutableSetOf<String>()
        valueMatch?.let { used.add(it.value) }
        distMatch?.let { used.add(it.value) }
        timeMatch?.let { used.add(it.value) }

        val allDecimals = DECIMAL_REGEX.findAll(text)
            .filter { it.value !in used }
            .toList()
        for (m in allDecimals.reversed()) {
            val v = parseBrNumber(m.groupValues[1])
            if (v != null && v >= 4.0 && v <= 5.1) return v
        }
        return null
    }

    private fun parseBrNumber(raw: String): Double? {
        return raw.replace(",", ".").toDoubleOrNull()
    }

    private fun runOnUi(ride: RideData) {
        val db = DatabaseHelper(this)
        db.insert(RideRecord(
            value = ride.value,
            distanceKm = ride.distanceKm,
            timeMin = ride.timeMin,
            rating = ride.rating,
            pricePerKm = ride.effectivePricePerKm,
            pricePerHour = ride.effectivePricePerHour,
            appName = ride.appName,
            timestamp = System.currentTimeMillis()
        ))

        prefs.edit().apply {
            ride.value?.let { putFloat(SettingsActivity.KEY_LAST_VALUE, it.toFloat()) }
            ride.distanceKm?.let { putFloat(SettingsActivity.KEY_LAST_DISTANCE, it.toFloat()) }
            ride.timeMin?.let { putInt(SettingsActivity.KEY_LAST_TIME, it) }
            ride.rating?.let { putFloat(SettingsActivity.KEY_LAST_RATING, it.toFloat()) }
            putString(SettingsActivity.KEY_LAST_APP, ride.appName)
            putLong(SettingsActivity.KEY_LAST_TIMESTAMP, System.currentTimeMillis())
            apply()
        }

        FloatingCardService.start(this, Intent().apply {
            ride.value?.let { putExtra("value", it) }
            ride.distanceKm?.let { putExtra("distanceKm", it) }
            ride.timeMin?.let { putExtra("timeMin", it) }
            ride.rating?.let { putExtra("rating", it) }
            putExtra("appName", ride.appName)
        })

        FloatingBubbleService.start(this, Intent().apply {
            ride.value?.let { putExtra("value", it) }
            ride.distanceKm?.let { putExtra("distanceKm", it) }
            ride.timeMin?.let { putExtra("timeMin", it) }
            ride.rating?.let { putExtra("rating", it) }
        })
    }

    companion object {
        private const val TAG = "ProfitDriving"

        private val VALUE_PATTERNS = listOf(
            Regex("""R\$\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:[.,]\d+)?)\s*reais""", RegexOption.IGNORE_CASE),
            Regex("""[Vv]alor[:\s]*R?\$?\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
        )
        private val DISTANCE_PATTERNS = listOf(
            Regex("""(\d+(?:[.,]\d+)?)\s*km""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:[.,]\d+)?)\s*quilômetro""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:[.,]\d+)?)\s*quilometro""", RegexOption.IGNORE_CASE),
            Regex("""[Dd]istância[:\s]*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
        )
        private val MINUTE_REGEX = Regex("""(\d+)\s*(?:min|minuto|minutos)""", RegexOption.IGNORE_CASE)
        private val HOUR_REGEX = Regex("""(\d+)\s*(?:h|hora|horas)\b""", RegexOption.IGNORE_CASE)
        private val RATING_PREFIX_REGEX = Regex("""nota[:\s]*(\d[.,]\d{1,2})""", RegexOption.IGNORE_CASE)
        private val DECIMAL_REGEX = Regex("""(\d[.,]\d{1,2})""")
    }
}
