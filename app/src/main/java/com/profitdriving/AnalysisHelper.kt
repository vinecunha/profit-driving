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
    val totalRides: Int,
    val totalEarnings: Double,
    val totalKm: Double,
    val totalMinutes: Int,
    val avgPricePerKm: Double,
    val avgPricePerHour: Double,
    val acceptedPercent: Double,
    val declinedPercent: Double,
    val expiredPercent: Double,
    val goodPercent: Double,
    val mediumPercent: Double,
    val badPercent: Double,
    val priorityImpact: BonusImpact,
    val dynamicImpact: BonusImpact,
    val bestHourToAccept: HourStats,
    val peakDynamicHour: HourStats,
    val hourlyData: List<HourStats>,
    val topCities: List<CityStats>,
    val topNeighborhoods: List<NeighborhoodStats>
)

object AnalysisHelperV2 {

    fun calculate(rides: List<RideRecord>): AnalysisResultV2 {
        val totalRides = rides.size
        val totalEarnings = rides.mapNotNull { it.value }.sum()
        val totalKm = rides.mapNotNull { it.distanceKm }.sum()
        val totalMinutes = rides.mapNotNull { it.timeMin }.sum()
        val avgPricePerKm = rides.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val avgPricePerHour = rides.mapNotNull { it.pricePerHour }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

        // 1. Status percentages
        val acceptedCount = rides.count { it.status == "ACCEPTED" }
        val declinedCount = rides.count { it.status == "DECLINED" }
        val expiredCount = rides.count { it.status == "EXPIRED" }
        val acceptedPercent = if (totalRides > 0) acceptedCount.toDouble() / totalRides * 100 else 0.0
        val declinedPercent = if (totalRides > 0) declinedCount.toDouble() / totalRides * 100 else 0.0
        val expiredPercent = if (totalRides > 0) expiredCount.toDouble() / totalRides * 100 else 0.0

        // 2. Score evaluation (only on rides with scorePercent)
        val scored = rides.filter { it.scorePercent != null }
        val goodCount = scored.count { it.scorePercent!! >= 80 }
        val mediumCount = scored.count { it.scorePercent!! >= 50 && it.scorePercent!! < 80 }
        val badCount = scored.count { it.scorePercent!! < 50 }
        val totalScored = scored.size
        val goodPercent = if (totalScored > 0) goodCount.toDouble() / totalScored * 100 else 0.0
        val mediumPercent = if (totalScored > 0) mediumCount.toDouble() / totalScored * 100 else 0.0
        val badPercent = if (totalScored > 0) badCount.toDouble() / totalScored * 100 else 0.0

        // 3. Bonus impact
        val withPriority = rides.filter { (it.priorityBonus ?: 0.0) > 0 }
        val withoutPriority = rides.filter { (it.priorityBonus ?: 0.0) <= 0 }
        val withDynamic = rides.filter { (it.dynamicBonus ?: 0.0) > 0 }
        val withoutDynamic = rides.filter { (it.dynamicBonus ?: 0.0) <= 0 }

        val priorityImpact = BonusImpact(
            count = withPriority.size,
            percentage = if (totalRides > 0) withPriority.size.toDouble() / totalRides * 100 else 0.0,
            avgPricePerKm = withPriority.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            avgPricePerKmWithout = withoutPriority.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            goodRatePercent = if (withPriority.isNotEmpty())
                withPriority.count { (it.scorePercent ?: 0.0) >= 80.0 }.toDouble() / withPriority.size * 100 else 0.0,
            avgBonusValue = withPriority.mapNotNull { it.priorityBonus }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        )

        val dynamicImpact = BonusImpact(
            count = withDynamic.size,
            percentage = if (totalRides > 0) withDynamic.size.toDouble() / totalRides * 100 else 0.0,
            avgPricePerKm = withDynamic.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            avgPricePerKmWithout = withoutDynamic.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            goodRatePercent = if (withDynamic.isNotEmpty())
                withDynamic.count { (it.scorePercent ?: 0.0) >= 80.0 }.toDouble() / withDynamic.size * 100 else 0.0,
            avgBonusValue = withDynamic.mapNotNull { it.dynamicBonus }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        )

        // 4. Hourly analysis
        val acceptedRides = rides.filter { it.status == "ACCEPTED" && it.pricePerKm != null }
        val byHour = acceptedRides.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }

