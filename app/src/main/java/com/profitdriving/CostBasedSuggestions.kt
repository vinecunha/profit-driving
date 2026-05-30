package com.profitdriving

object CostBasedSuggestions {

    data class Suggestions(
        val minKm: Double,
        val idealKm: Double,
        val minHour: Double,
        val idealHour: Double,
        val minMinute: Double,
        val idealMinute: Double,
        val minRating: Double,
        val idealRating: Double,
        val explanation: String
    )

    fun calculateSuggestions(costPerKm: Double, costPerHour: Double, costPerMinute: Double): Suggestions {
        val breakEvenKm = costPerKm

        val minKm = roundToTwoDecimals(breakEvenKm * 1.15)
        val idealKm = roundToTwoDecimals(breakEvenKm * 1.50)

        val minHour = roundToTwoDecimals(costPerHour * 1.15)
        val idealHour = roundToTwoDecimals(costPerHour * 1.50)

        val minMinute = roundToTwoDecimals(costPerMinute * 1.15)
        val idealMinute = roundToTwoDecimals(costPerMinute * 1.50)

        val minRating = 4.5
        val idealRating = 4.9

        val explanation = buildString {
            append("Baseado no seu custo total de R$ ${String.format("%.2f", costPerKm)}/km:\n")
            append("• R$ ${String.format("%.2f", minKm)}/km = margem de 15% (mínimo aceitável)\n")
            append("• R$ ${String.format("%.2f", idealKm)}/km = margem de 50% (ideal)")
        }

        return Suggestions(minKm, idealKm, minHour, idealHour, minMinute, idealMinute, minRating, idealRating, explanation)
    }

    private fun roundToTwoDecimals(value: Double): Double {
        return Math.round(value * 100) / 100.0
    }
}
