package com.profitdriving.accessibility.extractor

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.profitdriving.L
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object UberCardExtractor {

    private const val TAG = "UberCardExtractor"

    fun extract(root: AccessibilityNodeInfo, pkg: String): RawCardData? {
        if (!pkg.contains("ubercab")) {
            L.d(TAG, "Pacote não é Uber ($pkg), abortando")
            return null
        }

        val allTexts = mutableListOf<String>()
        collectTexts(root, allTexts)
        L.d(TAG, "Textos coletados (${allTexts.size}): ${allTexts.take(10)}")

        // Se a árvore está completamente vazia (sem filhos, sem texto),
        // não faz sentido gastar tempo com fallback/advanced techniques
        if (allTexts.isEmpty() && root.childCount == 0) {
            L.d(TAG, "Árvore vazia (childCount=0) — pulando fallback/advanced techniques")
        } else {
            // Fallback: se não encontrou textos via recursão, tentar busca por texto
            if (allTexts.isEmpty()) {
                tryFallbackByText(root, allTexts)
            }

            // Técnicas avançadas de acessibilidade para WebView
            if (allTexts.isEmpty()) {
                tryAdvancedAccessibility(root, allTexts)
            }
        }

        val cardType = detectCardType(allTexts, pkg)
        L.d(TAG, "CardType detectado: $cardType")

        val valueNode = findValueNode(allTexts)
        val pickupNode = findPickupNode(allTexts)
        val tripNode = findTripNode(allTexts, cardType)
        val ratingNode = findRatingNode(allTexts)
        val serviceNode = findServiceNode(allTexts)
        val bonusNodes = findBonusNodes(allTexts)
        val acceptNode = findAcceptNode(allTexts)

        return RawCardData(
            cardType = cardType,
            valueNode = valueNode,
            pickupNode = pickupNode,
            tripNode = tripNode,
            ratingNode = ratingNode,
            serviceNode = serviceNode,
            bonusNodes = bonusNodes,
            acceptNode = acceptNode,
            rawTexts = allTexts
        )
    }

    private fun tryFallbackByText(root: AccessibilityNodeInfo, texts: MutableList<String>) {
        L.d(TAG, "Nenhum texto via recursão — tentando findAccessibilityNodeInfosByText")
        try {
            val nodesWithR = root.findAccessibilityNodeInfosByText("R")
            L.d(TAG, "findAccessibilityNodeInfosByText(\"R\") retornou ${nodesWithR?.size} nós")
            nodesWithR?.forEach { node ->
                if (node.text?.toString()?.isNotBlank() == true) {
                    texts.add(node.text.toString())
                }
                if (node.contentDescription?.toString()?.isNotBlank() == true) {
                    texts.add(node.contentDescription.toString())
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    node.recycle()
                }
            }
            L.d(TAG, "Fallback coletou ${texts.size} textos adicionais")
        } catch (e: Exception) {
            L.e(TAG, "Fallback findAccessibilityNodeInfosByText falhou", e)
        }
    }

    private fun tryAdvancedAccessibility(root: AccessibilityNodeInfo, texts: MutableList<String>) {
        L.d(TAG, "Tentando técnicas avançadas de acessibilidade para WebView...")

        // 1. Refresh root e re-coleta
        root.refresh()
        collectTexts(root, texts)
        if (texts.isNotEmpty()) {
            L.d(TAG, "Após refresh root: ${texts.size} textos")
            return
        }

        // 2. Tenta refresh em cada nó durante a travessia
        collectTextsRefresh(root, texts)
        if (texts.isNotEmpty()) {
            L.d(TAG, "Após refresh por nó: ${texts.size} textos")
            return
        }

        // 3. Busca nó WebView explicitamente e tenta extrair conteúdo
        val wv = findWebViewNode(root)
        if (wv != null) {
            L.d(TAG, "Nó WebView encontrado — tentando extrair conteúdo...")
            collectTexts(wv, texts)
            if (texts.isEmpty()) {
                collectTextsRefresh(wv, texts)
            }
            if (texts.isEmpty()) {
                collectExtrasFromTree(wv, texts)
            }
            if (texts.isNotEmpty()) {
                L.d(TAG, "WebView rendeu ${texts.size} textos")
                return
            }
        }

        // 4. Tenta coletar extras de toda a árvore
        collectExtrasFromTree(root, texts)
        if (texts.isNotEmpty()) {
            L.d(TAG, "Extras renderam ${texts.size} textos")
        }
    }

    private fun findWebViewNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val cn = node.className?.toString() ?: ""
        if (cn.contains("WebView", ignoreCase = true) ||
            cn.contains("chromium", ignoreCase = true) ||
            cn.contains("WebContents", ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findWebViewNode(child)
            if (result != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
                return result
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
        return null
    }

    private fun collectTextsRefresh(node: AccessibilityNodeInfo, list: MutableList<String>) {
        node.refresh()
        if (node.text?.toString()?.isNotBlank() == true) {
            list.add(node.text.toString())
        } else if (node.contentDescription?.toString()?.isNotBlank() == true) {
            list.add(node.contentDescription.toString())
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectTextsRefresh(child, list)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
        }
    }

    private fun collectExtrasFromTree(node: AccessibilityNodeInfo, list: MutableList<String>) {
        try {
            val extras = node.extras
            if (extras != null && !extras.isEmpty()) {
                for (key in extras.keySet()) {
                    val value = extras.get(key)?.toString()
                    if (value != null && value.isNotBlank() && value.length > 2) {
                        list.add(value)
                    }
                }
            }
        } catch (_: Exception) {}
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectExtrasFromTree(child, list)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
        }
    }

    private fun detectCardType(texts: List<String>, pkg: String): CardType {
        val full = texts.joinToString(" ").lowercase(Locale.ROOT)

        // EXCLUSIVO: tem "exclusivo" no texto E botão "aceitar"
        // RADAR: tem "selecionar" como botão (broadcast para múltiplos motoristas)
        val hasExclusiveText = full.contains("exclusivo")
        val hasAceitar = full.contains("aceitar") || full.contains("accept")
        val hasSelecionar = full.contains("selecionar")

        if (hasExclusiveText && hasAceitar) {
            return CardType.EXCLUSIVE
        }

        if (hasSelecionar && full.contains("r$")) {
            return CardType.RADAR
        }

        if (hasAceitar && full.contains("r$")) {
            return CardType.RADAR
        }

        return CardType.UNKNOWN
    }

    private fun findValueNode(texts: List<String>): String? {
        for (text in texts) {
            val match = VALUE_PATTERN.find(text)
            if (match != null) {
                val v = parseBr(match.groupValues[1])
                if (v != null && v > 1.0) return text
            }
        }
        return null
    }

    private fun findPickupNode(texts: List<String>): String? {
        for (text in texts) {
            if (PICKUP_PATTERN.containsMatchIn(text)) {
                val match = PICKUP_PATTERN.find(text)
                if (match != null) {
                    val km = parseBr(match.groupValues[2])
                    val min = match.groupValues[1].toIntOrNull()
                    if (km != null && km > 0 && km < 50 && min != null && min > 0) return text
                }
            }
        }
        for (text in texts) {
            if (PICKUP_PATTERN.containsMatchIn(text)) return text
        }
        return null
    }

    private fun findTripNode(texts: List<String>, cardType: CardType): String? {
        return when (cardType) {
            CardType.RADAR,
            CardType.EXCLUSIVE,
            CardType.UNKNOWN,
            CardType.APP_99,
            CardType.APP_99_NEGOTIATE,
            CardType.APP_99_PRIORITY -> {
                texts.firstOrNull { VIAGEM_PATTERN.containsMatchIn(it) }
            }
        }
    }

    private fun findRatingNode(texts: List<String>): String? {
        for (text in texts) {
            for (regex in listOf(RATING_STAR_REGEX, RATING_COUNT_REGEX, RATING_BULLET_REGEX)) {
                val m = regex.find(text)
                if (m != null) {
                    val v = parseBr(m.groupValues[1])
                    if (v != null && v in 1.0..5.1) return text
                }
            }
        }
        for (text in texts) {
            val m = RATING_DECIMAL_REGEX.find(text)
            if (m != null) {
                val v = parseBr(m.groupValues[1])
                if (v != null && v in 4.0..5.1) return text
            }
        }
        return null
    }

    private fun findServiceNode(texts: List<String>): String? {
        for (text in texts) {
            val lower = text.lowercase(Locale.ROOT)
            for (entry in SERVICE_TYPE_LIST) {
                val regex = Regex("\\b${Regex.escape(entry.first)}\\b")
                if (regex.containsMatchIn(lower)) return text
            }
        }
        return null
    }

    private fun findBonusNodes(texts: List<String>): List<String> {
        val bonuses = mutableListOf<String>()
        for (text in texts) {
            if (PRIORITY_BONUS_PATTERN.containsMatchIn(text) || DYNAMIC_BONUS_PATTERN.containsMatchIn(text)) {
                bonuses.add(text)
            }
        }
        return bonuses
    }

    private fun findAcceptNode(texts: List<String>): String? {
        for (text in texts) {
            val lower = text.lowercase(Locale.ROOT)
            if (lower.contains("aceitar") || lower.contains("accept") || lower.contains("selecionar")) {
                return text
            }
        }
        return null
    }

    private fun collectTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        if (node.text?.toString()?.isNotBlank() == true) {
            list.add(node.text.toString())
        } else if (node.contentDescription?.toString()?.isNotBlank() == true) {
            list.add(node.contentDescription.toString())
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

    fun parseBr(raw: String): Double? {
        return raw.replace(",", ".").toDoubleOrNull()
    }

    private val VALUE_PATTERN = Regex("""R\$\s*(\d+(?:[.,]\d+)?)""")
    private val PICKUP_PATTERN = Regex(
        """(\d+)\s*min(?:uto)?s?\s*\(\s*(\d+[.,]\d+)\s*km\s*\)\s*de\s*dist[âa]ncia""",
        RegexOption.IGNORE_CASE
    )
    private val VIAGEM_PATTERN = Regex(
        """[Vv]iagem\s+de\s+(?:(\d+)\s*[Hh](?:ora(?:s)?)?\s*e\s*)?(\d+)\s*[Mm]in(?:uto)?s?\s*\((\d+[.,]\d+)\s*km\)""",
        RegexOption.IGNORE_CASE
    )
    private val RATING_STAR_REGEX = Regex("""(\d[.,]\d{1,2})\s*[★⭐*]""")
    private val RATING_COUNT_REGEX = Regex("""(\d[.,]\d{1,2})\s*\(\d+\)""")
    private val RATING_BULLET_REGEX = Regex("""(\d[.,]\d{1,2})\s*[·•]""")
    private val RATING_DECIMAL_REGEX = Regex("""(\d[.,]\d{1,2})""")
    private val PRIORITY_BONUS_PATTERN = Regex(
        """\+R\$\s*(\d+(?:[.,]\d+)?)\s*inclu[íi]do\s+para\s+prioridade""",
        RegexOption.IGNORE_CASE
    )
    private val DYNAMIC_BONUS_PATTERN = Regex(
        """\+R\$\s*(\d+(?:[.,]\d+)?)\s*inclu[íi]do(?!\s+para\s+prioridade)""",
        RegexOption.IGNORE_CASE
    )

    fun extractWithOCR(context: Context, bitmap: Bitmap): RawCardData? {
        try {
            L.d(TAG, "🔍 [UberOCR] UberCardExtractor.extractWithOCR() INICIADO")
            if (!ensureTessData(context)) return null

            val fractions = OCREnhancer.getCropFractions(bitmap.height)
            L.d(TAG, "📐 [UberOCR] Altura=${bitmap.height}, tentando crops: $fractions")

            val api = TessBaseAPI()
            val initOk = api.init(context.cacheDir.absolutePath, "por")
            if (!initOk) {
                L.e(TAG, "❌ [UberOCR] TessBaseAPI.init() falhou!")
                api.end()
                return null
            }
            OCREnhancer.configureTesseract(api)

            var bestLines: List<String>? = null
            var bestText = ""
            var bestFraction = 0f

            var validCropCount = 0

            for (fraction in fractions) {
                val cropped = OCREnhancer.cropToCardRegion(bitmap, fraction)
                val enhanced = OCREnhancer.preprocessForOCR(cropped)
                if (cropped !== bitmap) cropped.recycle()

                val swBitmap = enhanced.copy(Bitmap.Config.ARGB_8888, false)
                api.setImage(swBitmap)
                val text = api.utF8Text
                swBitmap.recycle()
                if (enhanced !== cropped) enhanced.recycle()

                if (text.isBlank()) {
                    L.d(TAG, "📐 [UberOCR] Crop ${fraction}x: blank")
                    continue
                }

                val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                val filtered = OCREnhancer.filterCardLines(lines)

                if (bestLines == null) {
                    bestLines = filtered
                    bestText = text
                    bestFraction = fraction
                }

                if (OCREnhancer.hasCardData(filtered)) {
                    L.d(TAG, "✅ [UberOCR] Crop ${fraction}x: ${lines.size}→${filtered.size} linhas, dados OK")
                    validCropCount++
                    if (filtered.size > (bestLines?.size ?: 0)) {
                        bestLines = filtered
                        bestText = text
                        bestFraction = fraction
                    }
                } else {
                    L.d(TAG, "📐 [UberOCR] Crop ${fraction}x: ${lines.size}→${filtered.size} linhas, dados insuficientes")
                }
            }

            api.end()

            if (bestLines == null || bestLines.isEmpty()) {
                L.w(TAG, "❌ [UberOCR] Nenhum crop encontrou dados do card")
                OCREnhancer.saveDiagnosticData(context, "Uber", bitmap, bestText, emptyList(), "no card data found")
                return null
            }

            if (!OCREnhancer.hasCardData(bestLines)) {
                L.w(TAG, "⚠️ [UberOCR] Crop ${bestFraction}x: apenas ${bestLines.size} linhas, dados podem estar incompletos")
                OCREnhancer.saveDiagnosticData(context, "Uber", bitmap, bestText, bestLines, "insufficient card data")
            } else {
                L.d(TAG, "✅ [UberOCR] Melhor crop ${bestFraction}x: ${bestLines.size} linhas")
            }

            val fixed = OCREnhancer.fixOCRDistances(bestLines.toMutableList())
            L.d(TAG, "📝 [UberOCR] ${fixed.size} linhas após correção de distâncias")

            L.d(TAG, "📊 [UberOCR] $validCropCount/${fractions.size} crops com dados válidos")
            return createFromTexts(fixed, validCropCount)
        } catch (e: Exception) {
            L.e(TAG, "❌ [UberOCR] extractWithOCR crashou: ${e.message}", e)
            return null
        }
    }

    fun createFromTexts(texts: List<String>, validCropCount: Int = 0): RawCardData {
        val cardType = detectCardType(texts, "ubercab")
        return RawCardData(
            cardType = cardType,
            valueNode = findValueNode(texts),
            pickupNode = findPickupNode(texts),
            tripNode = findTripNode(texts, cardType),
            ratingNode = findRatingNode(texts),
            serviceNode = findServiceNode(texts),
            bonusNodes = findBonusNodes(texts),
            acceptNode = findAcceptNode(texts),
            rawTexts = texts,
            validCropCount = validCropCount
        )
    }

    private fun ensureTessData(context: Context): Boolean {
        try {
            val tessDir = File(context.cacheDir, "tessdata")
            val traineddata = File(tessDir, "por.traineddata")
            if (traineddata.exists()) {
                L.d(TAG, "📁 [UberOCR] por.traineddata existe: ${traineddata.length()} bytes")
                return true
            }
            L.d(TAG, "📁 [UberOCR] por.traineddata não encontrado — copiando de assets...")
            tessDir.mkdirs()
            context.assets.open("tessdata/por.traineddata").use { input ->
                FileOutputStream(traineddata).use { output ->
                    input.copyTo(output)
                }
            }
            L.d(TAG, "✅ [UberOCR] por.traineddata copiado de assets (${traineddata.length()} bytes)")
            return true
        } catch (e: Exception) {
            L.e(TAG, "❌ [UberOCR] Falha ao extrair traineddata: ${e.message}")
            return false
        }
    }

    private val SERVICE_TYPE_LIST = listOf(
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
    )
}
