package com.profitdriving

import java.util.Calendar
import java.util.Date
import java.util.Locale

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

data class HourSlotForecast(
    val slotLabel: String,
    val slotStartHour: Int,
    val rideCount: Int,
    val avgEarningsPerHour: Double,
    val dynamicCount: Int,
    val dynamicPercent: Double,
    val barFraction: Float
)

data class ValueAcceptance(
    val rangeLabel: String,
    val offered: Int,
    val accepted: Int,
    val acceptanceRate: Double,
    val barFraction: Float
)

data class LostRidesInfo(
    val lostCount: Int,
    val avgLostValue: Double,
    val avgLostPricePerKm: Double,
    val peakLossHour: String,
    val peakLossPercent: Double,
    val topLostCategory: String,
    val topLostCategoryPercent: Double,
    val topLostCity: String,
    val topLostCityPercent: Double
)

data class DynamicTrendDay(
    val dayLabel: String,
    val dynamicPercent: Double,
    val isUp: Boolean,
    val barFraction: Float
)

data class DailyProjection(
    val currentEarnings: Double,
    val completedRides: Int,
    val hoursWorked: Double,
    val avgPerHour: Double,
    val targetDay: Double,
    val projectedWithTargetHours: Double,
    val targetHours: Double,
    val remaining: Double,
    val totalPauseHours: Double = 0.0,
    val idleTimePercent: Double = 0.0,
    val estimatedActiveHours: Double = 0.0,
    val firstRideTime: String = "",
    val lastRideTime: String = "",
)

data class WeekdayRankItem(
    val dayLabel: String,
    val dayOfWeek: Int,
    val avgEarnings: Double,
    val rideCount: Int,
    val dynamicPercent: Double,
    val avgPricePerKm: Double,
    val barFraction: Float,
    val position: Int
)

data class ScoreTrendDay(
    val dayLabel: String,
    val avgScore: Double,
    val rideCount: Int,
    val goodPercent: Double,
    val barFraction: Float
)

data class StopStats(
    val avgValue: Double,
    val avgPricePerKm: Double,
    val avgPricePerHour: Double,
    val avgDistance: Double,
    val avgTime: Int,
    val count: Int
)

data class MultipleStopsImpact(
    val withStops: StopStats,
    val withoutStops: StopStats,
    val totalWithStops: Int,
    val totalWithoutStops: Int
)

data class RangeStat(
    val label: String,
    val count: Int,
    val avgValue: Double,
    val avgPricePerKm: Double,
    val percentOfTotal: Double
)

data class RejectionPatterns(
    val byValue: List<RangeStat>,
    val byDistance: List<RangeStat>,
    val byHour: List<RangeStat>,
    val totalLost: Int,
    val totalLostValue: Double
)

data class ServiceTypeStats(
    val serviceType: String,
    val offeredCount: Int,
    val acceptedCount: Int,
    val acceptanceRate: Double,
    val totalEarnings: Double,
    val avgPricePerKm: Double,
    val avgPricePerHour: Double,
    val avgRating: Double,
    val goodPercent: Double,
    val lostCount: Int,
    val avgLostValue: Double,
    val dynamicPercent: Double,
    val priorityPercent: Double,
    val bestHour: String,
    val earningsPercentOfTotal: Double
)

data class DriverRating(
    val level: String,
    val stars: Int,
    val score: Int,
    val description: String
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
    val topNeighborhoods: List<NeighborhoodStats>,
    val hourlyForecast: List<HourSlotForecast>,
    val acceptanceByValue: List<ValueAcceptance>,
    val lostRides: LostRidesInfo,
    val dynamicTrend: List<DynamicTrendDay>,
    val dailyProjection: DailyProjection,
    val weekdayRanking: List<WeekdayRankItem>,
    val totalCostPerKm: Double = 0.0,
    val totalCost: Double = 0.0,
    val profitMargin: Double = 0.0,
    val previousPeriodEarnings: Double = 0.0,
    val previousPeriodRides: Int = 0,
    val floorSimulation: FloorSimulation? = null,
    val breakevenAnalysis: BreakevenAnalysisResult? = null,
    val scoreTrend: List<ScoreTrendDay> = emptyList(),
    val multipleStopsImpact: MultipleStopsImpact? = null,
    val rejectionPatterns: RejectionPatterns? = null,
    val serviceTypes: List<ServiceTypeStats> = emptyList(),
    val driverRating: DriverRating? = null
)

object AnalysisHelperV2 {

