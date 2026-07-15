package com.profitdriving.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.profitdriving.BuildConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import com.profitdriving.CardHashGenerator
import com.profitdriving.CaptureManager
import com.profitdriving.DatabaseHelper
import com.profitdriving.DecisionEngine
import com.profitdriving.EventAlertManager
import com.profitdriving.FloatingBubbleService
import com.profitdriving.FloatingCardService
import com.profitdriving.L
import com.profitdriving.RideData
import com.profitdriving.RideRecord
import com.profitdriving.SecurePreferences
import com.profitdriving.SettingsActivity
import com.profitdriving.accessibility.extractor.App99Extractor
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.accessibility.extractor.UberCardExtractor
import com.profitdriving.parser.App99CardParser
import com.profitdriving.parser.DiscoveryCardParser
import com.profitdriving.parser.ExclusiveCardParser
import com.profitdriving.parser.GenericRideCardParser
import com.profitdriving.parser.RadarCardParser
import com.profitdriving.parser.CardClassificationEngine
import com.profitdriving.parser.CardTier
import com.profitdriving.parser.ReservationDetailParser
import com.profitdriving.parser.RideDataParser


class RideAccessibilityServiceV2 : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingJob: Job? = null
    private var versionToken = 0L
    private val readMutex = Mutex()
    private var lastHash = ""
    private var lastSaveTime = 0L
    private var cardVisible = false
    private var lastInsertedId: Long = -1L
    private var statusLocked = false
    private var uberWindowWasVisible = false
    private var lastSavedValue: Double? = null
    private var currentRawLogId: Long = -1L
    private var isReScanTriggered = false
    private var dismissPending: kotlinx.coroutines.Job? = null
    private val eventAlertManager by lazy { EventAlertManager(this) }
    private val anomalyDetector = AnomalyDetector(this)

    private val parsers: List<RideDataParser> = listOf(
        ReservationDetailParser(),
        RadarCardParser(),
        ExclusiveCardParser(),
        App99CardParser(),
        DiscoveryCardParser(),
        GenericRideCardParser()
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
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
            if (pendingJob?.isActive == true) return
            versionToken++
            val token = versionToken

            val is99 = pkg.contains("taxis99") || pkg.contains("app99")

            pendingJob = scope.launch(Dispatchers.IO) {
                if (token != versionToken) return@launch
                readMutex.withLock {
                    if (token != versionToken) return@withLock
                    if (is99) {
                        if (isAppReadingEnabled("99")) {
                            val root = findApp99Window()
                            if (root != null) {
                                try {
                                    val texts = mutableListOf<String>()
                                    collectTextsForCheck(root, texts)
                                    val hasCardData = texts.any { t ->
                                        val lower = t.lowercase(java.util.Locale.ROOT)
                                        lower.contains("aceitar") || lower.contains("escolher") ||
                                        lower.contains("selecionar") ||
                                        (lower.contains("min") && lower.contains("km"))
                                    }
                                    if (hasCardData) {
                                        L.d(TAG, "99: card detectado na árvore, processando...")
                                        val raw = com.profitdriving.accessibility.extractor.App99Extractor.createFromTexts(texts)
                                        processRawCard(raw, pkg)
                                        return@withLock
                                    }
                                    L.d(TAG, "99: janela sem dados de corrida (home screen) — ignorando")
                                    return@withLock
                                } finally {
                                    recycleDeprecated(root)
                                }
                            }
                            captureAndProcessWithMLKit(pkg)
                        }
                    } else if (isAppReadingEnabled("uber")) {
                        val root = readStableTree(pkg)
                        if (root == null) return@withLock
                        val detectedPkg = root.packageName?.toString() ?: ""
                        try {
                            detectRideCard(root, detectedPkg)
                        } finally {
                            recycleDeprecated(root)
                        }
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

            if (pendingJob?.isActive == true) return
            versionToken++
            val token = versionToken

            pendingJob = scope.launch(Dispatchers.IO) {
                delay(300)
                if (token != versionToken) return@launch
                readMutex.withLock {
                    if (token != versionToken) return@withLock
                    val root = readStableTree(pkg) ?: return@withLock
                    val detectedPkg = root.packageName?.toString() ?: ""
                    try {
                        detectRideCard(root, detectedPkg)
                    } finally {
                        recycleDeprecated(root)
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
        instance = null
        FloatingBubbleService.stop(this)
        scope.cancel()
        super.onDestroy()
    }

    private fun findRideWindow(): AccessibilityNodeInfo? {
        return findUberWindow() ?: findApp99Window()
    }

    private fun findRideWindow(pkg: String): AccessibilityNodeInfo? {
        return when {
            pkg.contains("ubercab") -> findUberWindow()
            pkg.contains("taxis99") || pkg.contains("app99") -> findApp99Window()
            else -> findUberWindow() ?: findApp99Window()
        }
    }

    private fun findUberWindow(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        try {
            val allWindows = windows ?: return null
            logWindows(allWindows)
            val overlayWindows = allWindows.filter { it.type == 6 }
            for (window in overlayWindows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (pkg.contains("ubercab")) {
                    L.d(TAG, "Card overlay Uber encontrado! tipo=${window.type}")
                    return root
                }
                recycleDeprecated(root)
            }
            for (window in allWindows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (pkg.contains("ubercab") && hasTextContent(root)) {
                    L.d(TAG, "Janela Uber encontrada: tipo=${window.type} (com conteúdo)")
                    return root
                }
                recycleDeprecated(root)
            }
            for (window in allWindows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (window.type == 1 && pkg.contains("ubercab")) {
                    L.d(TAG, "Janela Uber encontrada (fallback sem texto): tipo=${window.type}")
                    return root
                }
                recycleDeprecated(root)
            }
        } catch (e: Exception) {
            L.e(TAG, "Erro findUberWindow: ${e.message}")
        }
        return null
    }

    private fun findApp99Window(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        try {
            val allWindows = windows ?: return null
            logWindows(allWindows)
            val overlayWindows = allWindows.filter { it.type == 6 }
            for (window in overlayWindows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (pkg.contains("taxis99") || pkg.contains("app99")) {
                    L.d(TAG, "Card overlay 99 encontrado! tipo=${window.type}")
                    return root
                }
                recycleDeprecated(root)
            }
            var lastMatch: AccessibilityNodeInfo? = null
            for (window in allWindows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (pkg.contains("taxis99") || pkg.contains("app99")) {
                    lastMatch?.let { recycleDeprecated(it) }
                    lastMatch = root
                    L.d(TAG, "Janela 99 acumulada: tipo=${window.type} (childCount=${root.childCount})")
                    continue
                }
                recycleDeprecated(root)
            }
            if (lastMatch != null) {
                L.d(TAG, "Retornando última janela 99 (childCount=${lastMatch.childCount})")
                return lastMatch
            }
        } catch (e: Exception) {
            L.e(TAG, "Erro findApp99Window: ${e.message}")
        }
        return null
    }

    private fun logWindows(allWindows: List<android.view.accessibility.AccessibilityWindowInfo>) {
        if (BuildConfig.DEBUG) {
            L.d(TAG, "Total de janelas: ${allWindows.size}")
            allWindows.forEachIndexed { i, w ->
                val r = w.root
                val texts = if (r != null) {
                    try {
                        val t = mutableListOf<String>()
                        collectTextsForCheck(r, t)
                        t.take(3).joinToString(", ")
                    } catch (_: Exception) { "?" }
                } else ""
                L.d(TAG, "Janela $i: tipo=${w.type} pkg=${r?.packageName} textos=[$texts]")
            }
        }
    }

    private fun recycleDeprecated(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    private fun hasTextContent(node: AccessibilityNodeInfo): Boolean {
        return try {
            val texts = mutableListOf<String>()
            collectTextsForCheck(node, texts)
            texts.any { it.contains("R$", ignoreCase = true) || it.contains("km", ignoreCase = true) || it.contains("min", ignoreCase = true) }
        } catch (e: Exception) {
            false
        }
    }

    private fun collectTextsForCheck(node: AccessibilityNodeInfo, list: MutableList<String>) {
        if (node.text?.toString()?.isNotBlank() == true) {
            list.add(node.text.toString())
        } else if (node.contentDescription?.toString()?.isNotBlank() == true) {
            list.add(node.contentDescription.toString())
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTextsForCheck(child, list)
                recycleDeprecated(child)
            }
        }
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
        FloatingCardService.stop(this)
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

    private suspend fun detectRideCard(root: AccessibilityNodeInfo, pkg: String) {
        try {
            L.d(TAG, "=== detectRideCard V2 iniciado para $pkg ===")

            if (!pkg.contains("ubercab")) return

            if (!isAppReadingEnabled("uber")) {
                L.d(TAG, "Leitura Uber desabilitada nas configurações")
                return
            }

            L.d(TAG, "🔍 Chamando UberCardExtractor")
            val raw = UberCardExtractor.extract(root, pkg)
            if (raw != null && raw.rawTexts.isNotEmpty()) {
                processRawCard(raw, pkg)
            } else {
                L.d(TAG, "UberCardExtractor retornou null/vazio — tentando ML Kit")
                captureAndProcessWithMLKit(pkg)
            }
        } catch (e: Exception) {
            L.e(TAG, "detectRideCard crashou: ${e.message}", e)
        }
    }

    private fun isAppReadingEnabled(app: String): Boolean {
        val prefs = try {
            SecurePreferences.get(this)
        } catch (_: Exception) {
            getSharedPreferences(SettingsActivity.PREF_NAME, 0)
        }
        return when (app) {
            "uber" -> prefs.getBoolean(SettingsActivity.KEY_READ_UBER, true)
            "99" -> prefs.getBoolean(SettingsActivity.KEY_READ_APP99, true)
            else -> true
        }
    }

    private suspend fun readStableTree(pkg: String? = null): AccessibilityNodeInfo? {
        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null) {
                val apkg = activeRoot.packageName?.toString() ?: ""
                if (pkg == null || apkg.contains("ubercab") || apkg.contains("taxis99") || apkg.contains("app99")) {
                    val texts = mutableListOf<String>()
                    collectTextsForCheck(activeRoot, texts)
                    val hasPrice = texts.any { it.contains("R$", ignoreCase = true) }
                    val hasAction = texts.any { t ->
                        val lower = t.lowercase()
                        lower.contains("aceitar") || lower.contains("accept") ||
                        lower.contains("selecionar") || lower.contains("escolher")
                    }
                    if (hasPrice && hasAction) {
                        L.d(TAG, "readStableTree: rootInActiveWindow funcionou (pkg=$apkg)")
                        return activeRoot
                    }
                    L.d(TAG, "readStableTree: rootInActiveWindow não tem conteúdo válido, varrendo janelas...")
                }
                recycleDeprecated(activeRoot)
            }
        } catch (_: Exception) { }

        val fastRoot = if (pkg != null) findRideWindow(pkg) else findRideWindow()
        if (fastRoot != null) {
            val t = mutableListOf<String>()
            collectTextsForCheck(fastRoot, t)
            if (t.isEmpty()) {
                L.d(TAG, "readStableTree: janela vazia (fast path), pulando retry loop")
                return fastRoot
            }
            recycleDeprecated(fastRoot)
        }

        val delays = listOf(0L, 200L, 400L, 800L)
        var lastRoot: AccessibilityNodeInfo? = null
        var result: AccessibilityNodeInfo? = null

        try {
            result = withTimeoutOrNull(2000L) {
                for ((i, delay) in delays.withIndex()) {
                    if (delay > 0) delay(delay)
                    val root = if (pkg != null) findRideWindow(pkg) else findRideWindow()
                    if (root == null) continue

                    if (root !== lastRoot) {
                        lastRoot?.let { recycleDeprecated(it) }
                        lastRoot = root
                    }

                    val texts = mutableListOf<String>()
                    collectTextsForCheck(root, texts)

                    if (texts.isEmpty()) {
                        L.d(TAG, "readStableTree: janela vazia (fast path na tentativa $i)")
                        return@withTimeoutOrNull root
                    }

                    val hasPrice = texts.any { it.contains("R$", ignoreCase = true) }
                    val hasAction = texts.any { t ->
                        val lower = t.lowercase()
                        lower.contains("aceitar") || lower.contains("accept") ||
                        lower.contains("selecionar") || lower.contains("escolher")
                    }

                    if (hasPrice && hasAction) {
                        L.d(TAG, "readStableTree: sucesso na tentativa $i (delay=${delay}ms)")
                        return@withTimeoutOrNull root
                    }

                    L.d(TAG, "readStableTree: tentativa $i (delay=${delay}ms) — price=$hasPrice action=$hasAction, textos=${texts.take(5)}")
                }
                null
            }
        } finally {
            if (result == null && lastRoot != null) {
                recycleDeprecated(lastRoot)
            }
        }

        return result
    }

    private fun captureAndProcessWithMLKit(pkg: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            L.d(TAG, "❌ [MLKit] ML Kit requer Android 14+ (API 34). Dispositivo: API ${Build.VERSION.SDK_INT}")
            return
        }
        val appName = when {
            pkg.contains("ubercab") -> "Uber"
            pkg.contains("taxis99") || pkg.contains("app99") -> "99"
            else -> "Unknown"
        }
        val tStart = System.currentTimeMillis()
        L.d(TAG, "📸 [MLKit] captureAndProcessWithMLKit() INICIADO - pkg=$pkg, app=$appName, API=${Build.VERSION.SDK_INT}")
        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val tAfterScreenshot = System.currentTimeMillis()
                        L.d(TAG, "⏱️ [MLKit] takeScreenshot onSuccess em ${tAfterScreenshot - tStart}ms")
                        try {
                            val hb = result.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(hb, null)
                            hb.close()
                            if (bitmap != null) {
                                processMLKitScreenshot(bitmap, appName, pkg)
                            }
                        } catch (e: Exception) {
                            L.e(TAG, "❌ [MLKit] Exceção no onSuccess: ${e.message}", e)
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        L.d(TAG, "❌ [MLKit] takeScreenshot.onFailure: errorCode=$errorCode (após ${System.currentTimeMillis() - tStart}ms)")
                    }
                })
        } catch (e: Exception) {
            L.e(TAG, "❌ [MLKit] Exceção em captureAndProcessWithMLKit: ${e.message}", e)
        }
    }

    private fun processMLKitScreenshot(bitmap: Bitmap, appName: String, pkg: String) {
        val t0 = System.currentTimeMillis()

        val windowBounds = getAppWindowBounds(pkg)
        L.d(TAG, "⏱️ [MLKit] windowBounds=$windowBounds")
        val raw = when {
            pkg.contains("ubercab") ->
                UberCardExtractor.extractWithMLKit(bitmap, windowBounds)
            else ->
                App99Extractor.extractWithMLKit(bitmap, windowBounds)
        }
        val tAfterOCR = System.currentTimeMillis()
        L.d(TAG, "⏱️ [MLKit] OCR levou ${tAfterOCR - t0}ms")

        if (raw != null) {
            processRawCard(raw, pkg)
        }

        val captureManager = CaptureManager(this@RideAccessibilityServiceV2)
        val rideHash = currentRawLogId.let { if (it >= 0) it.toString() else null }
        captureManager.saveCapture(bitmap, appName, rideHash)
        L.d(TAG, "⏱️ [MLKit] total=${System.currentTimeMillis() - t0}ms raw=${raw != null}")
        bitmap.recycle()
    }

    private fun getAppWindowBounds(pkg: String): android.graphics.Rect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        try {
            val allWindows = windows ?: return null
            for (window in allWindows) {
                val root = window.root ?: continue
                val wpkg = root.packageName?.toString() ?: ""
                val isTarget = when {
                    pkg.contains("ubercab") -> wpkg.contains("ubercab")
                    else -> wpkg.contains("taxis99") || wpkg.contains("app99")
                }
                if (isTarget) {
                    val bounds = android.graphics.Rect()
                    window.getBoundsInScreen(bounds)
                    recycleDeprecated(root)
                    if (!bounds.isEmpty()) return bounds
                } else {
                    recycleDeprecated(root)
                }
            }
        } catch (e: Exception) {
            L.e(TAG, "getAppWindowBounds error: ${e.message}")
        }
        return null
    }

    private fun processRawCard(raw: RawCardData, pkg: String) {
        val tStart = System.currentTimeMillis()
        try {
            L.d(TAG, "Textos (${raw.rawTexts.size}): ${raw.rawTexts.take(10)} — hash=${buildCardHash(raw.rawTexts)}")

            val db = DatabaseHelper(this)
            val hash = buildCardHash(raw.rawTexts)

            if (raw.acceptNode == null) {
                L.d(TAG, "Nenhum botão de aceitar encontrado — verificando se é card de corrida")
                if (raw.valueNode == null) {
                    L.d(TAG, "Card não é de corrida — ignorando")
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
                        FloatingCardService.stop(this)
                    }
                    return
                }
            }

            if (!isPlausible(raw)) {
                L.d(TAG, "Card reprovado na validação de plausibilidade — ignorando")
                return
            }

            if (hash == lastHash && cardVisible) {
                L.d(TAG, "Mesmo hash do card visível — ignorando (hash=$hash)")
                return
            }

            currentRawLogId = db.insertRawLog(
                cardHash = hash,
                pkg = pkg,
                cardType = raw.cardType.name,
                rawTexts = raw.rawTexts
            )
            L.d(TAG, "Raw log salvo: id=$currentRawLogId")

            val parser = selectParser(raw)
            if (parser == null) {
                L.d(TAG, "Nenhum parser disponível para este card")
                db.updateRawLogStatus(currentRawLogId, status = "failed", error = "no parser available")

                anomalyDetector.recordFailure(pkg)

                return
            }
            L.d(TAG, "Parser selecionado: ${parser::class.simpleName}")

            var ride = parser.parse(raw)
            if (ride == null || ride.value == null || ride.value <= 0) {
                L.d(TAG, "Parser retornou RideData inválido — ignorando")
                db.updateRawLogStatus(currentRawLogId, status = "failed", error = "invalid ride data")
                anomalyDetector.recordFailure(pkg)
                return
            }

            val classification = CardClassificationEngine().classify(ride, raw.validCropCount)
            ride = classification.rideData

            L.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            L.d(TAG, "📊 Classificação: ${classification.tier.displayName} (${classification.score}%)${if (classification.isCorrected) " (corrigido)" else ""}")
            classification.details.forEach { L.d(TAG, "  $it") }
            classification.corrections.forEach { L.d(TAG, "  🔧 $it") }
            if (classification.missingFields.isNotEmpty()) {
                L.d(TAG, "  ❌ Campos faltando: ${classification.missingFields.joinToString(", ")}")
            }
            if (classification.suspiciousPatterns.isNotEmpty()) {
                L.d(TAG, "  ⚠️ Padrões suspeitos: ${classification.suspiciousPatterns.joinToString("; ")}")
            }
            L.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            if (classification.tier.ordinal >= CardTier.BROKEN.ordinal) {
                L.w(TAG, "⛔ Card descartado: ${classification.tier.displayName} (${classification.score}%)")
                db.updateRawLogStatus(currentRawLogId, status = "failed", error = "${classification.tier.name} score=${classification.score}%")
                anomalyDetector.recordFailure(pkg)
                return
            }

            val cardHash = CardHashGenerator.generateStableHash(
                serviceType = ride.serviceType,
                pickupAddress = ride.pickupAddress,
                dropoffAddress = ride.dropoffAddress,
                rating = ride.rating,
                value = ride.value,
                distanceKm = ride.distanceKm,
                timeMin = ride.timeMin,
                exclusiveHash = ride.exclusiveHash
            )

            if (!isValidRide(ride)) {
                L.w(TAG, "⛔ Card inválido ignorado: value=${ride.value} km=${ride.distanceKm} time=${ride.timeMin}min")
                db.updateRawLogStatus(currentRawLogId, status = "failed", error = "invalid ride (no value or no distance/time)")
                anomalyDetector.recordFailure(pkg)
                return
            }

            val now = System.currentTimeMillis()
            val v = ride.value ?: return
            if (isValorSuspeito(v) && (now - lastSaveTime) < 30_000L) {
                L.d(TAG, "Valor suspeito R$ $v ignorado — possível tela de confirmação")
                db.updateRawLogStatus(currentRawLogId, status = "failed", error = "suspicious value")
                return
            }

            if ((now - lastSaveTime) < 5_000L && ride.value == lastSavedValue) {
                L.d(TAG, "Duplicata rápida: valor R$${ride.value} salvo há ${now - lastSaveTime}ms — ignorando")
                db.updateRawLogStatus(currentRawLogId, status = "duplicate", error = "same value within 5s")
                return
            }

            saveOrUpdateRide(ride, cardHash, db)
            db.updateRawLogRideData(currentRawLogId, ride)
            db.updateRawLogStatus(currentRawLogId, rideId = lastInsertedId.takeIf { it >= 0 }, status = "success")
            anomalyDetector.recordSuccess(pkg)

            cardVisible = true
            lastHash = hash
            lastSaveTime = now
            lastSavedValue = ride.value

        if (isReScanTriggered) {
            L.d(TAG, "Re-scan: forçando exibição do floating card")
            isReScanTriggered = false
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
                putExtra("reScan", true)
            })
        }

        L.d(TAG, "Card detectado V2 em $pkg: valor=${ride.value} km=${ride.distanceKm} " +
                "tempo=${ride.timeMin} nota=${ride.rating} " +
                "R$/km=${ride.effectivePricePerKm} R$/h=${ride.effectivePricePerHour}" +
                " — processRawCard total=${System.currentTimeMillis() - tStart}ms")
        } catch (e: Exception) {
            L.e(TAG, "processRawCard crashou: ${e.message}", e)
        }
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
                L.d(TAG, "Valor atualizado para corrida existente: hash=$cardHash, " +
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
                cardHash = cardHash.ifEmpty { null },
                kmState = result.params[0].state.ordinal,
                hourState = result.params[1].state.ordinal,
                minState = result.params[2].state.ordinal,
                ratingState = result.params[3].state.ordinal
            ))
            L.d(TAG, "Ride inserido com id=$lastInsertedId")
            eventAlertManager.check(
                rawPickup = ride.pickupAddress,
                rawDropoff = ride.dropoffAddress,
            )
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
        L.d(TAG, "FloatingCardService.start chamado de saveOrUpdateRide — valor=${ride.value} hash=$cardHash")

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

    private fun isPlausible(raw: RawCardData): Boolean {
        val value = parsePlausibleValue(raw.valueNode)
        if (value != null && (value < 2.0 || value > 10000.0)) {
            L.w(TAG, "Valor rejeitado: R$ $value (fora de 2,00–10.000,00)")
            return false
        }

        val pickup = parsePlausiblePickup(raw.pickupNode)
        if (pickup != null) {
            if (pickup.first > 120) return false
            if (pickup.second > 30) return false
        }

        val rating = parsePlausibleRating(raw.ratingNode)
        if (rating != null && (rating < 3.0 || rating > 5.0)) return false

        return true
    }

    private fun parsePlausibleValue(node: String?): Double? {
        if (node == null) return null
        val m = Regex("""R\$\s*(\d+(?:[.,]\d+)?)""").find(node) ?: return null
        return m.groupValues[1].replace(",", ".").toDoubleOrNull()
    }

    private fun parsePlausiblePickup(node: String?): Pair<Int, Double>? {
        if (node == null) return null
        val m = Regex("""(\d+)\s*min.*?(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(node) ?: return null
        val min = m.groupValues[1].toIntOrNull() ?: return null
        val km = m.groupValues[2].replace(",", ".").toDoubleOrNull() ?: return null
        return Pair(min, km)
    }

    private fun parsePlausibleRating(node: String?): Double? {
        if (node == null) return null
        val m = Regex("""(\d[.,]\d{1,2})""").find(node) ?: return null
        return m.groupValues[1].replace(",", ".").toDoubleOrNull()
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

    fun triggerReScan() {
        L.d(TAG, "triggerReScan() chamado via double-tap")
        isReScanTriggered = true
        scope.launch {
            delay(5000L)
            if (isReScanTriggered) {
                L.d(TAG, "Re-scan timeout: resetando flag")
                isReScanTriggered = false
            }
        }
        if (pendingJob?.isActive == true) return
        versionToken++
        val token = versionToken
        pendingJob = scope.launch(Dispatchers.IO) {
            delay(300)
            if (token != versionToken) return@launch
            readMutex.withLock {
                if (token != versionToken) return@withLock
                val root = readStableTree() ?: return@withLock
                val pkg = root.packageName?.toString() ?: ""
                try {
                    detectRideCard(root, pkg)
                } finally {
                    recycleDeprecated(root)
                }
            }
        }
    }

    companion object {
        private const val TAG = "RideAccessibilityV2"
        var instance: RideAccessibilityServiceV2? = null
    }
}
