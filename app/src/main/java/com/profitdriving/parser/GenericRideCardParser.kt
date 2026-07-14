package com.profitdriving.parser

import com.profitdriving.L
import com.profitdriving.RideData
import com.profitdriving.accessibility.extractor.CardType
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.accessibility.extractor.UberCardExtractor
import java.util.Locale

private val VALUE_REGEX get() = RideCardRegexes.value
private val PICKUP_REGEX get() = RideCardRegexes.pickup
private val VIAGEM_REGEX get() = RideCardRegexes.trip
private val DISTANCE_PATTERNS get() = RideCardRegexes.distanceFallback
private val TIME_PATTERNS get() = RideCardRegexes.timeFallback
private val RATING_STAR_REGEX get() = RideCardRegexes.ratingStar
private val RATING_COUNT_REGEX get() = RideCardRegexes.ratingCount
private val RATING_BULLET_REGEX get() = RideCardRegexes.ratingBullet
private val RATING_DECIMAL_REGEX get() = RideCardRegexes.ratingDecimal
private val SERVICE_TYPE_PATTERNS get() = SERVICE_TYPES
private val PRIORITY_BONUS_REGEX get() = RideCardRegexes.bonusPriority
private val DYNAMIC_BONUS_REGEX get() = RideCardRegexes.bonusDynamic
private val ADDRESS_AFTER_TIME_DIST get() = RideCardRegexes.addressLine
private val ADDRESS_TIME_DIST_NEW get() = RideCardRegexes.addressInline

class GenericRideCardParser : RideDataParser {

    override fun canParse(raw: RawCardData): Boolean {
        if (raw.cardType == CardType.APP_99 ||
            raw.cardType == CardType.APP_99_NEGOTIATE ||
            raw.cardType == CardType.APP_99_PRIORITY) return false
        val full = raw.rawTexts.joinToString(" ").lowercase(Locale.ROOT)
        val hasMoney = full.contains("r$")
        val hasKm = full.contains("km")
        val hasMin = full.contains("min")
        return hasMoney && (hasKm || hasMin)
    }

    override fun parse(raw: RawCardData): RideData? {
        L.d(TAG, "GenericRideCardParser.parse() iniciado")
        val text = raw.fullText

        val value = extractValue(text)
        if (value == null || value <= 0) {
            L.d(TAG, "Valor inválido — abortando")
            return null
        }

        var distance = extractDistance(text)
        var timeMin = extractTime(text)

        if (distance == null || timeMin == null) {
            L.d(TAG, "Estrutura primária falhou (dist=$distance time=$timeMin), tentando fallback numérico")
            val numeric = extractNumericFallback(text)
            if (distance == null) distance = numeric.first
            if (timeMin == null) timeMin = numeric.second
        }

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

        L.d(TAG, "GenericRideCardParser parsed: value=$value km=$distance tempo=$timeMin nota=$rating")

        if ((distance == null || distance <= 0) && (timeMin == null || timeMin <= 0)) {
            L.d(TAG, "Sem distância nem tempo — ride inválido")
            return null
        }

        return RideData(
            value = value,
            distanceKm = distance,
            timeMin = timeMin,
            rating = rating,
            appName = "Uber",
            pricePerKm = distance?.let { d ->
                if (d > 0) value / d else null
            },
            pricePerHour = timeMin?.let { t ->
                if (t > 0) value / (t / 60.0) else null
            },
            detectedBy = "generic_ocr",
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

    private fun extractNumericFallback(text: String): Pair<Double?, Int?> {
        val distCandidates = mutableListOf<Double>()
        for (pat in DISTANCE_PATTERNS) {
            for (m in pat.findAll(text)) {
                val v = UberCardExtractor.parseBr(m.groupValues[1])
                if (v != null && v > 0 && v < 200) distCandidates.add(v)
            }
        }
        if (distCandidates.isEmpty()) {
            val raw = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).findAll(text)
                .mapNotNull { UberCardExtractor.parseBr(it.groupValues[1]) }
                .filter { it > 0 && it < 200 }
                .toList()
            distCandidates.addAll(raw)
        }
        val bestDist = distCandidates.maxOrNull()

        val timeCandidates = mutableListOf<Int>()
        for (pat in TIME_PATTERNS) {
            for (m in pat.findAll(text)) {
                val v = m.groupValues[1].toIntOrNull()
                if (v != null && v > 0 && v < 600) timeCandidates.add(v)
            }
        }
        if (timeCandidates.isEmpty()) {
            val rawMin = Regex("""(\d+)\s*min(?:uto)?s?""", RegexOption.IGNORE_CASE).findAll(text)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it > 0 && it < 600 }
                .toList()
            timeCandidates.addAll(rawMin)
        }
        val bestTime = timeCandidates.maxOrNull()

        if (bestDist == null && bestTime == null) return Pair(null, null)

        L.d(TAG, "Fallback numérico: dist=$bestDist (de $distCandidates) time=$bestTime (de $timeCandidates)")
        return Pair(bestDist, bestTime)
    }

