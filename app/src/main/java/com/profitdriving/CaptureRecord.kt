package com.profitdriving

data class CaptureRecord(
    val id: String,
    val timestamp: Long,
    val appName: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val rideHash: String? = null,
    val savedToGallery: Boolean = false
)
