package com.profitdriving

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.Locale

class RideAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val bgHandler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null
    private var lastHash = ""
    private var lastSaveTime = 0L
    private var cardVisible = false
    private var lastInsertedId: Long = -1L
    private var statusLocked = false
    private var uberWindowWasVisible = false
    @Volatile private var isFirstCardEvent = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        FloatingBubbleService.start(this)
        L.d(TAG, "Acessibilidade conectada — bolha iniciada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val texto = event.contentDescription?.toString()
                ?: event.text.firstOrNull()?.toString()
                ?: return
            handleClick(texto)
            return
        }

        val isRelevantType = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                             event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                             event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!isRelevantType) return

        val isUberEvent = pkg.contains("ubercab") ||
                          pkg.contains("taxis99") ||
                          pkg.contains("app99")
        val isWindowChange = event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!isUberEvent && !isWindowChange) return

        if (isFirstCardEvent) {
            val root = findUberWindow()
            if (root != null) {
                val quickTexts = mutableListOf<String>()
                collectTextsLimited(root, quickTexts, maxNodes = 30)
                val quick = quickTexts.joinToString(" ")
                if (quick.contains("Aceitar", ignoreCase = true) && quick.contains("R$")) {
                    isFirstCardEvent = false
                    pendingRunnable?.let { bgHandler.removeCallbacks(it) }
                    pendingRunnable = Runnable {
                        val detectedRoot = findUberWindow() ?: return@Runnable
                        val dpkg = detectedRoot.packageName?.toString() ?: ""
                        try {
                            detectRideCard(detectedRoot, dpkg)
                        } finally {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                @Suppress("DEPRECATION")
                                detectedRoot.recycle()
                            }
                        }
                    }
                    bgHandler.postDelayed(pendingRunnable!!, 200)
                    return
                }
            }
        }

        pendingRunnable?.let { bgHandler.removeCallbacks(it) }

        pendingRunnable = Runnable {
            val root = findUberWindow() ?: return@Runnable
            val detectedPkg = root.packageName?.toString() ?: ""
            try {
                detectRideCard(root, detectedPkg)
            } finally {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    root.recycle()
                }
            }
        }

        val delay = if (quickScanHasRideCard()) 300L else 600L
        bgHandler.postDelayed(pendingRunnable!!, delay)

        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val uberVisibleNow = isUberWindowVisible()
            if (uberWindowWasVisible && !uberVisibleNow) {
                onUberWindowDismissed()
            }
            uberWindowWasVisible = uberVisibleNow
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
        bgHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        FloatingBubbleService.stop(this)
        handler.removeCallbacksAndMessages(null)
        bgHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun findUberWindow(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null) {
            val pkg = active.packageName?.toString() ?: ""
            if (pkg.contains("ubercab") || pkg.contains("taxis99") || pkg.contains("app99")) {
                return active
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                active.recycle()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val allWindows = windows ?: return null
                for (window in allWindows) {
                    val root = window.root ?: continue
                    val pkg = root.packageName?.toString() ?: ""
                    if (pkg.contains("ubercab") || pkg.contains("taxis99") || pkg.contains("app99")) {
                        L.d(TAG, "Janela Uber encontrada em segundo plano: $pkg tipo=${window.type} layer=${window.layer}")
                        return root
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        @Suppress("DEPRECATION")
                        root.recycle()
                    }
                }
            } catch (e: Exception) {
                L.e(TAG, "Erro ao varrer janelas", e)
            }
        }

        return null
    }

    private fun isUberWindowVisible(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        return try {
            windows?.any { window ->
                val pkg = window.root?.packageName?.toString() ?: ""
                pkg.contains("ubercab") || pkg.contains("taxis99") || pkg.contains("app99")
            } ?: false
        } catch (e: Exception) { false }
    }

    private fun onUberWindowDismissed() {
        handler.post {
            cardVisible = false
            lastHash = ""
            lastSaveTime = 0L
            isFirstCardEvent = true
            pendingRunnable?.let { bgHandler.removeCallbacks(it) }
            L.d(TAG, "Janela Uber saiu — estado resetado")
        }
    }

    private fun logAllWindows() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        try {
            val wins = windows ?: run {
                L.d(TAG, "windows = null")
                return
            }
            L.d(TAG, "Total de janelas abertas: ${wins.size}")
            wins.forEachIndexed { i, w ->
                val pkg = w.root?.packageName ?: "?"
                L.d(TAG, "Janela $i: pkg=$pkg tipo=${w.type} layer=${w.layer} focada=${w.isFocused} ativa=${w.isActive}")
            }
        } catch (e: Exception) {
            L.e(TAG, "Erro ao listar janelas", e)
        }
    }

    private fun quickScanHasRideCard(): Boolean {
        return try {
            val root = findUberWindow() ?: return false
            val texts = mutableListOf<String>()
            collectTextsLimited(root, texts, maxNodes = 20)
            val quick = texts.joinToString(" ")
            quick.contains("Aceitar", ignoreCase = true) && quick.contains("R$")
        } catch (e: Exception) { false }
    }

    private fun collectTextsLimited(node: AccessibilityNodeInfo, list: MutableList<String>, maxNodes: Int) {
        if (list.size >= maxNodes) return
        if (node.text?.toString()?.isNotBlank() == true) {
            list.add(node.text.toString())
        }
        for (i in 0 until node.childCount) {
            if (list.size >= maxNodes) break
            node.getChild(i)?.let { child ->
                collectTextsLimited(child, list, maxNodes)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
        }
    }

    private fun buildCardHash(texts: List<String>): String {
        val cardTokens = texts.filter { text ->
            text.contains("R$") ||
            text.contains("km", ignoreCase = true) ||
            text.contains("min", ignoreCase = true) ||
            text.contains("Aceitar", ignoreCase = true) ||
            (text.length < 20 && text.any { it.isDigit() })
        }
        return cardTokens.sorted().joinToString("|").hashCode().toString()
    }

    private fun detectRideCard(root: AccessibilityNodeInfo, pkg: String) {
        val allTexts = mutableListOf<String>()
        collectTexts(root, allTexts)
        val fullText = allTexts.joinToString(" ")
        val hash = buildCardHash(allTexts)

        if (!isRideRequestCard(allTexts)) {
            if (cardVisible) {
                L.d(TAG, "Card sumiu — resetando estado")
                if (!statusLocked && lastInsertedId >= 0) {
                    DatabaseHelper(this).updateStatus(lastInsertedId, "EXPIRED")
                    L.d(TAG, "Ride $lastInsertedId marcado como EXPIRADO")
                }
                cardVisible = false
                lastHash = ""
                lastSaveTime = 0L
                lastInsertedId = -1L
                statusLocked = false
                isFirstCardEvent = true
                pendingRunnable?.let { handler.removeCallbacks(it) }
            }
            return
        }

        val ride = parseRideData(fullText, pkg)
        if (ride.value == null || ride.value <= 0) return

        val now = System.currentTimeMillis()
        if (isValorSuspeito(ride.value) && (now - lastSaveTime) < 30_000L) {
            L.d(TAG, "Valor suspeito R$ ${ride.value} ignorado — possível tela de confirmação")
            return
        }

        if (!PICKUP_REGEX.containsMatchIn(fullText) &&
            !VIAGEM_REGEX.containsMatchIn(fullText)) {
            return
        }

        if (hash == lastHash && cardVisible) {
            L.d(TAG, "Mesmo card ainda visível — ignorando")
            return
        }

        cardVisible = true
        lastHash = hash
        lastSaveTime = now
        isFirstCardEvent = false

        L.d(TAG, "Card detectado em $pkg: valor=${ride.value} km=${ride.distanceKm} " +
                "tempo=${ride.timeMin} nota=${ride.rating} " +
                "R$/km=${ride.effectivePricePerKm} R$/h=${ride.effectivePricePerHour}")

        saveAndShow(ride)
    }

    private fun isRideRequestCard(texts: List<String>): Boolean {
        val full = texts.joinToString(" ")

        val temAceitar = full.contains("Aceitar", ignoreCase = true) ||
                         full.contains("Accept", ignoreCase = true) ||
                         full.contains("Selecionar", ignoreCase = true)
        if (!temAceitar) return false

        if (!full.contains("R$")) return false

        val temKm = full.contains("km", ignoreCase = true)
        val temMin = full.contains("min", ignoreCase = true)
        if (!temKm || !temMin) return false

        val temValorCard = VALUE_REGEX.containsMatchIn(full)
        if (!temValorCard) return false

        val temPadraoCard =
            full.contains("de distância", ignoreCase = true) ||
            full.contains("Viagem de", ignoreCase = true) ||
            full.contains("por km", ignoreCase = true)

        return temPadraoCard
    }

    private fun isValorSuspeito(valor: Double): Boolean {
        return valor == valor.toLong().toDouble() && valor >= 30.0
    }

    private fun collectTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        if (node.text?.toString()?.isNotBlank() == true) {
            list.add(node.text.toString())
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTexts(child, list)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
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
            L.d(TAG, "Clique em Aceitar detectado: $texto")
            if (lastInsertedId >= 0) {
                db.updateStatus(lastInsertedId, "ACCEPTED")
                statusLocked = true
                L.d(TAG, "Ride $lastInsertedId marcado como ACEITO")
            }
        }

        if (lower.contains("recusar") || lower.contains("negar") || lower.contains("x")) {
            L.d(TAG, "Clique em Recusar detectado: $texto")
            if (lastInsertedId >= 0) {
                db.updateStatus(lastInsertedId, "DECLINED")
                statusLocked = true
                L.d(TAG, "Ride $lastInsertedId marcado como RECUSADO")
            }
        }
    }

    private fun saveAndShow(ride: RideData) {
        L.d(TAG, "saveAndShow chamado: value=${ride.value} km=${ride.distanceKm}")
        if (ride.value == null) {
            L.w(TAG, "value é null — card não será exibido")
            return
        }
        lastSaveTime = System.currentTimeMillis()
        statusLocked = false
        val db = DatabaseHelper(this)

        val pricePerMin = ride.value.let { v ->
            ride.timeMin?.let { t -> if (t > 0) v / t.toDouble() else null }
        }
        val prefs = getSharedPreferences(SettingsActivity.PREF_NAME, 0)
        val result = DecisionEngine.evaluate(
            kmValue     = ride.effectivePricePerKm,
            hourValue   = ride.effectivePricePerHour,
            minValue    = pricePerMin,
            ratingValue = ride.rating,
            prefs       = prefs
        )

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
            stops = ride.stops,
            scorePercent = result.scorePercent
        ))
        L.d(TAG, "Ride inserido com id=$lastInsertedId")

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
            "uber bag" to "Black Bag",
            "business comfort" to "Business Comfort",
            "business black" to "Business Black",
            "envios moto" to "Envios Moto",
            "envios carro" to "Envios Carro",
            "black bag" to "Black Bag",
            "flash" to "Flash",
            "juntos" to "Juntos",
            "moto" to "Moto",
            "black" to "Black",
            "bag" to "Black Bag",
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
