package com.profitdriving.accessibility.extractor

import java.util.Locale

object OCREnhancer {

    private const val TAG = "OCREnhancer"

    fun hasCardData(lines: List<String>): Boolean {
        var score = 0
        val full = lines.joinToString(" ").lowercase(Locale.ROOT)

        if (Regex("""r\$\s*\d+(?:[.,]\s*)?\d+""").containsMatchIn(full)) score++
        if (Regex("""\d+\s*min.*\d+[.,]?\d*\s*km""").containsMatchIn(full)) score++
        if (Regex("""\d[.,]\d{1,2}\s*[★⭐*]""").containsMatchIn(full)) score++
        if (Regex("""aceitar|selecionar|exclusivo""").containsMatchIn(full)) score++
        if (Regex("""\+r\$\s*\d+""").containsMatchIn(full)) score++
        if (Regex("""^\s*(rua|av|avenida|travessa|praça|rodovia)""", RegexOption.MULTILINE)
                .containsMatchIn(full)) score++
        if (Regex("""verificado|visto""").containsMatchIn(full)) score++
        if (Regex("""iniciar\s+viagem""").containsMatchIn(full)) score++

        return score >= 2
    }

    fun filterCardLines(lines: List<String>): List<String> {
        return lines.filter { line ->
            val lower = line.lowercase(Locale.ROOT)

            if (CARD_LINE_PATTERNS.any { it.containsMatchIn(lower) }) return@filter true
            if (SERVICE_NAMES.any { it.containsMatchIn(lower) }) return@filter true

            if (lower.contains("r$") || lower.contains("+r$")) return@filter true

            val hasMin = lower.contains("min") && (lower.contains("km") || lower.contains("dist"))
            if (hasMin) return@filter true

            val hasKmAndNumber = Regex("""\d+\s*[,.]?\d*\s*km""").containsMatchIn(lower)
            if (hasKmAndNumber && lower.any { it.isDigit() }) return@filter true

            val hasRating = Regex("""\d[.,]\d{1,2}""").containsMatchIn(line)
            if (hasRating && line.length < 20) return@filter true

            false
        }
    }

    private val CARD_LINE_PATTERNS = listOf(
        Regex("""r\$\s*\d+(?:[.,]\s*)?\d+"""),
        Regex("""aceitar|selecionar|escolher|negocia"""),
        Regex("""verificado|visto"""),
        Regex("""\d+[.,]\d{1,2}\s*[★⭐*]"""),
        Regex("""\d+[.,]\d{1,2}\s*\(\d+\)"""),
        Regex("""\d+[.,]\d{1,2}\s*[·•]"""),
        Regex("""\+r\$\s*\d+[.,]?\d*"""),
        Regex("""benefício|bonus|inclu[íi]do"""),
        Regex("""^\s*(rua|av|avenida|travessa|praça|rodovia|estrada|alameda|beco|via|residencial|condomínio)"""),
        Regex("""\bkm\b.*\bdistância\b"""),
        Regex("""de\s+distância"""),
        Regex("""várias\s+paradas"""),
        Regex("""priorit[áa]rio"""),
        Regex("""iniciar\s+viagem"""),
        Regex("""exclusivo"""),
        Regex("""radar\s+de\s+viagens"""),
        Regex("""solicitação\s+de\s+reserva"""),
        Regex("""reservas""")
    )

    private val SERVICE_NAMES = listOf(
        Regex("""\buberx\b"""),
        Regex("""\bflash\b"""),
        Regex("""\bjuntos\b"""),
        Regex("""\bmoto\b"""),
        Regex("""\bblack\b"""),
        Regex("""\bcomfort\b"""),
        Regex("""\bpriority\b"""),
        Regex("""\b99pop\b"""),
        Regex("""\b99top\b"""),
        Regex("""\b99black\b"""),
        Regex("""\b99moto\b"""),
        Regex("""\b99flash\b"""),
        Regex("""\bentrega\b"""),
        Regex("""\bnegocia\b"""),
        Regex("""\btop\b"""),
        Regex("""\bpop\b""")
    )
}