    fun calculate(
        rides: List<RideRecord>,
        dailyRides: List<DailyRide>,
        costPerKm: Double = 0.0
    ): AnalysisResultV2 {
        val completedRideIds = dailyRides.filter { it.isCompleted }.map { it.rideId }.toSet()
        val dailyRidesMap = dailyRides.associateBy { it.rideId }

        val offered = rides
        val accepted = rides.filter { it.id in completedRideIds }
        val offeredCount = offered.size
        val acceptedCount = accepted.size
        val acceptanceRate = if (offeredCount > 0) acceptedCount.toDouble() / offeredCount * 100 else 0.0

        // ============================================================
        // VALORES AJUSTADOS COM GORJETAS E REAJUSTES
        // ============================================================

        // Faturamento bruto total (já com gorjetas e reajustes)
        val totalEarnings = accepted.sumOf { ride ->
            val daily = dailyRidesMap[ride.id]
            daily?.finalValue ?: ride.value ?: 0.0
        }

        // Total de gorjetas
        val totalTips = accepted.sumOf { ride ->
            val daily = dailyRidesMap[ride.id]
            daily?.tipAmount ?: 0.0
        }

        // Total de reajustes (positivos ou negativos)
        val totalAdjustments = accepted.sumOf { ride ->
            val daily = dailyRidesMap[ride.id]
            daily?.adjustmentDifference ?: 0.0
        }

        // Km total (não muda com reajuste)
        val totalKm = accepted.mapNotNull { it.distanceKm }.sum()
        val totalMinutes = accepted.mapNotNull { it.timeMin }.sum()

        // R$/km médio (usando valor final ajustado)
        val avgPricePerKm = accepted.mapNotNull { ride ->
            val daily = dailyRidesMap[ride.id]
            val finalValue = daily?.finalValue ?: ride.value ?: return@mapNotNull null
            val distance = ride.distanceKm ?: return@mapNotNull null
            if (distance > 0) finalValue / distance else null
        }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

        // R$/h médio (usando valor final ajustado)
        val avgPricePerHour = accepted.mapNotNull { ride ->
            val daily = dailyRidesMap[ride.id]
            val finalValue = daily?.finalValue ?: ride.value ?: return@mapNotNull null
            val timeMin = ride.timeMin ?: return@mapNotNull null
            if (timeMin > 0) finalValue / (timeMin / 60.0) else null
        }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

        val avgRating = accepted.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

        // Score distribution (não muda)
        val scored = rides.filter { it.scorePercent != null }
        val goodCount = scored.count { it.scorePercent!! >= 80 }
        val mediumCount = scored.count { it.scorePercent!! >= 50 && it.scorePercent!! < 80 }
        val badCount = scored.count { it.scorePercent!! < 50 }
        val totalScored = scored.size
        val goodPercent = if (totalScored > 0) goodCount.toDouble() / totalScored * 100 else 0.0
        val mediumPercent = if (totalScored > 0) mediumCount.toDouble() / totalScored * 100 else 0.0
        val badPercent = if (totalScored > 0) badCount.toDouble() / totalScored * 100 else 0.0

        // Bonus impacts (usando valores originais para comparar com/sem bônus)
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

        // Hourly stats (usando valores ajustados)
        val byHour = accepted.groupBy { getHourOfDay(it.timestamp) }
        val allByHour = offered.groupBy { getHourOfDay(it.timestamp) }

        val hourlyData = (0..23).map { hour ->
            val hourRides = byHour[hour] ?: emptyList()
            val allHourRides = allByHour[hour] ?: emptyList()

            // R$/km médio do horário (com valores ajustados)
            val avgPpk = hourRides.mapNotNull { ride ->
                val daily = dailyRidesMap[ride.id]
                val finalValue = daily?.finalValue ?: ride.value ?: return@mapNotNull null
                val distance = ride.distanceKm ?: return@mapNotNull null
                if (distance > 0) finalValue / distance else null
            }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

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

        // City stats (usando valores ajustados)
        val cityRides = accepted.filter { it.dropoffAddress != null }
        val byCity = cityRides.groupBy { extractCity(it.dropoffAddress) }
            .mapValues { (_, list) -> list }
            .filterKeys { it != null && it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }

        val topCities = byCity.map { (city, list) ->
            val cityByHour = list.groupBy { getHourOfDay(it.timestamp) }
            val bestHr = cityByHour.maxByOrNull { (_, rides) -> rides.size }?.key ?: 0
            val allCityRides = offered.filter { extractCity(it.dropoffAddress) == city }
            val dynCount = allCityRides.count { hasDynamicBonus(it) }

            // R$/km médio da cidade (com valores ajustados)
            val avgPpk = list.mapNotNull { ride ->
                val daily = dailyRidesMap[ride.id]
                val finalValue = daily?.finalValue ?: ride.value ?: return@mapNotNull null
                val distance = ride.distanceKm ?: return@mapNotNull null
                if (distance > 0) finalValue / distance else null
            }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

            CityStats(
                city = city!!,
                rideCount = list.size,
                avgPricePerKm = avgPpk,
                dynamicPercentage = if (allCityRides.isNotEmpty()) dynCount.toDouble() / allCityRides.size * 100 else 0.0,
                bestHour = bestHr
            )
        }.sortedByDescending { it.rideCount }.take(10)

        // Neighborhood stats (usando valores ajustados)
        val neighborhoodRides = accepted.filter { it.dropoffAddress != null }
        val byNeighborhood = neighborhoodRides.mapNotNull { ride ->
            val hood = extractNeighborhood(ride.dropoffAddress)
            val city = extractCity(ride.dropoffAddress)
            if (hood != null && hood.isNotBlank() && !hood.equals("Desconhecido", ignoreCase = true)) {
                Pair("${hood} - ${city ?: "?"}", ride)
            } else null
        }.groupBy { it.first }.mapValues { (_, list) -> list.map { it.second } }

        val topNeighborhoods = byNeighborhood.map { (key, list) ->
            val parts = key.split(" - ")
            val allNbRides = offered.filter {
                extractNeighborhood(it.dropoffAddress) == parts[0] &&
                    extractCity(it.dropoffAddress) == parts.getOrElse(1) { "" }
            }
            val dynCount = allNbRides.count { hasDynamicBonus(it) }

            // R$/km médio do bairro (com valores ajustados)
            val avgPpk = list.mapNotNull { ride ->
                val daily = dailyRidesMap[ride.id]
                val finalValue = daily?.finalValue ?: ride.value ?: return@mapNotNull null
                val distance = ride.distanceKm ?: return@mapNotNull null
                if (distance > 0) finalValue / distance else null
            }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

            NeighborhoodStats(
                neighborhood = parts[0],
                city = parts.getOrElse(1) { "" },
                rideCount = list.size,
                avgPricePerKm = avgPpk,
                dynamicPercentage = if (allNbRides.isNotEmpty()) dynCount.toDouble() / allNbRides.size * 100 else 0.0
            )
        }.sortedByDescending { it.rideCount }.take(10)

        val hourlyForecast = calculateHourlyForecast(accepted, offered, dailyRidesMap)
        val acceptanceByValue = calculateAcceptanceByValue(offered, accepted, dailyRidesMap)
        val lostRides = calculateLostRides(offered, accepted)
        val dynamicTrend = calculateDynamicTrend(offered)
        val dailyProjection = calculateDailyProjection(accepted, offered, dailyRidesMap)
        val weekdayRanking = calculateWeekdayRanking(accepted, dailyRidesMap)

        val totalCost = totalKm * costPerKm
        val profitMargin = if (totalEarnings > 0) ((totalEarnings - totalCost) / totalEarnings) * 100 else 0.0

        val shiftDuration = if (offered.isNotEmpty())
            (offered.maxOf { it.timestamp } - offered.minOf { it.timestamp }) / 60_000L
        else 0L

        val floorSimulation = calculateFloorSimulation(
            rides = rides,
            costPerKm = costPerKm,
        )

        val breakevenAnalysis = calculateBreakevenAnalysis(
            acceptedRides = accepted,
            costPerKm = costPerKm,
        )

        val scoreTrend = calculateScoreTrend(accepted, dailyRidesMap)

        val multipleStopsImpact = calculateMultipleStopsImpact(accepted, dailyRidesMap)

        val rejectionPatterns = calculateRejectionPatterns(offered, accepted)

        val serviceTypes = calculateServiceTypeStats(offered, accepted, dailyRidesMap, totalEarnings)

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
            topNeighborhoods = topNeighborhoods,
            hourlyForecast = hourlyForecast,
            acceptanceByValue = acceptanceByValue,
            lostRides = lostRides,
            dynamicTrend = dynamicTrend,
            dailyProjection = dailyProjection,
            weekdayRanking = weekdayRanking,
            totalCostPerKm = costPerKm,
            totalCost = totalCost,
            profitMargin = profitMargin,
            floorSimulation = floorSimulation,
            breakevenAnalysis = breakevenAnalysis,
            scoreTrend = scoreTrend,
            multipleStopsImpact = multipleStopsImpact,
            rejectionPatterns = rejectionPatterns,
            serviceTypes = serviceTypes,
            driverRating = calculateDriverRating(avgPricePerKm, avgPricePerHour, profitMargin, acceptanceRate, goodPercent)
        )
    }

