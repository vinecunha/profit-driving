package com.profitdriving

import java.security.MessageDigest
import java.util.Locale

object CardHashGenerator {

    fun generateStableHash(
        serviceType: String?,
        pickupAddress: String?,
        dropoffAddress: String?,
        rating: Double?
    ): String {
        val normalizedService = normalizeServiceType(serviceType)
        val normalizedPickup = normalizeAddress(pickupAddress)
        val normalizedDropoff = normalizeAddress(dropoffAddress)
        val normalizedRating = normalizeRating(rating)

        val input = listOf(
            normalizedService,
            normalizedPickup,
            normalizedDropoff,
            normalizedRating
        ).filter { it.isNotEmpty() }
            .joinToString("|")

        return sha256(input)
    }

    private fun normalizeServiceType(serviceType: String?): String {
        if (serviceType.isNullOrBlank()) return ""
        return serviceType
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .let { when {
                it.contains("uberx") -> "uberx"
                it.contains("black") -> "black"
                it.contains("comfort") -> "comfort"
                it.contains("moto") -> "moto"
                it.contains("flash") -> "flash"
                it.contains("juntos") -> "juntos"
                it.contains("priority") -> "priority"
                it.contains("99pop") -> "99pop"
                it.contains("99top") -> "99top"
                it.contains("99black") -> "99black"
                it.contains("99moto") -> "99moto"
                else -> it
            } }
    }

    private fun normalizeAddress(address: String?): String {
        if (address.isNullOrBlank()) return ""
        return address
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""\d+"""), "")
            .replace(Regex("""[^\p{L}\s]"""), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
    }

    private fun normalizeRating(rating: Double?): String {
        if (rating == null || rating <= 0) return ""
        return String.format("%.1f", rating)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
