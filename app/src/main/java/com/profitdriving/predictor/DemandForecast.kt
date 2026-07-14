package com.profitdriving.predictor

import com.profitdriving.DatabaseHelper
import com.profitdriving.L

class DemandForecast(private val db: DatabaseHelper) {

    data class HotspotResult(
        val neighborhood: String,
        val predictedOffers: Int,
        val confidence: Float
    )

    fun predictBestNeighborhoods(): List<HotspotResult> {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val recent = db.getRidesByDateRange(now - 30 * dayMs, now)
        if (recent.isEmpty()) return emptyList()

        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val dayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)

        val matching = recent.filter { ride ->
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = ride.timestamp }
            cal.get(java.util.Calendar.DAY_OF_WEEK) == dayOfWeek &&
            kotlin.math.abs(cal.get(java.util.Calendar.HOUR_OF_DAY) - currentHour) <= 2
        }

        val byNeighborhood = matching.groupBy { it.pickupAddress ?: "Desconhecido" }
            .mapValues { (_, rides) -> rides.size }
            .entries
            .sortedByDescending { it.value }

        val maxCount = byNeighborhood.firstOrNull()?.value?.toFloat() ?: 1f
        return byNeighborhood.take(5).map { (hood, count) ->
            HotspotResult(
                neighborhood = hood,
                predictedOffers = count,
                confidence = (count.toFloat() / matching.size.toFloat()).coerceAtMost(1f)
            )
        }
    }
}