    fun calculateDriverRating(
        avgPricePerKm: Double,
        avgPricePerHour: Double,
        profitMargin: Double,
        acceptanceRate: Double,
        goodPercent: Double
    ): DriverRating {
        fun scoreMetric(value: Double, thresholds: List<Pair<Double, Int>>): Int {
            for ((threshold, score) in thresholds) {
                if (value >= threshold) return score
            }
            return 10
        }

        val pricePerKmScore = scoreMetric(avgPricePerKm, listOf(
            2.5 to 100, 2.0 to 80, 1.5 to 60, 1.2 to 40, 1.0 to 20
        ))
        val pricePerHourScore = scoreMetric(avgPricePerHour, listOf(
            40.0 to 100, 30.0 to 80, 25.0 to 60, 20.0 to 40, 15.0 to 20
        ))
        val profitMarginScore = scoreMetric(profitMargin, listOf(
            60.0 to 100, 45.0 to 80, 30.0 to 60, 15.0 to 40, 5.0 to 20
        ))
        val acceptanceScore = scoreMetric(acceptanceRate, listOf(
            80.0 to 100, 65.0 to 80, 50.0 to 60, 35.0 to 40, 20.0 to 20
        ))
        val goodPctScore = scoreMetric(goodPercent, listOf(
            70.0 to 100, 55.0 to 80, 40.0 to 60, 25.0 to 40, 10.0 to 20
        ))

        val overall = (pricePerKmScore + pricePerHourScore + profitMarginScore + acceptanceScore + goodPctScore) / 5

        val (level, stars, description) = when {
            overall >= 90 -> Triple("Lenda", 5,
                "Voc\u00EA domina a sele\u00E7\u00E3o de corridas! Suas m\u00E9tricas est\u00E3o no topo. Continue assim para maximizar seus ganhos.")
            overall >= 75 -> Triple("Profissional", 4,
                "\u00D3timo desempenho! Voc\u00EA sabe escolher bem as corridas. Pequenos ajustes podem te levar ao pr\u00F3ximo n\u00EDvel.")
            overall >= 50 -> Triple("Intermedi\u00E1rio", 3,
                "Voc\u00EA est\u00E1 no caminho certo. Tente filtrar melhor as corridas de baixo valor e foque nos hor\u00E1rios de pico.")
            overall >= 25 -> Triple("Aprendiz", 2,
                "Voc\u00EA est\u00E1 come\u00E7ando a entender o mercado. Preste aten\u00E7\u00E3o no R\$/km e evite corridas muito longas por pouco valor.")
            else -> Triple("Iniciante", 1,
                "Momento de aprendizado. Estude os indicadores de R\$/km e R\$/h para melhorar sua rentabilidade.")
        }

        return DriverRating(level, stars, overall, description)
    }

    private fun getHourSlot(hour: Int): String {
        return when (hour) {
            in 6..9 -> "06h-10h"
            in 10..13 -> "10h-14h"
            in 14..17 -> "14h-18h"
            in 18..21 -> "18h-22h"
            in 22..23 -> "22h-02h"
            in 0..1 -> "22h-02h"
            else -> "02h-06h"
        }
    }

    private fun getSlotStartHour(slot: String): Int {
        return when (slot) {
            "06h-10h" -> 6
            "10h-14h" -> 10
            "14h-18h" -> 14
            "18h-22h" -> 18
            "22h-02h" -> 22
            else -> 2
        }
    }

    fun calculateHourlyForecast(
        accepted: List<RideRecord>,
        offered: List<RideRecord>,
        dailyRidesMap: Map<Long, DailyRide>
    ): List<HourSlotForecast> {
        val slots = listOf("06h-10h", "10h-14h", "14h-18h", "18h-22h", "22h-02h", "02h-06h")
        val acceptedBySlot = accepted.groupBy { getHourSlot(getHourOfDay(it.timestamp)) }
        val offeredBySlot = offered.groupBy { getHourSlot(getHourOfDay(it.timestamp)) }

        val maxEarning = acceptedBySlot.maxOfOrNull { (_, rides) ->
            rides.sumOf { ride ->
                val daily = dailyRidesMap[ride.id]
                val finalValue = daily?.finalValue ?: ride.value ?: 0.0
                finalValue
            } / 1.0
        }?.coerceAtLeast(0.01) ?: 1.0

        return slots.map { slot ->
            val acc = acceptedBySlot[slot] ?: emptyList()
            val off = offeredBySlot[slot] ?: emptyList()

            val totalMin = acc.mapNotNull { it.timeMin }.sum().toDouble()
            val totalVal = acc.sumOf { ride ->
                val daily = dailyRidesMap[ride.id]
                daily?.finalValue ?: ride.value ?: 0.0
            }

            val avgEarnH = if (totalMin > 0) totalVal / (totalMin / 60.0) else 0.0
            val dynCount = off.count { hasDynamicBonus(it) }
            val dynPct = if (off.isNotEmpty()) dynCount.toDouble() / off.size * 100 else 0.0

            HourSlotForecast(
                slotLabel = slot,
                slotStartHour = getSlotStartHour(slot),
                rideCount = acc.size,
                avgEarningsPerHour = avgEarnH,
                dynamicCount = dynCount,
                dynamicPercent = dynPct,
                barFraction = (avgEarnH / maxEarning).toFloat()
            )
        }
    }

