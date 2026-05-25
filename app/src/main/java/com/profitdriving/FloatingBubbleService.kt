package com.profitdriving

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private var bubbleView: View? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(SettingsActivity.PREF_NAME, Context.MODE_PRIVATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val value = if (intent.hasExtra("value"))
            intent.getDoubleExtra("value", -1.0).let { if (it < 0) null else it } else null
        val distanceKm = if (intent.hasExtra("distanceKm"))
            intent.getDoubleExtra("distanceKm", -1.0).let { if (it < 0) null else it } else null
        val timeMin = if (intent.hasExtra("timeMin"))
            intent.getIntExtra("timeMin", -1).let { if (it < 0) null else it } else null
        val rating = if (intent.hasExtra("rating"))
            intent.getDoubleExtra("rating", -1.0).let { if (it < 0) null else it } else null

        showOrUpdateBubble(value, distanceKm, timeMin, rating)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID, "Profit Driving Ícone",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Ícone flutuante do Profit Driving"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Profit Driving")
        .setContentText("Ícone flutuante ativo — toque para abrir o app")
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setOngoing(true)
        .build()

    private fun showOrUpdateBubble(
        value: Double?,
        distanceKm: Double?,
        timeMin: Int?,
        rating: Double?
    ) {
        val allOk = if (value != null) {
            val pricePerKm = if (distanceKm != null && distanceKm > 0) value / distanceKm else null
            val pricePerHour = if (timeMin != null && timeMin > 0) value / (timeMin / 60.0) else null

            val minKm = prefs.getFloat(SettingsActivity.KEY_MIN_KM, 0f)
            val minHour = prefs.getFloat(SettingsActivity.KEY_MIN_HOUR, 0f)
            val minRating = prefs.getFloat(SettingsActivity.KEY_MIN_RATING, 0f)

            val kmOk = pricePerKm == null || pricePerKm >= minKm.toDouble()
            val hourOk = pricePerHour == null || pricePerHour >= minHour.toDouble()
            val ratingOk = rating == null || rating >= minRating.toDouble()

            kmOk && hourOk && ratingOk
        } else {
            null
        }

        if (bubbleView != null) {
            applyBubbleColors(bubbleView!!, allOk)
            return
        }
        showBubble(allOk)
    }

    private fun showBubble(status: Boolean?) {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.bubble_layout, null)
        applyBubbleColors(view, status)

        @Suppress("DEPRECATION")
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val yOffset = (displayMetrics.heightPixels * 0.30).toInt()

        val type: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 16
            y = yOffset
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
                    try { windowManager.updateViewLayout(v, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
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
            Log.d(TAG, "Bolha exibida")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao exibir bolha", e)
            stopSelf()
        }
    }

    private fun applyBubbleColors(view: View, status: Boolean?) {
        val ringColor: String
        val dotColor: String
        when (status) {
            true -> {
                ringColor = "#4CAF50"
                dotColor = "#4CAF50"
            }
            false -> {
                ringColor = "#FF5252"
                dotColor = "#FF5252"
            }
            null -> {
                ringColor = "#444444"
                dotColor = "#444444"
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

    private fun dismiss() {
        bubbleView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
        }
        bubbleView = null
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ProfitDriving"
        private const val CHANNEL_ID = "profit_driving_bubble"
        private const val NOTIF_ID = 1002

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun start(context: Context, intent: Intent) {
            intent.setClass(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
    }
}
