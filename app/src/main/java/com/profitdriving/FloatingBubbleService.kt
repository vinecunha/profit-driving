package com.profitdriving

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.profitdriving.SecurePreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bubbleView: View? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = SecurePreferences.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (bubbleView == null) {
            showBubble(null)
        }

        if (intent == null) return START_STICKY

        val decision = intent.getStringExtra("decision")
        if (decision != null) {
            val status = when (decision) {
                "ACEITAR" -> "good"
                "ANALISAR" -> "medium"
                else -> "bad"
            }
            updateBubbleColor(status)
            return START_STICKY
        }

        val value = if (intent.hasExtra("value"))
            intent.getDoubleExtra("value", -1.0).let { if (it < 0) null else it } else null
        val distanceKm = if (intent.hasExtra("distanceKm"))
            intent.getDoubleExtra("distanceKm", -1.0).let { if (it < 0) null else it } else null
        val timeMin = if (intent.hasExtra("timeMin"))
            intent.getIntExtra("timeMin", -1).let { if (it < 0) null else it } else null
        val rating = if (intent.hasExtra("rating"))
            intent.getDoubleExtra("rating", -1.0).let { if (it < 0) null else it } else null

        updateBubbleByRide(value, distanceKm, timeMin, rating)
        return START_STICKY
    }

    private fun updateBubbleByRide(value: Double?, distanceKm: Double?, timeMin: Int?, rating: Double?) {
        val status = if (value != null) {
            val pricePerKm = if (distanceKm != null && distanceKm > 0) value / distanceKm else null
            val pricePerHour = if (timeMin != null && timeMin > 0) value / (timeMin / 60.0) else null
            val pricePerMinute = if (timeMin != null && timeMin > 0) value / timeMin else null

            val result = DecisionEngine.evaluate(
                kmValue = pricePerKm,
                hourValue = pricePerHour,
                minValue = pricePerMinute,
                ratingValue = rating,
                prefs = prefs
            )

            when (result.decision) {
                DecisionEngine.Decision.ACEITAR -> "good"
                DecisionEngine.Decision.ANALISAR -> "medium"
                DecisionEngine.Decision.RECUSAR -> "bad"
            }
        } else {
            null
        }
        updateBubbleColor(status)
    }

    private fun updateBubbleColor(status: String?) {
        if (bubbleView == null) {
            showBubble(status)
        } else {
            applyBubbleColors(bubbleView!!, status)
        }
    }

    private fun showBubble(status: String?) {
        if (bubbleView != null) return

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.bubble_layout, null)
        applyBubbleColors(view, status)

        val displayMetrics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.maximumWindowMetrics
            windowMetrics.bounds.let { bounds ->
                android.util.DisplayMetrics().apply {
                    widthPixels = bounds.width()
                    heightPixels = bounds.height()
                }
            }
        } else {
            @Suppress("DEPRECATION")
            android.util.DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
        }
        val yOffset = (displayMetrics.heightPixels * 0.30).toInt()

        val type: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val savedX = prefs.getInt("bubble_x", 16)
        val savedY = prefs.getInt("bubble_y", yOffset)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = savedX
            y = savedY
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try { windowManager.updateViewLayout(v, params) } catch (e: Exception) { L.e(TAG, "Erro ao atualizar layout bubble: ${e.message}", e) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit()
                        .putInt("bubble_x", params.x)
                        .putInt("bubble_y", params.y)
                        .apply()
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (dx * dx + dy * dy < 100) {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
            bubbleView = view
            L.d(TAG, "Bolha exibida com sucesso")
        } catch (e: Exception) {
            L.e(TAG, "Falha ao exibir bolha: ${e.message}", e)
        }
    }

    private fun applyBubbleColors(view: View, status: String?) {
        val ringColor: String
        val dotColor: String
        when (status) {
            "good" -> {
                ringColor = "#00A86B"
                dotColor = "#00A86B"
            }
            "medium" -> {
                ringColor = "#F97316"
                dotColor = "#F97316"
            }
            "bad" -> {
                ringColor = "#DC2626"
                dotColor = "#DC2626"
            }
            else -> {
                ringColor = "#94A3B8"
                dotColor = "#94A3B8"
            }
        }

        val ring = view.findViewById<View>(R.id.statusRing)
        val ringDrawable = GradientDrawable()
        ringDrawable.shape = GradientDrawable.OVAL
        ringDrawable.setStroke(3.dpToPx(), Color.parseColor(ringColor))
        ringDrawable.setColor(Color.TRANSPARENT)
        ring.background = ringDrawable

        val dot = view.findViewById<View>(R.id.statusDot)
        val dotDrawable = GradientDrawable()
        dotDrawable.shape = GradientDrawable.OVAL
        dotDrawable.setColor(Color.parseColor(dotColor))
        dot.background = dotDrawable
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID, "CorridaCerta Ícone",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ícone flutuante do CorridaCerta"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("CorridaCerta")
        .setContentText("Ícone flutuante ativo")
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun dismiss() {
        bubbleView?.let { view ->
            try { windowManager.removeView(view) } catch (e: Exception) { L.e(TAG, "Erro ao remover bubble view no dismiss: ${e.message}", e) }
        }
        bubbleView = null
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) { L.e(TAG, "Erro ao remover bubble view no onDestroy: ${e.message}", e) }
        }
        bubbleView = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CorridaCerta"
        private const val CHANNEL_ID = "corrida_certa_bubble"
        private const val NOTIF_ID = 1002

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun start(context: Context, intent: Intent) {
            intent.setClass(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
    }
}
