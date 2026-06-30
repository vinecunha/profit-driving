package com.profitdriving.parser

import com.profitdriving.L
import com.profitdriving.RideData
import com.profitdriving.accessibility.extractor.CardType
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.accessibility.extractor.UberCardExtractor
import java.util.Locale

class RadarCardParser : RideDataParser {

    override fun canParse(raw: RawCardData): Boolean {
        if (raw.cardType == CardType.RADAR) return true
        val full = raw.rawTexts.joinToString(" ").lowercase(Locale.ROOT)
        val hasAccept = full.contains("aceitar") || full.contains("accept")
        val hasMoney = full.contains("r$")
        val hasKm = full.contains("km")
        val hasMin = full.contains("min")
        return hasAccept && hasMoney && hasKm && hasMin && !full.contains("exclusivo")
    }

    override fun parse(raw: RawCardData): RideData? {
        L.d(TAG, "RadarCardParser.parse() iniciado")
        val text = raw.fullText

        val value = extractValue(text)
        if (value == null || value <= 0) {
            L.d(TAG, "Valor inválido — RadarCardParser abortando")
            return null
        }

        val pricePerKm = extractPricePerKm(text)
        val pricePerHour = extractPricePerHour(text)
        val distance = extractDistance(text)
        val timeMin = extractTime(text)
        val rating = extractRating(text)
        val pickupKm = extractPickupDistance(text)
        val pickupTime = extractPickupTime(text)
        val tripKm = extractTripDistance(text)
        val tripTime = extractTripTime(text)
        val serviceType = extractServiceType(text)
        val priorityBonus = extractPriorityBonus(text)
        val dynamicBonus = extractDynamicBonus(text)
        val hasMultipleStops = extractStops(text)
        val (pickupAddress, dropoffAddress) = extractAddresses(text)

        L.d(TAG, "RadarCardParser parsed: value=$value km=$distance tempo=$timeMin nota=$rating")

        return RideData(
            value = value,
            distanceKm = distance,
            timeMin = timeMin,
            rating = rating,
            appName = "Uber",
            pricePerKm = pricePerKm ?: distance?.let { d ->
                if (d > 0) value / d else null
            },
            pricePerHour = pricePerHour ?: timeMin?.let { t ->
                if (t > 0) value / (t / 60.0) else null
            },
            detectedBy = "accessibility_radar",
            pickupDistanceKm = pickupKm,
            pickupTimeMin = pickupTime,
            tripDistanceKm = tripKm,
            tripTimeMin = tripTime,
            serviceType = serviceType,
            hasMultipleStops = hasMultipleStops,
            priorityBonus = priorityBonus,
            dynamicBonus = dynamicBonus,
            pickupAddress = pickupAddress,
            dropoffAddress = dropoffAddress,
            exclusiveHash = null
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
        var total = 0.0

        // 1. Novo formato: pickup = primeiro match, trip = segundo match
        val pickupMatches = PICKUP_REGEX.findAll(text).toList()
        val tripMatches = VIAGEM_REGEX.findAll(text).toList()

        if (pickupMatches.isNotEmpty()) {
            val v = UberCardExtractor.parseBr(pickupMatches[0].groupValues[2])
            if (v != null && v > 0 && v < 50) total += v
        }
        if (tripMatches.size >= 2) {
            val v = UberCardExtractor.parseBr(tripMatches[1].groupValues[3])
            if (v != null && v > 0 && v < 200) total += v
        } else if (tripMatches.size == 1 && pickupMatches.isEmpty()) {
            val v = UberCardExtractor.parseBr(tripMatches[0].groupValues[3])
            if (v != null && v > 0 && v < 200) total += v
        }

        if (total == 0.0) {
            // 2. Fallback: formato antigo
            val viagemOld = VIAGEM_REGEX_OLD.find(text)
            if (viagemOld != null) {
                val v = UberCardExtractor.parseBr(viagemOld.groupValues[3])
                if (v != null && v > 0 && v < 200) total += v
            }
            val pickupOld = PICKUP_REGEX_OLD.find(text)
            if (pickupOld != null) {
                val v = UberCardExtractor.parseBr(pickupOld.groupValues[2])
                if (v != null && v > 0 && v < 50) total += v
            }
        }

        if (total == 0.0) {
            for (pat in DISTANCE_PATTERNS) {
                val m = pat.find(text)
                if (m != null) {
                    val v = UberCardExtractor.parseBr(m.groupValues[1])
                    if (v != null && v > 0 && v < 200) return v
                }
            }
            return null
        }
        return total
    }

    private fun extractTime(text: String): Int? {
        var total = 0

        // 1. Novo formato: pickup = primeiro match, trip = segundo match
        val pickupMatches = PICKUP_REGEX.findAll(text).toList()
        val tripMatches = VIAGEM_REGEX.findAll(text).toList()

        if (pickupMatches.isNotEmpty()) {
            val v = pickupMatches[0].groupValues[1].toIntOrNull()
            if (v != null && v > 0) total += v
        }
        if (tripMatches.size >= 2) {
            val match = tripMatches[1]
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            total += hours * 60 + minutes
        } else if (tripMatches.size == 1 && pickupMatches.isEmpty()) {
            val match = tripMatches[0]
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            total += hours * 60 + minutes
        }

        if (total == 0) {
            // 2. Fallback: formato antigo
            val viagemOld = VIAGEM_REGEX_OLD.find(text)
            if (viagemOld != null) {
                val hours = viagemOld.groupValues[1].toIntOrNull() ?: 0
                val mins = viagemOld.groupValues[2].toIntOrNull() ?: 0
                total += hours * 60 + mins
            }
            val pickupOld = PICKUP_REGEX_OLD.find(text)
            if (pickupOld != null) {
                val v = pickupOld.groupValues[1].toIntOrNull()
                if (v != null && v > 0) total += v
            }
        }

        if (total == 0) {
            for (pat in TIME_PATTERNS) {
                val m = pat.find(text)
                if (m != null) {
                    val v = m.groupValues[1].toIntOrNull()
                    if (v != null && v > 0) return v
                }
            }
            return null
        }
        return total
    }

    private fun extractPickupDistance(text: String): Double? {
        // 1. Novo formato: findAll, PRIMEIRO match = pickup
        val allMatches = PICKUP_REGEX.findAll(text).toList()
        if (allMatches.isNotEmpty()) {
            val v = UberCardExtractor.parseBr(allMatches[0].groupValues[2])
            if (v != null && v > 0 && v < 50) return v
        }
        // 2. Fallback: formato antigo
        val pickupOld = PICKUP_REGEX_OLD.find(text)
        if (pickupOld != null) {
            val v = UberCardExtractor.parseBr(pickupOld.groupValues[2])
            if (v != null && v > 0 && v < 50) return v
        }
        return null
    }

    private fun extractPickupTime(text: String): Int? {
        // 1. Novo formato: findAll, PRIMEIRO match = pickup
        val allMatches = PICKUP_REGEX.findAll(text).toList()
        if (allMatches.isNotEmpty()) {
            val v = allMatches[0].groupValues[1].toIntOrNull()
            if (v != null && v > 0) return v
        }
        // 2. Fallback: formato antigo
        val pickupOld = PICKUP_REGEX_OLD.find(text)
        if (pickupOld != null) {
            val v = pickupOld.groupValues[1].toIntOrNull()
            if (v != null && v > 0) return v
        }
        return null
    }

    private fun extractTripDistance(text: String): Double? {
        // 1. Novo formato: findAll, SEGUNDO match = trip
        val allMatches = VIAGEM_REGEX.findAll(text).toList()
        if (allMatches.size >= 2) {
            val v = UberCardExtractor.parseBr(allMatches[1].groupValues[3])
            if (v != null && v > 0 && v < 200) return v
        } else if (allMatches.size == 1) {
            val v = UberCardExtractor.parseBr(allMatches[0].groupValues[3])
            if (v != null && v > 0 && v < 200) return v
        }
        // 2. Fallback: formato antigo
        val viagemOld = VIAGEM_REGEX_OLD.find(text)
        if (viagemOld != null) {
            val v = UberCardExtractor.parseBr(viagemOld.groupValues[3])
            if (v != null && v > 0 && v < 200) return v
        }
        return null
    }

    private fun extractTripTime(text: String): Int? {
        // 1. Novo formato: findAll, SEGUNDO match = trip
        val allMatches = VIAGEM_REGEX.findAll(text).toList()
        if (allMatches.size >= 2) {
            val match = allMatches[1]
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: return null
            val total = hours * 60 + minutes
            if (total > 0) return total
        } else if (allMatches.size == 1) {
            val match = allMatches[0]
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: return null
            val total = hours * 60 + minutes
            if (total > 0) return total
        }
        // 2. Fallback: formato antigo
        val matchOld = VIAGEM_REGEX_OLD.find(text)
        if (matchOld != null) {
            val hoursStr = matchOld.groupValues[1]
            val minutesStr = matchOld.groupValues[2]
            val hours = if (hoursStr.isNullOrEmpty()) 0 else hoursStr.toIntOrNull() ?: 0
            val minutes = if (minutesStr.isNullOrEmpty()) return null else minutesStr.toIntOrNull() ?: return null
            val total = hours * 60 + minutes
            if (total > 0) return total
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
            val pickupMatchOld = PICKUP_ADDRESS_REGEX_OLD.find(text)
            if (pickupMatchOld != null) {
                pickupAddress = cleanupAddress(pickupMatchOld.groupValues[1].trim())
                L.d(TAG, "Endereço de embarque (antigo): $pickupAddress")
            }
        }
        if (dropoffAddress == null) {
            val dropoffMatchOld = DROPOFF_ADDRESS_REGEX_OLD.find(text)
            if (dropoffMatchOld != null) {
                dropoffAddress = cleanupAddress(dropoffMatchOld.groupValues[1].trim())
                L.d(TAG, "Endereço de destino (antigo): $dropoffAddress")
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
        private const val TAG = "RadarCardParser"

        private val VALUE_REGEX = Regex(
            """(?:^|\s)R\$\s*(\d+(?:[.,]\d+)?)(?=\s|$)""",
            RegexOption.IGNORE_CASE
        )
        private val KM_PER_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*/\s*km""", RegexOption.IGNORE_CASE)
        private val KM_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*por km""", RegexOption.IGNORE_CASE)
        private val HOUR_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*/\s*h""", RegexOption.IGNORE_CASE)

        // NOVO formato (sem "de distância" ou "Viagem de")
        private val PICKUP_REGEX = Regex(
            """(\d+)\s*min(?:uto)?s?\s*\(\s*(\d+[.,]\d+)\s*km\s*\)""",
            RegexOption.IGNORE_CASE
        )

        private val VIAGEM_REGEX = Regex(
            """(?:(\d+)\s*[Hh](?:ora(?:s)?)?\s*e\s*)?(\d+)\s*[Mm]in(?:uto)?s?\s*\((\d+[.,]\d+)\s*km\)""",
            RegexOption.IGNORE_CASE
        )

        // Formato ANTIGO (com "de distância" e "Viagem de") - compatibilidade
        private val PICKUP_REGEX_OLD = Regex(
            """(\d+)\s*min(?:uto)?s?\s*\(\s*(\d+[.,]\d+)\s*km\s*\)\s*de\s*dist[âa]ncia""",
            RegexOption.IGNORE_CASE
        )

        private val VIAGEM_REGEX_OLD = Regex(
            """[Vv]iagem\s+de\s+(?:(\d+)\s*[Hh](?:ora(?:s)?)?\s*e\s*)?(\d+)\s*[Mm]in(?:uto)?s?\s*\((\d+[.,]\d+)\s*km\)""",
            RegexOption.IGNORE_CASE
        )

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

        // NOVO formato (sem CEP/UF) — captura endereço entre tempo/dist e próximo tempo/dist ou botões
        private val ADDRESS_TIME_DIST_NEW = Regex(
            """\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)\s+([A-Za-zÀ-Úà-ú0-9\s,./°-]+?)(?=\s*\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)|\s*(?:Reservas|Aceitar|Informações|Selecionar|$))""",
            RegexOption.IGNORE_CASE
        )

        // Formato ANTIGO (com "de distância") — compatibilidade
        private val PICKUP_ADDRESS_REGEX_OLD = Regex(
            """\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)\s*de\s*dist[âa]ncia\s+([A-Za-zÀ-Úà-ú0-9\s,.-]+?)(?=\s*(?:\d+\s*min|Viagem|Aceitar|$))""",
            RegexOption.IGNORE_CASE
        )

        // Formato ANTIGO (com "Viagem de" e CEP) — compatibilidade
        private val DROPOFF_ADDRESS_REGEX_OLD = Regex(
            """Viagem\s+de\s+\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)\s+([A-Za-zÀ-Úà-ú0-9\s,.-]+?\d{5}-\d{3})""",
            RegexOption.IGNORE_CASE
        )
    }
}
