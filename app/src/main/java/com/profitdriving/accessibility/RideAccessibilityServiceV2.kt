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
    private var lastOcrAttempt = 0L
    private val OCR_COOLDOWN = 30000L
    private var lastCaptureTime = 0L
    private val CAPTURE_COOLDOWN = 10000L
    private var parserMatched = false
    private var lastNoCardTime = 0L
    private var noCardBackoff = 1000L
    private var lastHomeScanHash = ""
    private val eventAlertManager by lazy { EventAlertManager(this) }

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
            // Fallback: primeira janela Uber tipo 1 (APPLICATION) mesmo sem texto
            // Necessário para Samsung Tab A9 onde ride card é WebView com nós ocultos
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
        L.d(TAG, "Janela Uber saiu — estado resetado, card flutuante removido")
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
        val now = System.currentTimeMillis()
        if (noCardBackoff > 0 && lastNoCardTime > 0 && (now - lastNoCardTime) < noCardBackoff) {
            L.d(TAG, "No-card backoff ativo (${(noCardBackoff - (now - lastNoCardTime)) / 1000}s) — pulando")
            return
        }
        try {
            L.d(TAG, "=== detectRideCard V2 iniciado para $pkg ===")

            when {
                pkg.contains("ubercab") -> {
                    if (!isAppReadingEnabled("uber")) {
                        L.d(TAG, "Leitura Uber desabilitada nas configurações")
                        return
                    }
                    L.d(TAG, "🔍 Chamando UberCardExtractor")
                    var raw = UberCardExtractor.extract(root, pkg)
                    val isEmptyOverlay = raw?.rawTexts?.isEmpty() == true && hasEmptyOverlay()
                    if (raw != null && raw.rawTexts.isEmpty() && isEmptyOverlay) {
                        // Tenta ativar o WebView via accessibility focus antes do OCR
                        L.d(TAG, "Card vazio — tentando accessibility focus no WebView...")
                        tryForceWebViewAccessibility(root)
                        delay(250)
                        raw = UberCardExtractor.extract(root, pkg)
                        L.d(TAG, "Re-leitura após focus: ${raw?.rawTexts?.size ?: 0} textos")
                    }
                    if (raw != null) {
                        parserMatched = false
                        processRawCard(raw, pkg)
                        if (parserMatched) {
                            captureScreen("Uber")
                        } else if (raw.acceptNode == null && raw.valueNode == null) {
                            captureAndProcessUberCard(pkg, force = isEmptyOverlay)
                        }
                    } else {
                        L.d(TAG, "UberCardExtractor retornou null")
                        captureAndProcessUberCard(pkg, force = isEmptyOverlay)
                    }
                }
                pkg.contains("taxis99") || pkg.contains("app99") -> {
                    if (!isAppReadingEnabled("99")) {
                        L.d(TAG, "Leitura 99 desabilitada nas configurações")
                        return
                    }
                    L.d(TAG, "🔍 Chamando App99Extractor")
                    val raw = App99Extractor.extract(root, pkg)
                    if (raw != null) {
                        processRawCard(raw, pkg)
                    } else {
                        L.d(TAG, "⚠️ App99Extractor falhou, chamando OCR...")
                        captureAndProcess99Card(pkg)
                    }
                }
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
        // Tenta rootInActiveWindow primeiro (mais rápido)
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

        // Fast path: se a janela ride já está vazia (ex: WebView com nós ocultos),
        // não adianta fazer retry — vai direto pra OCR.
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
                    L.d(TAG, "readStableTree: tentativa $i (delay=${delay}ms) — não atingiu critério (price=$hasPrice action=$hasAction, textos=${texts.take(5)})")
                }
                L.d(TAG, "readStableTree: nenhuma tentativa atingiu critério de estabilidade, usando última leitura")
                lastRoot
            }
        } finally {
            if (result == null && lastRoot != null) {
                recycleDeprecated(lastRoot)
            }
        }

        return result
    }

    private fun captureScreen(appName: String) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < CAPTURE_COOLDOWN) {
            L.d(TAG, "[captureScreen] Cooldown ativo (${(CAPTURE_COOLDOWN - (now - lastCaptureTime)) / 1000}s)")
            return
        }
        lastCaptureTime = now
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val hb = result.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(hb, null)
                            hb.close()
                            if (bitmap != null) {
                                L.d(TAG, "📸 Captura salva para $appName")
                                CaptureManager(this@RideAccessibilityServiceV2).saveCapture(bitmap, appName)
                                bitmap.recycle()
                            }
                        } catch (_: Exception) { }
                    }
                    override fun onFailure(c: Int) { }
                })
        } catch (_: Exception) { }
    }

    private fun captureAndProcess99Card(pkg: String) {
        L.d(TAG, "📸 [OCR] captureAndProcess99Card() INICIADO - pkg=$pkg, API=${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            L.d(TAG, "❌ [OCR] OCR requer Android 14+ (API 34). Dispositivo: API ${Build.VERSION.SDK_INT}")
            return
        }
        try {
            L.d(TAG, "📸 [OCR] Chamando takeScreenshot(Display, Executor, Callback)...")
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        L.d(TAG, "✅ [OCR] onSuccess chamado!")
                        try {
                            val hb = result.hardwareBuffer
                            L.d(TAG, "📸 [OCR] hardwareBuffer: ${hb.width}x${hb.height} format=${hb.format}")
                            val bitmap = Bitmap.wrapHardwareBuffer(hb, null)
                            hb.close()
                            if (bitmap != null) {
                                L.d(TAG, "✅ [OCR] Bitmap: ${bitmap.width}x${bitmap.height} config=${bitmap.config}")
                                L.d(TAG, "📸 [OCR] Salvando captura no histórico...")
                                val captureManager = CaptureManager(this@RideAccessibilityServiceV2)
                                val rideHash = currentRawLogId.let { if (it >= 0) it.toString() else null }
                                captureManager.saveCapture(bitmap, "99", rideHash)
                                L.d(TAG, "📸 [OCR] Chamando App99Extractor.extractWithOCR()...")
                                val raw = App99Extractor.extractWithOCR(this@RideAccessibilityServiceV2, bitmap)
                                L.d(TAG, "📸 [OCR] extractWithOCR retornou: ${if (raw != null) "SUCESSO" else "null"}")
                                bitmap.recycle()
                                if (raw != null) {
                                    L.d(TAG, "✅ [OCR] OCR funcionou! ${raw.rawTexts.size} textos")
                                    processRawCard(raw, pkg)
                                } else {
                                    L.d(TAG, "❌ [OCR] OCR falhou ou não extraiu texto")
                                }
                            } else {
                                L.d(TAG, "❌ [OCR] Bitmap.wrapHardwareBuffer retornou null!")
                            }
                        } catch (e: Exception) {
                            L.e(TAG, "❌ [OCR] Exceção no onSuccess: ${e.message}", e)
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        L.d(TAG, "❌ [OCR] takeScreenshot.onFailure: errorCode=$errorCode")
                    }
                })
            L.d(TAG, "📸 [OCR] takeScreenshot chamado (callback pendente)")
        } catch (e: Exception) {
            L.e(TAG, "❌ [OCR] Exceção em captureAndProcess99Card: ${e.message}", e)
        }
    }

    private fun hasEmptyOverlay(): Boolean {
        return try {
            windows?.any { w ->
                val isTargetPkg = w.root?.packageName?.toString()?.let { pkg ->
                    pkg.contains("ubercab") || pkg.contains("app99") || pkg.contains("taxis99")
                } == true
                if (!isTargetPkg) return@any false
                if (w.type != 6 && w.type != 1) return@any false
                w.root?.let { root ->
                    val t = mutableListOf<String>()
                    collectTextsForCheck(root, t)
                    t.isEmpty()
                } == true
            } ?: false
        } catch (_: Exception) { false }
    }

    private fun tryForceWebViewAccessibility(root: AccessibilityNodeInfo) {
        try {
            // Tenta encontrar o nó WebView e solicitar foco de acessibilidade
            // para forçar o WebView a popular sua árvore de acessibilidade
            val wv = findWebViewInTree(root)
            if (wv != null) {
                L.d(TAG, "WebView encontrado — solicitando accessibility focus")
                wv.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                // Alguns WebViews precisam de dois focus requests
                wv.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            } else {
                L.d(TAG, "Nenhum nó WebView encontrado — tentando focus na raiz")
                root.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            }
        } catch (e: Exception) {
            L.e(TAG, "tryForceWebViewAccessibility falhou: ${e.message}")
        }
    }

    private fun findWebViewInTree(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val cn = node.className?.toString() ?: ""
        if (cn.contains("WebView", ignoreCase = true) ||
            cn.contains("chromium", ignoreCase = true) ||
            cn.contains("WebContents", ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findWebViewInTree(child)
            if (result != null) {
                recycleDeprecated(child)
                return result
            }
            recycleDeprecated(child)
        }
        return null
    }

    private fun captureAndProcessUberCard(pkg: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastOcrAttempt < OCR_COOLDOWN) {
            L.d(TAG, "[UberOCR] Cooldown ativo (${(OCR_COOLDOWN - (now - lastOcrAttempt)) / 1000}s restantes)")
            return
        }
        lastOcrAttempt = now

        L.d(TAG, "📸 [UberOCR] captureAndProcessUberCard() INICIADO - pkg=$pkg, API=${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return

        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val hb = result.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(hb, null)
                            hb.close()
                            if (bitmap != null) {
                                val captureManager = CaptureManager(this@RideAccessibilityServiceV2)
                                val rideHash = currentRawLogId.let { if (it >= 0) it.toString() else null }
                                captureManager.saveCapture(bitmap, "Uber", rideHash)
                                val raw = UberCardExtractor.extractWithOCR(this@RideAccessibilityServiceV2, bitmap)
                                bitmap.recycle()
                                if (raw != null) {
                                    L.d(TAG, "✅ [UberOCR] OCR funcionou! ${raw.rawTexts.size} textos")
                                    processRawCard(raw, pkg)
                                } else {
                                    L.d(TAG, "❌ [UberOCR] OCR falhou ou não extraiu texto")
                                }
                            }
                        } catch (e: Exception) {
                            L.e(TAG, "❌ [UberOCR] Exceção no onSuccess: ${e.message}", e)
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        L.d(TAG, "❌ [UberOCR] takeScreenshot.onFailure: errorCode=$errorCode")
                    }
                })
        } catch (e: Exception) {
            L.e(TAG, "❌ [UberOCR] Exceção em captureAndProcessUberCard: ${e.message}", e)
        }
    }

    private fun processRawCard(raw: RawCardData, pkg: String) {
        try {
            L.d(TAG, "Textos coletados (${raw.rawTexts.size}): ${raw.rawTexts.take(10)}")

            val db = DatabaseHelper(this)
            val hash = buildCardHash(raw.rawTexts)

            if (raw.acceptNode == null) {
                L.d(TAG, "Nenhum botão de aceitar encontrado — verificando se é card de corrida")
                if (raw.valueNode == null) {
                    L.d(TAG, "Card não é de corrida — ignorando")
                    lastNoCardTime = System.currentTimeMillis()
                    noCardBackoff = (noCardBackoff * 2).coerceAtMost(8000L)
                    lastHomeScanHash = hash
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

            // Só salva raw log se realmente é um card de corrida
            currentRawLogId = db.insertRawLog(
                cardHash = hash,
                pkg = pkg,
                cardType = raw.cardType.name,
                rawTexts = raw.rawTexts
            )
            L.d(TAG, "\uD83D\uDCDD Raw log salvo: id=$currentRawLogId")

            if (hash == lastHash && cardVisible) {
                L.d(TAG, "Mesmo card ainda visível — prosseguindo")
                db.updateRawLogStatus(currentRawLogId, status = "duplicate", error = "same card still visible")
            }

            val parser = selectParser(raw)
            if (parser == null) {
                L.d(TAG, "Nenhum parser disponível para este card")
                db.updateRawLogStatus(currentRawLogId, status = "failed", error = "no parser available")
                return
            }
            L.d(TAG, "Parser selecionado: ${parser::class.simpleName}")

            var ride = parser.parse(raw)
            if (ride == null || ride.value == null || ride.value <= 0) {
                L.d(TAG, "Parser retornou RideData inválido — ignorando")
                db.updateRawLogStatus(currentRawLogId, status = "failed", error = "invalid ride data")
                return
            }

            parserMatched = true
            lastNoCardTime = 0L
            noCardBackoff = 1000L
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
                L.d(TAG, "⏱️ Duplicata rápida: valor R$${ride.value} salvo há ${now - lastSaveTime}ms — prosseguindo")
                db.updateRawLogStatus(currentRawLogId, status = "duplicate", error = "same value within 5s")
            }

            lastNoCardTime = 0L
            noCardBackoff = 1000L
            saveOrUpdateRide(ride, cardHash, db)
            db.updateRawLogRideData(currentRawLogId, ride)
            db.updateRawLogStatus(currentRawLogId, rideId = lastInsertedId.takeIf { it >= 0 }, status = "success")

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
                "R$/km=${ride.effectivePricePerKm} R$/h=${ride.effectivePricePerHour}")
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

    fun diagnosticWebViewNodes() {
        val root = rootInActiveWindow ?: return
        findWebViewNodes(root, 0)
        recycleDeprecated(root)
    }

    private fun findWebViewNodes(node: AccessibilityNodeInfo, depth: Int) {
        val className = node.className?.toString() ?: ""
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        if (className.contains("WebView", ignoreCase = true) && text.isNotEmpty()) {
            L.d(WV_TAG, "WebView depth=$depth: className=$className text=$text")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findWebViewNodes(child, depth + 1)
            recycleDeprecated(child)
        }
    }

    companion object {
        private const val TAG = "RideAccessibilityV2"
        private const val WV_TAG = "WebViewDiagnostic"
        var instance: RideAccessibilityServiceV2? = null
    }
}
