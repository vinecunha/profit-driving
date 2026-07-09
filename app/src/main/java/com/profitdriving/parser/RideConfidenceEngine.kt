package com.profitdriving.parser

import com.profitdriving.L
import com.profitdriving.RideData

class RideConfidenceEngine {

    data class ConfidenceScore(
        val rideData: RideData,
        val score: Int,
        val details: List<String>,
        val corrections: List<String>,
        val isValid: Boolean,
        val isCorrected: Boolean
    )

    fun evaluate(ride: RideData, validCropCount: Int = 0): ConfidenceScore {
        val details = mutableListOf<String>()
        val corrections = mutableListOf<String>()
        var points = 0
        val maxPoints = 9

        var data = ride

        // ==========================================
        // 1. VALIDAÇÃO DOS CAMPOS
        // ==========================================

        val value = data.value ?: 0.0
        val distance = data.distanceKm ?: data.tripDistanceKm ?: data.pickupDistanceKm ?: 0.0
        val time = data.timeMin ?: data.tripTimeMin ?: data.pickupTimeMin ?: 0

        if (value > 1.0) {
            points++
            details.add("+ preço R$ ${"%.2f".format(value)}")
        } else {
            details.add("- preço inválido: ${"%.2f".format(value)}")
        }

        if (distance in 0.3..200.0) {
            points++
            details.add("+ distância ${"%.1f".format(distance)}km")
        } else if (distance > 0.0) {
            details.add("? distância suspeita: ${"%.1f".format(distance)}km")
        } else {
            details.add("- sem distância")
        }

        if (time in 1..240) {
            points++
            details.add("+ tempo ${time}min")
        } else if (time > 0) {
            details.add("? tempo suspeito: ${time}min")
        } else {
            details.add("- sem tempo")
        }

        if (data.rating != null && data.rating in 1.0..5.1) {
            points++
            details.add("+ avaliação ${data.rating}")
        }

        if (!data.pickupAddress.isNullOrBlank() && data.pickupAddress.length > 5) {
            points++
            details.add("+ endereço embarque: ${data.pickupAddress.take(30)}")
        }

        if (!data.dropoffAddress.isNullOrBlank() && data.dropoffAddress.length > 5) {
            points++
            details.add("+ endereço destino: ${data.dropoffAddress.take(30)}")
        }

        // ==========================================
        // 2. COERÊNCIA VALOR × DISTÂNCIA × TEMPO
        // ==========================================

        if (value > 0 && distance > 0 && time > 0) {
            val pricePerKm = value / distance
            val speed = distance / (time / 60.0)

            if (pricePerKm in 0.3..15.0) {
                points++
                details.add("+ R$/km ${"%.2f".format(pricePerKm)}")
            } else {
                details.add("? R$/km ${"%.2f".format(pricePerKm)}")
            }

            if (speed in 5.0..120.0) {
                points++
                details.add("+ velocidade ${"%.0f".format(speed)}km/h")
            } else {
                details.add("? velocidade ${"%.0f".format(speed)}km/h")
            }
        }

        // ==========================================
        // 3. CORREÇÃO AUTOMÁTICA
        // ==========================================

        var corrected = false

        // Distância < 1.0 km → provável erro de OCR (0.x → x.0)
        if (distance in 0.1..0.9) {
            val newDist = distance * 10
            if (newDist in 0.5..50.0) {
                data = data.copy(
                    distanceKm = newDist,
                    tripDistanceKm = if (data.tripDistanceKm != null && data.tripDistanceKm == distance) newDist else data.tripDistanceKm,
                    pickupDistanceKm = if (data.pickupDistanceKm != null && data.pickupDistanceKm == distance) newDist else data.pickupDistanceKm
                )
                corrections.add("distância ${"%.1f".format(distance)}km → ${"%.1f".format(newDist)}km")
                corrected = true
            }
        }

        // Preço < 2.0 com distância > 1.0 → OCR perdeu vírgula
        if (value in 0.5..2.0 && distance > 1.0) {
            val newValue = value * 10
            if (newValue in 5.0..99.0) {
                data = data.copy(value = newValue)
                corrections.add("preço R$ ${"%.2f".format(value)} → R$ ${"%.2f".format(newValue)}")
                corrected = true
            }
        }

        // Tempo 1-2 min com distância > 2km → OCR perdeu dígito
        if (time in 1..2 && distance > 2.0) {
            val newTime = time * 10
            data = data.copy(
                timeMin = newTime,
                tripTimeMin = if (data.tripTimeMin != null && data.tripTimeMin == time) newTime else data.tripTimeMin,
                pickupTimeMin = if (data.pickupTimeMin != null && data.pickupTimeMin == time) newTime else data.pickupTimeMin
            )
            corrections.add("tempo ${time}min → ${newTime}min")
            corrected = true
        }

        // Preço inteiro (exato) ≥ 30 → provável tela de confirmação (não card)
        if (value == value.toLong().toDouble() && value >= 30.0) {
            details.add("! preço exato R$ ${"%.0f".format(value)} (confirmação?)")
        }

        // ==========================================
        // 4. CROSS-CROP VALIDATION
        // ==========================================

        if (validCropCount >= 2) {
            points++
            details.add("+ $validCropCount crops válidos")
        } else if (validCropCount == 1) {
            details.add("= 1 crop válido")
        } else {
            details.add("- sem crops")
        }

        // ==========================================
        // 5. SCORE FINAL
        // ==========================================

        val score = (points * 100) / maxPoints

        L.d(TAG, "ConfidenceScore: $score% (${points}/$maxPoints)${if (corrected) " com correções" else ""}")

        return ConfidenceScore(
            rideData = data,
            score = score,
            details = details,
            corrections = corrections,
            isValid = score >= 35,
            isCorrected = corrected
        )
    }

    companion object {
        private const val TAG = "ConfidenceEngine"
    }
}
