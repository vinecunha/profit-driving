package com.profitdriving.support

enum class SupportStatus {
    PENDING, ANALYZING, RESOLVED, REJECTED, NEEDS_INFO
}

enum class SupportPriority {
    NORMAL, HIGH, CRITICAL
}

data class SupportReport(
    val id: Long = 0,
    val rawLogId: Long? = null,
    val rideId: Long? = null,
    val cardHash: String? = null,
    val userNotes: String? = null,
    val status: SupportStatus = SupportStatus.PENDING,
    val priority: SupportPriority = SupportPriority.NORMAL,
    val deviceModel: String? = null,
    val androidVersion: String? = null,
    val appVersion: String? = null,
    val screenshotPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val resolutionNotes: String? = null
)
