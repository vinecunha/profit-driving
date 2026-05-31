package com.profitdriving

import java.util.Calendar

data class ServiceTypeStats(
    val serviceType: String,
    val count: Int,
    val avgPricePerKm: Double,
    val avgPricePerHour: Double,
    val avgRating: Double?,
    val totalEarnings: Double,
    val avgTimeBetweenRidesMin: Double?
)

data class AnalysisResult(
    val totalRides: Int,
    val totalEarnings: Double,
    val totalKm: Double,
    val totalMinutes: Int,
    val avgPricePerKm: Double,
    val avgPricePerHour: Double,
    val avgRating: Double?,
    val bestHour: Int,
    val bestHourAvgKm: Double,
    val hoursMap: Map<Int, Double>,
    val bestDayOfWeek: Int,
    val bestDayAvgKm: Double,
    val daysMap: Map<Int, Double>,
    val byServiceType: List<ServiceTypeStats>,
    val avgTimeBetweenRidesMin: Double?,
    val fastestServiceType: String?,
    val projectedDailyEarnings: Double?,
    val acceptedCount: Int = 0,
    val declinedCount: Int = 0,
    val expiredCount: Int = 0,
    val acceptanceRate: Double? = null,
    val avgScorePercent: Double? = null,
    val todayCount: Int = 0,
    val todayHoursWorked: Double = 0.0
)

object AnalysisHelper {

    private val dayNames = listOf(
        "Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"
    )
    private val shortDays = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    private val hourLabels = listOf(
        "0h", "1h", "2h", "3h", "4h", "5h", "6h", "7h", "8h", "9h", "10h", "11h",
        "12h", "13h", "14h", "15h", "16h", "17h", "18h", "19h", "20h", "21h", "22h", "23h"
    )

    fun calculate(rides: List<RideRecord>): AnalysisResult {
        val totalRides = rides.size
        val totalEarnings = rides.mapNotNull { it.value }.sum()
        val totalKm = rides.mapNotNull { it.distanceKm }.sum()
        val totalMinutes = rides.mapNotNull { it.timeMin }.sum()

        val avgPricePerKm = rides.mapNotNull { it.pricePerKm }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val avgPricePerHour = rides.mapNotNull { it.pricePerHour }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val avgRating = rides.mapNotNull { it.rating }
            .takeIf { it.isNotEmpty() }?.average()

        val hoursMap = rides
            .filter { r -> val p = r.pricePerKm; p != null && p > 0 }
            .groupBy {
                Calendar.getInstance()
                    .apply { timeInMillis = it.timestamp }
                    .get(Calendar.HOUR_OF_DAY)
            }
            .mapValues { (_, list) ->
                list.mapNotNull { it.pricePerKm }.average()
            }
        val bestHour = hoursMap.maxByOrNull { it.value }?.key ?: 0
        val bestHourAvgKm = hoursMap[bestHour] ?: 0.0

        val daysMap = rides
            .filter { r -> val p = r.pricePerKm; p != null && p > 0 }
            .groupBy {
                Calendar.getInstance()
                    .apply { timeInMillis = it.timestamp }
                    .get(Calendar.DAY_OF_WEEK)
            }
            .mapValues { (_, list) ->
                list.mapNotNull { it.pricePerKm }.average()
            }
        val bestDayOfWeek = daysMap.maxByOrNull { it.value }?.key ?: 1
        val bestDayAvgKm = daysMap[bestDayOfWeek] ?: 0.0

        val byServiceType = rides
            .groupBy { it.serviceType ?: "Desconhecido" }
            .map { (type, list) ->
                val sorted = list.sortedBy { it.timestamp }
                val gaps = sorted.zipWithNext { a, b ->
                    (b.timestamp - a.timestamp) / 60_000.0
                }.filter { it < 120 }
                ServiceTypeStats(
                    serviceType = type,
                    count = list.size,
                    avgPricePerKm = list.mapNotNull { it.pricePerKm }
                        .takeIf { it.isNotEmpty() }?.average() ?: 0.0,
                    avgPricePerHour = list.mapNotNull { it.pricePerHour }
                        .takeIf { it.isNotEmpty() }?.average() ?: 0.0,
                    avgRating = list.mapNotNull { it.rating }
                        .takeIf { it.isNotEmpty() }?.average(),
                    totalEarnings = list.mapNotNull { it.value }.sum(),
                    avgTimeBetweenRidesMin = gaps
                        .takeIf { it.isNotEmpty() }?.average()
                )
            }.sortedByDescending { it.avgPricePerKm }

        val allSorted = rides.sortedBy { it.timestamp }
        val allGaps = allSorted.zipWithNext { a, b ->
            (b.timestamp - a.timestamp) / 60_000.0
        }.filter { it < 120 }
        val avgTimeBetweenRidesMin = allGaps
            .takeIf { it.isNotEmpty() }?.average()

        val fastestServiceType = byServiceType
            .filter { it.avgTimeBetweenRidesMin != null }
            .minByOrNull { it.avgTimeBetweenRidesMin!! }
            ?.serviceType

        val todayRides = rides.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val today = Calendar.getInstance()
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        }
        val acceptedCount = rides.count { it.status == "ACCEPTED" }
        val declinedCount = rides.count { it.status == "DECLINED" }
        val expiredCount = rides.count { it.status == "EXPIRED" }
        val acceptanceRate = if (acceptedCount + declinedCount > 0)
            acceptedCount.toDouble() / (acceptedCount + declinedCount) * 100.0
        else null
        val avgScorePercent = rides.mapNotNull { it.scorePercent }
            .takeIf { it.isNotEmpty() }?.average()

