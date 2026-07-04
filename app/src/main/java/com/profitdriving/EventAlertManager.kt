package com.profitdriving

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.profitdriving.L

class EventAlertManager(private val context: Context) {

    private val TAG = "EventAlertManager"

    private val db = DatabaseHelper(context)
    private val prefs = PreferenceManager(context)
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val cooldownMap = HashMap<String, Long>()

    companion object {
        const val CHANNEL_ID  = "event_alerts"
        const val COOLDOWN_MS = 30 * 60 * 1000L
        const val WINDOW_MS   = 60 * 60 * 1000L
    }

    init {
        createChannelIfNeeded()
    }

    fun check(rawPickup: String?, rawDropoff: String?) {
        // Verificar se a feature está ativada
        if (!prefs.isEventNotificationsEnabled()) {
            L.d(TAG, "Notificações de eventos desabilitadas pelo usuário")
            return
        }

        val threshold = prefs.getEventNotificationsThreshold()
        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_MS

        listOf(
            rawPickup  to "pickup",
            rawDropoff to "dropoff",
        ).forEach { (raw, type) ->
            val normalized = normalize(raw) ?: return@forEach

            val lastNotified = cooldownMap[normalized] ?: 0L
            if (now - lastNotified < COOLDOWN_MS) return@forEach

            val count = db.countAddressInLastHour(normalized, windowStart)
            if (count >= threshold) {
                notify(normalized, count, type)
                cooldownMap[normalized] = now
            }
        }
    }

    fun resetCooldown() {
        cooldownMap.clear()
        L.d(TAG, "Cooldown resetado")
    }

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        val prefixes = listOf(
            "avenida ", "av. ", "av ", "rua ", "r. ", "r ",
            "travessa ", "tv. ", "alameda ", "al. "
        )

        var s = raw.lowercase().trim()
        for (prefix in prefixes) {
            if (s.startsWith(prefix)) {
                s = s.removePrefix(prefix).trim()
                break
            }
        }

        s = s.replace(Regex("\\s+"), " ")
            .replace(Regex("[,\\-]+\\s*$"), "")
            .trim()

        if (s.length < 6) return null
        return s.take(30)
    }

    private fun notify(address: String, count: Int, type: String) {
        val (title, message) = when (type) {
            "pickup" -> Pair(
                "Evento próximo",
                "$count corridas saindo de \"${address.replaceFirstChar { it.uppercase() }}\" na última hora"
            )
            else -> Pair(
                "Evento no destino",
                "$count corridas chegando em \"${address.replaceFirstChar { it.uppercase() }}\" na última hora"
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(address.hashCode(), notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertas de Evento",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerta quando um endereço aparece com frequência na última hora"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
