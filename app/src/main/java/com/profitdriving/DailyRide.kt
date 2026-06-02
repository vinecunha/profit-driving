package com.profitdriving

data class DailyRide(
    val id: Long = 0,
    val rideId: Long,
    val date: String,
    val originalValue: Double,
    val adjustedValue: Double? = null,
    val tipAmount: Double = 0.0,
    val isCompleted: Boolean = false,
    val cancelledWithFee: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val finalValue: Double
        get() = (adjustedValue ?: originalValue) + tipAmount

    val adjustmentDifference: Double
        get() = (adjustedValue ?: originalValue) - originalValue
}
