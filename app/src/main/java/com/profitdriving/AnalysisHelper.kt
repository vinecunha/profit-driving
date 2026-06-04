package com.profitdriving

import java.util.Calendar
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
    val estimatedActiveHours: Double = 0.0
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
    val previousPeriodRides: Int = 0
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
            profitMargin = profitMargin
        )
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
            estimatedActiveHours = estimatedActiveHours
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
