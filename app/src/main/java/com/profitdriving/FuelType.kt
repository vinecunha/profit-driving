package com.profitdriving

enum class FuelType(
    val display: String,
    val unit: String
) {
    GASOLINA("Gasolina", "L"),
    ETANOL("Etanol", "L"),
    DIESEL("Diesel", "L"),
    GNV("GNV", "m³"),
    ELETRICO("Elétrico", "kWh"),
    HIBRIDO("Híbrido", "L");

    companion object {
        fun fromEnergyType(energyType: EnergyType): FuelType = when (energyType) {
            EnergyType.GASOLINE -> GASOLINA
            EnergyType.ETHANOL -> ETANOL
            EnergyType.DIESEL -> DIESEL
            EnergyType.GNV -> GNV
            EnergyType.ELECTRIC_AC, EnergyType.ELECTRIC_DC -> ELETRICO
            EnergyType.HYBRID_CHARGE -> HIBRIDO
        }

        fun fromDbValue(value: String): FuelType {
            return try {
                fromEnergyType(EnergyType.fromString(value))
            } catch (_: Exception) {
                GASOLINA
            }
        }
    }
}
