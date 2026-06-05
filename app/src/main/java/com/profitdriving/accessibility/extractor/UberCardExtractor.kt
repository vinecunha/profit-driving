package com.profitdriving.accessibility.extractor

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.profitdriving.L
import java.util.Locale

object UberCardExtractor {

    private const val TAG = "UberCardExtractor"

    fun extract(root: AccessibilityNodeInfo, pkg: String): RawCardData {
        val allTexts = mutableListOf<String>()
        collectTexts(root, allTexts)
        L.d(TAG, "Textos coletados (${allTexts.size}): ${allTexts.take(10)}")

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

    private fun detectCardType(texts: List<String>, pkg: String): CardType {
        if (pkg.contains("app99") || pkg.contains("taxis99")) {
            return CardType.APP_99
        }

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
            CardType.RADAR -> {
                texts.firstOrNull { VIAGEM_PATTERN.containsMatchIn(it) }
            }
            CardType.EXCLUSIVE, CardType.UNKNOWN -> {
                texts.firstOrNull { VIAGEM_PATTERN.containsMatchIn(it) }
                    ?: texts.firstOrNull { VIAGEM_EXCLUSIVO_PATTERN.containsMatchIn(it) }
            }
            CardType.APP_99 -> {
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
    private val VIAGEM_EXCLUSIVO_PATTERN = Regex(
        """(\d+)\s*(?:[Mm]in(?:uto)?s?)\s*\((\d+[.,]\d+)\s*km\)(?!\s*de\s*dist[âa]ncia)"""
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
