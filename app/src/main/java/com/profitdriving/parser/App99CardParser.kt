package com.profitdriving.parser

import com.profitdriving.L
import com.profitdriving.RideData
import com.profitdriving.accessibility.extractor.CardType
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.accessibility.extractor.UberCardExtractor
import java.util.Locale

class App99CardParser : RideDataParser {

    override fun canParse(raw: RawCardData): Boolean {
        if (raw.cardType == CardType.APP_99 ||
            raw.cardType == CardType.APP_99_NEGOTIATE ||
            raw.cardType == CardType.APP_99_PRIORITY) return true
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
        L.d(TAG, "TEXTO_OCR: $text")

        val value = extractValue(text)
        if (value == null || value <= 0) {
            L.d(TAG, "Valor inválido — App99CardParser abortando")
            return null
        }

        val pricePerKm       = extractPricePerKm(text)
        val pricePerHour     = extractPricePerHour(text)
        val (pickupTime, pickupDist) = extractPickupInfo(text)
        val (tripTime, tripDist) = extractTripInfo(text)
        val rating           = extractRating(text)
        val serviceType      = extractServiceType(text)
        val priorityBonus    = extractPriorityBonus(text)
        val dynamicBonus     = extractDynamicBonus(text)
        val hasMultipleStops = extractStops(text)
        val (pickupAddress, dropoffAddress) = extractAddresses(text)
        val dynamicMultiplier = extractDynamicMultiplier(text)
        val isNegotiate       = extractIsNegotiate(text)
        val negotiateOptions  = extractNegotiateOptions(text)
        val baseRate          = extractBaseRate(text)

        val totalDist = when {
            pickupDist != null && tripDist != null -> pickupDist + tripDist
            tripDist != null -> tripDist
            pickupDist != null -> pickupDist
            else -> null
        }
        val totalTime = when {
            pickupTime != null && tripTime != null -> pickupTime + tripTime
            tripTime != null -> tripTime
            pickupTime != null -> pickupTime
            else -> null
        }

        L.d(TAG, "App99 parsed: value=$value rating=$rating pickupDist=$pickupDist tripDist=$tripDist totalDist=$totalDist " +
            "pickupTime=$pickupTime tripTime=$tripTime totalTime=$totalTime " +
            "isNegotiate=$isNegotiate multiplier=$dynamicMultiplier " +
            "baseRate=$baseRate negotiateOptions=$negotiateOptions " +
            "pickup=$pickupAddress dropoff=$dropoffAddress")

        return RideData(
            value             = value,
            distanceKm        = totalDist,
            timeMin           = totalTime,
            pickupDistanceKm  = pickupDist,
            pickupTimeMin     = pickupTime,
            tripDistanceKm    = tripDist,
            tripTimeMin       = tripTime,
            rating            = rating,
            appName           = "99",
            pricePerKm        = pricePerKm ?: totalDist?.let { d -> if (d > 0) value / d else null },
            pricePerHour      = pricePerHour ?: totalTime?.let { t -> if (t > 0) value / (t / 60.0) else null },
            detectedBy        = "accessibility_99",
            serviceType       = serviceType,
            hasMultipleStops  = hasMultipleStops,
            priorityBonus     = priorityBonus,
            dynamicBonus      = dynamicBonus,
            pickupAddress     = pickupAddress,
            dropoffAddress    = dropoffAddress,
            dynamicMultiplier = dynamicMultiplier,
            isNegotiate       = isNegotiate,
            negotiateOptions  = negotiateOptions,
            baseRate          = baseRate,
        )
    }

