package com.profitdriving.parser

import com.profitdriving.L
import com.profitdriving.RideData

enum class CardTier(val displayName: String, val minScore: Int) {
    COMPLETE("Completo", 80),
    STANDARD("Padrão", 60),
    MINIMAL("Mínimo", 40),
    BROKEN("Quebrado", 20),
    GARBAGE("Lixo", 0)
}

data class CardClassification(
    val tier: CardTier,
    val score: Int,
    val rideData: RideData,
    val details: List<String>,
    val corrections: List<String>,
    val isCorrected: Boolean,
    val missingFields: List<String>,
    val suspiciousPatterns: List<String>
)

class CardClassificationEngine {

    fun classify(ride: RideData, validCropCount: Int = 0): CardClassification {
        val details = mutableListOf<String>()
        val corrections = mutableListOf<String>()
        val missingFields = mutableListOf<String>()
        val suspiciousPatterns = mutableListOf<String>()
        var points = 0
        val maxPoints = 100

        var data = ride

        val value = data.value ?: 0.0
        val distance = data.distanceKm ?: data.tripDistanceKm ?: data.pickupDistanceKm ?: 0.0
        val time = data.timeMin ?: data.tripTimeMin ?: data.pickupTimeMin ?: 0

        val hasTripDistance = (data.tripDistanceKm ?: 0.0) > 0
        val hasTripTime = (data.tripTimeMin ?: 0) > 0

        if (value > 0) {
            if (value > 1.0) {
                points += 20
                details.add("+ R$ ${"%.2f".format(value)}")
            } else if (distance > 1.0) {
                data = data.copy(value = value * 10)
                corrections.add("R$ ${"%.2f".format(value)} → R$ ${"%.2f".format(data.value)}")
                points += 15
                details.add("+ R$ ${"%.2f".format(data.value)} (corrigido)")
            } else {
                points += 5
                details.add("? R$ ${"%.2f".format(value)} (muito baixo)")
            }
        } else {
            missingFields.add("valor")
            details.add("- sem valor")
        }

        if (distance > 0) {
            if (distance in 0.3..200.0) {
                points += 20
                details.add("+ distância ${"%.1f".format(distance)}km")
            } else if (distance < 0.3) {
                val newDist = distance * 10
                if (newDist in 0.5..50.0) {
                    data = data.copy(
                        distanceKm = newDist,
                        tripDistanceKm = if (data.tripDistanceKm != null && data.tripDistanceKm == distance) newDist else data.tripDistanceKm,
                        pickupDistanceKm = if (data.pickupDistanceKm != null && data.pickupDistanceKm == distance) newDist else data.pickupDistanceKm
                    )
                    corrections.add("distância ${"%.1f".format(distance)}km → ${"%.1f".format(newDist)}km")
                    points += 15
                    details.add("+ distância ${"%.1f".format(newDist)}km (corrigido)")
                } else {
                    points += 5
                    suspiciousPatterns.add("distância muito baixa: ${"%.1f".format(distance)}km")
                    details.add("? distância ${"%.1f".format(distance)}km (suspeita)")
                }
            } else {
                suspiciousPatterns.add("distância irreal: ${"%.1f".format(distance)}km")
                details.add("? distância ${"%.1f".format(distance)}km (suspeita)")
            }
        } else {
            missingFields.add("distância")
            details.add("- sem distância")
        }

        if (time > 0) {
            if (time in 1..480) {
                points += 20
                details.add("+ tempo ${time}min")
            } else {
                suspiciousPatterns.add("tempo irreal: ${time}min")
                points += 5
                details.add("? tempo ${time}min (suspeito)")
            }
        } else {
            missingFields.add("tempo")
            details.add("- sem tempo")
        }

        if (data.rating != null && data.rating in 1.0..5.0) {
            points += 10
            details.add("+ avaliação ${data.rating}")
        } else {
            details.add("- sem avaliação")
        }

        if (!data.pickupAddress.isNullOrBlank() && data.pickupAddress.length > 3) {
            points += 5
            details.add("+ endereço embarque")
        } else {
            details.add("- sem endereço embarque")
        }

        if (!data.dropoffAddress.isNullOrBlank() && data.dropoffAddress.length > 3) {
            points += 5
            details.add("+ endereço destino")
        } else {
            details.add("- sem endereço destino")
        }

        if (value > 0 && distance > 0) {
            val pricePerKm = value / distance
            if (pricePerKm in 0.5..15.0) {
                points += 10
                details.add("+ R$/km ${"%.2f".format(pricePerKm)}")
            } else {
                suspiciousPatterns.add("R$/km fora da faixa: ${"%.2f".format(pricePerKm)}")
                details.add("? R$/km ${"%.2f".format(pricePerKm)} (fora da faixa)")
            }
        }

        if (distance > 0 && time > 0) {
            val speed = distance / (time / 60.0)
            if (speed in 5.0..120.0) {
                points += 5
                details.add("+ velocidade ${"%.0f".format(speed)}km/h")
            } else {
                suspiciousPatterns.add("velocidade irreal: ${"%.0f".format(speed)}km/h")
                details.add("? velocidade ${"%.0f".format(speed)}km/h (suspeita)")
            }
        }

        if (validCropCount >= 2) {
            points += 5
            details.add("+ $validCropCount crops")
        }

        val score = points.coerceAtMost(maxPoints)
        val tier = determineTier(score)

        if (tier <= CardTier.BROKEN) {
            L.w(TAG, "Card classificado como ${tier.displayName} (${score}%): ${missingFields.joinToString(", ")} ${suspiciousPatterns.joinToString("; ")}")
        }

        return CardClassification(
            tier = tier,
            score = score,
            rideData = data,
            details = details,
            corrections = corrections,
            isCorrected = corrections.isNotEmpty(),
            missingFields = missingFields,
            suspiciousPatterns = suspiciousPatterns
        )
    }

    private fun determineTier(score: Int): CardTier {
        return CardTier.entries.firstOrNull { score >= it.minScore } ?: CardTier.GARBAGE
    }

    companion object {
        private const val TAG = "CardClassification"
    }
}

operator fun CardTier.compareTo(other: CardTier): Int {
    return this.minScore.compareTo(other.minScore)
}
