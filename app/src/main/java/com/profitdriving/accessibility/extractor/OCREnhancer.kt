package com.profitdriving.accessibility.extractor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OCREnhancer {

    private const val TAG = "OCREnhancer"

    fun getCropFractions(height: Int): List<Float> {
        return when {
            height > 2000 -> listOf(0.80f, 0.70f, 0.55f, 0.50f)
            else -> listOf(0.80f, 0.70f, 0.60f, 0.55f)
        }
    }

    fun cropToCardRegion(bitmap: Bitmap, bottomFraction: Float): Bitmap {
        val h = bitmap.height
        val cropY = (h * (1f - bottomFraction)).toInt().coerceAtLeast(0)
        val cropH = (h * bottomFraction).toInt().coerceAtMost(h - cropY)
        if (cropY <= 0 && cropH >= h) return bitmap
        return Bitmap.createBitmap(bitmap, 0, cropY, bitmap.width, cropH)
    }

    fun preprocessForOCR(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap

        // Two-pass contrast: first extreme, then normal
        val cm = ColorMatrix(
            floatArrayOf(
                3.0f, 0f, 0f, 0f, -200f,
                0f, 3.0f, 0f, 0f, -200f,
                0f, 3.0f, 0f, 0f, -200f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(result).drawBitmap(result, 0f, 0f, paint)
        return result
    }

    fun configureTesseract(api: TessBaseAPI) {
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
        api.setVariable("tessedit_char_whitelist",
            "R$ 0123456789,.-kmintuosahcedprvalçãáàâéêíóôúABCDEFGHIJLMNOPQRSTUVXZabcdefghijlmnopqrstuvxz/★")
        api.setVariable("classify_enable_adaptive_matcher", "0")
    }

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

    fun fixOCRDistances(lines: MutableList<String>): MutableList<String> {
        val minutes = mutableListOf<Int>()
        for (line in lines) {
            val m = Regex("""(\d+)\s*min""").find(line.lowercase(Locale.ROOT))
            if (m != null) m.groupValues[1].toIntOrNull()?.let { minutes.add(it) }
        }
        val avgMin = if (minutes.isEmpty()) null else minutes.average()

        return lines.map { line ->
            var result = line
            val kmMatch = Regex("""(\d{1,2})[.,]?(\d)?\s*km""").find(line.lowercase(Locale.ROOT))
            if (kmMatch != null) {
                val intPart = kmMatch.groupValues[1].toIntOrNull() ?: return@map line
                val decPart = kmMatch.groupValues[2]

                if (decPart.isNotEmpty()) return@map line

                val minNearby = Regex("""(\d+)\s*min""").find(line.lowercase(Locale.ROOT))
                    ?.groupValues?.get(1)?.toDoubleOrNull()
                val refMin = minNearby ?: avgMin

                if (intPart >= 19 && intPart <= 99 && refMin != null && refMin < 20.0) {
                    val fixed = "%.1f".format(intPart / 10.0).replace(".", ",")
                    result = line.replace(Regex("""${intPart}\s*km"""), "${fixed} km")
                }
            }
            result
        }.toMutableList()
    }

    fun saveDiagnosticData(
        context: Context,
        appName: String,
        bitmap: Bitmap,
        rawText: String,
        filteredLines: List<String>,
        reason: String
    ) {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val dir = File(context.cacheDir, "ocr_debug")
        dir.mkdirs()

        try {
            val imgFile = File(dir, "${appName}_${ts}.jpg")
            FileOutputStream(imgFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (_: Exception) {}

        try {
            val txtFile = File(dir, "${appName}_${ts}.txt")
            txtFile.writeText("""Reason: $reason
Filtered lines: ${filteredLines.size}
Raw length: ${rawText.length}

=== raw OCR text ===
$rawText

=== filtered lines ===
${filteredLines.joinToString("\n")}
""")
        } catch (_: Exception) {}
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
