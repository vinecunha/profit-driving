package com.profitdriving

import java.util.Calendar

object CostCalculator {

    fun calculateMonthlyFixedCost(
        value: Double,
        periodicity: Periodicity,
        usefulLifeMonths: Int?,
        paymentStatus: String = "PENDING",
        paidAmount: Double = 0.0,
        installmentTotal: Int = 1,
        installmentCurrent: Int = 1
    ): Double {
        if (paymentStatus == "PAID") return 0.0

        val baseMonthly = when (periodicity) {
            Periodicity.YEARLY -> value / 12
            Periodicity.MONTHLY -> value
        }

        if (paymentStatus == "PARTIAL") {
            val remainingMonths = (installmentTotal - installmentCurrent).coerceAtLeast(1)
            val remainingValue = (value - paidAmount).coerceAtLeast(0.0)
            val perPeriod = if (periodicity == Periodicity.YEARLY) remainingValue / 12 else remainingValue
            return (perPeriod / remainingMonths * installmentTotal).coerceAtMost(baseMonthly)
        }

        return baseMonthly
    }

    fun calculateCostPerKm(monthlyCost: Double, monthlyKm: Double): Double {
        return if (monthlyKm > 0) monthlyCost / monthlyKm else 0.0
    }

    fun calculateCostSummary(
        refuels: List<RefuelRecord>,
        expenses: List<Expense>,
        monthlyKm: Int,
        currentFuelType: String = "gasoline",
        averageRevenuePerKm: Double = 0.0
    ): CostSummary {
        val avgConsumption = calculateAvgConsumption(refuels, currentFuelType)
        val avgPricePerLiter = refuels.filter { it.fuelType == currentFuelType && it.liters > 0 }
            .map { it.pricePerLiter }
            .average().takeIf { it > 0 } ?: 0.0

        val fuelCostPerKm = if (avgConsumption > 0) avgPricePerLiter / avgConsumption else 0.0

        // Separate expenses by type
        val fixedExpenses = expenses.filter { it.costType == CostType.FIXED }
        val perKmExpenses = expenses.filter { it.costType == CostType.PER_KM }
        val eventExpenses = expenses.filter { it.costType == CostType.EVENT }

        // Fixed costs
        var totalFixedMonthlyCost = 0.0
        val fixedNormalizedExpenses = fixedExpenses.map { expense ->
            val monthlyCost = calculateMonthlyFixedCost(
                value = expense.value,
                periodicity = expense.periodicity ?: Periodicity.MONTHLY,
                usefulLifeMonths = expense.usefulLifeMonths,
                paymentStatus = expense.paymentStatus,
                paidAmount = expense.paidAmount,
                installmentTotal = expense.installmentTotal,
                installmentCurrent = expense.installmentCurrent
            )
            totalFixedMonthlyCost += monthlyCost

            NormalizedExpense(
                name = expense.name,
                monthlyCost = monthlyCost,
                costPerKm = calculateCostPerKm(monthlyCost, monthlyKm.toDouble()),
                category = expense.category.display,
                periodicity = expense.periodicity?.name?.lowercase()
            )
        }

        // Variable costs (per km)
        val variableCostPerKm = perKmExpenses.sumOf { expense ->
            if (expense.percentageOfProfit != null && averageRevenuePerKm > 0) {
                val profitPerKm = averageRevenuePerKm - fuelCostPerKm -
                    perKmExpenses.filter { it.id != expense.id }
                        .sumOf { it.value }
                (profitPerKm * expense.percentageOfProfit / 100.0).coerceAtLeast(0.0)
            } else {
                expense.value
            }
        }

        val variableNormalizedExpenses = perKmExpenses.map { expense ->
            val cost = if (expense.percentageOfProfit != null && averageRevenuePerKm > 0) {
                val profitPerKm = averageRevenuePerKm - fuelCostPerKm -
                    perKmExpenses.filter { it.id != expense.id }
                        .sumOf { it.value }
                (profitPerKm * expense.percentageOfProfit / 100.0).coerceAtLeast(0.0)
            } else {
                expense.value
            }
            NormalizedExpense(
                name = expense.name,
                monthlyCost = cost * monthlyKm,
                costPerKm = cost,
                category = expense.category.display,
                periodicity = null
            )
        }

        // Event costs
        val eventCostPerKm = eventExpenses.sumOf { expense ->
            val eventsPerMonth = expense.estimatedEventsPerMonth ?: 1
            val monthlyEventCost = expense.value * eventsPerMonth
            calculateCostPerKm(monthlyEventCost, monthlyKm.toDouble())
        }

        val eventNormalizedExpenses = eventExpenses.map { expense ->
            val eventsPerMonth = expense.estimatedEventsPerMonth ?: 1
            val monthlyEventCost = expense.value * eventsPerMonth
            val costPerKm = calculateCostPerKm(monthlyEventCost, monthlyKm.toDouble())
            NormalizedExpense(
                name = expense.name,
                monthlyCost = monthlyEventCost,
                costPerKm = costPerKm,
                category = expense.category.display,
                periodicity = null
            )
        }

        val allNormalizedExpenses = fixedNormalizedExpenses + variableNormalizedExpenses + eventNormalizedExpenses

        val fixedCostPerKm = if (monthlyKm > 0) totalFixedMonthlyCost / monthlyKm else 0.0
        val totalVariableCostPerKm = fuelCostPerKm + variableCostPerKm + eventCostPerKm
        val totalCostPerKm = fixedCostPerKm + totalVariableCostPerKm
        val costPerHour = totalCostPerKm * 30
        val costPerMinute = costPerHour / 60

        return CostSummary(
            avgConsumption = avgConsumption,
            fuelCostPerKm = fuelCostPerKm,
            normalizedExpenses = allNormalizedExpenses,
            totalFixedMonthlyCost = totalFixedMonthlyCost,
            fixedCostPerKm = fixedCostPerKm,
            variableCostPerKm = totalVariableCostPerKm,
            totalCostPerKm = totalCostPerKm,
            costPerHour = costPerHour,
            costPerMinute = costPerMinute
        )
    }