    private val valueRanges = listOf(
        0.0..15.0 to "R\$ 0-15",
        15.0..30.0 to "R\$ 15-30",
        30.0..50.0 to "R\$ 30-50",
        Double.POSITIVE_INFINITY..Double.POSITIVE_INFINITY to "R\$ 50+"
    )

    fun calculateAcceptanceByValue(
        offered: List<RideRecord>,
        accepted: List<RideRecord>,
        dailyRidesMap: Map<Long, DailyRide>
    ): List<ValueAcceptance> {
        val acceptedIds = accepted.map { it.id }.toSet()
        val rangeDefs = listOf(
            Triple(0.0, 15.0, "R\$ 0-15"),
            Triple(15.0, 30.0, "R\$ 15-30"),
            Triple(30.0, 50.0, "R\$ 30-50"),
            Triple(50.0, Double.POSITIVE_INFINITY, "R\$ 50+")
        )

        val maxRate = rangeDefs.maxOfOrNull { (lo, hi, _) ->
            val off = offered.filter { r ->
                val daily = dailyRidesMap[r.id]
                val v = daily?.finalValue ?: r.value ?: 0.0
                v >= lo && (hi.isInfinite() || v < hi)
            }
            if (off.isEmpty()) 0.0 else off.count { it.id in acceptedIds }.toDouble() / off.size
        }?.coerceAtLeast(0.01) ?: 1.0

        return rangeDefs.map { (lo, hi, label) ->
            val off = offered.filter { r ->
                val daily = dailyRidesMap[r.id]
                val v = daily?.finalValue ?: r.value ?: 0.0
                v >= lo && (hi.isInfinite() || v < hi)
            }
            val acc = off.count { it.id in acceptedIds }
            val rate = if (off.isNotEmpty()) acc.toDouble() / off.size * 100 else 0.0
            ValueAcceptance(
                rangeLabel = label,
                offered = off.size,
                accepted = acc,
                acceptanceRate = rate,
                barFraction = (rate / 100.0 / (maxRate / 100.0)).toFloat()
            )
        }
    }

    fun calculateLostRides(offered: List<RideRecord>, accepted: List<RideRecord>): LostRidesInfo {
        val acceptedIds = accepted.map { it.id }.toSet()
        val lost = offered.filter { it.id !in acceptedIds }
        val lostCount = lost.size
        val avgLostValue = lost.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val avgLostPpk = lost.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

        val lostBySlot = lost.groupBy { getHourSlot(getHourOfDay(it.timestamp)) }
        val peakSlot = lostBySlot.maxByOrNull { (_, list) -> list.size }
        val peakLossPercent = if (lostCount > 0 && peakSlot != null)
            peakSlot.value.size.toDouble() / lostCount * 100 else 0.0

        val lostByCategory = lost.groupBy { it.serviceType ?: "Desconhecido" }
        val topCat = lostByCategory.maxByOrNull { (_, list) -> list.size }
        val topCatPercent = if (lostCount > 0 && topCat != null)
            topCat.value.size.toDouble() / lostCount * 100 else 0.0

        val lostByCity = lost.groupBy { extractCity(it.dropoffAddress) ?: "Desconhecido" }
        val topCity = lostByCity.maxByOrNull { (_, list) -> list.size }
        val topCityPercent = if (lostCount > 0 && topCity != null)
            topCity.value.size.toDouble() / lostCount * 100 else 0.0

        return LostRidesInfo(
            lostCount = lostCount,
            avgLostValue = avgLostValue,
            avgLostPricePerKm = avgLostPpk,
            peakLossHour = peakSlot?.key ?: "N/A",
            peakLossPercent = peakLossPercent,
            topLostCategory = topCat?.key ?: "N/A",
            topLostCategoryPercent = topCatPercent,
            topLostCity = topCity?.key ?: "N/A",
            topLostCityPercent = topCityPercent
        )
    }

