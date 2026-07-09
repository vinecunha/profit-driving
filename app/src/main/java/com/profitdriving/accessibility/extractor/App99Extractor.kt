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

object App99Extractor {

    private const val TAG = "App99Extractor"

    fun extract(root: AccessibilityNodeInfo, pkg: String): RawCardData? {
        if (!pkg.contains("taxis99") && !pkg.contains("app99")) {
            L.d(TAG, "Pacote não é 99 ($pkg), abortando")
            return null
        }

        try {
            logTree(root, 0)

            val allTexts = mutableListOf<String>()
            collectTexts(root, allTexts)
            L.d(TAG, "Textos coletados via recursão (${allTexts.size}): ${allTexts.take(10)}")

            L.d(TAG, "Buscando por palavras-chave via findAccessibilityNodeInfosByText...")
            for (term in SEARCH_TERMS) {
                val found = searchByText(root, term)
                if (found.isNotEmpty()) {
                    L.d(TAG, "  '$term' → ${found.size} textos: ${found.take(3)}")
                    allTexts.addAll(found)
                }
            }

            val unique = allTexts.distinct()
            allTexts.clear()
            allTexts.addAll(unique)
            L.d(TAG, "Textos totais após fallback (${allTexts.size}): ${allTexts.take(10)}")

            if (allTexts.isEmpty()) {
                L.d(TAG, "Nenhum texto encontrado para 99 — abortando")
                return null
            }

            val cardType = detectCardType(allTexts)

            return RawCardData(
                cardType = cardType,
                valueNode = findValueNode(allTexts),
                pickupNode = findPickupNode(allTexts),
                tripNode = findTripNode(allTexts, cardType),
                ratingNode = findRatingNode(allTexts),
                serviceNode = findServiceNode(allTexts),
                bonusNodes = findBonusNodes(allTexts),
                acceptNode = findAcceptNode(allTexts),
                rawTexts = allTexts
            )
        } catch (e: Exception) {
            L.e(TAG, "extract() crashou: ${e.message}", e)
            return null
        }
    }

    fun extractWithOCR(context: Context, bitmap: Bitmap): RawCardData? {
        try {
            L.d(TAG, "🔍 [OCR] App99Extractor.extractWithOCR() INICIADO")

            val tessDir = File(context.cacheDir, "tessdata")
            val traineddata = File(tessDir, "por.traineddata")
            L.d(TAG, "📁 [OCR] TessData path: ${tessDir.absolutePath}")

            if (!traineddata.exists()) {
                L.d(TAG, "📁 [OCR] por.traineddata não encontrado — copiando de assets...")
                try {
                    tessDir.mkdirs()
                    context.assets.open("tessdata/por.traineddata").use { input ->
                        FileOutputStream(traineddata).use { output ->
                            input.copyTo(output)
                        }
                    }
                    L.d(TAG, "✅ [OCR] por.traineddata copiado de assets (${traineddata.length()} bytes)")
                } catch (e: Exception) {
                    L.e(TAG, "❌ [OCR] Falha ao copiar traineddata de assets: ${e.message}")
                    L.d(TAG, "📥 Baixe de: https://github.com/tesseract-ocr/tessdata/raw/main/por.traineddata")
                    L.d(TAG, "📥 E coloque em: ${traineddata.absolutePath}")
                    return null
                }
            } else {
                L.d(TAG, "📁 [OCR] por.traineddata existe: ${traineddata.length()} bytes")
            }

            val fractions = OCREnhancer.getCropFractions(bitmap.height)
            L.d(TAG, "📐 [OCR] Altura=${bitmap.height}, tentando crops: $fractions")

            val api = TessBaseAPI()
            L.d(TAG, "📁 [OCR] TessBaseAPI instanciada, chamando init(${context.cacheDir.absolutePath}, por)...")
            val initOk = api.init(context.cacheDir.absolutePath, "por")
            L.d(TAG, "📁 [OCR] Tess init() retornou: $initOk")

            if (!initOk) {
                L.e(TAG, "❌ [OCR] TessBaseAPI.init() falhou!")
                api.end()
                return null
            }

            OCREnhancer.configureTesseract(api)

            var bestLines: List<String>? = null
            var bestText = ""
            var bestFraction = 0f

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
                    L.d(TAG, "📐 [OCR] Crop ${fraction}x: blank, tentando próximo")
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
                    L.d(TAG, "✅ [OCR] Crop ${fraction}x: ${lines.size}→${filtered.size} linhas, dados OK")
                    bestLines = filtered
                    bestText = text
                    bestFraction = fraction
                    break
                }
                L.d(TAG, "📐 [OCR] Crop ${fraction}x: ${lines.size}→${filtered.size} linhas, dados insuficientes")
            }

