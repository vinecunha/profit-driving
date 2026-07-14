package com.profitdriving.predictor

import com.profitdriving.DatabaseHelper
import com.profitdriving.L

class OnlinePredictor(private val db: DatabaseHelper) {

    data class OnlineWindow(
        val startHour: Int,
        val predictedDurationMin: Int,
        val confidence: Float
    )

    fun predictOnlineWindows(): List<OnlineWindow> {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val recent = db.getRidesByDateRange(now - 30 * dayMs, now)
        if (recent.isEmpty()) return emptyList()

        val dayOfWeek = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        val sameDay = recent.filter {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }
            cal.get(java.util.Calendar.DAY_OF_WEEK) == dayOfWeek
        }

        val byHour = sameDay.groupBy {
            java.util.Calendar.getInstance().apply { timeInMillis = it.timestamp }
                .get(java.util.Calendar.HOUR_OF_DAY)
        }

        val windows = mutableListOf<OnlineWindow>()
        var i = 0
        while (i <= 23) {
            val rides = byHour[i].orEmpty()
            if (rides.size >= 2) {
                var duration = 1
                var j = i + 1
                while (j <= 23 && (byHour[j]?.size ?: 0) >= 2) {
                    duration++
                    j++
                }
                val avgDuration = duration * 60
                windows.add(OnlineWindow(
                    startHour = i,
                    predictedDurationMin = avgDuration,
                    confidence = (rides.size.toFloat() / sameDay.size.toFloat() * 5f).coerceAtMost(1f)
                ))
                i = j
            } else {
                i++
            }
        }

        return windows
    }
}
