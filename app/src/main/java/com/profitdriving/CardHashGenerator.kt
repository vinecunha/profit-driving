package com.profitdriving

import java.security.MessageDigest
import java.util.Locale

object CardHashGenerator {

    private val addressCache = mutableMapOf<String, String>()

    fun clearAddressCache() {
        addressCache.clear()
    }

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
        if (fullAddress.isNullOrBlank()) return "Endereço não informado"
        addressCache[fullAddress]?.let { return it }

        var masked = fullAddress

        // 1. Remover número da casa e a vírgula anterior
        masked = masked.replace(Regex(""",?\s*\d+(?:-\d+)?\s*[,]?"""), " ")
        masked = masked.replace(Regex("""\s+nº\s*\d+""", RegexOption.IGNORE_CASE), " ")

        // 2. Remover UF e CEP no final (ex: " - RJ, 26070-584" ou ", SP 01305-000")
        masked = masked.replace(Regex("""\s*[-–,]\s*[A-Za-z]{2}(?:\s*,?\s*\d{5}-\d{3})?\s*$"""), "")
        masked = masked.replace(Regex("""\s*\d{5}-\d{3}"""), "")

        // 3. Remover traço residual no final
        masked = masked.replace(Regex("""[-–,]\s*$"""), "")

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

        val result = masked.ifBlank { "Endereço não informado" }
        addressCache[fullAddress] = result
        return result
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
        val rawLog = db.getRawCardByHash(hash)
        val rawData = rawLog?.rideDataJson ?: db.getRawRideDataByCardHash(hash) ?: return record
        val lines = rawData.lines()
        fun parseLine(prefix: String): String? {
            return lines.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
                ?.removePrefix(prefix)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        }

        val rawValue = parseLine("Value:")?.toDoubleOrNull()
        val rawDist = parseLine("Distance:")?.toDoubleOrNull()
        val rawTime = parseLine("Time:")?.toIntOrNull()
        val rawRating = parseLine("Rating:")?.toDoubleOrNull()
        val rawServiceType = parseLine("ServiceType:")
        val rawPickupAddress = parseLine("PickupAddress:")
        val rawDropoffAddress = parseLine("DropoffAddress:")
        val rawPickupDist = parseLine("PickupDistance:")?.toDoubleOrNull()
        val rawPickupTime = parseLine("PickupTime:")?.toIntOrNull()
        val rawTripDist = parseLine("TripDistance:")?.toDoubleOrNull()
        val rawTripTime = parseLine("TripTime:")?.toIntOrNull()
        val rawPriorityBonus = parseLine("PriorityBonus:")?.toDoubleOrNull()
        val rawDynamicBonus = parseLine("DynamicBonus:")?.toDoubleOrNull()

        val distOk = (record.distanceKm != null && record.distanceKm > 0) ||
            ((record.pickupDistanceKm ?: 0.0) + (record.tripDistanceKm ?: 0.0)) > 0
        val timeOk = (record.timeMin != null && record.timeMin > 0) ||
            ((record.pickupTimeMin ?: 0) + (record.tripTimeMin ?: 0)) > 0
        val addressOk = !record.pickupAddress.isNullOrBlank() || !record.dropoffAddress.isNullOrBlank()

        val hasAnyChange = listOf(
            !distOk to rawDist,
            !timeOk to rawTime,
            !addressOk to rawPickupAddress,
            !addressOk to rawDropoffAddress,
            (record.value == null || record.value <= 0) to rawValue,
            (record.rating == null || record.rating <= 0) to rawRating,
            (record.serviceType.isNullOrBlank()) to rawServiceType,
            (record.pickupDistanceKm == null || record.pickupDistanceKm <= 0) to rawPickupDist,
            (record.pickupTimeMin == null || record.pickupTimeMin <= 0) to rawPickupTime,
            (record.tripDistanceKm == null || record.tripDistanceKm <= 0) to rawTripDist,
            (record.tripTimeMin == null || record.tripTimeMin <= 0) to rawTripTime,
            (record.priorityBonus == null || record.priorityBonus <= 0) to rawPriorityBonus,
            (record.dynamicBonus == null || record.dynamicBonus <= 0) to rawDynamicBonus
        ).any { (needsRecovery, raw) -> needsRecovery && raw != null }

        if (!hasAnyChange) return record