    fun calculateDynamicTrend(offered: List<RideRecord>): List<DynamicTrendDay> {
        val cal = Calendar.getInstance()
        val dayNames = listOf("Domingo", "Segunda", "Ter\u00E7a", "Quarta", "Quinta", "Sexta", "S\u00E1bado")
        val days = mutableListOf<DynamicTrendDay>()
        val now = System.currentTimeMillis()

        for (i in 6 downTo 0) {
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, -i)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = cal.timeInMillis

            val dayRides = offered.filter { it.timestamp in dayStart until dayEnd }
            val dynCount = dayRides.count { hasDynamicBonus(it) }
            val dynPct = if (dayRides.isNotEmpty()) dynCount.toDouble() / dayRides.size * 100 else 0.0

            days.add(DynamicTrendDay(
                dayLabel = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1],
                dynamicPercent = dynPct,
                isUp = i > 0 && days.isNotEmpty() && dynPct >= (days.lastOrNull()?.dynamicPercent ?: 0.0),
                barFraction = (dynPct / 100.0).toFloat()
            ))
        }

        return days
    }

    fun calculateDailyProjection(
        accepted: List<RideRecord>,
        offered: List<RideRecord>,
        dailyRidesMap: Map<Long, DailyRide>
    ): DailyProjection {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val todayEnd = cal.timeInMillis

        // Corridas ACEITAS de hoje
        val todayAccepted = accepted.filter { it.timestamp in todayStart until todayEnd }
            .sortedBy { it.timestamp }

        // TODAS as ofertas do dia (incluindo recusadas/expiraram)
        val todayOffered = offered.filter { it.timestamp in todayStart until todayEnd }
            .sortedBy { it.timestamp }

        // ============================================================
        // 1. GANHOS ATUAIS (com gorjetas e reajustes)
        // ============================================================
        val currentEarnings = todayAccepted.sumOf { ride ->
            val daily = dailyRidesMap[ride.id]
            daily?.finalValue ?: ride.value ?: 0.0
        }
        val completedRides = todayAccepted.size

        // ============================================================
        // 2. HORAS TRABALHADAS (baseado em ofertas, não apenas aceitas)
        // ============================================================

        var firstRideTimestamp: Long? = null
        var lastRideTimestamp: Long? = null
        var totalPauseMinutes = 0L

        if (todayOffered.isNotEmpty()) {
            firstRideTimestamp = todayOffered.first().timestamp
            lastRideTimestamp = todayOffered.last().timestamp

            // Detectar hiatos grandes entre ofertas consecutivas
            for (i in 1 until todayOffered.size) {
                val current = todayOffered[i].timestamp
                val previous = todayOffered[i - 1].timestamp
                val gapMinutes = (current - previous) / 60_000L

                // Hiato > 60 minutos é considerado pausa (almoço, descanso)
                if (gapMinutes > 60) {
                    totalPauseMinutes += gapMinutes
                }
            }
        }

        // Se não houver ofertas, usar apenas corridas aceitas
        if (firstRideTimestamp == null && todayAccepted.isNotEmpty()) {
            firstRideTimestamp = todayAccepted.first().timestamp
            lastRideTimestamp = todayAccepted.last().timestamp
        }

        // Calcular horas trabalhadas (descontando pausas)
        var hoursWorked = 0.0
        if (firstRideTimestamp != null && lastRideTimestamp != null) {
            val totalMinutes = (lastRideTimestamp - firstRideTimestamp) / 60_000L
            val activeMinutes = totalMinutes - totalPauseMinutes
            hoursWorked = activeMinutes / 60.0
        }

        // Fallback: se não conseguiu calcular, estimativa por corrida
        if (hoursWorked <= 0 && todayAccepted.isNotEmpty()) {
            // Estimativa média: 45 minutos por corrida
            hoursWorked = todayAccepted.size * 0.75
        }

        // Limitar horas trabalhadas a um máximo razoável (18h)
        hoursWorked = hoursWorked.coerceIn(0.0, 18.0)

        // ============================================================
        // 3. MÉDIA POR HORA
        // ============================================================
        val avgPerHour = if (hoursWorked > 0) currentEarnings / hoursWorked else 0.0

        // ============================================================
        // 4. PROJEÇÕES
        // ============================================================
        val targetDay = 200.0  // Meta configurável no futuro
        val targetHours = 8.0
        val projectedWithTargetHours = avgPerHour * targetHours
        val remaining = targetDay - currentEarnings

        // ============================================================
        // 5. INSIGHTS ADICIONAIS (podem ser usados na UI)
        // ============================================================
        val totalPauseHours = totalPauseMinutes / 60.0
        val estimatedActiveHours = hoursWorked
        val idleTimePercent = if (hoursWorked > 0) (totalPauseHours / (hoursWorked + totalPauseHours)) * 100 else 0.0

        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val firstTime = firstRideTimestamp?.let { timeFormat.format(Date(it)) } ?: ""
        val lastTime = lastRideTimestamp?.let { timeFormat.format(Date(it)) } ?: ""

        return DailyProjection(
            currentEarnings = currentEarnings,
            completedRides = completedRides,
            hoursWorked = hoursWorked,
            avgPerHour = avgPerHour,
            targetDay = targetDay,
            projectedWithTargetHours = projectedWithTargetHours,
            targetHours = targetHours,
            remaining = remaining,
            totalPauseHours = totalPauseHours,
            idleTimePercent = idleTimePercent,
            estimatedActiveHours = estimatedActiveHours,
            firstRideTime = firstTime,
            lastRideTime = lastTime,
        )
    }

    fun calculateWeekdayRanking(
        accepted: List<RideRecord>,
        dailyRidesMap: Map<Long, DailyRide>
    ): List<WeekdayRankItem> {
        val dayNames = listOf("Domingo", "Segunda", "Ter\u00E7a", "Quarta", "Quinta", "Sexta", "S\u00E1bado")
        val byWeekday = accepted.groupBy { getDayOfWeek(it.timestamp) }

        val maxEarnings = byWeekday.maxOfOrNull { (_, rides) ->
            rides.sumOf { ride ->
                val daily = dailyRidesMap[ride.id]
                daily?.finalValue ?: ride.value ?: 0.0
            } / rides.size.toDouble()
        }?.coerceAtLeast(0.01) ?: 1.0

        return byWeekday.map { (day, rides) ->
            val totalEarnings = rides.sumOf { ride ->
                val daily = dailyRidesMap[ride.id]
                daily?.finalValue ?: ride.value ?: 0.0
            }
            val avgEarn = if (rides.isNotEmpty()) totalEarnings / rides.size else 0.0
            val dynCount = rides.count { hasDynamicBonus(it) }
            val dynPct = if (rides.isNotEmpty()) dynCount.toDouble() / rides.size * 100 else 0.0

            val avgPpk = rides.mapNotNull { ride ->
                val daily = dailyRidesMap[ride.id]
                val finalValue = daily?.finalValue ?: ride.value ?: return@mapNotNull null
                val distance = ride.distanceKm ?: return@mapNotNull null
                if (distance > 0) finalValue / distance else null
            }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

            WeekdayRankItem(
                dayLabel = dayNames[day - 1],
                dayOfWeek = day,
                avgEarnings = avgEarn,
                rideCount = rides.size,
                dynamicPercent = dynPct,
                avgPricePerKm = avgPpk,
                barFraction = (avgEarn / maxEarnings).toFloat(),
                position = 0
            )
        }.sortedByDescending { it.avgEarnings }
            .mapIndexed { idx, item -> item.copy(position = idx + 1) }
            .take(7)
    }

    private fun getDayOfWeek(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(Calendar.DAY_OF_WEEK)
    }

    private fun getTotalBonus(ride: RideRecord): Double {
        var total = (ride.priorityBonus ?: 0.0) + (ride.dynamicBonus ?: 0.0)
        if (total == 0.0 && ride.bonusAmount != null && ride.bonusAmount > 0) {
            total = ride.bonusAmount
        }
        return total
    }

    internal fun hasPriorityBonus(ride: RideRecord): Boolean =
        (ride.priorityBonus != null && ride.priorityBonus > 0) ||
        (ride.bonusAmount != null && ride.bonusAmount > 0)

    internal fun hasDynamicBonus(ride: RideRecord): Boolean =
        ride.dynamicBonus != null && ride.dynamicBonus > 0

    fun getHourOfDay(timestamp: Long): Int =
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
        val lastDash = address.lastIndexOf(" - ")
        if (lastDash >= 0) return address.substring(lastDash + 3).trim()
        return null
    }

    fun extractNeighborhood(address: String?): String? {
        if (address.isNullOrBlank()) return null
        val match = Regex("""-\s*([A-Za-zÀ-ÿ\s]+?)\s*-""").find(address)
        return match?.groupValues?.get(1)?.trim()
    }

    fun hoursMinutes(totalMin: Int): String =
        if (totalMin > 0) "${totalMin / 60}h${totalMin % 60}min" else "0min"
}

