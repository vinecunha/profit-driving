package com.profitdriving

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    fun invalidate() {
        cachedSummary = null
        lastUpdateTime = 0L
    }
}