    private fun extractValue(text: String): Double? {
        val beforeNegotiate = text.substringBefore("Aceitar por").ifBlank { text }
        val aceitarIdx = beforeNegotiate.indexOf("Aceitar", ignoreCase = true)
        val searchIn = if (aceitarIdx >= 0) beforeNegotiate.substring(0, aceitarIdx) else beforeNegotiate

        // Tenta ambos os padrões: normal (R$XX,XX) e OCR com espaço (R$XX XX)
        val normalMatches = VALUE_REGEX.findAll(searchIn).toList()
        val ocrMatches = VALUE_REGEX_OCR.findAll(searchIn).toList()

        val values = mutableListOf<Pair<Int, Double>>()

        for (m in normalMatches) {
            val before = if (m.range.first > 0) searchIn[m.range.first - 1] else ' '
            if (before != '+') {
                val v = UberCardExtractor.parseBr(m.groupValues[1])
                if (v != null && v > 3.0) values.add(m.range.first to v)
            }
        }
        for (m in ocrMatches) {
            val before = if (m.range.first > 0) searchIn[m.range.first - 1] else ' '
            if (before != '+') {
                val intPart = m.groupValues[1]
                val decPart = m.groupValues[2]
                val v = UberCardExtractor.parseBr("$intPart,$decPart")
                if (v != null && v > 3.0) values.add(m.range.first to v)
            }
        }

        if (values.isEmpty()) return null

        val isNegotiate = NEGOCIA_REGEX.containsMatchIn(text)

        if (isNegotiate) {
            // Negocia: pega o valor mais próximo da tag "Negocia"
            val negociaMatch = NEGOCIA_REGEX.find(text)
            val negociaPos = negociaMatch?.range?.first ?: -1
            val afterAccept = text.substringAfter("Aceitar por", "")
            val optionValues = NEGOTIATE_OPTION_REGEX.findAll(afterAccept)
                .mapNotNull { UberCardExtractor.parseBr(it.groupValues[1]) }
                .filter { it > 3.0 }
                .toSet()
            val remaining = values.filter { (_, v) -> v !in optionValues }
            if (remaining.size == 1) return remaining.first().second
            if (remaining.size > 1 && negociaPos >= 0) {
                return remaining.minBy { (pos, _) -> kotlin.math.abs(pos - negociaPos) }.second
            }
            if (remaining.isNotEmpty()) return remaining.first().second
        }

        // Não-Negocia: pega o maior valor DENTRO de 100 caracteres do segmento de rota
        val routePos = findFirstRoutePosition(text)
        if (routePos >= 0) {
            val nearRoute = values.filter { (pos, _) ->
                kotlin.math.abs(pos - routePos) < 100
            }
            if (nearRoute.isNotEmpty()) {
                return nearRoute.maxBy { (_, v) -> v }.second
            }
        }
        // Fallback: maior valor geral
        return values.maxBy { (_, v) -> v }.second
    }

    private fun findFirstRoutePosition(text: String): Int {
        val m = ROUTE_FIND_REGEX.find(text)
        return m?.range?.first ?: -1
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
        val (_, tripDist) = extractTripInfo(text)
        if (tripDist != null && tripDist > 0) return tripDist
        val (_, pickupDist) = extractPickupInfo(text)
        return pickupDist
    }

    private fun extractTime(text: String): Int? {
        val (tripTime, _) = extractTripInfo(text)
        if (tripTime != null && tripTime > 0) return tripTime
        val (pickupTime, _) = extractPickupInfo(text)
        return pickupTime
    }

    private fun extractPickupInfo(text: String): Pair<Int?, Double?> =
        extractRouteSegment(text, index = 0)

    private fun extractTripInfo(text: String): Pair<Int?, Double?> =
        extractRouteSegment(text, index = 1)

