package com.profitdriving

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.CombinedVibration
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.view.accessibility.AccessibilityWindowInfo
import com.profitdriving.accessibility.extractor.UberCardExtractor
import com.profitdriving.parser.App99CardParser
import com.profitdriving.parser.ExclusiveCardParser
import com.profitdriving.parser.RadarCardParser
import com.profitdriving.parser.RideDataParser
import java.util.Locale

/**
 * V1 - ACCESSIBILITY SERVICE (BACKUP)
 *
 * Este é o serviço original. O V2 está ativo em produção.
 * Descomentar no AndroidManifest.xml e comentar o V2 para rollback.
 *
 * Mantido apenas para emergências.
 */
class RideAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val bgThread = HandlerThread("RideDetectionThread").also { it.start() }
    private val bgHandler = Handler(bgThread.looper)
    private var pendingRunnable: Runnable? = null
    private var lastHash = ""
    private var lastSaveTime = 0L
    private var cardVisible = false
    private var lastInsertedId: Long = -1L
    private var statusLocked = false
    private var uberWindowWasVisible = false
    private var lastSavedValue: Double? = null
    override fun onServiceConnected() {
        super.onServiceConnected()
        FloatingBubbleService.start(this)
        L.d(TAG, "Acessibilidade conectada — bolha iniciada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""

        L.d(TAG, "Evento: type=${event.eventType}, pkg=$pkg")

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val texto = event.contentDescription?.toString()
                ?: event.text.firstOrNull()?.toString()
                ?: return
            handleClick(texto)
            return
        }

        val isUberEvent = pkg.contains("ubercab") ||
                          pkg.contains("taxis99") ||
                          pkg.contains("app99")

        if (isUberEvent) {
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

            bgHandler.postDelayed(pendingRunnable!!, 300L)
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val uberVisibleNow = isUberWindowVisible()
            if (uberWindowWasVisible && !uberVisibleNow) {
                onUberWindowDismissed()
            }
            uberWindowWasVisible = uberVisibleNow

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

            bgHandler.postDelayed(pendingRunnable!!, 300L)
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
        bgHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        FloatingBubbleService.stop(this)
        bgThread.quitSafely()
        handler.removeCallbacksAndMessages(null)
        bgHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun findUberWindow(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null

        try {
            val allWindows = windows ?: return null

            L.d(TAG, "Total de janelas: ${allWindows.size}")
            allWindows.forEachIndexed { i, w ->
                L.d(TAG, "Janela $i: tipo=${w.type}, layer=${w.layer}, pkg=${w.root?.packageName}")
            }

            val overlayWindows = allWindows.filter {
                it.type == 6
            }

            for (window in overlayWindows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (pkg.contains("ubercab") || pkg.contains("taxis99")) {
                    L.d(TAG, "Card overlay encontrado! tipo=${window.type}, layer=${window.layer}")
                    return root
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    root.recycle()
                }
            }

            for (window in allWindows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (pkg.contains("ubercab") || pkg.contains("taxis99")) {
                    L.d(TAG, "Janela Uber normal encontrada: tipo=${window.type}")
                    return root
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    root.recycle()
                }
            }
        } catch (e: Exception) {
            L.e(TAG, "Erro ao buscar janelas: ${e.message}")
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
        L.d(TAG, "=== detectRideCard iniciado para $pkg ===")

        val allTexts = mutableListOf<String>()
        collectTexts(root, allTexts)
        L.d(TAG, "Textos coletados (${allTexts.size}): ${allTexts.take(10)}")

        val fullText = allTexts.joinToString(" ")

        if (!isRideRequestCard(allTexts)) {
            L.d(TAG, "Card não é de corrida — ignorando")
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
                pendingRunnable?.let { bgHandler.removeCallbacks(it) }
            }
            return
        }

        val hash = buildCardHash(allTexts)

        if (hash == lastHash && cardVisible) {
            L.d(TAG, "Mesmo card ainda visível — ignorando")
            return
        }

        val ride = parseRideData(fullText, pkg)
        if (ride.value == null || ride.value <= 0) {
            L.d(TAG, "Valor inválido: ${ride.value} — ignorando")
            return
        }

        val now = System.currentTimeMillis()
        if (isValorSuspeito(ride.value) && (now - lastSaveTime) < 30_000L) {
            L.d(TAG, "Valor suspeito R$ ${ride.value} ignorado — possível tela de confirmação")
            return
        }

        if ((now - lastSaveTime) < 15_000L
            && ride.value == lastSavedValue
            && hash == lastHash) {
            L.d(TAG, "⏱️ Duplicata: mesmo valor+hash em menos de 5s — ignorando")
            return
        }

        cardVisible = true
        lastHash = hash
        lastSaveTime = now
        lastSavedValue = ride.value

        L.d(TAG, "Card detectado em $pkg: valor=${ride.value} km=${ride.distanceKm} " +
                "tempo=${ride.timeMin} nota=${ride.rating} " +
                "R$/km=${ride.effectivePricePerKm} R$/h=${ride.effectivePricePerHour}")

        // logV2Comparison(root, pkg)  // V2 comparison desativado (V1 é backup)

        saveAndShow(ride)
    }

    private fun isRideRequestCard(texts: List<String>): Boolean {
        val full = texts.joinToString(" ").lowercase(Locale.ROOT)

        val hasAccept = full.contains("aceitar") || full.contains("accept") || full.contains("selecionar")
        val hasMoney = full.contains("r$")
        val hasKm = full.contains("km")
        val hasMin = full.contains("min")

        L.d(TAG, "Validação card: Accept=$hasAccept, Money=$hasMoney, Km=$hasKm, Min=$hasMin")

        return hasAccept && hasMoney && hasKm && hasMin
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
        val priorityBonus = extractPriorityBonus(text)
        val dynamicBonus = extractDynamicBonus(text)

        val hasMultipleStops = extractStops(text)

        val (pickupAddress, dropoffAddress) = extractAddresses(text)

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
            hasMultipleStops = hasMultipleStops,
            priorityBonus = priorityBonus,
            dynamicBonus = dynamicBonus,
            pickupAddress = pickupAddress,
            dropoffAddress = dropoffAddress
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
        } else {
            // Fallback card exclusivo: pega o maior km que NÃO é de embarque
            val tripKm = VIAGEM_REGEX_EXCLUSIVO.findAll(text)
                .mapNotNull { parseBr(it.groupValues[2]) }
                .filter { it > 0 && it < 200 }
                .maxOrNull()
            if (tripKm != null) total += tripKm
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
        } else {
            // Fallback card exclusivo: pega o maior tempo que NÃO é de embarque
            // VIAGEM_REGEX_EXCLUSIVO groupValues[1] = minutos
            val tripMin = VIAGEM_REGEX_EXCLUSIVO.findAll(text)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it > 0 }
                .maxOrNull()
            if (tripMin != null) total += tripMin
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
        // Tenta primeiro com prefixo "Viagem de" (radar e card exclusivo com prefixo)
        val viagem = VIAGEM_REGEX.find(text)
        if (viagem != null) {
            val v = parseBr(viagem.groupValues[3])
            if (v != null && v > 0 && v < 200) return v
        }
        // Fallback para card exclusivo sem prefixo "Viagem de"
        // findAll pega todos os matches, pega o maior km (que é a viagem, não o embarque)
        val exclusivo = VIAGEM_REGEX_EXCLUSIVO.findAll(text)
            .mapNotNull { parseBr(it.groupValues[2]) }
            .filter { it > 0 && it < 200 }
            .maxOrNull()
        return exclusivo
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

        if (lower.trim() == "recusar" || lower.trim() == "negar" || lower.trim() == "x") {
            L.d(TAG, "Clique em Recusar detectado: $texto")
            if (lastInsertedId >= 0) {
                db.updateStatus(lastInsertedId, "DECLINED")
                statusLocked = true
                L.d(TAG, "Ride $lastInsertedId marcado como RECUSADO")
            }
        }
    }

    private fun logV2Comparison(root: AccessibilityNodeInfo, pkg: String) {
        try {
            val prefs = getSharedPreferences(SettingsActivity.PREF_NAME, 0)
            if (!prefs.getBoolean("use_v2_parser", false)) return

            val raw = UberCardExtractor.extract(root, pkg) ?: return
            val parser = selectParser(raw) ?: run {
                L.d(TAG, "V2: nenhum parser disponível")
                return
            }
            val v2ride = parser.parse(raw)
            if (v2ride != null) {
                L.d(TAG, "V2 parse: value=${v2ride.value} km=${v2ride.distanceKm} " +
                        "tripKm=${v2ride.tripDistanceKm} tripMin=${v2ride.tripTimeMin}")
            } else {
                L.d(TAG, "V2 parse retornou null")
            }
        } catch (e: Exception) {
            L.e(TAG, "V2 comparison error", e)
        }
    }

    private fun selectParser(raw: com.profitdriving.accessibility.extractor.RawCardData): RideDataParser? {
        val parsers = listOf(
            RadarCardParser(),
            ExclusiveCardParser(),
            App99CardParser()
        )
        for (parser in parsers) {
            if (parser.canParse(raw)) return parser
        }
        return null
    }

    private fun saveAndShow(ride: RideData) {
        L.d(TAG, "saveAndShow chamado: value=${ride.value} km=${ride.distanceKm}")
        if (ride.value == null) {
            L.w(TAG, "value é null — card não será exibido")
            return
        }
        lastSaveTime = System.currentTimeMillis()
        statusLocked = false
        vibrateFeedback()
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
            hasMultipleStops = ride.hasMultipleStops,
            scorePercent = result.scorePercent,
            priorityBonus = ride.priorityBonus,
            dynamicBonus = ride.dynamicBonus,
            pickupAddress = ride.pickupAddress,
            dropoffAddress = ride.dropoffAddress,
            kmState = result.params[0].state.ordinal,
            hourState = result.params[1].state.ordinal,
            minState = result.params[2].state.ordinal,
            ratingState = result.params[3].state.ordinal
        ))
        L.d(TAG, "Ride inserido com id=$lastInsertedId")

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("NEW_RIDE_SAVED"))

        getSharedPreferences(SettingsActivity.PREF_NAME, 0).edit().apply {
            ride.value?.let { putFloat("last_value", it.toFloat()) }
            ride.distanceKm?.let { putFloat("last_distance", it.toFloat()) }
            ride.timeMin?.let { putInt("last_time", it) }
            ride.rating?.let { putFloat("last_rating", it.toFloat()) }
            putString("last_app", ride.appName)
            putLong("last_timestamp", System.currentTimeMillis())
            ride.serviceType?.let { putString("last_service_type", it) }
            apply()
        }

        FloatingCardService.start(this, Intent().apply {
            ride.value?.let { putExtra("value", it) }
            ride.distanceKm?.let { putExtra("distanceKm", it) }
            ride.timeMin?.let { putExtra("timeMin", it) }
            ride.rating?.let { putExtra("rating", it) }
            putExtra("appName", ride.appName)
            ride.serviceType?.let { putExtra("serviceType", it) }
            ride.priorityBonus?.let { putExtra("priorityBonus", it) }
            ride.dynamicBonus?.let { putExtra("dynamicBonus", it) }
            ride.pickupAddress?.let { putExtra("pickupAddress", it) }
            ride.dropoffAddress?.let { putExtra("dropoffAddress", it) }
            putExtra("hasMultipleStops", ride.hasMultipleStops)
        })

        FloatingBubbleService.start(this, Intent().apply {
            ride.value?.let { putExtra("value", it) }
            ride.distanceKm?.let { putExtra("distanceKm", it) }
            ride.timeMin?.let { putExtra("timeMin", it) }
            ride.rating?.let { putExtra("rating", it) }
            putExtra("decision", result.decision.name)
        })
    }

    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibrator = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibrator.vibrate(CombinedVibration.createParallel(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractServiceType(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        for ((regex, label) in SERVICE_TYPE_PATTERNS) {
            if (regex.containsMatchIn(lower)) return label
        }
        return null
    }

    private fun extractPriorityBonus(text: String): Double? {
        val m = PRIORITY_BONUS_REGEX.find(text) ?: return null
        val v = parseBr(m.groupValues[1])
        return if (v != null && v > 0) v else null
    }

    private fun extractDynamicBonus(text: String): Double? {
        val m = DYNAMIC_BONUS_REGEX.find(text) ?: return null
        val v = parseBr(m.groupValues[1])
        return if (v != null && v > 0) v else null
    }

    private fun extractStops(text: String): Boolean {
        val lowerText = text.lowercase(Locale.ROOT)
        val hasStops = Regex("""\b(?:\d+\s+)?(?:várias\s+)?paradas?\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerText)

        if (hasStops) {
            L.d(TAG, "Paradas detectado!")
        }
        return hasStops
    }

    private fun extractAddresses(text: String): Pair<String?, String?> {
        var pickupAddress: String? = null
        var dropoffAddress: String? = null

        val pickupMatch = PICKUP_ADDRESS_REGEX.find(text)
        if (pickupMatch != null) {
            pickupAddress = pickupMatch.groupValues[1].trim()
            pickupAddress = cleanupAddress(pickupAddress)
            L.d(TAG, "Endereço de embarque encontrado: $pickupAddress")
        }

        val dropoffMatch = DROPOFF_ADDRESS_REGEX.find(text)
        if (dropoffMatch != null) {
            dropoffAddress = dropoffMatch.groupValues[1].trim()
            dropoffAddress = cleanupAddress(dropoffAddress)
            L.d(TAG, "Endereço de viagem encontrado: $dropoffAddress")
        }

        return Pair(pickupAddress, dropoffAddress)
    }

    private fun cleanupAddress(address: String): String {
        var cleaned = address
            .replace(Regex("\\s+"), " ")
            .replace(Regex(",\\s*,+"), ",")
            .trim()

        if (cleaned.length > 150) {
            cleaned = cleaned.take(150)
        }

        return cleaned
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
            """[Vv]iagem\s+de\s+(?:(\d+)\s*[Hh]\s*e\s*)?(\d+)\s*(?:[Mm]in(?:uto)?s?)\s*\((\d+[.,]\d+)\s*km\)"""
        )

        private val VIAGEM_REGEX_EXCLUSIVO = Regex(
            """(\d+)\s*(?:[Mm]in(?:uto)?s?)\s*\((\d+[.,]\d+)\s*km\)(?!\s*de\s*dist[âa]ncia)"""
        )

        private val DISTANCE_PATTERNS = listOf(
            Regex("""\((\d+[.,]?\d*)\s*km""", RegexOption.IGNORE_CASE),
            Regex("""(\d+[.,]?\d*)\s*km""", RegexOption.IGNORE_CASE),
            Regex("""km[:\s]*(\d+[.,]?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+[.,]?\d*)\s*quilômetro""", RegexOption.IGNORE_CASE),
            Regex("""dist[âa]ncia[:\s]*(\d+[.,]?\d*)\s*km""", RegexOption.IGNORE_CASE),
            Regex("""(\d+[.,]?\d*)\s*km\s*de\s*dist[âa]ncia""", RegexOption.IGNORE_CASE)
        )
        private val TIME_PATTERNS = listOf(
            Regex("""(\d+)\s*[Mm]in(?:uto)?s?"""),
            Regex("""(\d+)\s*[Hh](?:ora)?s?"""),
            Regex("""tempo[:\s]*(\d+)\s*min""", RegexOption.IGNORE_CASE),
            Regex("""duração[:\s]*(\d+)\s*min""", RegexOption.IGNORE_CASE)
        )
        private val RATING_STAR_REGEX = Regex("""(\d[.,]\d{1,2})\s*[★⭐*]""")
        private val RATING_COUNT_REGEX = Regex("""(\d[.,]\d{1,2})\s*\(\d+\)""")
        private val RATING_BULLET_REGEX = Regex("""(\d[.,]\d{1,2})\s*[·•]""")
        private val RATING_DECIMAL_REGEX = Regex("""(\d[.,]\d{1,2})""")

        private val SERVICE_TYPE_PATTERNS: List<Pair<Regex, String>> = listOf(
            "uberx" to "UberX",
            "uber flash" to "Flash",
            "uber juntos" to "Juntos",
            "uber moto" to "Moto",
            "uber black" to "Black",
            "uber comfort" to "Comfort",
            "uber bag" to "Black Bag",
            "uber priority" to "Prioridade",
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
            "priority" to "Prioridade",
            "99pop" to "99Pop",
            "99top" to "99Top",
            "99black" to "99Black",
            "99moto" to "99Moto",
            "99flash" to "99Flash",
            "99entrega" to "Entrega",
            "pop" to "Pop",
            "top" to "Top",
            "entrega" to "Entrega"
        ).map { (keyword, label) ->
            Regex("\\b${Regex.escape(keyword)}\\b", RegexOption.IGNORE_CASE) to label
        }

        private val PRIORITY_BONUS_REGEX = Regex(
            """\+R\$\s*(\d+(?:[.,]\d+)?)\s*inclu[íi]do\s+para\s+prioridade""",
            RegexOption.IGNORE_CASE
        )

        private val DYNAMIC_BONUS_REGEX = Regex(
            """\+R\$\s*(\d+(?:[.,]\d+)?)\s*inclu[íi]do(?!\s+para\s+prioridade)""",
            RegexOption.IGNORE_CASE
        )

        private val PICKUP_ADDRESS_REGEX = Regex(
            """\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)\s*de\s*dist[âa]ncia\s+([A-Za-zÀ-Úà-ú0-9\s,.-]+?)(?=\s*(?:\d+\s*min|Viagem|Aceitar|$))""",
            RegexOption.IGNORE_CASE
        )

        private val DROPOFF_ADDRESS_REGEX = Regex(
            """Viagem\s+de\s+\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)\s+([A-Za-zÀ-Úà-ú0-9\s,.-]+?\d{5}-\d{3})""",
            RegexOption.IGNORE_CASE
        )
    }
}