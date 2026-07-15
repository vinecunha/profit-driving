package com.profitdriving.predictor

import com.profitdriving.CostCalculator
import com.profitdriving.DatabaseHelper
import com.profitdriving.L
import com.profitdriving.RideData
import java.util.Calendar

data class EnhancedOfferAnalysis(
    val currentPpk: Double,
    val diffPercent: Double,
    val classification: OfferClassification,
    val percentile: Int,
    val bestToday: Double,
    val worstToday: Double,
    val avgToday: Double,
    val chanceBetterIn15min: Int,
    val netProfit: Double,
    val costPerKm: Double,
    val sameServiceAvg: Double,
    val sameServiceDiff: Double,
    val estimatedHourlyEarning: Double,
    val avgHourlyEarning: Double,
    val historicalAvg: Double,
    val potentialExtra: Double,
    val historicalRidesCount: Int
)

class EnhancedOfferAnalyzer(private val db: DatabaseHelper) {

    companion object {
        private const val TAG = "EnhancedOfferAnalyzer"
        private const val WINDOW_HOURS = 72L
        private const val HOUR_RANGE = 2
    }

    fun analyze(ride: RideData): EnhancedOfferAnalysis? {
        val currentPpk = ride.effectivePricePerKm ?: return null
        if (currentPpk <= 0) return null

        return try {
            val now = System.currentTimeMillis()
            val since72h = now - WINDOW_HOURS * 60 * 60 * 1000

            val recentRides = db.getRidesByDateRange(since72h, now)
                .filter { it.appName == ride.appName }

            val rideHour = Calendar.getInstance().apply { timeInMillis = now }
                .get(Calendar.HOUR_OF_DAY)

            val sameSlot = recentRides.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                val h = cal.get(Calendar.HOUR_OF_DAY)
                h >= (rideHour - HOUR_RANGE) && h <= (rideHour + HOUR_RANGE)
            }.mapNotNull { it.pricePerKm }.filter { it > 0 }

            val historicalAvg = if (sameSlot.size >= 2) sameSlot.average()
            else currentPpk

            val diffPercent = if (historicalAvg > 0)
                ((currentPpk - historicalAvg) / historicalAvg) * 100.0
            else 0.0

            val distanceKm = (ride.distanceKm ?: ride.tripDistanceKm ?: 0.0)
            val potentialExtra = (currentPpk - historicalAvg) * distanceKm

            val classification = when {
                diffPercent > 15 -> OfferClassification.EXCELLENT
                diffPercent > 5 -> OfferClassification.GOOD
                diffPercent > -5 -> OfferClassification.AVERAGE
                diffPercent > -15 -> OfferClassification.BELOW_AVERAGE
                else -> OfferClassification.POOR
            }

            val percentile = calculatePercentile(currentPpk, recentRides)
            val todayStats = getTodayStats(recentRides)
            val chanceBetter = getChanceBetterIn15min(recentRides)
            val costPerKm = getCostPerKm()
            val netProfit = ride.value?.let { v ->
                v - (distanceKm * costPerKm)
            } ?: 0.0
            val serviceAvg = getServiceAverage(ride.appName, ride.serviceType)
            val serviceDiff = if (serviceAvg > 0)
                ((currentPpk - serviceAvg) / serviceAvg) * 100.0
            else 0.0
            val estimatedHourly = currentPpk * 25.0
            val avgHourly = getAverageHourly(recentRides)

            EnhancedOfferAnalysis(
                currentPpk = currentPpk,
                diffPercent = diffPercent,
                classification = classification,
                percentile = percentile,
                bestToday = todayStats.best,
                worstToday = todayStats.worst,
                avgToday = todayStats.avg,
                chanceBetterIn15min = chanceBetter,
                netProfit = netProfit,
                costPerKm = costPerKm,
                sameServiceAvg = serviceAvg,
                sameServiceDiff = serviceDiff,
                estimatedHourlyEarning = estimatedHourly,
                avgHourlyEarning = avgHourly,
                historicalAvg = historicalAvg,
                potentialExtra = potentialExtra,
                historicalRidesCount = sameSlot.size
            )
        } catch (e: Exception) {
            L.e(TAG, "Erro na análise enriquecida", e)
            null
        }
    }

    private fun calculatePercentile(currentPpk: Double, rides: List<com.profitdriving.RideRecord>): Int {
        val ppkList = rides.mapNotNull { it.pricePerKm }.filter { it > 0 }
        if (ppkList.isEmpty()) return 50
        val sorted = ppkList.sorted()
        val index = sorted.binarySearch(currentPpk)
        val position = if (index < 0) -(index + 1) else index + 1
        return ((position.toDouble() / sorted.size) * 100).toInt().coerceIn(0, 100)
    }

    private data class TodayStats(val best: Double, val worst: Double, val avg: Double)

    private fun getTodayStats(rides: List<com.profitdriving.RideRecord>): TodayStats {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val today = rides.filter { it.timestamp >= todayStart }
            .mapNotNull { it.pricePerKm }.filter { it > 0 }

        return TodayStats(
            best = today.maxOrNull() ?: 0.0,
            worst = today.minOrNull() ?: 0.0,
            avg = if (today.isNotEmpty()) today.average() else 0.0
        )
    }

    private fun getChanceBetterIn15min(rides: List<com.profitdriving.RideRecord>): Int {
        val sorted = rides.sortedBy { it.timestamp }
        if (sorted.size < 2) return 50

        var betterCount = 0
        for (i in 1 until sorted.size) {
            val prevPpk = sorted[i - 1].pricePerKm ?: continue
            val currPpk = sorted[i].pricePerKm ?: continue
            if (prevPpk <= 0 || currPpk <= 0) continue
            if (currPpk > prevPpk) betterCount++
        }
        return ((betterCount.toDouble() / (sorted.size - 1)) * 100).toInt().coerceIn(0, 100)
    }

    private fun getCostPerKm(): Double {
        return try {
            val refuels = db.getRefuels()
            val expenses = db.getAllExpenses()
            val monthlyKm = db.getMonthlyKm()
            CostCalculator.calculateCostSummary(refuels, expenses, monthlyKm).totalCostPerKm
        } catch (e: Exception) {
            L.e(TAG, "Erro ao obter custo/km", e)
            2.5
        }
    }

    private fun getServiceAverage(app: String, serviceType: String?): Double {
        if (serviceType == null) return 0.0
        val since = System.currentTimeMillis() - WINDOW_HOURS * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val rides = db.getRidesByDateRange(since, now)
            .filter { it.appName == app && it.serviceType == serviceType }
            .mapNotNull { it.pricePerKm }.filter { it > 0 }
        return if (rides.isNotEmpty()) rides.average() else 0.0
    }

    private fun getAverageHourly(rides: List<com.profitdriving.RideRecord>): Double {
        val hourly = rides.mapNotNull {
            if (it.timeMin != null && it.timeMin!! > 0 && it.value != null)
                it.value!! / (it.timeMin!! / 60.0)
            else null
        }
        return if (hourly.isNotEmpty()) hourly.average() else 0.0
    }
}
