package com.profitdriving.predictor

import com.profitdriving.DatabaseHelper
import com.profitdriving.L
import java.util.Calendar

class EarningsPredictor(private val db: DatabaseHelper) {

    data class PredictionResult(
        val predictedEarnings: Double,
        val minEarnings: Double,
        val maxEarnings: Double,
        val confidence: Float,
        val factors: List<String>
    )

    fun predictTodayEarnings(): PredictionResult {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000

        val todayStart = now - (now % dayMs)
        val todayEnd = todayStart + dayMs

        val last30Days = db.getRidesByDateRange(todayStart - 30 * dayMs, todayStart)
        if (last30Days.isEmpty()) return PredictionResult(0.0, 0.0, 0.0, 0f, emptyList())

        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val sameDay = last30Days.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            cal.get(Calendar.DAY_OF_WEEK) == dayOfWeek
        }

        val baseline = sameDay.mapNotNull { it.value }.average()
        val allAvg = last30Days.mapNotNull { it.value }.average()
        val avg = if (sameDay.size >= 3) baseline else allAvg

        return PredictionResult(
            predictedEarnings = avg,
            minEarnings = avg * 0.7,
            maxEarnings = avg * 1.4,
            confidence = (sameDay.size.toFloat() / 30f).coerceAtMost(1f),
            factors = buildList {
                if (sameDay.size >= 3) add("Média de ${sameDay.size} ${
                    if (dayOfWeek == Calendar.SUNDAY) "domingos" else "dias de semana"
                } similares: R$ ${"%.2f".format(baseline)}")
                add("Média geral (30 dias): R$ ${"%.2f".format(allAvg)}")
            }
        )
    }

    fun predictBestHours(): List<HourPrediction> {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val recent = db.getRidesByDateRange(now - 30 * dayMs, now)
        if (recent.isEmpty()) return emptyList()

        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val sameDay = recent.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            cal.get(Calendar.DAY_OF_WEEK) == dayOfWeek
        }

        val byHour = sameDay.groupBy { ride ->
            Calendar.getInstance().apply { timeInMillis = ride.timestamp }.get(Calendar.HOUR_OF_DAY)
        }

        return (6..23).map { hour ->
            val rides = byHour[hour].orEmpty()
            val avgPpk = if (rides.isNotEmpty()) rides.mapNotNull { it.pricePerKm }.average() else 0.0
            val avgPph = if (rides.isNotEmpty()) rides.mapNotNull { it.pricePerHour }.average() else 0.0
            val count = rides.size

            HourPrediction(
                hour = hour,
                predictedPpk = avgPpk,
                predictedPph = avgPph,
                confidence = (count.toFloat() / sameDay.size.toFloat() * 10f).coerceAtMost(1f)
            )
        }
    }

    data class HourPrediction(
        val hour: Int,
        val predictedPpk: Double,
        val predictedPph: Double,
        val confidence: Float
    )
}
