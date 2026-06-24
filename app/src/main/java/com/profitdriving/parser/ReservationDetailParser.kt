package com.profitdriving.parser

import com.profitdriving.L
import com.profitdriving.RideData
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.accessibility.extractor.UberCardExtractor
import java.util.Locale

class ReservationDetailParser : RideDataParser {

    override fun canParse(raw: RawCardData): Boolean {
        val full = raw.rawTexts.joinToString(" ").lowercase(Locale.ROOT)
        val hasTitle = full.contains("solicitação de reserva")
        val hasMoney = full.contains("r$")
        val hasRating = full.contains("⭐")
        val hasTripInfo = TRIP_PATTERN.containsMatchIn(full)
        val hasAcceptButton = ACCEPT_BUTTON_PATTERN.containsMatchIn(full)
        return hasTitle && hasMoney && hasRating && hasTripInfo && hasAcceptButton
    }

    override fun parse(raw: RawCardData): RideData? {
        L.d(TAG, "ReservationDetailParser.parse() iniciado")
        val text = raw.fullText

        val serviceType = extractServiceType(raw.rawTexts)
        L.d(TAG, "Tipo de serviço: $serviceType")

        val value = extractValue(text)
        if (value == null || value <= 0) {
            L.d(TAG, "Valor inválido — ReservationDetailParser abortando")
            return null
        }

        val rating = extractRating(text)
        val timeMin = extractTime(text)
        val distance = extractDistance(text)

        val (pickupAddress, pickupTime) = extractPickup(raw.rawTexts)
        val (dropoffAddress, dropoffTime) = extractDropoff(raw.rawTexts)

        L.d(TAG, "ReservationDetailParser parsed: value=$value km=$distance time=${timeMin}min rating=$rating")

        return RideData(
            value = value,
            distanceKm = distance,
            timeMin = timeMin,
            rating = rating,
            appName = "Uber",
            pricePerKm = if (distance != null && distance > 0) value / distance else null,
            pricePerHour = timeMin?.let { if (it > 0) value / (it / 60.0) else null },
            detectedBy = "accessibility_discovery_detail",
            pickupAddress = pickupAddress,
            dropoffAddress = dropoffAddress,
            serviceType = serviceType,
            pickupTimeMin = null,
            pickupDistanceKm = null,
            tripDistanceKm = distance,
            tripTimeMin = timeMin,
            hasMultipleStops = false,
            priorityBonus = null,
            dynamicBonus = null
        )
    }

    private fun extractServiceType(rawTexts: List<String>): String? {
        for (text in rawTexts) {
            val trimmed = text.trim()
            for ((regex, label) in SERVICE_TYPE_PATTERNS) {
                if (regex.matches(trimmed)) return label
            }
        }
        return null
    }

    private fun extractValue(text: String): Double? {
        val m = VALUE_PATTERN.find(text) ?: return null
        return UberCardExtractor.parseBr(m.groupValues[1])
    }

    private fun extractRating(text: String): Double? {
        val m = RATING_PATTERN.find(text) ?: return null
        return UberCardExtractor.parseBr(m.groupValues[1])
    }

    private fun extractTime(text: String): Int? {
        val m = TRIP_PATTERN.find(text) ?: return null
        return m.groupValues[1].toIntOrNull()
    }

    private fun extractDistance(text: String): Double? {
        val m = TRIP_PATTERN.find(text) ?: return null
        return UberCardExtractor.parseBr(m.groupValues[2])
    }

    private fun extractPickup(rawTexts: List<String>): Pair<String?, String?> {
        val addressNodes = rawTexts.filter { ADDRESS_REGEX.matches(it.trim()) }
        if (addressNodes.isEmpty()) return null to null
        val address = addressNodes[0]
        val idx = rawTexts.indexOf(address)
        val time = if (idx >= 0 && idx + 1 < rawTexts.size) {
            val t = rawTexts[idx + 1].trim()
            if (TIME12_PATTERN.matches(t)) t else null
        } else null
        return address to time
    }

    private fun extractDropoff(rawTexts: List<String>): Pair<String?, String?> {
        val addressNodes = rawTexts.filter { ADDRESS_REGEX.matches(it.trim()) }
        if (addressNodes.size < 2) return null to null
        val address = addressNodes[1]
        val idx = rawTexts.indexOf(address)
        val time = if (idx >= 0 && idx + 1 < rawTexts.size) {
            val t = rawTexts[idx + 1].trim()
            if (TIME12_PATTERN.matches(t)) t else null
        } else null
        return address to time
    }

    companion object {
        private const val TAG = "ReservationDetailParser"

        private val TRIP_PATTERN = Regex(
            """(?:viagem\s+de\s+)?(\d+)\s*min(?:uto)?s?\s*\((\d+[.,]\d+)\s*km\)""",
            RegexOption.IGNORE_CASE
        )

        private val VALUE_PATTERN = Regex(
            """(?:^|\s)R\$\s*(\d+[.,]\d+)(?=\s|$)""",
            RegexOption.IGNORE_CASE
        )

        private val RATING_PATTERN = Regex(
            """[⭐*]*\s*(\d[.,]\d{1,2})"""
        )

        private val RESERVATION_TIME_PATTERN = Regex(
            """(Hoje|Amanhã)\s+à\(s\)\s+(\d{1,2}:\d{2})\s+(AM|PM)""",
            RegexOption.IGNORE_CASE
        )

        private val ACCEPT_BUTTON_PATTERN = Regex(
            """Aceitar\s*•\s*R\$\s*(\d+[.,]\d+)""",
            RegexOption.IGNORE_CASE
        )

        // NOVO: captura texto entre pickups que parece endereço (sem CEP obrigatório)
        private val ADDRESS_REGEX = Regex(
            """(?:.*\d{5}-\d{3}.*|^[A-Za-zÀ-Úà-ú].*[a-záéíóú].*\d)""",
            RegexOption.IGNORE_CASE
        )

        // ANTIGO (somente CEP) — mantido como fallback em extractPickup/extractDropoff

        private val TIME12_PATTERN = Regex(
            """\d{1,2}:\d{2}\s+(AM|PM)""",
            RegexOption.IGNORE_CASE
        )

        private val SERVICE_TYPE_PATTERNS: List<Pair<Regex, String>> = listOf(
            "Comfort" to "Comfort",
            "UberX" to "UberX",
            "Uber Black" to "Black",
            "Uber Moto" to "Moto",
            "Uber Flash" to "Flash",
            "Uber Juntos" to "Juntos",
            "Black" to "Black",
            "Moto" to "Moto",
            "Flash" to "Flash",
            "Juntos" to "Juntos",
            "Pop" to "Pop",
            "Top" to "Top"
        ).map { (keyword, label) ->
            Regex("^${Regex.escape(keyword)}$", RegexOption.IGNORE_CASE) to label
        }
    }
}