// ═══════════════════════════════════════════════════════════════════
// Floor Simulation: Accept Threshold Simulator
// ═══════════════════════════════════════════════════════════════════

data class Scenario(
    val thresholdPerKm: Double,
    val acceptedCount: Int,
    val avgPricePerKm: Double,
    val effectivePerHour: Double,
    val netGainVsActual: Double,
    val avgPricePerHour: Double,
    val totalEarnings: Double = 0.0,
    val totalKm: Double = 0.0,
)

data class FloorSimulation(
    val scenarios: List<Scenario>,
    val recommendedThreshold: Double,
    val breakEvenKm: Double,
    val totalRides: Int,
)

fun calculateFloorSimulation(
    rides: List<RideRecord>,
    costPerKm: Double,
): FloorSimulation? {

    if (rides.isEmpty()) return null

    val pricesPerKm = rides.mapNotNull { it.pricePerKm }.sorted()
    if (pricesPerKm.isEmpty()) return null

    val p25 = pricesPerKm[(pricesPerKm.size * 0.25).toInt().coerceIn(0, pricesPerKm.size - 1)]
    val p50 = pricesPerKm[(pricesPerKm.size * 0.50).toInt().coerceIn(0, pricesPerKm.size - 1)]
    val p75 = pricesPerKm[(pricesPerKm.size * 0.75).toInt().coerceIn(0, pricesPerKm.size - 1)]

    val thresholds = listOf(
        costPerKm,
        (costPerKm + p25) / 2,
        p25,
        (p25 + p50) / 2,
        p50,
        (p50 + p75) / 2,
        p75
    ).distinct().sorted()

    // --- Time-aware realistic projection ---
    val cal = Calendar.getInstance()

    // Group offered rides by day
    val byDay: Map<String, List<RideRecord>> = rides.groupBy { ride ->
        cal.timeInMillis = ride.timestamp
        "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    // For each day, calculate online time (window minus pauses) and avg ride duration
    data class DayInfo(
        val key: String,
        val offered: List<RideRecord>,
        val onlineMinutes: Long,
        val avgRideTimeMin: Double,
    )

    val daysInfo = byDay.map { (key, dayOffered) ->
        val sorted = dayOffered.sortedBy { it.timestamp }

        // Janela total (primeira à última oferta)
        val windowMinutes = (sorted.last().timestamp - sorted.first().timestamp) / 60_000L

        // Descontar pausas > 60 min entre ofertas consecutivas
        var pauseMinutes = 0L
        for (i in 1 until sorted.size) {
            val gap = (sorted[i].timestamp - sorted[i - 1].timestamp) / 60_000L
            if (gap > 60) pauseMinutes += gap
        }
        val onlineMinutes = (windowMinutes - pauseMinutes).coerceAtLeast(30L)

        // Duração média das corridas deste dia
        val times = sorted.mapNotNull { it.timeMin }.filter { it > 0 }
        val avgTime = if (times.isNotEmpty()) times.average().coerceIn(10.0, 120.0) else 30.0

        DayInfo(key, dayOffered, onlineMinutes, avgTime)
    }

    val scenarios = thresholds.map { threshold ->
        var totalEarnings = 0.0
        var totalKm = 0.0
        var totalAccepted = 0

        for (day in daysInfo) {
            // Simula aceitar corridas na ordem em que aparecem (por timestamp)
            val eligible = day.offered
                .filter { (it.pricePerKm ?: 0.0) >= threshold }
                .sortedBy { it.timestamp }

            if (eligible.isEmpty()) continue

            val maxPerDay = (day.onlineMinutes / day.avgRideTimeMin).toInt().coerceIn(1, 50)
            val selected = eligible.take(maxPerDay)

            totalAccepted += selected.size
            totalEarnings += selected.sumOf { it.value ?: 0.0 }
            totalKm += selected.sumOf { it.distanceKm ?: 0.0 }
        }

        val avgPricePerKm = if (totalKm > 0) totalEarnings / totalKm else 0.0
        val netEarnings = totalEarnings - totalKm * costPerKm
        val totalShiftMinutes = daysInfo.sumOf { it.onlineMinutes }
        val totalShiftHours = totalShiftMinutes / 60.0
        val effectivePerHour = if (totalShiftHours > 0) netEarnings / totalShiftHours else 0.0
        val netGainVsActual = avgPricePerKm - pricesPerKm.average()

        Scenario(
            thresholdPerKm = threshold,
            acceptedCount = totalAccepted,
            avgPricePerKm = avgPricePerKm,
            effectivePerHour = effectivePerHour,
            netGainVsActual = netGainVsActual,
            avgPricePerHour = if (totalShiftHours > 0) totalEarnings / totalShiftHours else 0.0,
            totalEarnings = totalEarnings,
            totalKm = totalKm,
        )
    }

    val best = scenarios.maxByOrNull { it.effectivePerHour }
    val recommended = best?.thresholdPerKm ?: p75

    return FloorSimulation(
        scenarios = scenarios,
        recommendedThreshold = recommended,
        breakEvenKm = costPerKm,
        totalRides = rides.size,
    )
}

// ═══════════════════════════════════════════════════════════════════
// Breakeven Analysis: Ride-level Profit/Loss
// ═══════════════════════════════════════════════════════════════════

data class BreakevenRide(
    val earnings: Double,
    val distanceKm: Double,
    val pricePerKm: Double,
    val costPerKm: Double,
    val marginPerKm: Double,
    val netResult: Double,
    val origin: String,
    val hour: Int,
)

data class BreakevenAnalysisResult(
    val costPerKm: Double,
    val aboveBreakeven: List<BreakevenRide>,
    val belowBreakeven: List<BreakevenRide>,
    val totalNetProfit: Double,
    val totalNetLoss: Double,
    val netBalance: Double,
    val avgMarginAbove: Double,
    val avgMarginBelow: Double,
    val worstHour: Int,
    val bestHour: Int,
    val pctAbove: Double,
)

fun calculateBreakevenAnalysis(
    acceptedRides: List<RideRecord>,
    costPerKm: Double,
): BreakevenAnalysisResult {

    if (acceptedRides.isEmpty() || costPerKm <= 0) return BreakevenAnalysisResult(
        costPerKm, emptyList(), emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0, -1, -1, 0.0
    )

    val rides = acceptedRides.map { r ->
        val ppk = r.pricePerKm ?: run {
            val d = r.distanceKm ?: 0.0
            val v = r.value ?: 0.0
            if (d > 0) v / d else 0.0
        }
        val value = r.value ?: 0.0
        val dist = r.distanceKm ?: 0.0
        val margin = ppk - costPerKm
        BreakevenRide(
            earnings = value,
            distanceKm = dist,
            pricePerKm = ppk,
            costPerKm = costPerKm,
            marginPerKm = margin,
            netResult = value - (dist * costPerKm),
            origin = r.pickupAddress ?: "",
            hour = AnalysisHelperV2.getHourOfDay(r.timestamp),
        )
    }

    val above = rides.filter { it.marginPerKm >= 0 }
    val below = rides.filter { it.marginPerKm < 0 }

    return BreakevenAnalysisResult(
        costPerKm = costPerKm,
        aboveBreakeven = above.sortedByDescending { it.marginPerKm },
        belowBreakeven = below.sortedBy { it.marginPerKm },
        totalNetProfit = above.sumOf { it.netResult },
        totalNetLoss = below.sumOf { -it.netResult },
        netBalance = rides.sumOf { it.netResult },
        avgMarginAbove = if (above.isEmpty()) 0.0 else above.sumOf { it.marginPerKm } / above.size,
        avgMarginBelow = if (below.isEmpty()) 0.0 else below.sumOf { it.marginPerKm } / below.size,
        worstHour = below.groupBy { it.hour }.maxByOrNull { it.value.size }?.key ?: -1,
        bestHour = above.groupBy { it.hour }
            .maxByOrNull { (_, v) -> v.sumOf { r -> r.marginPerKm } / v.size }
            ?.key ?: -1,
        pctAbove = if (rides.isEmpty()) 0.0 else above.size.toDouble() / rides.size * 100,
    )
}

fun calculateScoreTrend(
    accepted: List<RideRecord>,
    dailyRidesMap: Map<Long, DailyRide>
): List<ScoreTrendDay> {
    if (accepted.size < 3) return emptyList()

    val byDay = accepted.groupBy {
        val cal = Calendar.getInstance()
        cal.timeInMillis = it.timestamp
        "${cal.get(Calendar.DAY_OF_MONTH).let { if (it < 10) "0$it" else "$it" }}/${(cal.get(Calendar.MONTH) + 1).let { if (it < 10) "0$it" else "$it" }}"
    }

    val maxScore = byDay.maxOfOrNull { (_, rides) ->
        rides.mapNotNull { it.scorePercent }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    }?.coerceAtLeast(0.01) ?: 1.0

    return byDay.entries.map { (day, rides) ->
        val scores = rides.mapNotNull { it.scorePercent }
        val avgScore = if (scores.isNotEmpty()) scores.average() else 0.0
        val goodCount = scores.count { it >= 80 }
        ScoreTrendDay(
            dayLabel = day,
            avgScore = avgScore,
            rideCount = rides.size,
            goodPercent = if (scores.isNotEmpty()) goodCount.toDouble() / scores.size * 100 else 0.0,
            barFraction = (avgScore / maxScore).toFloat()
        )
    }.sortedBy { it.dayLabel }
}

fun calculateMultipleStopsImpact(
    accepted: List<RideRecord>,
    dailyRidesMap: Map<Long, DailyRide>
): MultipleStopsImpact? {
    val withStops = accepted.filter { it.hasMultipleStops }
    val withoutStops = accepted.filter { !it.hasMultipleStops }

    if (withStops.isEmpty() || withoutStops.isEmpty()) return null

    fun calcStats(rides: List<RideRecord>): StopStats {
        val values = rides.mapNotNull { ride ->
            val daily = dailyRidesMap[ride.id]
            daily?.finalValue ?: ride.value
        }
        val distances = rides.mapNotNull { it.distanceKm }
        val times = rides.mapNotNull { it.timeMin }
        return StopStats(
            avgValue = if (values.isNotEmpty()) values.average() else 0.0,
            avgPricePerKm = rides.mapNotNull { ride ->
                val daily = dailyRidesMap[ride.id]
                val v = daily?.finalValue ?: ride.value ?: return@mapNotNull null
                val d = ride.distanceKm ?: return@mapNotNull null
                if (d > 0) v / d else null
            }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            avgPricePerHour = rides.mapNotNull { ride ->
                val daily = dailyRidesMap[ride.id]
                val v = daily?.finalValue ?: ride.value ?: return@mapNotNull null
                val t = ride.timeMin ?: return@mapNotNull null
                if (t > 0) v / (t / 60.0) else null
            }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            avgDistance = if (distances.isNotEmpty()) distances.average() else 0.0,
            avgTime = if (times.isNotEmpty()) times.average().toInt() else 0,
            count = rides.size
        )
    }

    return MultipleStopsImpact(
        withStops = calcStats(withStops),
        withoutStops = calcStats(withoutStops),
        totalWithStops = withStops.size,
        totalWithoutStops = withoutStops.size
    )
}

fun calculateRejectionPatterns(
    offered: List<RideRecord>,
    accepted: List<RideRecord>
): RejectionPatterns {
    val acceptedIds = accepted.map { it.id }.toSet()
    val lost = offered.filter { it.id !in acceptedIds }
    val totalLost = lost.size
    val totalLostValue = lost.mapNotNull { it.value }.sum()

    if (lost.isEmpty()) return RejectionPatterns(emptyList(), emptyList(), emptyList(), 0, 0.0)

    fun calcRangeStats(
        items: List<RideRecord>,
        groupFn: (RideRecord) -> String,
        sortKey: (Map.Entry<String, List<RideRecord>>) -> Int
    ): List<RangeStat> {
        val grouped = items.groupBy(groupFn)
        return grouped.entries.sortedBy(sortKey).map { (label, rides) ->
            RangeStat(
                label = label,
                count = rides.size,
                avgValue = rides.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
                avgPricePerKm = rides.mapNotNull { it.pricePerKm }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
                percentOfTotal = rides.size.toDouble() / totalLost * 100
            )
        }
    }

    val byValue = calcRangeStats(lost, { ride ->
        val v = ride.value ?: 0.0
        when {
            v < 10 -> "< R\$10"
            v < 20 -> "R\$10-20"
            v < 30 -> "R\$20-30"
            v < 50 -> "R\$30-50"
            else -> "R\$50+"
        }
    }) { entry ->
        val order = listOf("< R\$10", "R\$10-20", "R\$20-30", "R\$30-50", "R\$50+")
        order.indexOf(entry.key).let { if (it < 0) Int.MAX_VALUE else it }
    }

    val byDistance = calcRangeStats(lost, { ride ->
        val d = ride.distanceKm ?: 0.0
        when {
            d <= 0 -> "0 km"
            d < 3 -> "< 3 km"
            d < 8 -> "3-8 km"
            d < 15 -> "8-15 km"
            else -> "15+ km"
        }
    }) { entry ->
        val order = listOf("0 km", "< 3 km", "3-8 km", "8-15 km", "15+ km")
        order.indexOf(entry.key).let { if (it < 0) Int.MAX_VALUE else it }
    }

    val byHour = calcRangeStats(lost, { ride ->
        val h = AnalysisHelperV2.getHourOfDay(ride.timestamp)
        when (h) {
            in 0..5 -> "Madrugada"
            in 6..11 -> "Manhã"
            in 12..17 -> "Tarde"
            else -> "Noite"
        }
    }) { entry ->
        val order = listOf("Madrugada", "Manhã", "Tarde", "Noite")
        order.indexOf(entry.key).let { if (it < 0) Int.MAX_VALUE else it }
    }

    return RejectionPatterns(
        byValue = byValue,
        byDistance = byDistance,
        byHour = byHour,
        totalLost = totalLost,
        totalLostValue = totalLostValue
    )
}

fun AnalysisHelperV2.calculateServiceTypeStats(
    offered: List<RideRecord>,
    accepted: List<RideRecord>,
    dailyRidesMap: Map<Long, DailyRide>,
    totalEarnings: Double
): List<ServiceTypeStats> {
    val acceptedIds = accepted.map { it.id }.toSet()
    val byType = offered.groupBy { if (it.serviceType.isNullOrBlank()) "UberX" else it.serviceType!! }
        val typePriority = listOf("UberX", "Comfort", "Black", "Pop", "99Pop", "99Top", "99Black", "Moto", "Entrega", "Flash")

        return byType.entries.sortedBy { (key, _) ->
            val idx = typePriority.indexOf(key)
            if (idx >= 0) idx else Int.MAX_VALUE
        }.map { (type, rides) ->
            val acceptedRides = rides.filter { it.id in acceptedIds }
            val acceptedCount = acceptedRides.size
            val offeredCount = rides.size
            val acceptanceRate = if (offeredCount > 0) acceptedCount.toDouble() / offeredCount * 100 else 0.0

            val totalEarningsType = acceptedRides.sumOf { ride ->
                val daily = dailyRidesMap[ride.id]
                daily?.finalValue ?: ride.value ?: 0.0
            }

            val avgPpk = acceptedRides.mapNotNull { ride ->
                val daily = dailyRidesMap[ride.id]
                val v = daily?.finalValue ?: ride.value ?: return@mapNotNull null
                val d = ride.distanceKm ?: return@mapNotNull null
                if (d > 0) v / d else null
            }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

            val avgPph = acceptedRides.mapNotNull { ride ->
                val daily = dailyRidesMap[ride.id]
                val v = daily?.finalValue ?: ride.value ?: return@mapNotNull null
                val t = ride.timeMin ?: return@mapNotNull null
                if (t > 0) v / (t / 60.0) else null
            }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

            val avgRating = acceptedRides.mapNotNull { it.rating }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

            val scored = rides.filter { it.scorePercent != null }
            val goodCount = scored.count { it.scorePercent!! >= 80 }
            val goodPct = if (scored.isNotEmpty()) goodCount.toDouble() / scored.size * 100 else 0.0

            val lostRides = rides.filter { it.id !in acceptedIds }
            val lostValue = lostRides.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

            val dynCount = rides.count { AnalysisHelperV2.hasDynamicBonus(it) }
            val dynPct = if (offeredCount > 0) dynCount.toDouble() / offeredCount * 100 else 0.0

            val priCount = rides.count { AnalysisHelperV2.hasPriorityBonus(it) }
            val priPct = if (offeredCount > 0) priCount.toDouble() / offeredCount * 100 else 0.0

            val bestHour = acceptedRides.groupBy { AnalysisHelperV2.getHourOfDay(it.timestamp) }
                .maxByOrNull { (_, list) -> list.size }?.key?.let { "$it:00" } ?: "-"

            val earningsPct = if (totalEarnings > 0) totalEarningsType / totalEarnings * 100 else 0.0

            ServiceTypeStats(
                serviceType = type,
                offeredCount = offeredCount,
                acceptedCount = acceptedCount,
                acceptanceRate = acceptanceRate,
                totalEarnings = totalEarningsType,
                avgPricePerKm = avgPpk,
                avgPricePerHour = avgPph,
                avgRating = avgRating,
                goodPercent = goodPct,
                lostCount = lostRides.size,
                avgLostValue = lostValue,
                dynamicPercent = dynPct,
                priorityPercent = priPct,
                bestHour = bestHour,
                earningsPercentOfTotal = earningsPct
            )
        }
    }

data class DriverInputData(
    val totalKmPanel: Double,
    val totalHoursWorked: Double
)

data class AnalysisExtendedResult(
    val base: AnalysisResultV2,
    val emptyKm: Double,
    val efficiencyPercent: Double,
    val actualConsumption: Double,
    val driverInput: DriverInputData?
)

fun AnalysisHelperV2.calculateEfficiency(
    result: AnalysisResultV2,
    driverInput: DriverInputData?
): AnalysisExtendedResult {
    if (driverInput == null || driverInput.totalKmPanel <= 0) {
        return AnalysisExtendedResult(result, 0.0, 100.0, 0.0, null)
    }

    val totalKmFromRides = result.totalKm
    val emptyKm = maxOf(0.0, driverInput.totalKmPanel - totalKmFromRides)
    val efficiencyPercent = if (driverInput.totalKmPanel > 0) {
        maxOf(0.0, 100.0 - (emptyKm / driverInput.totalKmPanel * 100))
    } else 100.0

    val actualConsumption = if (driverInput.totalHoursWorked > 0) {
        driverInput.totalKmPanel / driverInput.totalHoursWorked
    } else 0.0

    return AnalysisExtendedResult(result, emptyKm, efficiencyPercent, actualConsumption, driverInput)
}
