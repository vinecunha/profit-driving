package com.profitdriving

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CostBreakdownItem(
    val name: String,
    val costPerKm: Double,
    val monthlyCost: Double,
    val percentage: Float,
    val color: Int
)

object CostSummaryCache {

    private var cachedSummary: CostSummary? = null
    private var lastUpdateTime = 0L
    private val CACHE_DURATION = 30 * 60 * 1000L

    suspend fun getCurrentSummary(context: Context): CostSummary {
        return withContext(Dispatchers.IO) {
            if (cachedSummary == null || System.currentTimeMillis() - lastUpdateTime > CACHE_DURATION) {
                val db = DatabaseHelper(context)
                val refuels = db.getRefuels()
                val expenses = db.getAllExpenses()
                val monthlyKm = db.getMonthlyKm()
                cachedSummary = CostCalculator.calculateCostSummary(refuels, expenses, monthlyKm)
                lastUpdateTime = System.currentTimeMillis()
            }
            cachedSummary!!
        }
    }

    fun getCostBreakdown(summary: CostSummary): List<CostBreakdownItem> {
        val items = mutableListOf<CostBreakdownItem>()

        if (summary.fuelCostPerKm > 0) {
            val fuelMonthly = summary.fuelCostPerKm * 3000
            items.add(CostBreakdownItem(
                name = "Combustível",
                costPerKm = summary.fuelCostPerKm,
                monthlyCost = fuelMonthly,
                percentage = 0f,
                color = 0xFF3B82F6.toInt()
            ))
        }

        val categoryColors = listOf(
            0xFFF97316.toInt(),
            0xFF8B5CF6.toInt(),
            0xFFEC4899.toInt(),
            0xFF14B8A6.toInt(),
            0xFFF59E0B.toInt(),
            0xFF6366F1.toInt(),
            0xFF84CC16.toInt(),
            0xFF06B6D4.toInt()
        )

        for ((i, exp) in summary.normalizedExpenses.withIndex()) {
            val color = categoryColors[i % categoryColors.size]
            items.add(CostBreakdownItem(
                name = "${exp.category}: ${exp.name}",
                costPerKm = exp.costPerKm,
                monthlyCost = exp.monthlyCost,
                percentage = 0f,
                color = color
            ))
        }

        val totalCpk = summary.totalCostPerKm
        return items.map { item ->
            item.copy(percentage = if (totalCpk > 0) (item.costPerKm / totalCpk).toFloat() else 0f)
        }
    }

    fun invalidate() {
        cachedSummary = null
        lastUpdateTime = 0L
    }
}
