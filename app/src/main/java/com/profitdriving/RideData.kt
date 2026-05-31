package com.profitdriving

data class RideData(
    val value: Double?,
    val distanceKm: Double?,
    val timeMin: Int?,
    val rating: Double?,
    val appName: String,
    val pricePerKm: Double? = null,
    val pricePerHour: Double? = null,
    val detectedBy: String = "notification",
    val pickupDistanceKm: Double? = null,
    val pickupTimeMin: Int? = null,
    val tripDistanceKm: Double? = null,
    val tripTimeMin: Int? = null,
    val serviceType: String? = null,
    val stops: Int? = null,
    val priorityBonus: Double? = null,
    val dynamicBonus: Double? = null,
    val pickupAddress: String? = null,
    val dropoffAddress: String? = null,
    val hasExactStopCount: Boolean = false
) {
    val effectivePricePerKm: Double?
        get() = pricePerKm ?: if (value != null && distanceKm != null && distanceKm > 0)
            value / distanceKm else null

    val effectivePricePerHour: Double?
        get() = pricePerHour ?: if (value != null && timeMin != null && timeMin > 0)
            value / (timeMin / 60.0) else null

    val effectivePricePerMinute: Double?
        get() = if (value != null && timeMin != null && timeMin > 0)
            value / timeMin else null
}
