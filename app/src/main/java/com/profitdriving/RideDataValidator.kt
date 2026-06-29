package com.profitdriving

import android.util.Log

object RideDataValidator {
    
    private const val TAG = "RideValidator"
    
    data class ValidationResult(
        val isValid: Boolean,
        val cleanedRide: RideData?,
        val reason: String? = null,
        val quality: DataQuality = DataQuality.REJECTED
    )
    
    enum class DataQuality {
        COMPLETE,           // Dados de pickup e trip distintos
        ONLY_TRIP,          // Só tem dados da viagem (pickup faltando)
        ONLY_PICKUP,        // Só tem dados do embarque (trip faltando)
        DUPLICATED,         // Pickup e trip são iguais (provavelmente erro)
        FALLBACK,           // Usou fallback (distância total disponível)
        REJECTED            // Dados insuficientes
    }
    
    /**
     * Valida e limpa os dados, comparando pickup vs trip
     */
    fun validateAndClean(ride: RideData): ValidationResult {
        var cleaned = ride
        var quality = DataQuality.COMPLETE
        val issues = mutableListOf<String>()
        
        // ============================================================
        // 1. VALIDAÇÃO BÁSICA - Valor da corrida
        // ============================================================
        if (ride.value == null || ride.value <= 0) {
            return ValidationResult(false, null, "Valor inválido", DataQuality.REJECTED)
        }
        
        // ============================================================
        // 2. EXTRAIR DADOS DISPONÍVEIS
        // ============================================================
        val pickupDist = ride.pickupDistanceKm
        val pickupTime = ride.pickupTimeMin
        val tripDist = ride.tripDistanceKm
        val tripTime = ride.tripTimeMin
        val totalDist = ride.distanceKm
        val totalTime = ride.timeMin
        
        val pickupAddr = ride.pickupAddress
        val dropoffAddr = ride.dropoffAddress
        
        val hasPickupDist = pickupDist != null && pickupDist > 0
        val hasPickupTime = pickupTime != null && pickupTime > 0
        val hasTripDist = tripDist != null && tripDist > 0
        val hasTripTime = tripTime != null && tripTime > 0
        val hasTotalDist = totalDist != null && totalDist > 0
        val hasTotalTime = totalTime != null && totalTime > 0
        
        val hasPickupAddr = !isInvalidAddress(pickupAddr)
        val hasDropoffAddr = !isInvalidAddress(dropoffAddr)
        
        // ============================================================
        // 2.1 Verificar se pickup e trip são diferentes
        // ============================================================
        val arePickupAndTripDifferent = when {
            hasPickupDist && hasTripDist -> Math.abs(pickupDist!! - tripDist!!) > 0.3
            hasPickupTime && hasTripTime -> Math.abs(pickupTime!! - tripTime!!) > 2
            else -> false
        }
        
        // ============================================================
        // 2.2 Verificar se endereços são diferentes
        // ============================================================
        val areAddressesDifferent = when {
            hasPickupAddr && hasDropoffAddr -> !isSameAddress(pickupAddr!!, dropoffAddr!!)
            else -> false
        }
        
        // ============================================================
        // VARIÁVEIS PARA ARMAZENAR OS VALORES CORRIGIDOS
        // ============================================================
        var finalPickupDist = pickupDist
        var finalPickupTime = pickupTime
        var finalTripDist = tripDist
        var finalTripTime = tripTime
        
        // ============================================================
        // 3. CASO 1: PICKUP E TRIP SÃO DIFERENTES - DADOS COMPLETOS
        // ============================================================
        if (arePickupAndTripDifferent) {
            L.d(TAG, "✅ COMPLETE: pickup e trip diferentes")
            quality = DataQuality.COMPLETE
            // Mantém os dados originais
            finalPickupDist = pickupDist
            finalPickupTime = pickupTime
            finalTripDist = tripDist
            finalTripTime = tripTime
        }
        
        // ============================================================
        // 4. CASO 2: APENAS TRIP (pickup vazio) - QUALIDADE MÉDIA
        // ============================================================
        else if (!hasPickupDist && !hasPickupTime && (hasTripDist || hasTripTime)) {
            Log.w(TAG, "⚠️ ONLY_TRIP: dados de embarque não capturados")
            quality = DataQuality.ONLY_TRIP
            finalPickupDist = null
            finalPickupTime = null
            finalTripDist = tripDist
            finalTripTime = tripTime
            issues.add("Embarque não capturado - usando apenas dados da viagem")
        }
        
        // ============================================================
        // 5. CASO 3: APENAS PICKUP (trip vazio) - QUALIDADE BAIXA
        // ============================================================
        else if ((hasPickupDist || hasPickupTime) && !hasTripDist && !hasTripTime) {
            Log.w(TAG, "⚠️ ONLY_PICKUP: dados de viagem não capturados")
            quality = DataQuality.ONLY_PICKUP
            finalPickupDist = pickupDist
            finalPickupTime = pickupTime
            finalTripDist = pickupDist  // Usa pickup como trip (fallback)
            finalTripTime = pickupTime  // Usa pickup como trip (fallback)
            issues.add("Viagem não capturada - usando dados de embarque como viagem")
        }
        
        // ============================================================
        // 6. CASO 4: PICKUP E TRIP EXISTEM - APLICAR TABELA VERDADE
        // ============================================================
        else if (hasPickupDist && hasTripDist) {
            val distancesEqual = Math.abs(pickupDist!! - tripDist!!) < 0.3
            val timesEqual = hasPickupTime && hasTripTime && Math.abs(pickupTime!! - tripTime!!) <= 2
            val addressesDifferent = areAddressesDifferent
            
            when {
                // CASO 1: Distâncias DIFERENTES, Tempos DIFERENTES, Endereços DIFERENTES
                !distancesEqual && hasPickupTime && hasTripTime && !timesEqual && addressesDifferent -> {
                    L.d(TAG, "✅ CASO 1: Corrida normal (distâncias e tempos diferentes, endereços diferentes)")
                    quality = DataQuality.COMPLETE
                    finalPickupDist = pickupDist
                    finalPickupTime = pickupTime
                    finalTripDist = tripDist
                    finalTripTime = tripTime
                }
                
                // CASO 2: Distâncias DIFERENTES, Tempos DIFERENTES, Endereços IGUAIS
                !distancesEqual && hasPickupTime && hasTripTime && !timesEqual && !addressesDifferent -> {
                    L.d(TAG, "🔄 CASO 2: Ida e volta (distâncias e tempos diferentes, mesmo local)")
                    quality = DataQuality.COMPLETE
                    finalPickupDist = pickupDist
                    finalPickupTime = pickupTime
                    finalTripDist = tripDist
                    finalTripTime = tripTime
                    issues.add("Pickup e dropoff no mesmo local - possível ida/volta")
                }
                
                // CASO 3: Distâncias DIFERENTES, Tempos IGUAIS, Endereços DIFERENTES
                !distancesEqual && hasPickupTime && hasTripTime && timesEqual && addressesDifferent -> {
                    Log.w(TAG, "⚠️ CASO 3: Distâncias diferentes mas tempos iguais (possível erro de parser)")
                    quality = DataQuality.DUPLICATED
                    finalPickupDist = pickupDist
                    finalPickupTime = pickupTime
                    finalTripDist = tripDist
                    finalTripTime = tripTime
                    issues.add("Distâncias diferentes mas tempos iguais - possível erro no tempo")
                }
                
                // CASO 4: Distâncias DIFERENTES, Tempos IGUAIS, Endereços IGUAIS
                !distancesEqual && hasPickupTime && hasTripTime && timesEqual && !addressesDifferent -> {
                    Log.w(TAG, "⚠️ CASO 4: Distâncias diferentes mas tempos iguais no mesmo local")
                    quality = DataQuality.DUPLICATED
                    finalPickupDist = pickupDist
                    finalPickupTime = pickupTime
                    finalTripDist = tripDist
                    finalTripTime = tripTime
                    issues.add("Distâncias diferentes mas tempos iguais no mesmo local")
                }
                
                // CASO 5: Distâncias IGUAIS, Tempos DIFERENTES, Endereços DIFERENTES
                distancesEqual && hasPickupTime && hasTripTime && !timesEqual && addressesDifferent -> {
                    Log.w(TAG, "⚠️ CASO 5: Distâncias iguais mas tempos diferentes (possível erro)")
                    quality = DataQuality.DUPLICATED
                    // Remove pickup duplicado, mantém trip
                    finalPickupDist = null
                    finalPickupTime = null
                    finalTripDist = tripDist
                    finalTripTime = tripTime
                    issues.add("Distâncias iguais (${pickupDist}km) mas tempos diferentes - removendo pickup duplicado")
                }
                
                // CASO 6: Distâncias IGUAIS, Tempos DIFERENTES, Endereços IGUAIS
                distancesEqual && hasPickupTime && hasTripTime && !timesEqual && !addressesDifferent -> {
                    Log.w(TAG, "⚠️ CASO 6: Distâncias iguais, tempos diferentes, mesmo local")
                    quality = DataQuality.DUPLICATED
                    finalPickupDist = null
                    finalPickupTime = null
                    finalTripDist = tripDist
                    finalTripTime = tripTime
                    issues.add("Distâncias iguais, tempos diferentes, mesmo local - removendo pickup duplicado")
                }
                
                // CASO 7: Distâncias IGUAIS, Tempos IGUAIS, Endereços DIFERENTES
                distancesEqual && (!hasPickupTime || !hasTripTime || timesEqual) && addressesDifferent -> {
                    Log.w(TAG, "⚠️ CASO 7: Distâncias e tempos iguais, endereços diferentes")
                    quality = DataQuality.DUPLICATED
                    // Mantém ambos, mas marca como suspeito
                    finalPickupDist = pickupDist
                    finalPickupTime = pickupTime
                    finalTripDist = tripDist
                    finalTripTime = tripTime
                    issues.add("Pickup e trip com mesmos valores mas endereços diferentes - possível erro")
                }
                
                // CASO 8: Distâncias IGUAIS, Tempos IGUAIS, Endereços IGUAIS
                distancesEqual && (!hasPickupTime || !hasTripTime || timesEqual) && !addressesDifferent -> {
                    Log.w(TAG, "❌ CASO 8: Pickup e trip COMPLETAMENTE IGUAIS (erro de parser)")
                    quality = DataQuality.DUPLICATED
                    // Remover pickup duplicado
                    finalPickupDist = null
                    finalPickupTime = null
                    finalTripDist = tripDist
                    finalTripTime = tripTime
                    issues.add("Pickup e trip duplicados - removendo dados repetidos")
                }
                
                // CASO FALLBACK: Sem informações suficientes para comparar
                else -> {
                    Log.w(TAG, "⚠️ CASO FALLBACK: Dados insuficientes para comparação")
                    quality = DataQuality.DUPLICATED
                    finalPickupDist = null
                    finalPickupTime = null
                    finalTripDist = tripDist
                    finalTripTime = tripTime
                    issues.add("Dados incompletos - usando trip como referência")
                }
            }
        }
        
        // ============================================================
        // 7. CALCULAR DISTÂNCIA E TEMPO TOTAL (SEMPRE A SOMA!)
        // ============================================================
        val effectivePickupDist = finalPickupDist ?: 0.0
        val effectivePickupTime = finalPickupTime ?: 0
        val effectiveTripDist = finalTripDist ?: 0.0
        val effectiveTripTime = finalTripTime ?: 0
        
        val totalDistance = effectivePickupDist + effectiveTripDist
        val totalTimeValue = effectivePickupTime + effectiveTripTime
        
        // Verificar consistência com totalDist se disponível
        if (hasTotalDist && totalDistance > 0) {
            val diff = Math.abs(totalDistance - totalDist!!)
            if (diff > 0.5) {
                Log.w(TAG, "⚠️ Inconsistência: soma(pickup+trip)=${totalDistance}km, total=${totalDist}km")
                issues.add("Soma de distâncias (${String.format("%.1f", totalDistance)}km) não confere com total (${String.format("%.1f", totalDist)}km)")
            }
        }
        
        // ============================================================
        // 8. ATUALIZAR O RIDE COM OS VALORES CORRIGIDOS
        // ============================================================
        cleaned = cleaned.copy(
            pickupDistanceKm = if (effectivePickupDist > 0) effectivePickupDist else null,
            pickupTimeMin = if (effectivePickupTime > 0) effectivePickupTime else null,
            tripDistanceKm = if (effectiveTripDist > 0) effectiveTripDist else null,
            tripTimeMin = if (effectiveTripTime > 0) effectiveTripTime else null,
            distanceKm = if (totalDistance > 0) totalDistance else null,
            timeMin = if (totalTimeValue > 0) totalTimeValue else null
        )
        
        // ============================================================
        // 9. VALIDAÇÃO DE ENDEREÇOS
        // ============================================================
        val cleanedPickupAddr = if (hasPickupAddr) pickupAddr else null
        val cleanedDropoffAddr = if (hasDropoffAddr) dropoffAddr else null
        
        cleaned = cleaned.copy(
            pickupAddress = cleanedPickupAddr,
            dropoffAddress = cleanedDropoffAddr
        )
        
        if (!hasPickupAddr) issues.add("Endereço de embarque inválido ou não capturado")
        if (!hasDropoffAddr) issues.add("Endereço de destino inválido ou não capturado")
        
        // ============================================================
        // 10. REJEITAR CASOS SEM DADOS ÚTEIS
        // ============================================================
        if (totalDistance <= 0 && effectiveTripDist <= 0 && effectivePickupDist <= 0) {
            return ValidationResult(false, null, 
                "Nenhuma distância válida após validação", DataQuality.REJECTED)
        }
        
        // ============================================================
        // 11. CALCULAR VALORES DERIVADOS
        // ============================================================
        val finalDistance = totalDistance
        val finalTime = totalTimeValue
        
        val pricePerKm = if (finalDistance > 0) ride.value / finalDistance else null
        val pricePerHour = if (finalTime > 0) ride.value / (finalTime / 60.0) else null
        
        cleaned = cleaned.copy(
            pricePerKm = pricePerKm,
            pricePerHour = pricePerHour
        )
        
        val reason = if (issues.isEmpty()) "OK" else issues.joinToString("; ")
        L.d(TAG, "Validação final: quality=$quality, totalDistance=${String.format("%.1f", totalDistance)}km, totalTime=${totalTimeValue}min, $reason")
        
        return ValidationResult(true, cleaned, reason, quality)
    }
    