        // All rides (including non-accepted) by hour for dynamic analysis
        val allByHour = rides.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }

        val hourlyData = (0..23).map { hour ->
            val hourRides = byHour[hour] ?: emptyList()
            val allHourRides = allByHour[hour] ?: emptyList()
            val avgPpk = hourRides.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val dynamicCount = allHourRides.count { (it.dynamicBonus ?: 0.0) > 0 }
            val dynPct = if (allHourRides.isNotEmpty()) dynamicCount.toDouble() / allHourRides.size * 100 else 0.0
            HourStats(
                hour = hour,
                rideCount = hourRides.size,
                avgPricePerKm = avgPpk,
                dynamicPercentage = dynPct
            )
        }

        val bestHourToAccept = hourlyData.filter { it.rideCount > 0 }.maxByOrNull { it.avgPricePerKm }
            ?: HourStats(0, 0, 0.0, 0.0)
        val peakDynamicHour = hourlyData.filter { it.rideCount > 0 }.maxByOrNull { it.dynamicPercentage }
            ?: HourStats(0, 0, 0.0, 0.0)

        // 5. City analysis (from dropoffAddress)
        val cityRides = acceptedRides.filter { it.dropoffAddress != null }
        val byCity = cityRides.groupBy { extractCity(it.dropoffAddress) ?: "Desconhecido" }
        val topCities = byCity.map { (city, list) ->
            val cityByHour = list.groupBy {
                Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
            }
            val bestHr = cityByHour.maxByOrNull { (_, rides) -> rides.size }?.key ?: 0
            val allCityRides = rides.filter { extractCity(it.dropoffAddress) == city }
            val dynCount = allCityRides.count { (it.dynamicBonus ?: 0.0) > 0 }
            CityStats(
                city = city,
                rideCount = list.size,
                avgPricePerKm = list.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
                dynamicPercentage = if (allCityRides.isNotEmpty()) dynCount.toDouble() / allCityRides.size * 100 else 0.0,
                bestHour = bestHr
            )
        }.sortedByDescending { it.rideCount }.take(10)

        // 6. Neighborhood analysis
        val neighborhoodRides = acceptedRides.filter { it.dropoffAddress != null }
        val byNeighborhood = neighborhoodRides.groupBy {
            val hood = extractNeighborhood(it.dropoffAddress)
            val city = extractCity(it.dropoffAddress)
            "${hood ?: "Desconhecido"} - ${city ?: "?"}"
        }
        val topNeighborhoods = byNeighborhood.map { (key, list) ->
            val parts = key.split(" - ")
            val allNbRides = rides.filter {
                extractNeighborhood(it.dropoffAddress) == parts[0] &&
                    extractCity(it.dropoffAddress) == parts.getOrElse(1) { "" }
            }
            val dynCount = allNbRides.count { (it.dynamicBonus ?: 0.0) > 0 }
            NeighborhoodStats(
                neighborhood = parts[0],
                city = parts.getOrElse(1) { "" },
                rideCount = list.size,
                avgPricePerKm = list.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
                dynamicPercentage = if (allNbRides.isNotEmpty()) dynCount.toDouble() / allNbRides.size * 100 else 0.0
            )
        }.sortedByDescending { it.rideCount }.take(10)

        return AnalysisResultV2(
            totalRides = totalRides,
            totalEarnings = totalEarnings,
            totalKm = totalKm,
            totalMinutes = totalMinutes,
            avgPricePerKm = avgPricePerKm,
            avgPricePerHour = avgPricePerHour,
            acceptedPercent = acceptedPercent,
            declinedPercent = declinedPercent,
            expiredPercent = expiredPercent,
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
