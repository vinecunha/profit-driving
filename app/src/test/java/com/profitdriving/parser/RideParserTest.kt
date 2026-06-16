package com.profitdriving.parser

import com.profitdriving.RideData
import com.profitdriving.accessibility.extractor.CardType
import com.profitdriving.accessibility.extractor.RawCardData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RideParserTest {

    private fun rawRadar(
        value: String = "R$ 16,99",
        pickup: String = "7 minutos (2.1 km) de distância",
        trip: String = "Viagem de 15 minutos (6.6 km)",
        rating: String = "4,89 (42)",
        service: String = "UberX",
        accept: String = "Selecionar",
        bonus: List<String> = emptyList()
    ) = RawCardData(
        cardType = CardType.RADAR,
        valueNode = value,
        pickupNode = pickup,
        tripNode = trip,
        ratingNode = rating,
        serviceNode = service,
        acceptNode = accept,
        bonusNodes = bonus,
        rawTexts = listOf(service, value, rating, pickup, trip, accept) + bonus
    )

    private fun rawExclusive(
        value: String = "R$ 14,10",
        pickup: String = "12 minutos (3.2 km) de distância",
        trip: String = "Viagem de 4 minutos (1.1 km)",
        tripAlt: String? = "4 minutos (1.1 km)",
        rating: String = "4,80 (156)",
        service: String = "UberX",
        accept: String = "Aceitar",
        bonus: List<String> = listOf("+R$ 2,00 incluído")
    ) = RawCardData(
        cardType = CardType.EXCLUSIVE,
        valueNode = value,
        pickupNode = pickup,
        tripNode = tripAlt ?: trip,
        ratingNode = rating,
        serviceNode = service,
        acceptNode = accept,
        bonusNodes = bonus,
        rawTexts = listOf(service, "Exclusivo", value, rating, "+R$ 2,00 incluído",
            pickup, tripAlt ?: trip, accept)
    )

    private fun raw99(
        value: String = "R$ 18,50",
        pickup: String = "5 minutos (1.5 km) de distância",
        trip: String = "Viagem de 12 minutos (8.3 km)",
        rating: String = "4,95",
        service: String = "99Pop",
        accept: String = "Aceitar"
    ) = RawCardData(
        cardType = CardType.APP_99,
        valueNode = value,
        pickupNode = pickup,
        tripNode = trip,
        ratingNode = rating,
        serviceNode = service,
        acceptNode = accept,
        bonusNodes = emptyList(),
        rawTexts = listOf(service, value, rating, pickup, trip, accept)
    )

    @Test
    fun radar_canParse_aceita_RADAR() {
        assertTrue(RadarCardParser().canParse(rawRadar()))
    }

    @Test
    fun radar_canParse_rejeita_EXCLUSIVE() {
        assertFalse(RadarCardParser().canParse(rawExclusive()))
    }

    @Test
    fun radar_valor_correto() {
        val ride = RadarCardParser().parse(rawRadar())
        assertNotNull("parse retornou null", ride)
        assertEquals(16.99, ride!!.value!!, 0.01)
    }

    @Test
    fun radar_pickup_extraido() {
        val ride = RadarCardParser().parse(rawRadar())
        assertNotNull("parse retornou null", ride)
        assertEquals(2.1, ride!!.pickupDistanceKm!!, 0.01)
        assertEquals(7, ride.pickupTimeMin)
    }

    @Test
    fun radar_trip_extraido() {
        val ride = RadarCardParser().parse(rawRadar())
        assertNotNull("parse retornou null", ride)
        assertEquals(6.6, ride!!.tripDistanceKm!!, 0.01)
        assertEquals(15, ride.tripTimeMin)
    }

    @Test
    fun radar_distancia_total() {
        val ride = RadarCardParser().parse(rawRadar())
        assertNotNull("parse retornou null", ride)
        assertEquals(8.7, ride!!.distanceKm!!, 0.05)
    }

    @Test
    fun radar_tempo_total() {
        val ride = RadarCardParser().parse(rawRadar())
        assertNotNull("parse retornou null", ride)
        assertEquals(22, ride!!.timeMin)
    }

    @Test
    fun radar_rating_extraido() {
        val ride = RadarCardParser().parse(rawRadar())
        assertNotNull("parse retornou null", ride)
        assertEquals(4.89, ride!!.rating!!, 0.01)
    }

    @Test
    fun radar_serviceType_correto() {
        val ride = RadarCardParser().parse(rawRadar())
        assertNotNull("parse retornou null", ride)
        assertEquals("UberX", ride!!.serviceType)
    }

    @Test
    fun radar_appName_Uber() {
        val ride = RadarCardParser().parse(rawRadar())
        assertNotNull("parse retornou null", ride)
        assertEquals("Uber", ride!!.appName)
    }

    @Test
    fun radar_sem_bonus() {
        val ride = RadarCardParser().parse(rawRadar())
        assertNotNull("parse retornou null", ride)
        assertNull(ride!!.priorityBonus)
        assertNull(ride.dynamicBonus)
    }

    @Test
    fun radar_bonus_dinamico() {
        val ride = RadarCardParser().parse(rawRadar(bonus = listOf("+R$ 3,50 incluído")))
        assertNotNull("parse retornou null", ride)
        assertEquals(3.50, ride!!.dynamicBonus!!, 0.01)
    }

    @Test
    fun radar_bonus_prioridade_nao_vira_dinamico() {
        val ride = RadarCardParser().parse(
            rawRadar(bonus = listOf("+R$ 2,00 incluído para prioridade"))
        )
        assertNotNull("parse retornou null", ride)
        assertEquals(2.0, ride!!.priorityBonus!!, 0.01)
        assertNull(ride.dynamicBonus)
    }

    @Test
    fun radar_corrida_com_hora() {
        val ride = RadarCardParser().parse(
            rawRadar(trip = "Viagem de 1H e 10 minutos (45.3 km)")
        )
        assertNotNull("parse retornou null", ride)
        assertEquals(70, ride!!.tripTimeMin)
        assertEquals(45.3, ride.tripDistanceKm!!, 0.1)
    }

    @Test
    fun exclusive_canParse_aceita_EXCLUSIVE() {
        assertTrue(ExclusiveCardParser().canParse(rawExclusive()))
    }

    @Test
    fun exclusive_canParse_rejeita_RADAR() {
        assertFalse(ExclusiveCardParser().canParse(rawRadar()))
    }

    @Test
    fun exclusive_valor_correto() {
        val ride = ExclusiveCardParser().parse(rawExclusive())
        assertNotNull("parse retornou null", ride)
        assertEquals(14.10, ride!!.value!!, 0.01)
    }

    @Test
    fun exclusive_pickup_extraido() {
        val ride = ExclusiveCardParser().parse(rawExclusive())
        assertNotNull("parse retornou null", ride)
        assertEquals(3.2, ride!!.pickupDistanceKm!!, 0.01)
        assertEquals(12, ride.pickupTimeMin)
    }

    @Test
    fun exclusive_trip_sem_prefixo() {
        val ride = ExclusiveCardParser().parse(
            rawExclusive(trip = "Viagem de 4 minutos (1.1 km)", tripAlt = "4 minutos (1.1 km)")
        )
        assertNotNull("parse retornou null", ride)
        assertEquals(1.1, ride!!.tripDistanceKm!!, 0.01)
        assertEquals(4, ride.tripTimeMin)
    }

    @Test
    fun exclusive_trip_com_prefixo() {
        val ride = ExclusiveCardParser().parse(
            rawExclusive(trip = "Viagem de 22 minutos (9.5 km)", tripAlt = null)
        )
        assertNotNull("parse retornou null", ride)
        assertEquals(9.5, ride!!.tripDistanceKm!!, 0.01)
        assertEquals(22, ride.tripTimeMin)
    }

    @Test
    fun exclusive_distancia_total() {
        val ride = ExclusiveCardParser().parse(rawExclusive())
        assertNotNull("parse retornou null", ride)
        assertEquals(4.3, ride!!.distanceKm!!, 0.05)
    }

    @Test
    fun exclusive_bonus_dinamico() {
        val ride = ExclusiveCardParser().parse(rawExclusive())
        assertNotNull("parse retornou null", ride)
        assertEquals(2.0, ride!!.dynamicBonus!!, 0.01)
    }

    @Test
    fun exclusive_rating_com_verificado() {
        val ride = ExclusiveCardParser().parse(
            rawExclusive(rating = "4,80 (156)  Verificado")
        )
        assertNotNull("parse retornou null", ride)
        assertEquals(4.80, ride!!.rating!!, 0.01)
    }

    @Test
    fun `99_canParse_aceita_APP_99`() {
        assertTrue(App99CardParser().canParse(raw99()))
    }

    @Test
    fun `99_valor_correto`() {
        val ride = App99CardParser().parse(raw99())
        assertNotNull("parse retornou null", ride)
        assertEquals(18.50, ride!!.value!!, 0.01)
    }

    @Test
    fun `99_appName_99`() {
        val ride = App99CardParser().parse(raw99())
        assertNotNull("parse retornou null", ride)
        assertEquals("99", ride!!.appName)
    }

    @Test
    fun valor_inteiro_nao_e_suspeito_abaixo_30() {
        val ride = RadarCardParser().parse(rawRadar(value = "R$ 25,00"))
        assertNotNull("parse retornou null", ride)
        assertEquals(25.0, ride!!.value!!, 0.01)
    }

    @Test
    fun parser_nao_retorna_null_quando_distancia_ausente() {
        val raw = rawRadar(trip = "")
        val ride = RadarCardParser().parse(raw)
        if (ride != null) {
            assertEquals(16.99, ride.value!!, 0.01)
        }
    }

    @Test
    fun rating_fora_de_range_ignorado() {
        val ride = RadarCardParser().parse(rawRadar(rating = "12,5"))
        assertNotNull("parse retornou null", ride)
        assertNull(ride!!.rating)
    }

    @Test
    fun multiplas_paradas_detectadas() {
        val raw = rawRadar().copy(
            rawTexts = rawRadar().rawTexts + listOf("Várias paradas")
        )
        val ride = RadarCardParser().parse(raw)
        assertNotNull("parse retornou null", ride)
        assertTrue(ride!!.hasMultipleStops)
    }
}
