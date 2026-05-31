package com.profitdriving

import java.util.Calendar

data class BonusImpact(
    val count: Int,
    val percentage: Double,
    val avgPricePerKm: Double,
    val avgPricePerKmWithout: Double,
    val goodRatePercent: Double,
    val avgBonusValue: Double
)

data class HourStats(
    val hour: Int,
    val rideCount: Int,
    val avgPricePerKm: Double,
    val dynamicPercentage: Double
)

data class CityStats(
    val city: String,
    val rideCount: Int,
    val avgPricePerKm: Double,
    val dynamicPercentage: Double,
    val bestHour: Int
)

data class NeighborhoodStats(
    val neighborhood: String,
    val city: String,
    val rideCount: Int,
    val avgPricePerKm: Double,
    val dynamicPercentage: Double
)

data class AnalysisResultV2(
    val offeredCount: Int,
    val acceptedCount: Int,
    val acceptanceRate: Double,
    val totalEarnings: Double,
    val totalKm: Double,
    val totalMinutes: Int,
    val avgPricePerKm: Double,
    val avgPricePerHour: Double,
    val avgRating: Double,
    val goodPercent: Double,
    val mediumPercent: Double,
    val badPercent: Double,
    val priorityImpact: BonusImpact,
    val dynamicImpact: BonusImpact,
    val bestHourToAccept: Int,
    val peakDynamicHour: Int,
    val hourlyData: List<HourStats>,
    val topCities: List<CityStats>,
    val topNeighborhoods: List<NeighborhoodStats>
)

object AnalysisHelperV2 {

    fun calculate(rides: List<RideRecord>, dailyRides: List<DailyRide>): AnalysisResultV2 {
        val completedRideIds = dailyRides.filter { it.isCompleted }.map { it.rideId }.toSet()

        val offered = rides
        val accepted = rides.filter { it.id in completedRideIds }
        val offeredCount = offered.size
        val acceptedCount = accepted.size
        val acceptanceRate = if (offeredCount > 0) acceptedCount.toDouble() / offeredCount * 100 else 0.0

        val totalEarnings = accepted.mapNotNull { it.value }.sum()
        val totalKm = accepted.mapNotNull { it.distanceKm }.sum()
        val totalMinutes = accepted.mapNotNull { it.timeMin }.sum()
        val avgPricePerKm = accepted.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val avgPricePerHour = accepted.mapNotNull { it.pricePerHour }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val avgRating = accepted.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

        val scored = rides.filter { it.scorePercent != null }
        val goodCount = scored.count { it.scorePercent!! >= 80 }
        val mediumCount = scored.count { it.scorePercent!! >= 50 && it.scorePercent!! < 80 }
        val badCount = scored.count { it.scorePercent!! < 50 }
        val totalScored = scored.size
        val goodPercent = if (totalScored > 0) goodCount.toDouble() / totalScored * 100 else 0.0
        val mediumPercent = if (totalScored > 0) mediumCount.toDouble() / totalScored * 100 else 0.0
        val badPercent = if (totalScored > 0) badCount.toDouble() / totalScored * 100 else 0.0

        val withPriority = offered.filter { hasPriorityBonus(it) }
        val withoutPriority = offered.filter { !hasPriorityBonus(it) }
        val withDynamic = offered.filter { hasDynamicBonus(it) }
        val withoutDynamic = offered.filter { !hasDynamicBonus(it) }

        val priorityImpact = BonusImpact(
            count = withPriority.size,
            percentage = if (offeredCount > 0) withPriority.size.toDouble() / offeredCount * 100 else 0.0,
            avgPricePerKm = withPriority.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            avgPricePerKmWithout = withoutPriority.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            goodRatePercent = if (withPriority.isNotEmpty())
                withPriority.count { (it.scorePercent ?: 0.0) >= 80.0 }.toDouble() / withPriority.size * 100 else 0.0,
            avgBonusValue = withPriority.mapNotNull { it.priorityBonus ?: it.bonusAmount }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        )

        val dynamicImpact = BonusImpact(
            count = withDynamic.size,
            percentage = if (offeredCount > 0) withDynamic.size.toDouble() / offeredCount * 100 else 0.0,
            avgPricePerKm = withDynamic.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            avgPricePerKmWithout = withoutDynamic.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            goodRatePercent = if (withDynamic.isNotEmpty())
                withDynamic.count { (it.scorePercent ?: 0.0) >= 80.0 }.toDouble() / withDynamic.size * 100 else 0.0,
            avgBonusValue = withDynamic.mapNotNull { it.dynamicBonus }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        )

        val byHour = accepted.groupBy { getHourOfDay(it.timestamp) }
        val allByHour = offered.groupBy { getHourOfDay(it.timestamp) }

        val hourlyData = (0..23).map { hour ->
            val hourRides = byHour[hour] ?: emptyList()
            val allHourRides = allByHour[hour] ?: emptyList()
            val avgPpk = hourRides.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val dynCount = allHourRides.count { hasDynamicBonus(it) }
            val dynPct = if (allHourRides.isNotEmpty()) dynCount.toDouble() / allHourRides.size * 100 else 0.0
            HourStats(
                hour = hour,
                rideCount = hourRides.size,
                avgPricePerKm = avgPpk,
                dynamicPercentage = dynPct
            )
        }

        val bestHourToAccept = hourlyData.filter { it.rideCount > 0 }.maxByOrNull { it.avgPricePerKm }?.hour ?: 0
        val peakDynamicHour = hourlyData.filter { it.rideCount > 0 }.maxByOrNull { it.dynamicPercentage }?.hour ?: 0

        val cityRides = accepted.filter { it.dropoffAddress != null }
        val byCity = cityRides.groupBy { extractCity(it.dropoffAddress) ?: "Desconhecido" }
        val topCities = byCity.map { (city, list) ->
            val cityByHour = list.groupBy { getHourOfDay(it.timestamp) }
            val bestHr = cityByHour.maxByOrNull { (_, rides) -> rides.size }?.key ?: 0
            val allCityRides = offered.filter { extractCity(it.dropoffAddress) == city }
            val dynCount = allCityRides.count { hasDynamicBonus(it) }
            CityStats(
                city = city,
                rideCount = list.size,
                avgPricePerKm = list.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
                dynamicPercentage = if (allCityRides.isNotEmpty()) dynCount.toDouble() / allCityRides.size * 100 else 0.0,
                bestHour = bestHr
            )
        }.sortedByDescending { it.rideCount }.take(10)

        val neighborhoodRides = accepted.filter { it.dropoffAddress != null }
        val byNeighborhood = neighborhoodRides.groupBy {
            val hood = extractNeighborhood(it.dropoffAddress)
            val city = extractCity(it.dropoffAddress)
            "${hood ?: "Desconhecido"} - ${city ?: "?"}"
        }
        val topNeighborhoods = byNeighborhood.map { (key, list) ->
            val parts = key.split(" - ")
            val allNbRides = offered.filter {
                extractNeighborhood(it.dropoffAddress) == parts[0] &&
                    extractCity(it.dropoffAddress) == parts.getOrElse(1) { "" }
            }
            val dynCount = allNbRides.count { hasDynamicBonus(it) }
            NeighborhoodStats(
                neighborhood = parts[0],
                city = parts.getOrElse(1) { "" },
                rideCount = list.size,
                avgPricePerKm = list.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
                dynamicPercentage = if (allNbRides.isNotEmpty()) dynCount.toDouble() / allNbRides.size * 100 else 0.0
            )
        }.sortedByDescending { it.rideCount }.take(10)

        return AnalysisResultV2(
            offeredCount = offeredCount,
            acceptedCount = acceptedCount,
            acceptanceRate = acceptanceRate,
            totalEarnings = totalEarnings,
            totalKm = totalKm,
            totalMinutes = totalMinutes,
            avgPricePerKm = avgPricePerKm,
            avgPricePerHour = avgPricePerHour,
            avgRating = avgRating,
            goodPercent = goodPercent,
            mediumPercent = mediumPercent,
            badPercent = badPercent,
            priorityImpact = priorityImpact,
            dynamicImpact = dynamicImpact,
            bestHourToAccept = bestHourToAccept,
            peakDynamicHour = peakDynamicHour,
            hourlyData = hourlyData,
            topCities = topCities,
            topNeighborhoods = topNeighborhoods
        )
    }

