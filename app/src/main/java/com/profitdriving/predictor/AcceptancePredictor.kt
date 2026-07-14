package com.profitdriving.predictor

import com.profitdriving.DatabaseHelper
import com.profitdriving.L

class AcceptancePredictor(private val db: DatabaseHelper) {

    data class Recommendation(
        val shouldAccept: Boolean,
        val reason: String,
        val confidence: Float,
        val comparisonPpk: Double? = null,
        val avgPpk: Double? = null
    )

    fun evaluate(ride: com.profitdriving.RideData): Recommendation {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val recent = db.getRidesByDateRange(now - 7 * dayMs, now)
            .filter { it.appName == ride.appName && (it.pricePerKm ?: 0.0) > 0 }

        val avgPpk = if (recent.size >= 3) recent.mapNotNull { it.pricePerKm }.average() else 0.0

        if (avgPpk <= 0) return Recommendation(
            shouldAccept = true,
            reason = "Histórico insuficiente para análise",
            confidence = 0f
        )

        val currentPpk = ride.effectivePricePerKm ?: 0.0
        val diff = if (avgPpk > 0) ((currentPpk / avgPpk) - 1.0) * 100.0 else 0.0

        return when {
            diff > 15 -> Recommendation(
                shouldAccept = true,
                reason = "R$/km ${"%.0f".format(diff)}% acima da média!",
                confidence = 0.9f,
                comparisonPpk = currentPpk,
                avgPpk = avgPpk
            )
            diff > 5 -> Recommendation(
                shouldAccept = true,
                reason = "${"%.0f".format(diff)}% melhor que a média",
                confidence = 0.8f,
                comparisonPpk = currentPpk,
                avgPpk = avgPpk
            )
            diff > -5 -> Recommendation(
                shouldAccept = true,
                reason = "Na média (${"%.0f".format(diff)}%)",
                confidence = 0.6f,
                comparisonPpk = currentPpk,
                avgPpk = avgPpk
            )
            diff > -15 -> Recommendation(
                shouldAccept = false,
                reason = "${"%.0f".format(-diff)}% abaixo da média — pode surgir melhor",
                confidence = 0.7f,
                comparisonPpk = currentPpk,
                avgPpk = avgPpk
            )
            else -> Recommendation(
                shouldAccept = false,
                reason = "${"%.0f".format(-diff)}% abaixo da média — evite",
                confidence = 0.8f,
                comparisonPpk = currentPpk,
                avgPpk = avgPpk
            )
        }
    }
}
