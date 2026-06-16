package com.profitdriving.parser

import com.profitdriving.L
import com.profitdriving.RideData
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.accessibility.extractor.UberCardExtractor
import java.util.Locale

class DiscoveryCardParser : RideDataParser {

    override fun canParse(raw: RawCardData): Boolean {
        val full = raw.rawTexts.joinToString(" ").lowercase(Locale.ROOT)
        val hasDescubra = full.contains("descubra")
        val hasReservas = full.contains("reservas")
        val hasAccept = full.contains("aceitar")
        val hasInfo = full.contains("informações")
        val hasMoney = full.contains("r$")
        val hasKm = full.contains("km")
        val semExclusivo = !full.contains("exclusivo")
        return hasDescubra && hasReservas && hasAccept && hasInfo && hasMoney && hasKm && semExclusivo
    }

    override fun parse(raw: RawCardData): RideData? {
        L.d(TAG, "DiscoveryCardParser.parse() iniciado")
        val text = raw.fullText

        val tripMatch = TRIP_PATTERN.findAll(text).firstOrNull() ?: run {
            L.d(TAG, "Nenhum padrão de corrida encontrado — DiscoveryCardParser abortando")
            return null
        }

        val timeStr = tripMatch.groupValues[1]
        val value = UberCardExtractor.parseBr(tripMatch.groupValues[2])
        if (value == null || value <= 0) {
            L.d(TAG, "Valor inválido — DiscoveryCardParser abortando")
            return null
        }

        val distance = UberCardExtractor.parseBr(tripMatch.groupValues[3])
        val serviceType = tripMatch.groupValues[4].trim().replaceFirstChar { it.uppercase() }

        val addressMatch = ADDRESS_PATTERN.find(text)
        val pickupAddress = addressMatch?.groupValues?.get(1)?.trim()
        val dropoffAddress = addressMatch?.groupValues?.get(2)?.trim()

        L.d(TAG, "DiscoveryCardParser parsed: $timeStr value=$value km=$distance tipo=$serviceType")

        return RideData(
            value = value,
            distanceKm = distance,
            timeMin = null,
            rating = null,
            appName = "Uber",
            pricePerKm = if (distance != null && distance > 0) value / distance else null,
            pricePerHour = null,
            detectedBy = "accessibility_discovery",
            pickupAddress = pickupAddress,
            dropoffAddress = dropoffAddress,
            serviceType = serviceType,
            pickupTimeMin = null,
            pickupDistanceKm = null,
            tripDistanceKm = distance,
            tripTimeMin = null,
            hasMultipleStops = false,
            priorityBonus = null,
            dynamicBonus = null
        )
    }

    companion object {
        private const val TAG = "DiscoveryCardParser"

        private val TRIP_PATTERN = Regex(
            """(\d{1,2}:\d{2})\s+R\$\s*(\d+[.,]\d+),\s*(\d+[.,]\d+)\s*km\s+([A-Za-z\s]+)""",
            RegexOption.IGNORE_CASE
        )

        private val ADDRESS_PATTERN = Regex(
            """(.+?)\s*→\s*(.+)""",
            RegexOption.IGNORE_CASE
        )

        private val TIME_PATTERN = Regex(
            """(\d{1,2}:\d{2})"""
        )

        private val DISCOVER_SCREEN_PATTERN = Regex(
            """descubra.*?aceitar.*?informações""",
            RegexOption.IGNORE_CASE
        )
    }
}
