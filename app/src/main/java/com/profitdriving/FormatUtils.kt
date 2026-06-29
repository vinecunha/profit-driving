package com.profitdriving

import java.text.NumberFormat
import java.util.Locale

object FormatUtils {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val decimal2Format = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val decimal1Format = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    private val decimal4Format = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
        minimumFractionDigits = 4
        maximumFractionDigits = 4
    }
    private val intFormat = NumberFormat.getIntegerInstance(Locale("pt", "BR"))

    fun currency(value: Double?): String {
        return value?.let { currencyFormat.format(it) } ?: "R$ 0,00"
    }

    fun currency(value: Float?): String {
        return value?.let { currencyFormat.format(it.toDouble()) } ?: "R$ 0,00"
    }

    fun decimal(value: Double?): String {
        return value?.let { decimal2Format.format(it) } ?: "0,00"
    }

    fun decimal(value: Float?): String {
        return value?.let { decimal2Format.format(it.toDouble()) } ?: "0,00"
    }

    fun decimal1(value: Double?): String {
        return value?.let { decimal1Format.format(it) } ?: "0,0"
    }

    fun decimal1(value: Float?): String {
        return value?.let { decimal1Format.format(it.toDouble()) } ?: "0,0"
    }

    fun decimal4(value: Double?): String {
        return value?.let { decimal4Format.format(it) } ?: "0,0000"
    }

    fun decimal4(value: Float?): String {
        return value?.let { decimal4Format.format(it.toDouble()) } ?: "0,0000"
    }

    fun percent(value: Double?): String {
        return value?.let { "${intFormat.format(it)}%" } ?: "0%"
    }

    fun distance(value: Double?): String {
        return value?.let { decimal1Format.format(it) + " km" } ?: "0,0 km"
    }

    fun time(value: Int?): String {
        return value?.let { "${it} min" } ?: ""
    }

    fun integer(value: Int?): String {
        return value?.let { intFormat.format(it) } ?: "0"
    }

    fun integer(value: Long?): String {
        return value?.let { intFormat.format(it) } ?: "0"
    }

    fun currencyPerKm(value: Double?): String {
        return value?.let { "R$ ${decimal2Format.format(it)}/km" } ?: "R$ 0,00/km"
    }

    fun currencyPerKm(value: Float?): String {
        return value?.let { "R$ ${decimal2Format.format(it.toDouble())}/km" } ?: "R$ 0,00/km"
    }
}
