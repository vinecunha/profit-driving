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

    fun maskAddress(fullAddress: String?): String {
        if (fullAddress.isNullOrBlank()) return ""

        var masked = fullAddress

        // 1. Remover número da casa e a vírgula anterior
        masked = masked.replace(Regex(""",?\s*\d+(?:-\d+)?\s*[,]?"""), " ")
        masked = masked.replace(Regex("""\s+nº\s*\d+""", RegexOption.IGNORE_CASE), " ")

        // 2. Normalizar hífens
        masked = masked.replace(Regex("""\s*-\s*"""), " - ")

        // 3. Mascarar CEP (manter apenas 3 últimos dígitos)
        masked = masked.replace(Regex("""(\d{5})[-]?(\d{3})""")) { match ->
            val suffix = match.groupValues[2]
            "XXXXX-$suffix"
        }

        // 4. Limpar múltiplos espaços e vírgulas
        masked = masked
            .replace(Regex("\\s+"), " ")
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("\\s*,\\s*"), ", ")
            .trim()

        // 5. Remover vírgula no início ou final
        masked = masked.replace(Regex("^,|,$"), "")

        // 6. Limitar tamanho
        if (masked.length > 100) {
            masked = masked.take(100) + "..."
        }

        return masked
    }

    fun isValidRide(record: RideRecord): Boolean {
        val hasDistance = (record.distanceKm != null && record.distanceKm > 0) ||
            ((record.pickupDistanceKm ?: 0.0) + (record.tripDistanceKm ?: 0.0)) > 0
        val hasTime = (record.timeMin != null && record.timeMin > 0) ||
            ((record.pickupTimeMin ?: 0) + (record.tripTimeMin ?: 0)) > 0
        val hasAddress = !record.pickupAddress.isNullOrBlank() || !record.dropoffAddress.isNullOrBlank()
        val hasValue = record.value != null && record.value > 0
        val hasMetrics = record.pricePerKm != null || record.pricePerHour != null
        val hasRating = record.rating != null && record.rating > 0
        return hasDistance || hasTime || hasAddress || hasValue || hasMetrics || hasRating
    }

    fun deduplicateRides(rides: List<RideRecord>): List<RideRecord> {
        return rides.groupBy { record ->
            if (!record.cardHash.isNullOrBlank()) record.cardHash
            else {
                val v = (record.value ?: 0.0)
                val ts = record.timestamp / 60_000
                val dist = ((record.pickupDistanceKm ?: 0.0) + (record.tripDistanceKm ?: record.distanceKm ?: 0.0))
                val dur = ((record.pickupTimeMin ?: 0) + (record.tripTimeMin ?: record.timeMin ?: 0))
                "v=${"%.2f".format(v)}|ts=$ts|d=${"%.1f".format(dist)}|t=$dur"
            }
        }.values.map { group -> group.maxByOrNull { it.timestamp }!! }
    }
}