        val projectedDailyEarnings = if (todayRides.size >= 2) {
            val firstTs = todayRides.minOf { it.timestamp }
            val lastTs = todayRides.maxOf { it.timestamp }
            val hoursWorked = (lastTs - firstTs) / 3_600_000.0
            val earnedSoFar = todayRides.mapNotNull { it.value }.sum()
            if (hoursWorked > 0) (earnedSoFar / hoursWorked) * 8.0 else null
        } else null
        val todayCount = todayRides.size
        val todayHoursWorked = if (todayRides.size >= 2) {
            val firstTs = todayRides.minOf { it.timestamp }
            val lastTs = todayRides.maxOf { it.timestamp }
            (lastTs - firstTs) / 3_600_000.0
        } else 0.0

        return AnalysisResult(
            totalRides = totalRides,
            totalEarnings = totalEarnings,
            totalKm = totalKm,
            totalMinutes = totalMinutes,
            avgPricePerKm = avgPricePerKm,
            avgPricePerHour = avgPricePerHour,
            avgRating = avgRating,
            bestHour = bestHour,
            bestHourAvgKm = bestHourAvgKm,
            hoursMap = hoursMap,
            bestDayOfWeek = bestDayOfWeek,
            bestDayAvgKm = bestDayAvgKm,
            daysMap = daysMap,
            byServiceType = byServiceType,
            avgTimeBetweenRidesMin = avgTimeBetweenRidesMin,
            fastestServiceType = fastestServiceType,
            projectedDailyEarnings = projectedDailyEarnings,
            acceptedCount = acceptedCount,
            declinedCount = declinedCount,
            expiredCount = expiredCount,
            acceptanceRate = acceptanceRate,
            avgScorePercent = avgScorePercent,
            todayCount = todayCount,
            todayHoursWorked = todayHoursWorked
        )
    }

    fun dayName(dow: Int): String =
        dayNames.getOrElse(dow - 1) { "?" }

    fun shortDayName(dow: Int): String =
        shortDays.getOrElse(dow - 1) { "?" }

    fun hourLabel(h: Int): String =
        hourLabels.getOrElse(h) { "${h}h" }

    fun hoursMinutes(totalMin: Int): String {
        val h = totalMin / 60
        val m = totalMin % 60
        return buildString {
            if (h > 0) append("${h}h")
            if (m > 0) append("${m}min")
            if (isEmpty()) append("0min")
        }
    }

    fun formatBr(value: Double): String =
        "%.2f".format(value).replace(".", ",")

    fun formatBr1(value: Double): String =
        "%.1f".format(value).replace(".", ",")
}
