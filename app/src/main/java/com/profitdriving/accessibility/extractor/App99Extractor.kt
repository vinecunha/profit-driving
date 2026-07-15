package com.profitdriving.accessibility.extractor

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.profitdriving.L
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Extrator de cards do app 99.
 *
 * O app 99 bloqueia a árvore de acessibilidade em todas as versões do Android.
 * O único caminho de extração é via ML Kit Text Recognition sobre screenshot.
 * Não há extração via acessibilidade — o método extract() foi removido intencionalmente.
 */
object App99Extractor {

    private const val TAG = "App99Extractor"

    fun extractWithMLKit(bitmap: Bitmap, windowBounds: Rect? = null): RawCardData? {
        return try {
            L.d(TAG, "🔍 [MLKit] extractWithMLKit() INICIADO — bounds=$windowBounds")

            val cardBitmap = if (windowBounds != null && !windowBounds.isEmpty) {
                cropToBounds(bitmap, windowBounds)
            } else {
                val y = (bitmap.height * 0.45f).toInt()
                Bitmap.createBitmap(bitmap, 0, y, bitmap.width, bitmap.height - y)
            }

            val image = InputImage.fromBitmap(cardBitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = Tasks.await(recognizer.process(image), 15, TimeUnit.SECONDS)
            val rawText = result.text
            recognizer.close()

            if (cardBitmap !== bitmap) cardBitmap.recycle()

            if (rawText.isBlank()) {
                L.w(TAG, "❌ [MLKit] ML Kit retornou texto vazio")
                return null
            }

            val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            L.d(TAG, "📝 [MLKit] ${lines.size} linhas reconhecidas: ${lines.take(8)}")

            val filtered = OCREnhancer.filterCardLines(lines)
            L.d(TAG, "📝 [MLKit] ${filtered.size} linhas após filtro")

            if (!OCREnhancer.hasCardData(filtered)) {
                L.w(TAG, "⚠️ [MLKit] Dados insuficientes para card 99")
                return null
            }

            L.d(TAG, "✅ [MLKit] Card 99 reconhecido — ${filtered.size} linhas")
            createFromTexts(filtered)
        } catch (e: Exception) {
            L.e(TAG, "❌ [MLKit] extractWithMLKit crashou: ${e.message}", e)
            null
        }
    }

    fun createFromTexts(texts: List<String>): RawCardData {
        val cardType = detectCardType(texts)
        return RawCardData(
            cardType = cardType,
            valueNode = findValueNode(texts),
            pickupNode = findPickupNode(texts),
            tripNode = findTripNode(texts),
            ratingNode = findRatingNode(texts),
            serviceNode = findServiceNode(texts),
            bonusNodes = findBonusNodes(texts),
            acceptNode = findAcceptNode(texts),
            rawTexts = texts
        )
    }

    private fun cropToBounds(bitmap: Bitmap, bounds: Rect): Bitmap {
        val left   = bounds.left.coerceIn(0, bitmap.width - 1)
        val top    = bounds.top.coerceIn(0, bitmap.height - 1)
        val right  = bounds.right.coerceIn(left + 1, bitmap.width)
        val bottom = bounds.bottom.coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun detectCardType(texts: List<String>): CardType {
        val full = texts.joinToString(" ").lowercase(Locale.ROOT)
        return when {
            full.contains("negocia") || full.contains("aceitar por r$") -> CardType.APP_99_NEGOTIATE
            full.contains("prioritário") || full.contains("prioritario") -> CardType.APP_99_PRIORITY
            else -> CardType.APP_99
        }
    }

    private fun findValueNode(texts: List<String>): String? =
        texts.firstOrNull { text ->
            VALUE_PATTERN.find(text)?.let { parseBr(it.groupValues[1])?.let { v -> v > 1.0 } } == true
        }

    private fun findPickupNode(texts: List<String>): String? {
        for (text in texts) {
            if (!PICKUP_PATTERN.containsMatchIn(text)) continue
            val match = PICKUP_PATTERN.find(text) ?: continue
            val km = parseBr(match.groupValues[2]) ?: continue
            val min = match.groupValues[1].toIntOrNull() ?: continue
            if (km > 0 && km < 50 && min > 0) return text
        }
        return texts.firstOrNull { PICKUP_PATTERN.containsMatchIn(it) }
    }

    private fun findTripNode(texts: List<String>): String? =
        texts.firstOrNull { VIAGEM_PATTERN.containsMatchIn(it) }

    private fun findRatingNode(texts: List<String>): String? {
        for (text in texts) {
            for (regex in listOf(RATING_STAR_REGEX, RATING_COUNT_REGEX, RATING_BULLET_REGEX)) {
                val v = regex.find(text)?.let { parseBr(it.groupValues[1]) }
                if (v != null && v in 1.0..5.1) return text
            }
        }
        return texts.firstOrNull {
            val v = RATING_DECIMAL_REGEX.find(it)?.let { m -> parseBr(m.groupValues[1]) }
            v != null && v in 4.0..5.1
        }
    }

    private fun findServiceNode(texts: List<String>): String? {
        for (text in texts) {
            val lower = text.lowercase(Locale.ROOT)
            for ((keyword, _) in SERVICE_TYPE_LIST) {
                if (Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(lower)) return text
            }
        }
        return null
    }

    private fun findBonusNodes(texts: List<String>): List<String> =
        texts.filter {
            PRIORITY_BONUS_PATTERN.containsMatchIn(it) || DYNAMIC_BONUS_PATTERN.containsMatchIn(it)
        }

    private fun findAcceptNode(texts: List<String>): String? =
        texts.firstOrNull {
            val lower = it.lowercase(Locale.ROOT)
            lower.contains("aceitar") || lower.contains("selecionar")
        }

    fun parseBr(raw: String): Double? = raw.replace(",", ".").toDoubleOrNull()

    private val VALUE_PATTERN        = Regex("""R\$\s*(\d+(?:[.,]\d+)?)""")
    private val PICKUP_PATTERN       = Regex("""(\d+)\s*min(?:uto)?s?\s*\(\s*(\d+[.,]\d+)\s*km\s*\)\s*de\s*dist[âa]ncia""", RegexOption.IGNORE_CASE)
    private val VIAGEM_PATTERN       = Regex("""[Vv]iagem\s+de\s+(?:(\d+)\s*[Hh](?:ora(?:s)?)?\s*e\s*)?(\d+)\s*[Mm]in(?:uto)?s?\s*\((\d+[.,]\d+)\s*km\)""", RegexOption.IGNORE_CASE)
    private val RATING_STAR_REGEX    = Regex("""(\d[.,]\d{1,2})\s*[★⭐*]""")
    private val RATING_COUNT_REGEX   = Regex("""(\d[.,]\d{1,2})\s*\(\d+\)""")
    private val RATING_BULLET_REGEX  = Regex("""(\d[.,]\d{1,2})\s*[·•]""")
    private val RATING_DECIMAL_REGEX = Regex("""(\d[.,]\d{1,2})""")
    private val PRIORITY_BONUS_PATTERN = Regex("""\+R\$\s*(\d+(?:[.,]\d+)?)\s*inclu[íi]do\s+para\s+(?:(?:embarque\s+)?priorit[áa]rio|prioridade|embarque)""", RegexOption.IGNORE_CASE)
    private val DYNAMIC_BONUS_PATTERN  = Regex("""\+R\$\s*(\d+(?:[.,]\d+)?)\s*inclu[íi]do(?!\s+para\s+(?:(?:embarque\s+)?priorit[áa]rio|prioridade|embarque))""", RegexOption.IGNORE_CASE)
    private val SERVICE_TYPE_LIST = listOf(
        "99pop" to "99Pop", "99top" to "99Top", "99black" to "99Black",
        "99moto" to "99Moto", "99flash" to "99Flash", "99entrega" to "Entrega",
        "pop" to "Pop", "top" to "Top", "entrega" to "Entrega", "negocia" to "Negocia"
    )
}