    private fun extractRouteSegment(text: String, index: Int): Pair<Int?, Double?> {
        val formatOcr = Regex("""[.\s]*(\d*)\s*min\s*\((\d+(?:[.,]\d*)?)\s*(k?m)\)""", RegexOption.IGNORE_CASE)
        val formatA = Regex("""(\d+)\s*min\s*\((\d+[.,]\d*)\s*km\)""", RegexOption.IGNORE_CASE)
        val formatB = Regex("""\((\d+)\s*min\s+(\d+[.,]\d*)\s*km\)""", RegexOption.IGNORE_CASE)

        for (pattern in listOf(formatOcr, formatA, formatB)) {
            val matches = pattern.findAll(text).toList()
            L.d(TAG, "extractRouteSegment(index=$index) pattern=${pattern.pattern}: ${matches.size} matches -> " +
                matches.map { "g1='${it.groupValues[1]}' g2='${it.groupValues[2]}' g3='${it.groupValues.getOrNull(3)}'" })
            if (matches.size > index) {
                val m = matches[index]
                val time = m.groupValues[1].toIntOrNull()
                val rawDist = UberCardExtractor.parseBr(m.groupValues[2])
                val isMeters = m.groupValues.getOrNull(3) == "m"
                val dist = if (rawDist != null && isMeters) rawDist / 1000.0 else rawDist
                val timeVal = if (time != null && time > 0) time else null
                L.d(TAG, "extractRouteSegment(index=$index) → time=$timeVal dist=$dist")
                if (timeVal != null || dist != null) return Pair(timeVal, dist)
            }
        }
        return Pair(null, null)
    }

    private fun extractRating(text: String): Double? {
        for (regex in listOf(
            RATING_CORRIDAS_REGEX, RATING_AFTER_CORRIDAS_REGEX,
            RATING_STAR_REGEX, RATING_COUNT_REGEX, RATING_BULLET_REGEX
        )) {
            val m = regex.find(text)
            if (m != null) {
                val v = UberCardExtractor.parseBr(m.groupValues[1])
                L.d(TAG, "  extractRating: regex=${regex.pattern} matched='${m.groupValues[1]}' parsed=$v")
                if (v != null && v in 1.0..5.1) return v
            }
        }
        L.d(TAG, "  extractRating: nenhum match encontrado")
        return null
    }

