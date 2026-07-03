package com.profitdriving.parser

import com.profitdriving.L
import com.profitdriving.RideData
import com.profitdriving.accessibility.extractor.CardType
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.accessibility.extractor.UberCardExtractor
import java.util.Locale

class App99CardParser : RideDataParser {

    override fun canParse(raw: RawCardData): Boolean {
        if (raw.cardType == CardType.APP_99) return true
        val full = raw.rawTexts.joinToString(" ").lowercase(Locale.ROOT)
        val hasMoney = full.contains("r$")
        val hasKm = full.contains("km")
        val hasMin = full.contains("min")
        val hasAccept = full.contains("aceitar") || full.contains("accept")
        val has99 = full.contains("99pop") || full.contains("99top") || full.contains("99black") ||
                full.contains("99moto") || full.contains("99flash") || full.contains("99entrega")
        return (hasAccept || has99) && hasMoney && (hasKm || hasMin)
    }

    override fun parse(raw: RawCardData): RideData? {
        L.d(TAG, "App99CardParser.parse() iniciado")
        val text = raw.fullText

        val value = extractValue(text)
        if (value == null || value <= 0) {
            L.d(TAG, "Valor inválido — App99CardParser abortando")
            return null
        }

        val pricePerKm = extractPricePerKm(text)
        val pricePerHour = extractPricePerHour(text)
        val distance = extractDistance(text)
        val timeMin = extractTime(text)
        val rating = extractRating(text)
        val serviceType = extractServiceType(text)
        val priorityBonus = extractPriorityBonus(text)
        val dynamicBonus = extractDynamicBonus(text)
        val hasMultipleStops = extractStops(text)
        val (pickupAddress, dropoffAddress) = extractAddresses(text)

        L.d(TAG, "App99CardParser parsed: value=$value km=$distance tempo=$timeMin nota=$rating")

        return RideData(
            value = value,
            distanceKm = distance,
            timeMin = timeMin,
            rating = rating,
            appName = "99",
            pricePerKm = pricePerKm ?: distance?.let { d ->
                if (d > 0) value / d else null
            },
            pricePerHour = pricePerHour ?: timeMin?.let { t ->
                if (t > 0) value / (t / 60.0) else null
            },
            detectedBy = "accessibility_99",
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
            .mapNotNull { UberCardExtractor.parseBr(it.groupValues[1]) }
            .filter { it > 1.0 }
            .toList()
        if (matches.isEmpty()) return null
        return matches.max()
    }

    private fun extractPricePerKm(text: String): Double? {
        val m = KM_PER_REAL_REGEX.find(text)
        if (m != null) return UberCardExtractor.parseBr(m.groupValues[1])
        val m2 = KM_REAL_REGEX.find(text)
        return m2?.let { UberCardExtractor.parseBr(it.groupValues[1]) }
    }

    private fun extractPricePerHour(text: String): Double? {
        return HOUR_REAL_REGEX.find(text)?.let {
            UberCardExtractor.parseBr(it.groupValues[1])
        }
    }

    private fun extractDistance(text: String): Double? {
        for (pat in DISTANCE_PATTERNS) {
            val m = pat.find(text)
            if (m != null) {
                val v = UberCardExtractor.parseBr(m.groupValues[1])
                if (v != null && v > 0 && v < 200) return v
            }
        }
        return null
    }

    private fun extractTime(text: String): Int? {
        for (pat in TIME_PATTERNS) {
            val m = pat.find(text)
            if (m != null) {
                val v = m.groupValues[1].toIntOrNull()
                if (v != null && v > 0) return v
            }
        }
        return null
    }

    private fun extractRating(text: String): Double? {
        for (regex in listOf(RATING_STAR_REGEX, RATING_COUNT_REGEX, RATING_BULLET_REGEX)) {
            val m = regex.find(text)
            if (m != null) {
                val v = UberCardExtractor.parseBr(m.groupValues[1])
                if (v != null && v in 1.0..5.1) return v
            }
        }
        return RATING_DECIMAL_REGEX.findAll(text).mapNotNull {
            UberCardExtractor.parseBr(it.groupValues[1])
        }.firstOrNull { it in 4.0..5.1 }
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
        val v = UberCardExtractor.parseBr(m.groupValues[1])
        return if (v != null && v > 0) v else null
    }

    private fun extractDynamicBonus(text: String): Double? {
        val m = DYNAMIC_BONUS_REGEX.find(text) ?: return null
        val v = UberCardExtractor.parseBr(m.groupValues[1])
        return if (v != null && v > 0) v else null
    }

    private fun extractStops(text: String): Boolean {
        val lowerText = text.lowercase(Locale.ROOT)
        return lowerText.contains("várias paradas") || lowerText.contains("paradas")
    }

    private fun extractAddresses(text: String): Pair<String?, String?> {
        var pickupAddress: String? = null
        var dropoffAddress: String? = null

        // 1. Tentar novo formato (sem CEP/UF) — tempo/dist sem "de distância" ou "Viagem de"
        val newMatches = ADDRESS_TIME_DIST_NEW.findAll(text).toList()
        if (newMatches.isNotEmpty()) {
            pickupAddress = cleanupAddress(newMatches[0].groupValues[1].trim())
            L.d(TAG, "Endereço de embarque (novo): $pickupAddress")
            if (newMatches.size > 1) {
                dropoffAddress = cleanupAddress(newMatches[1].groupValues[1].trim())
                L.d(TAG, "Endereço de destino (novo): $dropoffAddress")
            }
        }

        // 2. Fallback: formato antigo (com "de distância" e/ou CEP)
        if (pickupAddress == null) {
            val pickupMatch = PICKUP_ADDRESS_REGEX.find(text)
            if (pickupMatch != null) {
                pickupAddress = cleanupAddress(pickupMatch.groupValues[1].trim())
                L.d(TAG, "Endereço de embarque (antigo): $pickupAddress")
            }
        }
        if (dropoffAddress == null) {
            val dropoffMatch = DROPOFF_ADDRESS_REGEX.find(text)
            if (dropoffMatch != null) {
                dropoffAddress = cleanupAddress(dropoffMatch.groupValues[1].trim())
                L.d(TAG, "Endereço de viagem (antigo): $dropoffAddress")
            }
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
        private const val TAG = "App99CardParser"

        private val VALUE_REGEX = Regex(
            """(?:^|\s)R\$\s*(\d+(?:[.,]\d+)?)(?=\s|$)""",
            RegexOption.IGNORE_CASE
        )
        private val KM_PER_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*/\s*km""", RegexOption.IGNORE_CASE)
        private val KM_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*por km""", RegexOption.IGNORE_CASE)
        private val HOUR_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*/\s*h""", RegexOption.IGNORE_CASE)

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

        private val ADDRESS_TIME_DIST_NEW = Regex(
            """\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)\s+([A-Za-zÀ-Úà-ú0-9\s,./°-]+?)(?=\s*\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)|\s*(?:Reservas|Aceitar|Informações|Selecionar|$))""",
            RegexOption.IGNORE_CASE
        )
    }
}