        return record.copy(
            value = if ((record.value == null || record.value <= 0) && rawValue != null) rawValue else record.value,
            distanceKm = if (!distOk && rawDist != null) rawDist else record.distanceKm,
            timeMin = if (!timeOk && rawTime != null) rawTime else record.timeMin,
            rating = if ((record.rating == null || record.rating <= 0) && rawRating != null) rawRating else record.rating,
            serviceType = if (record.serviceType.isNullOrBlank() && rawServiceType != null) rawServiceType else record.serviceType,
            pickupAddress = if (!addressOk && rawPickupAddress != null) rawPickupAddress else record.pickupAddress,
            dropoffAddress = if (!addressOk && rawDropoffAddress != null) rawDropoffAddress else record.dropoffAddress,
            pickupDistanceKm = if ((record.pickupDistanceKm == null || record.pickupDistanceKm <= 0) && rawPickupDist != null) rawPickupDist else record.pickupDistanceKm,
            pickupTimeMin = if ((record.pickupTimeMin == null || record.pickupTimeMin <= 0) && rawPickupTime != null) rawPickupTime else record.pickupTimeMin,
            tripDistanceKm = if ((record.tripDistanceKm == null || record.tripDistanceKm <= 0) && rawTripDist != null) rawTripDist else record.tripDistanceKm,
            tripTimeMin = if ((record.tripTimeMin == null || record.tripTimeMin <= 0) && rawTripTime != null) rawTripTime else record.tripTimeMin,
            priorityBonus = if ((record.priorityBonus == null || record.priorityBonus <= 0) && rawPriorityBonus != null) rawPriorityBonus else record.priorityBonus,
            dynamicBonus = if ((record.dynamicBonus == null || record.dynamicBonus <= 0) && rawDynamicBonus != null) rawDynamicBonus else record.dynamicBonus
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

    fun recoverRidesFromRawLogs(rides: List<RideRecord>, db: DatabaseHelper): List<RideRecord> {
        if (rides.isEmpty()) return rides

        val hashes = rides.mapNotNull { it.cardHash }.filter { it.isNotBlank() }.distinct()
        if (hashes.isEmpty()) return rides

        val rawLogsMap = db.getRawCardsByHashes(hashes)

        return rides.map { ride ->
            val hash = ride.cardHash ?: return@map ride
            val rawLog = rawLogsMap[hash] ?: return@map ride
            val rawData = rawLog.rideDataJson ?: return@map ride

            val lines = rawData.lines()
            fun parseLine(prefix: String): String? {
                return lines.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
                    ?.removePrefix(prefix)?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            }

            val rawValue = parseLine("Value:")?.toDoubleOrNull()
            val rawDist = parseLine("Distance:")?.toDoubleOrNull()
            val rawTime = parseLine("Time:")?.toIntOrNull()
            val rawRating = parseLine("Rating:")?.toDoubleOrNull()
            val rawServiceType = parseLine("ServiceType:")
            val rawPickupAddress = parseLine("PickupAddress:")
            val rawDropoffAddress = parseLine("DropoffAddress:")
            val rawPickupDist = parseLine("PickupDistance:")?.toDoubleOrNull()
            val rawPickupTime = parseLine("PickupTime:")?.toIntOrNull()
            val rawTripDist = parseLine("TripDistance:")?.toDoubleOrNull()
            val rawTripTime = parseLine("TripTime:")?.toIntOrNull()
            val rawPriorityBonus = parseLine("PriorityBonus:")?.toDoubleOrNull()
            val rawDynamicBonus = parseLine("DynamicBonus:")?.toDoubleOrNull()

            val distOk = (ride.distanceKm != null && ride.distanceKm > 0) ||
                ((ride.pickupDistanceKm ?: 0.0) + (ride.tripDistanceKm ?: 0.0)) > 0
            val timeOk = (ride.timeMin != null && ride.timeMin > 0) ||
                ((ride.pickupTimeMin ?: 0) + (ride.tripTimeMin ?: 0)) > 0
            val addressOk = !ride.pickupAddress.isNullOrBlank() || !ride.dropoffAddress.isNullOrBlank()

            val hasAnyChange = listOf(
                !distOk to rawDist,
                !timeOk to rawTime,
                !addressOk to rawPickupAddress,
                !addressOk to rawDropoffAddress,
                (ride.value == null || ride.value <= 0) to rawValue,
                (ride.rating == null || ride.rating <= 0) to rawRating,
                (ride.serviceType.isNullOrBlank()) to rawServiceType,
                (ride.pickupDistanceKm == null || ride.pickupDistanceKm <= 0) to rawPickupDist,
                (ride.pickupTimeMin == null || ride.pickupTimeMin <= 0) to rawPickupTime,
                (ride.tripDistanceKm == null || ride.tripDistanceKm <= 0) to rawTripDist,
                (ride.tripTimeMin == null || ride.tripTimeMin <= 0) to rawTripTime,
                (ride.priorityBonus == null || ride.priorityBonus <= 0) to rawPriorityBonus,
                (ride.dynamicBonus == null || ride.dynamicBonus <= 0) to rawDynamicBonus
            ).any { (needsRecovery, raw) -> needsRecovery && raw != null }

            if (!hasAnyChange) return@map ride

            ride.copy(
                value = if ((ride.value == null || ride.value <= 0) && rawValue != null) rawValue else ride.value,
                distanceKm = if (!distOk && rawDist != null) rawDist else ride.distanceKm,
                timeMin = if (!timeOk && rawTime != null) rawTime else ride.timeMin,
                rating = if ((ride.rating == null || ride.rating <= 0) && rawRating != null) rawRating else ride.rating,
                serviceType = if (ride.serviceType.isNullOrBlank() && rawServiceType != null) rawServiceType else ride.serviceType,
                pickupAddress = if (!addressOk && rawPickupAddress != null) rawPickupAddress else ride.pickupAddress,
                dropoffAddress = if (!addressOk && rawDropoffAddress != null) rawDropoffAddress else ride.dropoffAddress,
                pickupDistanceKm = if ((ride.pickupDistanceKm == null || ride.pickupDistanceKm <= 0) && rawPickupDist != null) rawPickupDist else ride.pickupDistanceKm,
                pickupTimeMin = if ((ride.pickupTimeMin == null || ride.pickupTimeMin <= 0) && rawPickupTime != null) rawPickupTime else ride.pickupTimeMin,
                tripDistanceKm = if ((ride.tripDistanceKm == null || ride.tripDistanceKm <= 0) && rawTripDist != null) rawTripDist else ride.tripDistanceKm,
                tripTimeMin = if ((ride.tripTimeMin == null || ride.tripTimeMin <= 0) && rawTripTime != null) rawTripTime else ride.tripTimeMin,
                priorityBonus = if ((ride.priorityBonus == null || ride.priorityBonus <= 0) && rawPriorityBonus != null) rawPriorityBonus else ride.priorityBonus,
                dynamicBonus = if ((ride.dynamicBonus == null || ride.dynamicBonus <= 0) && rawDynamicBonus != null) rawDynamicBonus else ride.dynamicBonus
            )
        }
    }
}
