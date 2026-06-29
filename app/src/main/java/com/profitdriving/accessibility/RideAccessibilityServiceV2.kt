package com.profitdriving.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.profitdriving.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.profitdriving.CardHashGenerator
import com.profitdriving.DatabaseHelper
import com.profitdriving.DecisionEngine
import com.profitdriving.FloatingBubbleService
import com.profitdriving.FloatingCardService
import com.profitdriving.L
import com.profitdriving.RideData
import com.profitdriving.RideRecord
import com.profitdriving.SettingsActivity
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.accessibility.extractor.UberCardExtractor
import com.profitdriving.parser.App99CardParser
import com.profitdriving.parser.DiscoveryCardParser
import com.profitdriving.parser.ExclusiveCardParser
import com.profitdriving.parser.RadarCardParser
import com.profitdriving.parser.ReservationDetailParser
import com.profitdriving.parser.RideDataParser


class RideAccessibilityServiceV2 : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingJob: Job? = null
    private var lastHash = ""
    private var lastSaveTime = 0L
    private var cardVisible = false
    private var lastInsertedId: Long = -1L
    private var statusLocked = false
    private var uberWindowWasVisible = false
    private var lastSavedValue: Double? = null
    private var currentRawLogId: Long = -1L

    private val parsers: List<RideDataParser> = listOf(
        ReservationDetailParser(),
        RadarCardParser(),
        ExclusiveCardParser(),
        App99CardParser(),
        DiscoveryCardParser()
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        FloatingBubbleService.start(this)
        L.d(TAG, "RideAccessibilityServiceV2 conectado — bolha iniciada")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: ""

        if (BuildConfig.DEBUG) {
            L.d(TAG, "Evento V2: type=${event.eventType}, pkg=$pkg")
        }

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
            pendingJob?.cancel()

            pendingJob = scope.launch(Dispatchers.IO) {
                delay(300)
                val root = findUberWindow() ?: return@launch
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
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val uberVisibleNow = isUberWindowVisible()
            if (uberWindowWasVisible && !uberVisibleNow) {
                onUberWindowDismissed()
            }
            uberWindowWasVisible = uberVisibleNow

            pendingJob?.cancel()

            pendingJob = scope.launch(Dispatchers.IO) {
                delay(300)
                val root = findUberWindow() ?: return@launch
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
        }
    }

    override fun onInterrupt() {
        pendingJob?.cancel()
        pendingJob = null
    }

    override fun onDestroy() {
        FloatingBubbleService.stop(this)
        scope.cancel()
        super.onDestroy()
    }

    private fun findUberWindow(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null

        try {
            val allWindows = windows ?: return null

            if (BuildConfig.DEBUG) {
                L.d(TAG, "Total de janelas: ${allWindows.size}")
                allWindows.forEachIndexed { i, w ->
                    L.d(TAG, "Janela $i: tipo=${w.type}, layer=${w.layer}, pkg=${w.root?.packageName}")
                }
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
        cardVisible = false
        lastHash = ""
        lastSaveTime = 0L
        pendingJob?.cancel()
        pendingJob = null
        L.d(TAG, "Janela Uber saiu — estado resetado")
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
        L.d(TAG, "=== detectRideCard V2 iniciado para $pkg ===")

        val raw = UberCardExtractor.extract(root, pkg)
            if (BuildConfig.DEBUG) {
                L.d(TAG, "Textos coletados (${raw.rawTexts.size}): ${raw.rawTexts.take(10)}")
            }

        val db = DatabaseHelper(this)
        val hash = buildCardHash(raw.rawTexts)
        currentRawLogId = db.insertRawLog(
            cardHash = hash,
            pkg = pkg,
            cardType = raw.cardType.name,
            rawTexts = raw.rawTexts
        )
        L.d(TAG, "📝 Raw log salvo: id=$currentRawLogId")

        if (raw.acceptNode == null) {
            L.d(TAG, "Nenhum botão de aceitar encontrado — verificando se é card de corrida")
            if (raw.valueNode == null) {
                L.d(TAG, "Card não é de corrida — ignorando")
                db.updateRawLogStatus(currentRawLogId, status = "failed", error = "no accept node or value node")
                if (cardVisible) {
                    L.d(TAG, "Card sumiu — resetando estado")
                    if (!statusLocked && lastInsertedId >= 0) {
                        db.updateStatus(lastInsertedId, "EXPIRED")
                        L.d(TAG, "Ride $lastInsertedId marcado como EXPIRADO")
                    }
                    cardVisible = false
                    lastHash = ""
                    lastSaveTime = 0L
                    lastInsertedId = -1L
                    statusLocked = false
                    pendingJob?.cancel()
                }
                return
            }
        }

        if (hash == lastHash && cardVisible) {
            L.d(TAG, "Mesmo card ainda visível — ignorando")
            db.updateRawLogStatus(currentRawLogId, status = "duplicate", error = "same card still visible")
            return
        }

        val parser = selectParser(raw)
        if (parser == null) {
            L.d(TAG, "Nenhum parser disponível para este card")
            db.updateRawLogStatus(currentRawLogId, status = "failed", error = "no parser available")
            return
        }
        L.d(TAG, "Parser selecionado: ${parser::class.simpleName}")

        val ride = parser.parse(raw)
        if (ride == null || ride.value == null || ride.value <= 0) {
            L.d(TAG, "Parser retornou RideData inválido — ignorando")
            db.updateRawLogStatus(currentRawLogId, status = "failed", error = "invalid ride data")
            return
        }

        val cardHash = CardHashGenerator.generateStableHash(
            serviceType = ride.serviceType,
            pickupAddress = ride.pickupAddress,
            dropoffAddress = ride.dropoffAddress,
            rating = ride.rating
        )

        if (!isValidRide(ride)) {
            L.w(TAG, "⛔ Card inválido ignorado: value=${ride.value} km=${ride.distanceKm} time=${ride.timeMin}min")
            db.updateRawLogStatus(currentRawLogId, status = "failed", error = "invalid ride (no value or no distance/time)")
            return
        }

        val now = System.currentTimeMillis()
        if (isValorSuspeito(ride.value) && (now - lastSaveTime) < 30_000L) {
            L.d(TAG, "Valor suspeito R$ ${ride.value} ignorado — possível tela de confirmação")
            db.updateRawLogStatus(currentRawLogId, status = "failed", error = "suspicious value")
            return
        }

        if ((now - lastSaveTime) < 5_000L && ride.value == lastSavedValue) {
            L.d(TAG, "⏱️ Duplicata rápida: valor R$${ride.value} salvo há ${now - lastSaveTime}ms — ignorando")
            db.updateRawLogStatus(currentRawLogId, status = "duplicate", error = "same value within 5s")
            return
        }

        saveOrUpdateRide(ride, cardHash, db)
        db.updateRawLogRideData(currentRawLogId, ride)
        db.updateRawLogStatus(currentRawLogId, rideId = lastInsertedId.takeIf { it >= 0 }, status = "success")

        cardVisible = true
        lastHash = hash
        lastSaveTime = now
        lastSavedValue = ride.value

        L.d(TAG, "Card detectado V2 em $pkg: valor=${ride.value} km=${ride.distanceKm} " +
                "tempo=${ride.timeMin} nota=${ride.rating} " +
                "R$/km=${ride.effectivePricePerKm} R$/h=${ride.effectivePricePerHour}")
    }

    private fun saveOrUpdateRide(ride: RideData, cardHash: String, db: DatabaseHelper) {
        L.d(TAG, "saveOrUpdateRide: value=${ride.value} km=${ride.distanceKm} hash=$cardHash")
        if (ride.value == null) {
            L.w(TAG, "value é null — card não será exibido")
            return
        }

        val existingRideId = if (cardHash.isNotEmpty()) db.getRideIdByCardHash(cardHash) else null

        if (existingRideId != null) {
            val existingRide = db.getRideById(existingRideId)
            if (existingRide?.value != ride.value) {
                L.d(TAG, "🔄 Valor atualizado para corrida existente: hash=$cardHash, " +
                        "valor antigo=${existingRide?.value}, novo=${ride.value}")
                db.updateRideValueByHash(cardHash, ride.value ?: 0.0, System.currentTimeMillis())
                lastInsertedId = existingRideId
                L.d(TAG, "Ride $lastInsertedId atualizado com novo valor R$ ${ride.value}")
            } else {
                L.d(TAG, "⏭️ Card já existe com mesmo valor — ignorando")
                return
            }
        } else {
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
                cardHash = cardHash.ifEmpty { null }
            ))
            L.d(TAG, "Ride inserido com id=$lastInsertedId")
        }

        lastSaveTime = System.currentTimeMillis()
        statusLocked = false
        vibrateFeedback()

        sendBroadcast(Intent("NEW_RIDE_SAVED").setPackage(packageName))

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
        })
    }

    private fun selectParser(raw: RawCardData): RideDataParser? {
        for (parser in parsers) {
            if (parser.canParse(raw)) {
                return parser
            }
        }
        return null
    }

    private fun isValorSuspeito(valor: Double): Boolean {
        return valor == valor.toLong().toDouble() && valor >= 30.0
    }

    private fun handleClick(texto: String) {
        val lower = texto.lowercase()
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

    private fun isValidRide(ride: RideData): Boolean {
        if (ride.value == null || ride.value <= 0) return false
        val hasDistance = (ride.distanceKm ?: 0.0) > 0
            || (ride.tripDistanceKm ?: 0.0) > 0
            || (ride.pickupDistanceKm ?: 0.0) > 0
        val hasTime = (ride.timeMin ?: 0) > 0
            || (ride.tripTimeMin ?: 0) > 0
            || (ride.pickupTimeMin ?: 0) > 0
        return hasDistance || hasTime
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

    companion object {
        private const val TAG = "RideAccessibilityV2"
    }
}
