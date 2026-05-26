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
        val serviceType = intent.getStringExtra("serviceType")
        val bonusAmount = intent.getDoubleExtra("bonusAmount", -1.0).let { if (it < 0) null else it }

        val ride = RideData(value, distanceKm, timeMin, rating, appName, serviceType = serviceType, bonusAmount = bonusAmount)

        Log.d(TAG, "Card: valor=$value km=$distanceKm tempo=$timeMin nota=$rating demo=$isDemo")
        showOverlay(ride, isDemo)

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID, "CorridaCerta",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação para exibir card flutuante"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("CorridaCerta")
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
        val cardLayout = prefs.getString(SettingsActivity.KEY_CARD_LAYOUT, "column")
        val layoutRes = if (cardLayout == "row") R.layout.floating_card_row else R.layout.floating_card
        val view = inflater.inflate(layoutRes, null)

        val cardRoot     = view.findViewById<View>(R.id.cardRoot)
        val tvApp        = view.findViewById<TextView>(R.id.tvApp)
        val tvValue      = view.findViewById<TextView>(R.id.tvValue)
        val tvServiceType = view.findViewById<TextView>(R.id.tvServiceType)
        val tvBonus      = view.findViewById<TextView>(R.id.tvBonus)
        val tvKm         = view.findViewById<TextView>(R.id.tvKm)
        val tvHour       = view.findViewById<TextView>(R.id.tvHour)
        val tvRating     = view.findViewById<TextView>(R.id.tvRating)
        val tvDistance   = view.findViewById<TextView>(R.id.tvDistance)
        val tvTime       = view.findViewById<TextView>(R.id.tvTime)
        val tvDecision   = view.findViewById<TextView>(R.id.tvDecision)

        val pricePerKm   = ride.effectivePricePerKm
        val pricePerHour = ride.effectivePricePerHour

        tvApp.text = ride.appName.ifEmpty { "" }
        tvValue.text = ride.value
            ?.let { "R$ %.2f".format(it).replace(".", ",") } ?: "---"

        tvServiceType.text = ride.serviceType ?: ""
        tvServiceType.visibility =
            if (ride.serviceType != null) View.VISIBLE else View.GONE

        tvBonus.text = ride.bonusAmount
            ?.let { "Bônus: R$ %.2f".format(it).replace(".", ",") } ?: ""
        tvBonus.visibility =
            if (ride.bonusAmount != null) View.VISIBLE else View.GONE

        tvKm.text = if (pricePerKm != null)
            "%.2f".format(pricePerKm).replace(".", ",")
        else "---"

        tvHour.text = if (pricePerHour != null)
            "%.2f".format(pricePerHour).replace(".", ",")
        else "---"

        tvRating.text = if (ride.rating != null)
            "%.2f".format(ride.rating).replace(".", ",")
        else "---"

        tvDistance.text = ride.distanceKm
            ?.let { "%.1f km".format(it).replace(".", ",") } ?: ""
        tvTime.text = ride.timeMin?.let { "${it} min" } ?: ""

        // Individual status per metric
        val kmStatus = SettingsActivity.evaluateParameter(pricePerKm,
            prefs.getFloat(SettingsActivity.KEY_MIN_KM, 2.5f),
            prefs.getFloat(SettingsActivity.KEY_IDEAL_KM, 4.0f))
        val hourStatus = SettingsActivity.evaluateParameter(pricePerHour,
            prefs.getFloat(SettingsActivity.KEY_MIN_HOUR, 30f),
            prefs.getFloat(SettingsActivity.KEY_IDEAL_HOUR, 60f))
        val ratingStatus = SettingsActivity.evaluateParameter(ride.rating,
            prefs.getFloat(SettingsActivity.KEY_MIN_RATING, 4.5f),
            prefs.getFloat(SettingsActivity.KEY_IDEAL_RATING, 4.9f))

        // Color each metric value
        tvKm.setTextColor(SettingsActivity.getStatusColor(kmStatus))
        tvHour.setTextColor(SettingsActivity.getStatusColor(hourStatus))
        tvRating.setTextColor(SettingsActivity.getStatusColor(ratingStatus))

        // Overall decision (worst status wins)
        val overall = SettingsActivity.getOverallStatus(pricePerKm, pricePerHour, ride.rating, prefs)

        // Decision button
        tvDecision.text = SettingsActivity.getDecisionText(overall)
        val decisionBg = GradientDrawable()
        decisionBg.setColor(SettingsActivity.getDecisionBgColor(overall))
        decisionBg.cornerRadius = 6f * resources.displayMetrics.density
        tvDecision.background = decisionBg
        tvDecision.visibility = View.VISIBLE

        // Neutral dark card background
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#1A1A2E"))
        bg.cornerRadius = 14f * resources.displayMetrics.density
        cardRoot.background = bg

        @Suppress("DEPRECATION")
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val cardSide     = prefs.getString(
            SettingsActivity.KEY_CARD_SIDE, "left")
        val cardYPercent = prefs.getInt(
            SettingsActivity.KEY_CARD_Y_PERCENT, 30)

        val screenWidth  = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val yOffset      = (screenHeight * cardYPercent / 100.0).toInt()
        val cardWidthPx  = (220 * resources.displayMetrics.density).toInt()
        val xOffset      = if (cardSide == "right")
            screenWidth - cardWidthPx - 16
        else 16

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
            x = xOffset
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
        private const val TAG = "CorridaCerta"
        private const val CHANNEL_ID = "corrida_certa_card"
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
