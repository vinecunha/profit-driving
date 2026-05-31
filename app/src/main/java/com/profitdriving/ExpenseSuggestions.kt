package com.profitdriving

object ExpenseSuggestions {

    data class Suggestion(
        val name: String,
        val icon: String,
        val defaultValue: Double,
        val unit: String,
        val hint: String
    )

    val perKmSuggestions = listOf(
        Suggestion("Manuten\u00E7\u00E3o", "\uD83D\uDD27", 0.07, "km", "Preventiva + corretiva"),
        Suggestion("Pneus", "\uD83D\uDD04", 0.03, "km", "Troca a cada 50.000km"),
        Suggestion("Combust\u00EDvel", "\u26FD", 0.25, "km", "Baseado no consumo"),
        Suggestion("Lavagem", "\uD83E\uDDFC", 0.008, "km", "Lavagem simples semanal")
    )

    val fixedSuggestions = listOf(
        Suggestion("Seguro", "\uD83D\uDEE1\uFE0F", 250.0, "m\u00EAs", "Prote\u00E7\u00E3o veicular"),
        Suggestion("IPVA", "\uD83D\uDCB0", 1350.0, "ano", "Imposto anual (R\$ 1.350 \u00E0 vista)"),
        Suggestion("Financiamento", "\uD83C\uDFE6", 500.0, "m\u00EAs", "Parcela do banco"),
        Suggestion("Manuten\u00E7\u00E3o preventiva", "\uD83D\uDD27", 80.0, "m\u00EAs", "Revis\u00F5es peri\u00F3dicas")
    )

    val eventSuggestions = listOf(
        Suggestion("Estacionamento", "\uD83D\uDD7F\uFE0F", 20.0, "evento", "Rotativo ou mensalista"),
        Suggestion("Ped\u00E1gio", "\uD83D\uDEE3\uFE0F", 15.0, "evento", "Por viagem"),
        Suggestion("Lavagem especial", "\uD83E\uDDFC", 60.0, "evento", "Completa + higieniza\u00E7\u00E3o")
    )
}