    fun calculateMonthlyKm(refuels: List<RefuelRecord>): Map<Pair<Int, Int>, Double> {
        val result = mutableMapOf<Pair<Int, Int>, Double>()
        val grouped = refuels.groupBy {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }
        grouped.forEach { (period, list) ->
            val sorted = list.sortedBy { it.odometerKm }
            if (sorted.size >= 2) {
                val km = sorted.last().odometerKm - sorted.first().odometerKm
                if (km > 0) result[period] = km
            }
        }
        return result
    }

    fun calculateMonthlyFuelCost(refuels: List<RefuelRecord>): Map<Pair<Int, Int>, Double> {
        val result = mutableMapOf<Pair<Int, Int>, Double>()
        val grouped = refuels.groupBy {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }
        grouped.forEach { (period, list) ->
            result[period] = list.sumOf { it.totalValue }
        }
        return result
    }

    fun calculateMonthlyConsumption(refuels: List<RefuelRecord>): Map<Pair<Int, Int>, Double> {
        val result = mutableMapOf<Pair<Int, Int>, Double>()
        val grouped = refuels.groupBy {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }
        grouped.forEach { (period, list) ->
            val consumptions = mutableListOf<Double>()
            val sorted = list.sortedBy { it.odometerKm }
            for (i in 0 until sorted.size - 1) {
                val current = sorted[i + 1]
                val previous = sorted[i]
                val kmDiff = current.odometerKm - previous.odometerKm
                if (kmDiff > 0 && current.liters > 0) {
                    consumptions.add(kmDiff / current.liters)
                }
            }
            if (consumptions.isNotEmpty()) {
                result[period] = consumptions.average()
            }
        }
        return result
    }

    fun suggestMonthlyKmGoal(history: Map<Pair<Int, Int>, Double>): Double {
        if (history.isEmpty()) return 3000.0
        val last3Months = history.entries
            .sortedByDescending { period -> period.key.first * 12 + period.key.second }
            .take(3)
            .map { it.value }
        return if (last3Months.isEmpty()) 3000.0 else last3Months.average()
    }

    private fun calculateAvgConsumption(refuels: List<RefuelRecord>, fuelType: String? = null): Double {
        val filteredRefuels = if (fuelType != null) {
            refuels.filter { it.fuelType == fuelType && it.isFullTank && it.liters > 0 }
        } else {
            refuels.filter { it.isFullTank && it.liters > 0 }
        }
            .sortedByDescending { it.timestamp }

        if (filteredRefuels.size < 2) {
            val all = if (fuelType != null) refuels.filter { it.fuelType == fuelType } else refuels
            val sorted = all.sortedByDescending { it.timestamp }
            if (sorted.size < 2) return 0.0
            val consumptions = mutableListOf<Double>()
            for (i in 0 until sorted.size - 1) {
                val current = sorted[i]
                val previous = sorted[i + 1]
                val kmDiff = current.odometerKm - previous.odometerKm
                if (kmDiff > 0) consumptions.add(kmDiff / current.liters)
            }
            return consumptions.average().takeIf { it > 0 } ?: 0.0
        }

        val consumptions = mutableListOf<Double>()
        for (i in 0 until filteredRefuels.size - 1) {
            val current = filteredRefuels[i]
            val previous = filteredRefuels[i + 1]
            val kmDiff = current.odometerKm - previous.odometerKm
            if (kmDiff > 0) consumptions.add(kmDiff / current.liters)
        }
        return consumptions.average().takeIf { it > 0 } ?: 0.0
    }

    fun getRequiredPricePerKm(profitPercent: Int, costPerKm: Double): Double {
        return costPerKm * (1 + profitPercent / 100.0)
    }

    fun estimateProfit(rideValue: Double?, distanceKm: Double?, costPerKm: Double): Double? {
        if (rideValue == null || distanceKm == null || distanceKm <= 0) return null
        return rideValue - (distanceKm * costPerKm)
    }

    fun estimateProfitPercent(rideValue: Double?, distanceKm: Double?, costPerKm: Double): Double? {
        val profit = estimateProfit(rideValue, distanceKm, costPerKm) ?: return null
        if (rideValue == null || rideValue <= 0) return null
        return (profit / rideValue) * 100
    }

    fun simulatorRange(costPerKm: Double): Triple<Double, Double, Double> {
        val ruim = costPerKm * 1.0
        val aceitavel = costPerKm * 1.3
        val bom = costPerKm * 1.5
        return Triple(ruim, aceitavel, bom)
    }
}
