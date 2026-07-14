package com.profitdriving.accessibility

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.profitdriving.L
import com.profitdriving.accessibility.extractor.RawCardData
import java.util.Locale

class AnomalyDetector(private val context: Context) {

    data class AnomalyResult(
        val isAnomaly: Boolean,
        val severity: Severity,
        val presentFields: Int,
        val totalFields: Int,
        val missingFields: List<String>,
        val pkg: String
    )

    enum class Severity { NONE, LOW, MEDIUM, HIGH }

    fun analyze(raw: RawCardData, pkg: String): AnomalyResult {
        val full = raw.fullText.lowercase(Locale.ROOT)

        val hasPrice = Regex("""r\$\s*\d+""").containsMatchIn(full)
        val hasDistance = Regex("""\d+[.,]?\d*\s*km""").containsMatchIn(full)
        val hasTime = Regex("""\d+\s*min""").containsMatchIn(full)
        val hasAction = Regex("""aceitar|selecionar|negocia|escolher""").containsMatchIn(full)

        val fields = mapOf(
            "price" to hasPrice,
            "distance" to hasDistance,
            "time" to hasTime,
            "action" to hasAction
        )

        val missing = fields.filter { !it.value }.keys.toList()
        val present = fields.count { it.value }
        val hasSubstantialText = raw.rawTexts.any { it.length > 10 }

        val severity = when {
            !hasSubstantialText -> Severity.NONE
            present >= 3 -> Severity.NONE
            present == 2 -> Severity.LOW
            present == 1 -> Severity.MEDIUM
            else -> Severity.HIGH
        }

        return AnomalyResult(
            isAnomaly = severity >= Severity.MEDIUM,
            severity = severity,
            presentFields = present,
            totalFields = fields.size,
            missingFields = missing,
            pkg = pkg
        )
    }

    fun notify(result: AnomalyResult) {
        try {
            createChannel()
            val appLabel = if (result.pkg.contains("ubercab")) "Uber" else "99"
            val title = "Layout alterado — $appLabel"
            val message = "Campos não encontrados: ${result.missingFields.joinToString(", ")} " +
                    "(${result.presentFields}/${result.totalFields} presentes)"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            L.w(TAG, "🔔 Anomalia notificada: $title — $message")
        } catch (e: Exception) {
            L.e(TAG, "Falha ao notificar anomalia: ${e.message}")
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Alertas de Layout",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifica quando Uber/99 altera o layout dos cards de corrida"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
            nm.createNotificationChannel(channel)
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "AnomalyDetector"
        private const val CHANNEL_ID = "anomaly_alerts"
        private const val NOTIFICATION_ID = 9001
    }
}
