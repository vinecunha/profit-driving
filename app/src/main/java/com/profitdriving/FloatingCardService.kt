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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingCardService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var handler: Handler
    private var overlayView: View? = null

    private val dismissRunnable = Runnable { dismiss() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(SettingsActivity.PREF_NAME, Context.MODE_PRIVATE)
        handler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Permissão de sobreposição negada — card não exibido")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val isDemo = intent.getBooleanExtra("isDemo", false)
        val value = intent.getDoubleExtra("value", -1.0).let { if (it < 0) null else it }
        val distanceKm = intent.getDoubleExtra("distanceKm", -1.0).let { if (it < 0) null else it }
        val timeMin = intent.getIntExtra("timeMin", -1).let { if (it < 0) null else it }
        val rating = intent.getDoubleExtra("rating", -1.0).let { if (it < 0) null else it }
        val appName = intent.getStringExtra("appName") ?: ""

        val ride = RideData(value, distanceKm, timeMin, rating, appName)

        Log.d(TAG, "Card: valor=$value km=$distanceKm tempo=$timeMin nota=$rating demo=$isDemo")
        showOverlay(ride, isDemo)

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID, "Profit Driving",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação para exibir card flutuante"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Profit Driving")
        .setContentText("Analisando corridas...")
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setOngoing(false)
        .build()

    private fun showOverlay(ride: RideData, isDemo: Boolean = false) {
        overlayView?.let { view ->
            try { windowManager.removeView(view) } catch (_: Exception) {}
            overlayView = null
        }
        showCard(ride, isDemo)
    }

    private fun showCard(ride: RideData, isDemo: Boolean) {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.floating_card, null)

        val cardRoot = view.findViewById<View>(R.id.cardRoot)
        val tvApp = view.findViewById<TextView>(R.id.tvApp)
        val tvValue = view.findViewById<TextView>(R.id.tvValue)
        val tvKm = view.findViewById<TextView>(R.id.tvKm)
        val tvHour = view.findViewById<TextView>(R.id.tvHour)
        val tvRating = view.findViewById<TextView>(R.id.tvRating)
        val tvDistance = view.findViewById<TextView>(R.id.tvDistance)
        val tvTime = view.findViewById<TextView>(R.id.tvTime)
        val dotKm = view.findViewById<View>(R.id.dotKm)
        val dotHour = view.findViewById<View>(R.id.dotHour)
        val dotRating = view.findViewById<View>(R.id.dotRating)

        val pricePerKm = ride.effectivePricePerKm
        val pricePerHour = ride.effectivePricePerHour

        tvApp.text = ride.appName.ifEmpty { "" }
        tvValue.text = ride.value?.let { "R$ %.2f".format(it).replace(".", ",") } ?: "---"

        tvKm.text = if (pricePerKm != null)
            "%.2f".format(pricePerKm).replace(".", ",")
        else
            "---"

        tvHour.text = if (pricePerHour != null)
            "%.2f".format(pricePerHour).replace(".", ",")
        else
            "---"

        tvRating.text = if (ride.rating != null)
            "%.2f".format(ride.rating).replace(".", ",")
        else
            "---"

        tvDistance.text = ride.distanceKm?.let { "%.1f km".format(it).replace(".", ",") } ?: ""
        tvTime.text = ride.timeMin?.let { "${it} min" } ?: ""

        val minKm = prefs.getFloat(SettingsActivity.KEY_MIN_KM, 0f)
        val minHour = prefs.getFloat(SettingsActivity.KEY_MIN_HOUR, 0f)
        val minRating = prefs.getFloat(SettingsActivity.KEY_MIN_RATING, 0f)

        val kmOk = pricePerKm == null || pricePerKm >= minKm
        val hourOk = pricePerHour == null || pricePerHour >= minHour
        val ratingOk = ride.rating == null || ride.rating >= minRating

        val allOk = kmOk && hourOk && ratingOk

        val bgColor = if (allOk) "#1B7B4A" else "#B71C1C"
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor(bgColor))
        bg.cornerRadius = 14f * resources.displayMetrics.density
        cardRoot.background = bg

        val dotVisibility = if (allOk) View.GONE else View.VISIBLE
        dotKm.visibility = dotVisibility
        dotHour.visibility = dotVisibility
        dotRating.visibility = dotVisibility

        if (!allOk) {
            setDot(dotKm, kmOk)
            setDot(dotHour, hourOk)
            setDot(dotRating, ratingOk)
        }

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

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dismiss()
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
            Log.d(TAG, "Card exibido com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao exibir card", e)
            stopSelf()
            return
        }

        handler.removeCallbacks(dismissRunnable)
        if (!isDemo) {
            handler.postDelayed(dismissRunnable, 12000L)
        }
    }

    private fun setDot(dot: View, isOk: Boolean) {
        val color = if (isOk) "#4CAF50" else "#FF5252"
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(Color.parseColor(color))
        dot.background = d
    }

    private fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
        }
        overlayView = null
        handler.postDelayed({ stopSelf() }, 200)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        dismiss()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ProfitDriving"
        private const val CHANNEL_ID = "profit_driving_card"
        private const val NOTIF_ID = 1001

        fun start(context: Context, intent: Intent) {
            intent.setClass(context, FloatingCardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
