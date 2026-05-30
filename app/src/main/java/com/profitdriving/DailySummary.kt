package com.profitdriving

data class DailySummary(
    val rideCount: Int = 0,
    val totalDistance: Double = 0.0,
    val totalDuration: Int = 0,
    val grossRevenue: Double = 0.0,
    val totalTips: Double = 0.0,
    val totalAdjustments: Double = 0.0,
    val totalCost: Double = 0.0,
    val netProfit: Double = 0.0,
    val profitPercent: Double = 0.0,
    val revenuePerKm: Double = 0.0,
    val revenuePerHour: Double = 0.0,
    val avgTip: Double = 0.0
)
