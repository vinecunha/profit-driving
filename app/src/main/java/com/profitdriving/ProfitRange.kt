package com.profitdriving

enum class ProfitRange(
    val icon: String,
    val color: Int,
    val label: String,
    val minPercent: Double
) {
    LUCRO_ALTO("🟢💰", 0xFF22CB82.toInt(), "Lucro", 50.0),
    LUCRO_MEDIO("📈", 0xFFFC7E3B.toInt(), "Lucro Médio", 20.0),
    LUCRO_BAIXO("⚠️", 0xFFFC7E3B.toInt(), "Lucro Baixo", 0.0),
    PREJUIZO("🔴⚠️", 0xFFCE2C48.toInt(), "Prejuízo", Double.NEGATIVE_INFINITY);

    companion object {
        fun fromPercent(percent: Double): ProfitRange {
            return when {
                percent >= LUCRO_ALTO.minPercent -> LUCRO_ALTO
                percent >= LUCRO_MEDIO.minPercent -> LUCRO_MEDIO
                percent >= LUCRO_BAIXO.minPercent -> LUCRO_BAIXO
                else -> PREJUIZO
            }
        }
    }
}
