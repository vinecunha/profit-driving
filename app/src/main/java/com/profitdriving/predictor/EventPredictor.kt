package com.profitdriving.predictor

import com.profitdriving.DatabaseHelper
import com.profitdriving.L
import java.util.Calendar

class EventPredictor(private val db: DatabaseHelper) {

    data class DemandEvent(
        val date: Long,
        val type: String,
        val expectedMultiplier: Double,
        val description: String,
        val confidence: Float
    )

    fun predictUpcomingEvents(): List<DemandEvent> {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val events = mutableListOf<DemandEvent>()

        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

        val historical = db.getRidesByDateRange(now - 60 * dayMs, now)
        val byDayOfWeek = historical.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }
                .get(Calendar.DAY_OF_WEEK)
        }

        val avgWeekday = historical.filter { it.timestamp % dayMs < 5 * dayMs }
            .mapNotNull { it.value }.average().coerceAtLeast(1.0)
        val avgWeekend = historical.filter { it.timestamp % dayMs >= 5 * dayMs }
            .mapNotNull { it.value }.average().coerceAtLeast(1.0)

        val ratio = avgWeekend / avgWeekday
        if (ratio > 1.2 && (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)) {
            events.add(DemandEvent(
                date = now,
                type = "Fim de semana",
                expectedMultiplier = ratio,
                description = "Final de semana — média ${"%.0f".format((ratio - 1) * 100)}% maior que dias úteis",
                confidence = 0.8f
            ))
        }

        return events.sortedBy { it.date }
    }
}