    /**
     * Verifica se endereço é inválido (placeholder)
     */
    private fun isInvalidAddress(address: String?): Boolean {
        if (address.isNullOrBlank()) return true
        
        val invalidPatterns = listOf(
            "Endereço não disponível",
            "Endereço não informado",
            "null",
            "N/A",
            "---",
            "Carregando"
        )
        
        for (pattern in invalidPatterns) {
            if (address.equals(pattern, ignoreCase = true)) {
                return true
            }
        }
        
        // Endereço muito curto (< 10 chars) provavelmente é inválido
        if (address.length < 10) {
            return true
        }
        
        return false
    }
    
    /**
     * Verifica se dois endereços são o mesmo local (ignorando números e detalhes)
     */
    private fun isSameAddress(addr1: String, addr2: String): Boolean {
        // Normalizar endereços para comparação
        fun normalize(address: String): String {
            return address
                .lowercase()
                .replace(Regex("\\d+"), "")  // remove números
                .replace(Regex("[^a-zçãõáéíóú ]"), "")  // remove caracteres especiais
                .trim()
                .take(50)  // pega só os primeiros 50 chars
        }
        
        val norm1 = normalize(addr1)
        val norm2 = normalize(addr2)
        
        // Se mais de 70% igual, considera mesmo local
        val maxLen = maxOf(norm1.length, norm2.length)
        if (maxLen == 0) return false
        
        var matches = 0
        for (i in 0 until minOf(norm1.length, norm2.length)) {
            if (norm1[i] == norm2[i]) matches++
        }
        
        val similarity = matches.toDouble() / maxLen
        return similarity > 0.7
    }
    
    /**
     * Verifica duplicata recente
     */
    fun isDuplicate(ride: RideData, lastRideHash: String, lastRideTime: Long, thresholdMs: Long = 30000): Boolean {
        val currentHash = buildRideHash(ride)
        val now = System.currentTimeMillis()
        
        if (currentHash == lastRideHash && (now - lastRideTime) < thresholdMs) {
            L.d(TAG, "Corrida duplicada ignorada")
            return true
        }
        return false
    }
    
    private fun buildRideHash(ride: RideData): String {
        return buildString {
            append(ride.value)
            append("_")
            append(ride.distanceKm ?: ride.tripDistanceKm ?: ride.pickupDistanceKm)
            append("_")
            append(ride.timeMin ?: ride.tripTimeMin ?: ride.pickupTimeMin)
            append("_")
            append(ride.serviceType ?: "")
            append("_")
            append(ride.pickupAddress?.take(30) ?: "")
            append("_")
            append(ride.dropoffAddress?.take(30) ?: "")
        }.hashCode().toString()
    }
}
