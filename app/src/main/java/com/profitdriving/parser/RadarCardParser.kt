package com.profitdriving.parser

import com.profitdriving.L
import com.profitdriving.RideData
import com.profitdriving.accessibility.extractor.CardType
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.accessibility.extractor.UberCardExtractor
import java.util.Locale

// ─── Aliases para RideCardRegexes ───
private val VALUE_REGEX get() = RideCardRegexes.value
private val KM_PER_REAL_REGEX get() = RideCardRegexes.kmPerReal
private val KM_REAL_REGEX get() = RideCardRegexes.kmReal
private val HOUR_REAL_REGEX get() = RideCardRegexes.hourReal
private val PICKUP_REGEX get() = RideCardRegexes.pickup
private val PICKUP_REGEX_OLD get() = RideCardRegexes.pickupOld
private val VIAGEM_REGEX get() = RideCardRegexes.trip
private val VIAGEM_REGEX_OLD get() = RideCardRegexes.tripOld
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
private val PICKUP_ADDRESS_REGEX_OLD get() = RideCardRegexes.addressPickupOld
private val DROPOFF_ADDRESS_REGEX_OLD get() = RideCardRegexes.addressDropoffOld

class RadarCardParser : RideDataParser {

    override fun canParse(raw: RawCardData): Boolean {
        if (raw.cardType == CardType.RADAR) return true
        if (raw.cardType == CardType.APP_99) return false
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
        // Normaliza: "R$ 8 52" → "R$ 8.52" (OCR perdeu vírgula)
        val normalized = text.replace(
            Regex("""R\$\s*(\d{1,3})\s+(\d{2})(?!\s*\d)""", RegexOption.IGNORE_CASE)
        ) { match ->
            val reais = match.groupValues[1]
            val centavos = match.groupValues[2]
            "${'$'}$reais.$centavos"
        }
        val matches = VALUE_REGEX.findAll(normalized)
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
        return Regex("""\b(?:\d+\s+)?(?:várias\s+)?paradas?\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerText)
    }

    private fun extractAddresses(text: String): Pair<String?, String?> {
        var pickupAddress: String? = null
        var dropoffAddress: String? = null

        // 1. Formato linhas separadas — endereço na linha APÓS o par tempo-distância
        val lineMatches = ADDRESS_AFTER_TIME_DIST.findAll(text).toList()
        if (lineMatches.isNotEmpty()) {
            pickupAddress = cleanupAddress(lineMatches[0].groupValues[1].trim())
            L.d(TAG, "Endereço de embarque (linha): $pickupAddress")
            if (lineMatches.size > 1) {
                dropoffAddress = cleanupAddress(lineMatches.last().groupValues[1].trim())
                L.d(TAG, "Endereço de destino (linha): $dropoffAddress")
            }
        }

        // 2. Formato legado (sem CEP/UF)
        if (pickupAddress == null || dropoffAddress == null) {
            val legacyMatches = ADDRESS_TIME_DIST_NEW.findAll(text).toList()
            if (legacyMatches.isNotEmpty()) {
                if (pickupAddress == null) {
                    pickupAddress = cleanupAddress(legacyMatches[0].groupValues[1].trim())
                    L.d(TAG, "Endereço de embarque (legado): $pickupAddress")
                }
                if (dropoffAddress == null && legacyMatches.size > 1) {
                    dropoffAddress = cleanupAddress(legacyMatches[1].groupValues[1].trim())
                    L.d(TAG, "Endereço de destino (legado): $dropoffAddress")
                }
            }
        }

        // 3. Fallback: formato antigo (com "de distância" e/ou CEP)
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
    }
}
