package com.profitdriving.predictor

import com.profitdriving.DatabaseHelper
import com.profitdriving.L

class CostPredictor(private val db: DatabaseHelper) {

    data class CostPrediction(
        val predictedFuelCost: Double,
        val predictedFixedCost: Double,
        val predictedTotalCostPerKm: Double,
        val confidence: Float,
        val factors: List<String>
    )

    fun predictNextMonthCosts(): CostPrediction {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000

        val recentRides = db.getRidesByDateRange(now - 30 * dayMs, now)
        val totalKm = recentRides.sumOf { it.distanceKm ?: 0.0 }
        val avgKmPerMonth = totalKm.coerceAtLeast(1.0)

        val refuels = try {
            db.getRefuels()
        } catch (_: Exception) { emptyList<com.profitdriving.RefuelRecord>() }

        val fuelCost = if (refuels.isNotEmpty()) {
            val total = refuels.sumOf { it.totalValue }
            total / refuels.size * 30.0
        } else 0.0

        val fixedCost = try {
            val expenses = db.getExpenses()
            expenses.filter { it.periodicity == "monthly" }.sumOf { it.value } +
            expenses.filter { it.periodicity == "yearly" }.sumOf { it.value / 12.0 }
        } catch (_: Exception) { 0.0 }

        val totalCostPerKm = if (avgKmPerMonth > 0) (fuelCost + fixedCost) / avgKmPerMonth else 0.0

        return CostPrediction(
            predictedFuelCost = fuelCost,
            predictedFixedCost = fixedCost,
            predictedTotalCostPerKm = totalCostPerKm,
            confidence = if (refuels.isNotEmpty() && recentRides.size >= 10) 0.8f else 0.4f,
            factors = buildList {
                if (refuels.isNotEmpty()) add("Baseado em ${refuels.size} abastecimentos")
                if (recentRides.size >= 10) add("${recentRides.size} corridas no período")
            }
        )
    }
}