    private fun extractServiceType(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        for ((regex, label) in SERVICE_TYPE_PATTERNS) {
            if (regex.containsMatchIn(lower)) return label
        }
        if (NEGOCIA_REGEX.containsMatchIn(text)) return "Negocia"
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
        return Regex("""\b(?:\d+\s+)?(?:várias\s+)?paradas?\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerText)
    }

    private fun extractDynamicMultiplier(text: String): Double? {
        val m = DYNAMIC_MULTIPLIER_REGEX.find(text) ?: return null
        val v = UberCardExtractor.parseBr(m.groupValues[1])
        return if (v != null && v > 1.0) v else null
    }

    private fun extractNegotiateOptions(text: String): List<Double>? {
        val afterAccept = text.substringAfter("Aceitar por", "")
        if (afterAccept.isBlank()) return null

        val options = NEGOTIATE_OPTION_REGEX.findAll(afterAccept)
            .mapNotNull { UberCardExtractor.parseBr(it.groupValues[1]) }
            .filter { it > 3.0 }
            .toList()

        return if (options.size >= 2) options else null
    }

    private fun extractBaseRate(text: String): Double? {
        val m = BASE_RATE_REGEX.find(text) ?: return null
        return UberCardExtractor.parseBr(m.groupValues[1])
    }

    private fun extractIsNegotiate(text: String): Boolean =
        NEGOCIA_REGEX.containsMatchIn(text)
        || ACEITAR_POR_REGEX.containsMatchIn(text)

    private fun extractAddresses(text: String): Pair<String?, String?> {

        // === FORMATO A: Card único ===
        val formatsA = listOf(
            Regex("""(\d*)\s*min\s*\((\d+[.,]\d*)\s*km\)\s*\n\s*([^\n]+)""", RegexOption.IGNORE_CASE),
            Regex("""[.\s]*(\d*)\s*min\s*\((\d+(?:[.,]\d*)?)\s*k?m\)\s*\n\s*([^\n]+)""", RegexOption.IGNORE_CASE),
        )
        for (fmt in formatsA) {
            val matches = fmt.findAll(text).toList()
            if (matches.size >= 1) {
                val pickup = cleanupAddress(matches[0].groupValues[3].trim())
                val dropoff = if (matches.size >= 2)
                    cleanupAddress(matches[1].groupValues[3].trim())
                else null
                if (!pickup.isNullOrEmpty()) {
                    L.d(TAG, "Endereços (Formato A): pickup=$pickup dropoff=$dropoff")
                    return Pair(pickup, dropoff)
                }
            }
        }

        // === FORMATO B: Lista de Solicitações ===
        val formatsB = listOf(
            Regex("""\(\d+\s*min\s+\d+[.,]\d*\s*km\)\s+([A-Za-zÀ-Úà-ú0-9][^\(]{5,250}?)(?=\s*\(\d+\s*min|\s*Escolher|\s*$)""", RegexOption.IGNORE_CASE),
            Regex("""\(\d+\s*min\s+\d+(?:[.,]\d*)?\s*k?m\)\s+([A-Za-zÀ-Úà-ú0-9][^\(]{5,250}?)(?=\s*\(\d+\s*min|\s*Escolher|\s*$)""", RegexOption.IGNORE_CASE),
            // OCR-tolerant: leading noise like "." or "S" before min
            Regex("""[.\s]*\S*min\s+\d+(?:[.,]\d*)?\s*k?m\)\s+([A-Za-zÀ-Úà-ú0-9][^\(]{5,250}?)(?=\s*\(\d+\s*min|\s*Escolher|\s*$)""", RegexOption.IGNORE_CASE),
        )
        for (fmt in formatsB) {
            val matches = fmt.findAll(text).toList()
            if (matches.size >= 1) {
                val pickup = cleanupAddress(matches[0].groupValues[1].trim())
                val dropoff = if (matches.size >= 2)
                    cleanupAddress(matches[1].groupValues[1].trim())
                else null
                if (!pickup.isNullOrEmpty()) {
                    L.d(TAG, "Endereços (Formato B): pickup=$pickup dropoff=$dropoff")
                    return Pair(pickup, dropoff)
                }
            }
        }

        // === FORMATO C: Card único (Xmin (Y.Zkm) \n Address) ===
        val segmentHeader = Regex("""(\d*)\s*min\s*\((\d+(?:[.,]\d*)?)\s*k?m\)""", RegexOption.IGNORE_CASE)
        val segMatches = segmentHeader.findAll(text).toList()
        if (segMatches.size >= 2) {
            val pickupEnd = segMatches[0].range.last + 1
            val pickupText = text.substring(pickupEnd, segMatches[1].range.first).trim()
            val dropoffEnd = segMatches[1].range.last + 1
            val dropoffText = text.substring(dropoffEnd).trim()
            val pickup = cleanupAddress(pickupText)
            val dropoff = cleanupAddress(dropoffText)
            if (!pickup.isNullOrEmpty() && !dropoff.isNullOrEmpty()) {
                L.d(TAG, "Endereços (Formato C): pickup=$pickup dropoff=$dropoff")
                return Pair(pickup, dropoff)
            }
        }

        L.d(TAG, "Endereços: nenhum formato reconhecido")
        return Pair(null, null)
    }

    private fun cleanupAddress(address: String): String {
        var cleaned = address
            .replace(Regex("\\s+"), " ")
            .replace(Regex(",\\s*,+"), ",")
            .replace(Regex(",?\\s*go_button\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex(",?\\s*Isso é tudo por enquanto\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[,;]?\s*(?:\d+\s+)?(?:v[aá]rias?\s+)?paradas?\s*,?\s*""", RegexOption.IGNORE_CASE), ", ")
            .trim()
        if (cleaned.length > 150) {
            cleaned = cleaned.take(150)
        }
        return cleaned
    }

    companion object {
        private const val TAG = "App99CardParser"

        private val VALUE_REGEX = Regex(
            """R\$\s*(\d{1,4}[.,]\d{2})(?!\s*/\s*(?:km|h)\b)""",
            RegexOption.IGNORE_CASE
        )
        private val KM_PER_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*/\s*km""", RegexOption.IGNORE_CASE)
        private val KM_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*por km""", RegexOption.IGNORE_CASE)
        private val HOUR_REAL_REGEX = Regex("""R\$(\d+[.,]\d+)\s*/\s*h""", RegexOption.IGNORE_CASE)

        // rating ANTES de "corridas": "5,00 • 20 corridas" ou "5,00 - 37 corridas"
        private val RATING_CORRIDAS_REGEX = Regex(
            """(\d[.,]\d{1,2})\s*[-–·•]?\s*\d+\s*corridas?""",
            RegexOption.IGNORE_CASE
        )
        // rating DEPOIS de "corridas": "37 corridas - 5,00"
        private val RATING_AFTER_CORRIDAS_REGEX = Regex(
            """\d+\s*corridas?\s*[-–·•]?\s*(\d[.,]\d{1,2})""",
            RegexOption.IGNORE_CASE
        )
        private val RATING_STAR_REGEX = Regex("""(\d[.,]\d{1,2})\s*[★⭐*]""")
        private val RATING_COUNT_REGEX = Regex("""(\d[.,]\d{1,2})\s*\(\d+\)""")
        private val RATING_BULLET_REGEX = Regex("""(\d[.,]\d{1,2})\s*[·•]""")

        private val SERVICE_TYPE_PATTERNS: List<Pair<Regex, String>> = listOf(
            "negocia"      to "Negocia",
            "pop expresso" to "Pop Expresso",
            "99pop"        to "Pop",
            "pop"          to "Pop",
            "99moto"       to "Moto",
            "moto"         to "Moto",
            "99entrega"    to "Entrega",
            "entrega"      to "Entrega",
            "99táxi"       to "Táxi",
            "99taxi"       to "Táxi",
            "táxi"         to "Táxi",
            "taxi"         to "Táxi",
            "99plus"       to "Plus",
        ).map { (keyword, label) ->
            Regex("\\b${Regex.escape(keyword)}\\b", RegexOption.IGNORE_CASE) to label
        }

        private val PRIORITY_BONUS_REGEX = Regex(
            """\+R\$\s*(\d+(?:[.,]\d+)?)\s*inclu[íi]do\s+para\s+(?:prioridade|embarque)""",
            RegexOption.IGNORE_CASE
        )

        private val DYNAMIC_BONUS_REGEX = Regex(
            """\+R\$\s*(\d+(?:[.,]\d+)?)\s*inclu[íi]do(?!\s+para\s+(?:prioridade|embarque))""",
            RegexOption.IGNORE_CASE
        )

        private val DYNAMIC_MULTIPLIER_REGEX = Regex(
            """[⚡\u26A1]\s*(\d+[.,]\d+)\s*x""",
            RegexOption.IGNORE_CASE
        )

        // OCR fuzzy: "Negocia", "Negocla", "Negºciª", "Négocia" etc.
        private val NEGOCIA_REGEX = Regex(
            """[Nn][eéêè][gG][oóôòº][cC][i1Il][aAª]"""
        )
        private val ACEITAR_POR_REGEX = Regex(
            """[Aa]ceit[ae]r\s+por\s+R\$""",
            RegexOption.IGNORE_CASE
        )
        // OCR: "R$22 89" (espaço em vez de vírgula)
        private val VALUE_REGEX_OCR = Regex(
            """R\$\s*(\d{1,4})\s+(\d{2})(?!\s*/\s*(?:km|h)\b)""",
            RegexOption.IGNORE_CASE
        )

        private val ROUTE_FIND_REGEX = Regex("""\d+\s*min\s*\(""", RegexOption.IGNORE_CASE)

        private val NEGOTIATE_OPTION_REGEX = Regex(
            """R\$\s*(\d+[.,]\d+)""",
            RegexOption.IGNORE_CASE
        )

        private val BASE_RATE_REGEX = Regex(
            """R\$\s*(\d+[.,]\d+)\s+Tarifa\s+base""",
            RegexOption.IGNORE_CASE
        )
    }
}
