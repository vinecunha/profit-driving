package com.profitdriving

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class RideAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null
    private var lastDedupKey = ""
    private var lastSaveTime = 0L
    private var cardVisible = false
    private var lastInsertedId: Long = -1L
    private var statusLocked = false
    private val MIN_SAVE_INTERVAL_MS = 3_000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        FloatingBubbleService.start(this)
        Log.d(TAG, "Acessibilidade conectada — bolha iniciada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (!pkg.contains("ubercab") && !pkg.contains("taxis99")) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val texto = event.contentDescription?.toString()
                ?: event.text.firstOrNull()?.toString()
                ?: return
            handleClick(texto)
            return
        }

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

        if (!isRideRequestCard(allTexts)) {
            if (cardVisible) {
                Log.d(TAG, "Card sumiu — resetando estado")
                if (!statusLocked && lastInsertedId >= 0) {
                    DatabaseHelper(this).updateStatus(lastInsertedId, "EXPIRED")
                    Log.d(TAG, "Ride $lastInsertedId marcado como EXPIRADO")
                }
                cardVisible = false
                lastSaveTime = 0L
                lastDedupKey = ""
                lastInsertedId = -1L
                statusLocked = false
                pendingRunnable?.let { handler.removeCallbacks(it) }
            }
            return
        }

        val ride = parseRideData(fullText, pkg)
        if (ride.value == null || ride.value <= 0) return

        val now = System.currentTimeMillis()
        if (isValorSuspeito(ride.value) && (now - lastSaveTime) < 30_000L) {
            Log.d(TAG, "Valor suspeito R$ ${ride.value} ignorado — possível tela de confirmação")
            return
        }

        if (!PICKUP_REGEX.containsMatchIn(fullText) &&
            !VIAGEM_REGEX.containsMatchIn(fullText)) {
            return
        }

        val dedupKey = "${ride.value}|${ride.rating}"

        if (cardVisible && dedupKey == lastDedupKey) {
            Log.d(TAG, "Mesmo card (valor+nota) — ignorando")
            return
        }

        if ((now - lastSaveTime) < MIN_SAVE_INTERVAL_MS) return

        cardVisible = true
        lastDedupKey = dedupKey
        lastSaveTime = now

        Log.d(TAG, "Card detectado em $pkg: valor=${ride.value} km=${ride.distanceKm} " +
                "tempo=${ride.timeMin} nota=${ride.rating} " +
                "R$/km=${ride.effectivePricePerKm} R$/h=${ride.effectivePricePerHour}")

        saveAndShow(ride)
    }

    private fun isRideRequestCard(texts: List<String>): Boolean {
        val full = texts.joinToString(" ")
        val temAceitar = full.contains("Aceitar", ignoreCase = true) ||
                         full.contains("Accept", ignoreCase = true) ||
                         full.contains("Selecionar", ignoreCase = true)
        val temValor = full.contains("R$")
        val temViagem = full.contains("km", ignoreCase = true) &&
                        full.contains("min", ignoreCase = true)
        return temAceitar && temValor && temViagem
    }

    private fun isValorSuspeito(valor: Double): Boolean {
        return valor == valor.toLong().toDouble() && valor >= 30.0
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

        val serviceType = extractServiceType(text)
        val bonusAmount = extractBonus(text)

        val stops = MULTIPARADA_REGEX.find(text)
            ?.groupValues?.get(1)?.toIntOrNull()

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
            tripTimeMin = tripTime,
            serviceType = serviceType,
            bonusAmount = bonusAmount,
            stops = stops
        )
    }

    private fun extractValue(text: String): Double? {
        val matches = VALUE_REGEX.findAll(text)
            .mapNotNull { parseBr(it.groupValues[1]) }
            .filter { it > 1.0 }
            .toList()
        if (matches.isEmpty()) return null
        return matches.max()
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
            val v = parseBr(viagem.groupValues[3])
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
            val hours = viagem.groupValues[1].toIntOrNull() ?: 0
            val mins = viagem.groupValues[2].toIntOrNull() ?: 0
            val t = hours * 60 + mins
            if (t > 0) total += t
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
        val v = parseBr(viagem.groupValues[3])
        return if (v != null && v > 0 && v < 200) v else null
    }

    private fun extractTripTime(text: String): Int? {
        val viagem = VIAGEM_REGEX.find(text) ?: return null
        val hours = viagem.groupValues[1].toIntOrNull() ?: 0
        val mins = viagem.groupValues[2].toIntOrNull() ?: return null
        val total = hours * 60 + mins
        return if (total > 0) total else null
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

    private fun handleClick(texto: String) {
        val lower = texto.lowercase(Locale.ROOT)
        val db = DatabaseHelper(this)

        if (lower.contains("aceitar") || lower.contains("accept") || lower.contains("selecionar")) {
            Log.d(TAG, "Clique em Aceitar detectado: $texto")
            if (lastInsertedId >= 0) {
                db.updateStatus(lastInsertedId, "ACCEPTED")
                statusLocked = true
                Log.d(TAG, "Ride $lastInsertedId marcado como ACEITO")
            }
        }

        if (lower.contains("recusar") || lower.contains("negar") || lower.contains("x")) {
            Log.d(TAG, "Clique em Recusar detectado: $texto")
            if (lastInsertedId >= 0) {
                db.updateStatus(lastInsertedId, "DECLINED")
                statusLocked = true
                Log.d(TAG, "Ride $lastInsertedId marcado como RECUSADO")
            }
        }
    }

    private fun saveAndShow(ride: RideData) {
        Log.d(TAG, "saveAndShow chamado: value=${ride.value} km=${ride.distanceKm}")
        if (ride.value == null) {
            Log.w(TAG, "value é null — card não será exibido")
            return
        }
        lastSaveTime = System.currentTimeMillis()
        statusLocked = false
        val db = DatabaseHelper(this)
        lastInsertedId = db.insert(RideRecord(
            value = ride.value, distanceKm = ride.distanceKm,
            timeMin = ride.timeMin, rating = ride.rating,
            pricePerKm = ride.effectivePricePerKm,
            pricePerHour = ride.effectivePricePerHour,
            appName = ride.appName, timestamp = System.currentTimeMillis(),
            pickupDistanceKm = ride.pickupDistanceKm,
            pickupTimeMin = ride.pickupTimeMin,
            tripDistanceKm = ride.tripDistanceKm,
            tripTimeMin = ride.tripTimeMin,
            serviceType = ride.serviceType,
            bonusAmount = ride.bonusAmount,
            stops = ride.stops
        ))
        Log.d(TAG, "Ride inserido com id=$lastInsertedId")

        getSharedPreferences(SettingsActivity.PREF_NAME, 0).edit().apply {
            ride.value?.let { putFloat("last_value", it.toFloat()) }
            ride.distanceKm?.let { putFloat("last_distance", it.toFloat()) }
            ride.timeMin?.let { putInt("last_time", it) }
            ride.rating?.let { putFloat("last_rating", it.toFloat()) }
            putString("last_app", ride.appName)
            putLong("last_timestamp", System.currentTimeMillis())
            ride.serviceType?.let { putString("last_service_type", it) }
            ride.bonusAmount?.let { putFloat("last_bonus", it.toFloat()) }
            apply()
        }

        FloatingCardService.start(this, Intent().apply {
            ride.value?.let { putExtra("value", it) }
            ride.distanceKm?.let { putExtra("distanceKm", it) }
            ride.timeMin?.let { putExtra("timeMin", it) }
            ride.rating?.let { putExtra("rating", it) }
            putExtra("appName", ride.appName)
            ride.serviceType?.let { putExtra("serviceType", it) }
            ride.bonusAmount?.let { putExtra("bonusAmount", it) }
        })

        FloatingBubbleService.start(this, Intent().apply {
            ride.value?.let { putExtra("value", it) }
            ride.distanceKm?.let { putExtra("distanceKm", it) }
            ride.timeMin?.let { putExtra("timeMin", it) }
            ride.rating?.let { putExtra("rating", it) }
        })
    }

    private fun extractServiceType(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        for (entry in SERVICE_TYPE_LIST) {
            val regex = Regex("\\b${Regex.escape(entry.first)}\\b")
            if (regex.containsMatchIn(lower)) return entry.second
        }
        return null
    }

    private fun extractBonus(text: String): Double? {
        val chega = BONUS_REGEX_CHEGOU.find(text)
        if (chega != null) {
            val v = parseBr(chega.groupValues[1])
            if (v != null && v > 0) return v
        }
        val ganhe = BONUS_REGEX_GANHE.find(text)
        if (ganhe != null) {
            val v = parseBr(ganhe.groupValues[1])
            if (v != null && v > 0) return v
        }
        val prioridade = BONUS_REGEX_PRIORIDADE.find(text)
        if (prioridade != null) {
            val v = parseBr(prioridade.groupValues[1])
            if (v != null && v > 0) return v
        }
        return null
    }

    companion object {
        private const val TAG = "CorridaCerta"

        private val VALUE_REGEX = Regex("""R\$\s*(\d+(?:[.,]\d+)?)""")
        private val KM_PER_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*/\s*km""", RegexOption.IGNORE_CASE)
        private val KM_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*por km""", RegexOption.IGNORE_CASE)
        private val HOUR_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*/\s*h""", RegexOption.IGNORE_CASE)

        private val PICKUP_REGEX = Regex(
            """(\d+)\s*min(?:uto)?s?\s*\(\s*(\d+[.,]\d+)\s*km\s*\)\s*de\s*dist[âa]ncia""",
            RegexOption.IGNORE_CASE
        )

        private val VIAGEM_REGEX = Regex(
            """[Vv]iagem de (?:(\d+)\s*[Hh]\s*e\s*)?(\d+)\s*(?:[Mm]in(?:uto)?s?)\s*\((\d+[.,]\d+)\s*km\)"""
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

        private val SERVICE_TYPE_LIST = listOf(
            "uberx" to "UberX",
            "uber flash" to "Flash",
            "uber juntos" to "Juntos",
            "uber moto" to "Moto",
            "uber black" to "Black",
            "uber comfort" to "Comfort",
            "business comfort" to "Business Comfort",
            "business black" to "Business Black",
            "envios carro" to "Envios Carro",
            "flash" to "Flash",
            "juntos" to "Juntos",
            "moto" to "Moto",
            "black" to "Black",
            "comfort" to "Comfort",
            "99pop" to "99Pop",
            "99top" to "99Top",
            "99black" to "99Black",
            "99moto" to "99Moto",
            "99flash" to "99Flash",
            "99entrega" to "Entrega",
            "pop" to "Pop",
            "top" to "Top",
            "entrega" to "Entrega"
        )

        private val BONUS_REGEX_CHEGOU = Regex(
            """(?:Chegou|b[ôo]nus\s+extra\s+de)\s*R\$\s*(\d+(?:[.,]\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        private val BONUS_REGEX_GANHE = Regex(
            """Ganhe\s*at[ée]\s*R\$\s*(\d+(?:[.,]\d+)?)""",
            RegexOption.IGNORE_CASE
        )

        private val MULTIPARADA_REGEX = Regex(
            """(\d+)\s*parada[s]?""",
            RegexOption.IGNORE_CASE
        )

        private val BONUS_REGEX_PRIORIDADE = Regex(
            """\+R\$\s*(\d+(?:[.,]\d+)?)\s*inclu[íi]do\s*para\s*prioridade""",
            RegexOption.IGNORE_CASE
        )
    }
}