    private fun extractValue(text: String): Double? {
        val matches = VALUE_REGEX.findAll(text)
            .mapNotNull { UberCardExtractor.parseBr(it.groupValues[1]) }
            .filter { it > 1.0 }
            .toList()
        if (matches.isEmpty()) return null
        return matches.max()
    }

    private fun extractDistance(text: String): Double? {
        var total = 0.0

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
        val allMatches = PICKUP_REGEX.findAll(text).toList()
        if (allMatches.isNotEmpty()) {
            val v = UberCardExtractor.parseBr(allMatches[0].groupValues[2])
            if (v != null && v > 0 && v < 50) return v
        }
        return null
    }

    private fun extractPickupTime(text: String): Int? {
        val allMatches = PICKUP_REGEX.findAll(text).toList()
        if (allMatches.isNotEmpty()) {
            val v = allMatches[0].groupValues[1].toIntOrNull()
            if (v != null && v > 0) return v
        }
        return null
    }

    private fun extractTripDistance(text: String): Double? {
        val allMatches = VIAGEM_REGEX.findAll(text).toList()
        if (allMatches.size >= 2) {
            val v = UberCardExtractor.parseBr(allMatches[1].groupValues[3])
            if (v != null && v > 0 && v < 200) return v
        } else if (allMatches.size == 1) {
            val v = UberCardExtractor.parseBr(allMatches[0].groupValues[3])
            if (v != null && v > 0 && v < 200) return v
        }
        return null
    }

    private fun extractTripTime(text: String): Int? {
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
        return Regex("""\b(?:\d+\s+)?(?:várias\s+)?paradas?\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerText)
    }

    private fun extractAddresses(text: String): Pair<String?, String?> {
        var pickupAddress: String? = null
        var dropoffAddress: String? = null

        val lineMatches = ADDRESS_AFTER_TIME_DIST.findAll(text).toList()
        if (lineMatches.isNotEmpty()) {
            pickupAddress = cleanupAddress(lineMatches[0].groupValues[1].trim())
            if (lineMatches.size > 1) {
                dropoffAddress = cleanupAddress(lineMatches.last().groupValues[1].trim())
            }
        }

        if (pickupAddress == null || dropoffAddress == null) {
            val legacyMatches = ADDRESS_TIME_DIST_NEW.findAll(text).toList()
            if (legacyMatches.isNotEmpty()) {
                if (pickupAddress == null) {
                    pickupAddress = cleanupAddress(legacyMatches[0].groupValues[1].trim())
                }
                if (dropoffAddress == null && legacyMatches.size > 1) {
                    dropoffAddress = cleanupAddress(legacyMatches[1].groupValues[1].trim())
                }
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
        private const val TAG = "GenericRideCardParser"
    }
}