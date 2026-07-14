package com.profitdriving.predictor

import com.profitdriving.DatabaseHelper
import com.profitdriving.RideData
import java.util.Calendar

enum class OfferClassification {
    EXCELLENT, GOOD, AVERAGE, BELOW_AVERAGE, POOR
}

data class OfferAnalysis(
    val historicalAvg: Double,
    val currentPpk: Double,
    val diffPercent: Double,
    val classification: OfferClassification,
    val potentialExtra: Double,
    val historicalRidesCount: Int,
    val hourSlot: String,
    val app: String
)

class OfferAnalyzer(private val db: DatabaseHelper) {

    companion object {
        private const val WINDOW_HOURS = 72L
        private const val HOUR_RANGE = 2
    }

    fun analyze(ride: RideData): OfferAnalysis? {
        val currentPpk = ride.effectivePricePerKm ?: return null
        if (currentPpk <= 0) return null

        val now = System.currentTimeMillis()
        val since = now - WINDOW_HOURS * 60 * 60 * 1000

        val recent = db.getRidesByDateRange(since, now)
            .filter { it.appName == ride.appName }

        val rideHour = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
            .get(Calendar.HOUR_OF_DAY)

        val sameSlot = recent.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            h >= (rideHour - HOUR_RANGE) && h <= (rideHour + HOUR_RANGE)
        }.mapNotNull { it.pricePerKm }.filter { it > 0 }

        if (sameSlot.size < 2) return null

        val historicalAvg = sameSlot.average()
        val diffPercent = ((currentPpk / historicalAvg) - 1.0) * 100.0
        val distanceKm = ride.distanceKm ?: ride.tripDistanceKm ?: 0.0
        val potentialExtra = (currentPpk - historicalAvg) * distanceKm

        val classification = when {
            diffPercent > 15 -> OfferClassification.EXCELLENT
            diffPercent > 5 -> OfferClassification.GOOD
            diffPercent > -5 -> OfferClassification.AVERAGE
            diffPercent > -15 -> OfferClassification.BELOW_AVERAGE
            else -> OfferClassification.POOR
        }

        return OfferAnalysis(
            historicalAvg = historicalAvg,
            currentPpk = currentPpk,
            diffPercent = diffPercent,
            classification = classification,
            potentialExtra = potentialExtra,
            historicalRidesCount = sameSlot.size,
            hourSlot = "${rideHour.coerceAtLeast(0)}h",
            app = ride.appName
        )
    }

    /**
     * Returns average R$/km for a given app and hour range over the last 72h.
     * Used by the AnalysisActivity card for the hourly breakdown table.
     */
    data class HourSlot(
        val startHour: Int,
        val endHour: Int,
        val label: String,
        val avgPpk: Double,
        val count: Int
    )

    fun getHourSlotAverages(app: String): List<HourSlot> {
        val now = System.currentTimeMillis()
        val since = now - WINDOW_HOURS * 60 * 60 * 1000
        val rides = db.getRidesByDateRange(since, now)
            .filter { it.appName == app }

        val slots = listOf(
            0 to 5, 6 to 7, 8 to 9, 10 to 11,
            12 to 13, 14 to 15, 16 to 17, 18 to 19, 20 to 23
        )

        return slots.map { (start, end) ->
            val filtered = rides.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                val h = cal.get(Calendar.HOUR_OF_DAY)
                h in start..end
            }.mapNotNull { it.pricePerKm }.filter { it > 0 }

            HourSlot(
                startHour = start,
                endHour = end,
                label = "${start}h-${end}h",
                avgPpk = if (filtered.isNotEmpty()) filtered.average() else 0.0,
                count = filtered.size
            )
        }
    }
}
