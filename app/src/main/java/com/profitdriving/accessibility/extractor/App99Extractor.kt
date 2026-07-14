package com.profitdriving.accessibility.extractor

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.profitdriving.L
import java.util.Locale
import java.util.concurrent.TimeUnit

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

    fun extractWithMLKit(context: Context, bitmap: Bitmap, windowBounds: android.graphics.Rect? = null): RawCardData? {
        try {
            L.d(TAG, "🔍 [99MLKit] App99Extractor.extractWithMLKit() INICIADO")

            val inputBitmap = if (windowBounds != null && !windowBounds.isEmpty()) {
                cropToBounds(bitmap, windowBounds)
            } else {
                bitmap
            }
            val ownBitmap = inputBitmap !== bitmap

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(inputBitmap, 0)
            val result = Tasks.await(recognizer.process(image), 15, TimeUnit.SECONDS)
            val rawText = result.text
            recognizer.close()

            if (ownBitmap) inputBitmap.recycle()

            if (rawText.isBlank()) {
                L.w(TAG, "❌ [99MLKit] ML Kit retornou texto vazio")
                return null
            }

            L.d(TAG, "✅ [99MLKit] Texto extraído (${rawText.length} chars)")

            val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val filtered = OCREnhancer.filterCardLines(lines)

            if (filtered.isEmpty()) {
                L.w(TAG, "❌ [99MLKit] Nenhuma linha relevante após filtro")
                return null
            }

            val hasData = OCREnhancer.hasCardData(filtered)
            if (!hasData) {
                L.w(TAG, "⚠️ [99MLKit] Dados insuficientes no card")
            }

            val validCropCount = if (hasData) 2 else 1
            return createFromTexts(filtered, validCropCount)
        } catch (e: Exception) {
            L.e(TAG, "❌ [99MLKit] extractWithMLKit crashou: ${e.message}", e)
            return null
        }
    }

    fun createFromTexts(texts: List<String>, validCropCount: Int = 0): RawCardData {
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
            rawTexts = texts,
            validCropCount = validCropCount
        )
    }

    private fun cropToBounds(bitmap: Bitmap, bounds: android.graphics.Rect): Bitmap {
        val x = bounds.left.coerceAtLeast(0)
        val y = bounds.top.coerceAtLeast(0)
        val w = bounds.width().coerceAtMost(bitmap.width - x)
        val h = bounds.height().coerceAtMost(bitmap.height - y)
        if (w <= 0 || h <= 0) return bitmap
        return Bitmap.createBitmap(bitmap, x, y, w, h)
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
