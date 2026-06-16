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

    private val cachedSummaries = mutableMapOf<String, CostSummary>()
    private val lastUpdateTimes = mutableMapOf<String, Long>()
    private val CACHE_DURATION = 30 * 60 * 1000L

    suspend fun getCurrentSummary(context: Context, fuelType: String = "gasoline"): CostSummary {
        return withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val cacheKey = "summary_$fuelType"
            val lastUpdate = lastUpdateTimes[cacheKey] ?: 0L
            if (cachedSummaries[cacheKey] == null || System.currentTimeMillis() - lastUpdate > CACHE_DURATION) {
                val db = DatabaseHelper(appContext)
                val refuels = db.getRefuels()
                val expenses = db.getAllExpenses()
                val monthlyKm = db.getMonthlyKm()
                cachedSummaries[cacheKey] = CostCalculator.calculateCostSummary(
                    refuels, expenses, monthlyKm, currentFuelType = fuelType
                )
                lastUpdateTimes[cacheKey] = System.currentTimeMillis()
            }
            cachedSummaries[cacheKey]!!
        }
    }

    fun getCostBreakdown(summary: CostSummary): List<CostBreakdownItem> {
        val items = mutableListOf<CostBreakdownItem>()

        if (summary.fuelCostPerKm > 0) {
            val fuelMonthly = summary.fuelCostPerKm * 3000
            items.add(CostBreakdownItem(
                name = "Combust\u00EDvel",
                costPerKm = summary.fuelCostPerKm,
                monthlyCost = fuelMonthly,
                percentage = 0f,
                color = AppColors.accent
            ))
        }

        val categoryColors = listOf(
            AppColors.warning,
            0xFF8B5CF6.toInt(),
            0xFFEC4899.toInt(),
            0xFF14B8A6.toInt(),
            AppColors.warning,
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
        cachedSummaries.clear()
        lastUpdateTimes.clear()
    }
}
