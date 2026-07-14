package com.profitdriving.predictor

import com.profitdriving.DatabaseHelper
import com.profitdriving.L

class MarketTrendPredictor(private val db: DatabaseHelper) {

    data class TrendResult(
        val app: String,
        val currentAvg: Double,
        val trendDirection: String,
        val trendMagnitude: Double,
        val forecast7Days: List<Double>,
        val confidence: Float
    )

    fun predictTrend(app: String): TrendResult {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val history = db.getRidesByDateRange(now - 30 * dayMs, now)
            .filter { it.appName.equals(app, ignoreCase = true) }

        if (history.isEmpty()) return TrendResult(app, 0.0, "stable", 0.0, emptyList(), 0f)

        val ppkByDay = history.groupBy { it.timestamp / dayMs }
            .mapValues { (_, rides) -> rides.mapNotNull { it.pricePerKm }.average() }
            .entries
            .sortedBy { it.key }
            .map { it.value }

        val avg = ppkByDay.average()
        val trend = if (ppkByDay.size >= 3) {
            val n = ppkByDay.size
            val xMean = (n - 1) / 2.0
            val yMean = ppkByDay.average()
            val num = ppkByDay.mapIndexed { i, y -> (i - xMean) * (y - yMean) }.sum()
            val den = ppkByDay.mapIndexed { i, _ -> (i - xMean) * (i - xMean) }.sum()
            if (den != 0.0) num / den else 0.0
        } else 0.0

        val forecast = (1..7).map { daysAhead -> avg + trend * daysAhead }

        return TrendResult(
            app = app,
            currentAvg = avg,
            trendDirection = if (trend > 0.001) "up" else if (trend < -0.001) "down" else "stable",
            trendMagnitude = kotlin.math.abs(trend),
            forecast7Days = forecast,
            confidence = (ppkByDay.size.toFloat() / 30f).coerceAtMost(1f)
        )
    }
}
