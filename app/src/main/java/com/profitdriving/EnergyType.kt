package com.profitdriving

import android.content.Context
import androidx.core.content.ContextCompat

enum class EnergyType(
    val display: String,
    val unit: String,
    val icon: String,
    val colorRes: Int
) {
    GASOLINE("Gasolina", "L", "\u26FD", R.color.energy_gasoline),
    ETHANOL("Etanol", "L", "\uD83C\uDF3D", R.color.energy_ethanol),
    DIESEL("Diesel", "L", "\uD83D\uDEE2\uFE0F", R.color.energy_diesel),
    GNV("GNV", "m\u00B3", "\uD83D\uDD25", R.color.energy_gnv),
    ELECTRIC_AC("El\u00E9trico (AC)", "kWh", "\uD83D\uDD0C", R.color.energy_electric),
    ELECTRIC_DC("El\u00E9trico (DC r\u00E1pido)", "kWh", "\u26A1", R.color.energy_electric),
    HYBRID_CHARGE("H\u00EDbrido (recarga)", "kWh", "\uD83D\uDD0B", R.color.energy_hybrid);

    val isElectric: Boolean get() = this in listOf(ELECTRIC_AC, ELECTRIC_DC, HYBRID_CHARGE)
    val isLiquid: Boolean get() = this in listOf(GASOLINE, ETHANOL, DIESEL)

    companion object {
        fun fromString(value: String): EnergyType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: GASOLINE

        fun color(context: Context, type: EnergyType): Int =
            ContextCompat.getColor(context, type.colorRes)
    }
}