            api.end()

            if (bestLines == null || bestLines.isEmpty()) {
                L.w(TAG, "❌ [OCR] Nenhum crop encontrou dados do card")
                OCREnhancer.saveDiagnosticData(context, "App99", bitmap, bestText, emptyList(), "no card data found")
                return null
            }

            if (!OCREnhancer.hasCardData(bestLines)) {
                L.w(TAG, "⚠️ [OCR] Crop ${bestFraction}x: apenas ${bestLines.size} linhas, dados podem estar incompletos")
                OCREnhancer.saveDiagnosticData(context, "App99", bitmap, bestText, bestLines, "insufficient card data")
            } else {
                L.d(TAG, "✅ [OCR] Melhor crop ${bestFraction}x: ${bestLines.size} linhas")
            }

            val fixed = OCREnhancer.fixOCRDistances(bestLines.toMutableList())
            L.d(TAG, "📝 [OCR] ${fixed.size} linhas após correção de distâncias")

            return createFromTexts(fixed)
        } catch (e: Exception) {
            L.e(TAG, "❌ [OCR] extractWithOCR crashou: ${e.message}", e)
            return null
        }
    }

    fun createFromTexts(texts: List<String>): RawCardData {
        val cardType = detectCardType(texts)
        return RawCardData(
            cardType = cardType,
            valueNode = findValueNode(texts),
            pickupNode = findPickupNode(texts),
            tripNode = findTripNode(texts, cardType),
            ratingNode = findRatingNode(texts),
            serviceNode = findServiceNode(texts),
            bonusNodes = findBonusNodes(texts),
            acceptNode = findAcceptNode(texts),
            rawTexts = texts
        )
    }

    private fun logTree(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 5) return
        try {
            val indent = "  ".repeat(depth)
            val cls = node.className ?: "?"
            val txt = node.text?.toString()?.take(30) ?: ""
            val desc = node.contentDescription?.toString()?.take(30) ?: ""
            val viewId = node.viewIdResourceName ?: ""
            L.d(TAG, "${indent}node cls=$cls childCount=${node.childCount}" +
                    " text='$txt' desc='$desc' viewId='$viewId'")
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    logTree(child, depth + 1)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        @Suppress("DEPRECATION")
                        child.recycle()
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private fun searchByText(root: AccessibilityNodeInfo, query: String): List<String> {
        val results = mutableListOf<String>()
        try {
            val nodes = root.findAccessibilityNodeInfosByText(query)
            if (nodes != null && !nodes.isEmpty()) {
                L.d(TAG, "findAccessibilityNodeInfosByText(\"$query\") → ${nodes.size} nós")
                for (node in nodes) {
                    if (node.text?.toString()?.isNotBlank() == true) {
                        results.add(node.text.toString())
                    } else if (node.contentDescription?.toString()?.isNotBlank() == true) {
                        results.add(node.contentDescription.toString())
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        @Suppress("DEPRECATION")
                        node.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            L.d(TAG, "findAccessibilityNodeInfosByText(\"$query\") falhou: ${e.message}")
        }
        return results
    }

    private fun detectCardType(texts: List<String>): CardType {
        val full = texts.joinToString(" ").lowercase(Locale.ROOT)

        if (full.contains("negocia") || full.contains("aceitar por r$")) {
            return CardType.APP_99_NEGOTIATE
        }
        if (full.contains("prioritário") || full.contains("prioritario")) {
            return CardType.APP_99_PRIORITY
        }
        return CardType.APP_99
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
        return texts.firstOrNull { VIAGEM_PATTERN.containsMatchIn(it) }
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

    private val SERVICE_TYPE_LIST = listOf(
        "99pop" to "99Pop",
        "99top" to "99Top",
        "99black" to "99Black",
        "99moto" to "99Moto",
        "99flash" to "99Flash",
        "99entrega" to "Entrega",
        "pop" to "Pop",
        "top" to "Top",
        "entrega" to "Entrega",
        "negocia" to "Negocia"
    )

    private val SEARCH_TERMS = listOf("R$", "r$", "min", "km", "Negocia", "Aceitar", "Escolher")
}
