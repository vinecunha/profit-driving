package com.profitdriving

object ConsumptionCalculator {

    fun calculateConsumption(refuels: List<RefuelRecord>): ConsumptionResult {
        if (refuels.size < 2) {
            return ConsumptionResult(
                totalKm = 0.0,
                totalCost = 0.0,
                totalLiters = 0.0,
                costPerKm = 0.0,
                consumptionKmPerLiter = 0.0,
                method = CalculationMethod.INSUFFICIENT_DATA,
                detailsByType = emptyMap()
            )
        }

        val sorted = refuels.sortedBy { it.odometerKm }
        val firstKm = sorted.first().odometerKm
        val lastKm = sorted.last().odometerKm
        val totalKm = lastKm - firstKm
        val totalCost = refuels.sumOf { it.totalValue }
        val totalLiters = refuels.sumOf { it.amount }

        val costPerKm = if (totalKm > 0) totalCost / totalKm else 0.0
        val consumptionKmPerLiter = if (totalLiters > 0) totalKm / totalLiters else 0.0

        val detailsByType = calculateDetailsByType(sorted)

        return ConsumptionResult(
            totalKm = totalKm,
            totalCost = totalCost,
            totalLiters = totalLiters,
            costPerKm = costPerKm,
            consumptionKmPerLiter = consumptionKmPerLiter,
            method = detectCalculationMethod(refuels),
            detailsByType = detailsByType
        )
    }

    private fun calculateDetailsByType(
        sorted: List<RefuelRecord>
    ): Map<FuelType, TypeDetail> {
        val fuelTypes = sorted.map { FuelType.fromDbValue(it.fuelType) }.distinct()
        val totalLitersAll = sorted.sumOf { it.amount }

        return fuelTypes.associateWith { type ->
            val typeRefuels = sorted.filter { FuelType.fromDbValue(it.fuelType) == type }
            val liters = typeRefuels.sumOf { it.amount }
            val cost = typeRefuels.sumOf { it.totalValue }
            val percentage = if (totalLitersAll > 0) (liters / totalLitersAll) * 100 else 0.0

            TypeDetail(
                liters = liters,
                cost = cost,
                percentageOfTotal = percentage
            )
        }
    }

    private fun detectCalculationMethod(refuels: List<RefuelRecord>): CalculationMethod {
        val fuelTypes = refuels.map { it.fuelType }.distinct()

        return when {
            fuelTypes.size == 1 -> CalculationMethod.SINGLE_FUEL
            fuelTypes.size > 1 -> {
                val counts = refuels.groupingBy { it.fuelType }.eachCount()
                val minCount = counts.minByOrNull { it.value }?.value ?: 0
                if (minCount <= 2) CalculationMethod.PRIMARY_WITH_SECONDARY
                else CalculationMethod.MULTIPLE_SIMULTANEOUS
            }
            else -> CalculationMethod.UNKNOWN
        }
    }

    data class ConsumptionResult(
        val totalKm: Double,
        val totalCost: Double,
        val totalLiters: Double,
        val costPerKm: Double,
        val consumptionKmPerLiter: Double,
        val method: CalculationMethod,
        val detailsByType: Map<FuelType, TypeDetail>
    )

    data class TypeDetail(
        val liters: Double,
        val cost: Double,
        val percentageOfTotal: Double
    )

    enum class CalculationMethod {
        SINGLE_FUEL,
        PRIMARY_WITH_SECONDARY,
        MULTIPLE_SIMULTANEOUS,
        INSUFFICIENT_DATA,
        UNKNOWN
    }
}