    private fun getTotalBonus(ride: RideRecord): Double {
        var total = (ride.priorityBonus ?: 0.0) + (ride.dynamicBonus ?: 0.0)
        if (total == 0.0 && ride.bonusAmount != null && ride.bonusAmount > 0) {
            total = ride.bonusAmount
        }
        return total
    }

    private fun hasPriorityBonus(ride: RideRecord): Boolean =
        (ride.priorityBonus != null && ride.priorityBonus > 0) ||
        (ride.bonusAmount != null && ride.bonusAmount > 0)

    private fun hasDynamicBonus(ride: RideRecord): Boolean =
        ride.dynamicBonus != null && ride.dynamicBonus > 0

    private fun getHourOfDay(timestamp: Long): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)

    fun extractCity(address: String?): String? {
        if (address.isNullOrBlank()) return null
        val patterns = listOf(
            Regex("""-\s*([A-Za-zÀ-ÿ\s]+?)\s*-\s*[A-Z]{2}"""),
            Regex("""-\s*([A-Za-zÀ-ÿ\s]+?),\s*[A-Z]{2}""")
        )
        for (pattern in patterns) {
            val match = pattern.find(address)
            if (match != null) return match.groupValues[1].trim()
        }
        return null
    }

    fun extractNeighborhood(address: String?): String? {
        if (address.isNullOrBlank()) return null
        val match = Regex("""-\s*([A-Za-zÀ-ÿ\s]+?)\s*-""").find(address)
        return match?.groupValues?.get(1)?.trim()
    }

    fun formatBr(value: Double): String =
        "%.2f".format(value).replace(".", ",")

    fun formatBr1(value: Double): String =
        "%.1f".format(value).replace(".", ",")

    fun hoursMinutes(totalMin: Int): String =
        if (totalMin > 0) "${totalMin / 60}h${totalMin % 60}min" else "0min"
}
