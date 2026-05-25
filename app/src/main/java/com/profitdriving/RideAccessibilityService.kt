package com.profitdriving

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RideAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null
    private var lastHash = ""
    private var lastSaveTime = 0L
    private var cardVisible = false
    private val MIN_SAVE_INTERVAL_MS = 3_000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        FloatingBubbleService.start(this)
        Log.d(TAG, "Acessibilidade conectada — bolha iniciada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (!pkg.contains("ubercab") && !pkg.contains("taxis99")) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        pendingRunnable?.let { handler.removeCallbacks(it) }

        pendingRunnable = Runnable {
            val root = rootInActiveWindow ?: return@Runnable
            detectRideCard(root, pkg)
        }
        handler.postDelayed(pendingRunnable!!, 800)
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        FloatingBubbleService.stop(this)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun detectRideCard(root: AccessibilityNodeInfo, pkg: String) {
        val allTexts = mutableListOf<String>()
        collectTexts(root, allTexts)
        val fullText = allTexts.joinToString(" ")
        val hash = fullText.hashCode().toString()

        if (!fullText.contains("R$") || !fullText.contains("km")) {
            if (cardVisible) {
                Log.d(TAG, "Card sumiu — resetando estado")
                cardVisible = false
                lastSaveTime = 0L
                lastHash = ""
                pendingRunnable?.let { handler.removeCallbacks(it) }
            }
            return
        }

        if (hash == lastHash && cardVisible) {
            Log.d(TAG, "Mesmo card ainda visível — ignorando")
            return
        }

        cardVisible = true
        lastHash = hash
        val now = System.currentTimeMillis()
        if (hash == lastHash && (now - lastSaveTime) < MIN_SAVE_INTERVAL_MS) return
        lastSaveTime = now

        Log.d(TAG, "Card detectado em $pkg: $fullText")

        val ride = parseRideData(fullText, pkg)
        if (ride.value == null) return

        Log.d(TAG, "Dados: valor=${ride.value} km=${ride.distanceKm} " +
                "tempo=${ride.timeMin} nota=${ride.rating} " +
                "R$/km=${ride.effectivePricePerKm} R$/h=${ride.effectivePricePerHour}")

        saveAndShow(ride)
    }

    private fun collectTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        if (node.text?.toString()?.isNotBlank() == true) {
            list.add(node.text.toString())
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, list) }
        }
    }

    private fun parseRideData(text: String, pkg: String): RideData {
        val value = extractValue(text)
        val pricePerKm = extractPricePerKm(text)
        val pricePerHour = extractPricePerHour(text)
        val distance = extractDistance(text)
        val timeMin = extractTime(text)
        val rating = extractRating(text)

        val pickupKm = extractPickupDistance(text)
        val pickupTime = extractPickupTime(text)
        val tripKm = extractTripDistance(text)
        val tripTime = extractTripTime(text)

        val appName = if (pkg.contains("ubercab")) "Uber" else "99"

        return RideData(
            value = value,
            distanceKm = distance,
            timeMin = timeMin,
            rating = rating,
            appName = appName,
            pricePerKm = pricePerKm ?: value?.let { v ->
                distance?.let { d -> if (d > 0) v / d else null }
            },
            pricePerHour = pricePerHour ?: value?.let { v ->
                timeMin?.let { t -> if (t > 0) v / (t / 60.0) else null }
            },
            detectedBy = "accessibility",
            pickupDistanceKm = pickupKm,
            pickupTimeMin = pickupTime,
            tripDistanceKm = tripKm,
            tripTimeMin = tripTime
        )
    }

    private fun extractValue(text: String): Double? {
        val m = VALUE_REGEX.find(text) ?: return null
        return parseBr(m.groupValues[1])
    }

    private fun extractPricePerKm(text: String): Double? {
        val m = KM_PER_REAL_REGEX.find(text)
        if (m != null) return parseBr(m.groupValues[1])
        val m2 = KM_REAL_REGEX.find(text)
        return m2?.let { parseBr(it.groupValues[1]) }
    }

    private fun extractPricePerHour(text: String): Double? {
        return HOUR_REAL_REGEX.find(text)?.let {
            parseBr(it.groupValues[1])
        }
    }

    private fun extractDistance(text: String): Double? {
        var total = 0.0

        val viagem = VIAGEM_REGEX.find(text)
        if (viagem != null) {
            val v = parseBr(viagem.groupValues[2])
            if (v != null && v > 0 && v < 200) total += v
        }

        val pickup = PICKUP_REGEX.find(text)
        if (pickup != null) {
            val v = parseBr(pickup.groupValues[2])
            if (v != null && v > 0 && v < 50) total += v
        }

        if (total == 0.0) {
            for (pat in DISTANCE_PATTERNS) {
                val m = pat.find(text)
                if (m != null) {
                    val v = parseBr(m.groupValues[1])
                    if (v != null && v > 0 && v < 200) return v
                }
            }
            return null
        }
        return total
    }

    private fun extractTime(text: String): Int? {
        var total = 0

        val viagem = VIAGEM_REGEX.find(text)
        if (viagem != null) {
            val v = viagem.groupValues[1].toIntOrNull()
            if (v != null && v > 0) total += v
        }

        val pickup = PICKUP_REGEX.find(text)
        if (pickup != null) {
            val v = pickup.groupValues[1].toIntOrNull()
            if (v != null && v > 0) total += v
        }

        if (total == 0) {
            for (pat in TIME_PATTERNS) {
                val m = pat.find(text)
                if (m != null) {
                    val v = m.groupValues[1].toIntOrNull()
                    if (v != null && v > 0) return v
                }
            }
            return null
        }
        return total
    }

    private fun extractPickupDistance(text: String): Double? {
        val pickup = PICKUP_REGEX.find(text) ?: return null
        val v = parseBr(pickup.groupValues[2])
        return if (v != null && v > 0 && v < 50) v else null
    }

    private fun extractPickupTime(text: String): Int? {
        val pickup = PICKUP_REGEX.find(text) ?: return null
        val v = pickup.groupValues[1].toIntOrNull()
        return if (v != null && v > 0) v else null
    }

    private fun extractTripDistance(text: String): Double? {
        val viagem = VIAGEM_REGEX.find(text) ?: return null
        val v = parseBr(viagem.groupValues[2])
        return if (v != null && v > 0 && v < 200) v else null
    }

    private fun extractTripTime(text: String): Int? {
        val viagem = VIAGEM_REGEX.find(text) ?: return null
        val v = viagem.groupValues[1].toIntOrNull()
        return if (v != null && v > 0) v else null
    }

    private fun extractRating(text: String): Double? {
        for (regex in listOf(RATING_STAR_REGEX, RATING_COUNT_REGEX, RATING_BULLET_REGEX)) {
            val m = regex.find(text)
            if (m != null) {
                val v = parseBr(m.groupValues[1])
                if (v != null && v in 1.0..5.1) return v
            }
        }
        return RATING_DECIMAL_REGEX.findAll(text).mapNotNull {
            parseBr(it.groupValues[1])
        }.firstOrNull { it in 4.0..5.1 }
    }

    private fun parseBr(raw: String): Double? {
        return raw.replace(",", ".").toDoubleOrNull()
    }

    private fun saveAndShow(ride: RideData) {
        Log.d(TAG, "saveAndShow chamado: value=${ride.value} km=${ride.distanceKm}")
        if (ride.value == null) {
            Log.w(TAG, "value é null — card não será exibido")
            return
        }
        lastSaveTime = System.currentTimeMillis()
        val db = DatabaseHelper(this)
        db.insert(RideRecord(
            value = ride.value, distanceKm = ride.distanceKm,
            timeMin = ride.timeMin, rating = ride.rating,
            pricePerKm = ride.effectivePricePerKm,
            pricePerHour = ride.effectivePricePerHour,
            appName = ride.appName, timestamp = System.currentTimeMillis(),
            pickupDistanceKm = ride.pickupDistanceKm,
            pickupTimeMin = ride.pickupTimeMin,
            tripDistanceKm = ride.tripDistanceKm,
            tripTimeMin = ride.tripTimeMin
        ))

        getSharedPreferences(SettingsActivity.PREF_NAME, 0).edit().apply {
            ride.value?.let { putFloat("last_value", it.toFloat()) }
            ride.distanceKm?.let { putFloat("last_distance", it.toFloat()) }
            ride.timeMin?.let { putInt("last_time", it) }
            ride.rating?.let { putFloat("last_rating", it.toFloat()) }
            putString("last_app", ride.appName)
            putLong("last_timestamp", System.currentTimeMillis())
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

        private val VALUE_REGEX = Regex("""R\$\s*(\d+(?:[.,]\d+)?)""")
        private val KM_PER_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*/\s*km""", RegexOption.IGNORE_CASE)
        private val KM_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*por km""", RegexOption.IGNORE_CASE)
        private val HOUR_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*/\s*h""", RegexOption.IGNORE_CASE)

        private val PICKUP_REGEX = Regex(
            """(\d+)\s*minutos?\s*\((\d+[.,]\d+)\s*km\)\s*de\s*dist[âa]ncia""",
            RegexOption.IGNORE_CASE
        )

        private val VIAGEM_REGEX = Regex(
            """[Vv]iagem de (\d+)\s*(?:[Mm]in(?:uto)?s?)\s*\((\d+[.,]\d+)\s*km\)"""
        )

        private val DISTANCE_PATTERNS = listOf(
            Regex("""\((\d+[.,]?\d*)\s*km""", RegexOption.IGNORE_CASE),
            Regex("""(\d+[.,]?\d*)\s*km""", RegexOption.IGNORE_CASE),
            Regex("""km[:\s]*(\d+[.,]?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+[.,]?\d*)\s*quilômetro""", RegexOption.IGNORE_CASE)
        )
        private val TIME_PATTERNS = listOf(
            Regex("""(\d+)\s*[Mm]in(?:uto)?s?"""),
            Regex("""(\d+)\s*[Hh](?:ora)?s?""")
        )
        private val RATING_STAR_REGEX = Regex("""(\d[.,]\d{1,2})\s*[★⭐*]""")
        private val RATING_COUNT_REGEX = Regex("""(\d[.,]\d{1,2})\s*\(\d+\)""")
        private val RATING_BULLET_REGEX = Regex("""(\d[.,]\d{1,2})\s*[·•]""")
        private val RATING_DECIMAL_REGEX = Regex("""(\d[.,]\d{1,2})""")
    }
}
