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
        val hasValue = record.value != null && record.value > 0
        if (!hasValue) return false
        val hasDistance = (record.distanceKm != null && record.distanceKm > 0) ||
            ((record.pickupDistanceKm ?: 0.0) + (record.tripDistanceKm ?: 0.0)) > 0
        val hasTime = (record.timeMin != null && record.timeMin > 0) ||
            ((record.pickupTimeMin ?: 0) + (record.tripTimeMin ?: 0)) > 0
        val hasMetrics = record.pricePerKm != null || record.pricePerHour != null
        val hasAddress = !record.pickupAddress.isNullOrBlank() || !record.dropoffAddress.isNullOrBlank()
        return hasDistance || hasTime || hasMetrics || hasAddress
    }

    fun recoverRideFromRawLogs(record: RideRecord, db: DatabaseHelper): RideRecord {
        val hash = record.cardHash ?: return record
        val rawData = db.getRawRideDataByCardHash(hash) ?: return record
        val lines = rawData.lines()
        fun parseLine(prefix: String): String? {
            return lines.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
                ?.removePrefix(prefix)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        }

        val rawDist = parseLine("Distance:")?.toDoubleOrNull()
        val rawTime = parseLine("Time:")?.toIntOrNull()
        val rawPickup = parseLine("PickupAddress:")
        val rawDropoff = parseLine("DropoffAddress:")
        val rawValue = parseLine("Value:")?.toDoubleOrNull()
        val rawRating = parseLine("Rating:")?.toDoubleOrNull()

        val distOk = (record.distanceKm != null && record.distanceKm > 0) ||
            ((record.pickupDistanceKm ?: 0.0) + (record.tripDistanceKm ?: 0.0)) > 0
        val timeOk = (record.timeMin != null && record.timeMin > 0) ||
            ((record.pickupTimeMin ?: 0) + (record.tripTimeMin ?: 0)) > 0
        val addressOk = !record.pickupAddress.isNullOrBlank() || !record.dropoffAddress.isNullOrBlank()

        val recoveredDist = if (!distOk && rawDist != null) rawDist else null
        val recoveredTime = if (!timeOk && rawTime != null) rawTime else null
        val recoveredPickup = if (!addressOk && rawPickup != null) rawPickup else null
        val recoveredDropoff = if (!addressOk && rawDropoff != null) rawDropoff else null
        val recoveredValue = if ((record.value == null || record.value <= 0) && rawValue != null) rawValue else null
        val recoveredRating = if ((record.rating == null || record.rating <= 0) && rawRating != null) rawRating else null

        if (recoveredDist == null && recoveredTime == null && recoveredPickup == null &&
            recoveredDropoff == null && recoveredValue == null && recoveredRating == null) return record

        return record.copy(
            distanceKm = recoveredDist ?: record.distanceKm,
            timeMin = recoveredTime ?: record.timeMin,
            pickupAddress = recoveredPickup ?: record.pickupAddress,
            dropoffAddress = recoveredDropoff ?: record.dropoffAddress,
            value = recoveredValue ?: record.value,
            rating = recoveredRating ?: record.rating
        )
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
