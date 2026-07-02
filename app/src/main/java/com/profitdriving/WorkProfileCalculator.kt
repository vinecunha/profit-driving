package com.profitdriving

import com.profitdriving.models.WorkProfile
import java.util.Calendar

object WorkProfileCalculator {

    data class ProfileValues(
        val minKm: Double,
        val idealKm: Double,
        val minHour: Double,
        val idealHour: Double,
        val minMinute: Double,
        val idealMinute: Double,
        val minRating: Double,
        val idealRating: Double,
    )

    private const val MIN_RIDES_THRESHOLD = 10

    private val FALLBACK = ProfileValues(
        minKm = 2.5,
        idealKm = 4.0,
        minHour = 30.0,
        idealHour = 60.0,
        minMinute = 0.5,
        idealMinute = 1.0,
        minRating = 4.5,
        idealRating = 4.9,
    )

    private val DYNAMIC_FALLBACK = ProfileValues(
        minKm = 3.5,
        idealKm = 5.6,
        minHour = 42.0,
        idealHour = 84.0,
        minMinute = 0.7,
        idealMinute = 1.4,
        minRating = 4.7,
        idealRating = 4.93,
    )

    fun calculate(profile: WorkProfile, db: DatabaseHelper): ProfileValues {
        val threeMonthsAgo = Calendar.getInstance().apply {
            add(Calendar.MONTH, -3)
        }.timeInMillis

        return when (profile) {
            WorkProfile.BAD_DAY -> calculateBadDay(db, threeMonthsAgo)
            WorkProfile.NORMAL -> calculateNormal(db, threeMonthsAgo)
            WorkProfile.DYNAMIC -> calculateDynamic(db, threeMonthsAgo)
            WorkProfile.CUSTOM -> throw IllegalArgumentException("CUSTOM should not call calculate()")
        }
    }

    private fun calculateBadDay(db: DatabaseHelper, sinceMs: Long): ProfileValues {
        val stats = db.getRideStats(sinceMs)
        if (stats == null || stats.count < MIN_RIDES_THRESHOLD) return FALLBACK

        return ProfileValues(
            minKm = maxOf(stats.avgPricePerKm * 0.6, 0.80),
            idealKm = maxOf(stats.avgPricePerKm * 0.8, 0.80),
            minHour = maxOf(stats.avgPricePerHour * 0.6, 12.0),
            idealHour = maxOf(stats.avgPricePerHour * 0.8, 12.0),
            minMinute = maxOf(stats.avgPricePerHour / 60 * 0.6, 0.20),
            idealMinute = maxOf(stats.avgPricePerHour / 60 * 0.8, 0.20),
            minRating = maxOf(stats.avgRating - 0.3, 4.0),
            idealRating = maxOf(stats.avgRating - 0.1, 4.3),
        )
    }

    private fun calculateNormal(db: DatabaseHelper, sinceMs: Long): ProfileValues {
        val stats = db.getRideStats(sinceMs)
        if (stats == null || stats.count < MIN_RIDES_THRESHOLD) return FALLBACK

        return ProfileValues(
            minKm = stats.avgPricePerKm,
            idealKm = stats.avgPricePerKm * 1.3,
            minHour = stats.avgPricePerHour,
            idealHour = stats.avgPricePerHour * 1.3,
            minMinute = stats.avgPricePerHour / 60,
            idealMinute = stats.avgPricePerHour / 60 * 1.3,
            minRating = stats.avgRating,
            idealRating = minOf(stats.avgRating + 0.2, 5.0),
        )
    }

    private fun calculateDynamic(db: DatabaseHelper, sinceMs: Long): ProfileValues {
        val topStats = db.getRideStatsTop(sinceMs, 0.25)
        if (topStats == null || topStats.count < 5) return DYNAMIC_FALLBACK

        return ProfileValues(
            minKm = topStats.avgPricePerKm * 0.85,
            idealKm = topStats.avgPricePerKm,
            minHour = topStats.avgPricePerHour * 0.85,
            idealHour = topStats.avgPricePerHour,
            minMinute = topStats.avgPricePerHour / 60 * 0.85,
            idealMinute = topStats.avgPricePerHour / 60,
            minRating = maxOf(topStats.avgRating - 0.2, 4.0),
            idealRating = minOf(topStats.avgRating + 0.1, 5.0),
        )
    }
}
