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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.BadTokenException
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingCardService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var handler: Handler
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlayView: View? = null

    private val dismissRunnable = Runnable { dismiss() }
    private var lastRideHash = ""
    private var lastRideTime = 0L
    private var isProcessingCard = false
    private var currentCardRideHash: String? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = SecurePreferences.get(this)
        handler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                L.w(TAG, "Permissão de sobreposição negada — card não exibido")
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
        val priorityBonus = intent.getDoubleExtra("priorityBonus", -1.0).let { if (it < 0) null else it }
        val dynamicBonus = intent.getDoubleExtra("dynamicBonus", -1.0).let { if (it < 0) null else it }
        val pickupAddress = intent.getStringExtra("pickupAddress")
        val dropoffAddress = intent.getStringExtra("dropoffAddress")
        val hasMultipleStops = intent.getBooleanExtra("hasMultipleStops", false)

        val ride = RideData(value, distanceKm, timeMin, rating, appName, serviceType = serviceType, priorityBonus = priorityBonus, dynamicBonus = dynamicBonus, pickupAddress = pickupAddress, dropoffAddress = dropoffAddress, hasMultipleStops = hasMultipleStops)

        L.d(TAG, "Card: valor=$value km=$distanceKm tempo=$timeMin nota=$rating demo=$isDemo")
        showOverlay(ride, isDemo)

        return START_STICKY
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
            try { windowManager.removeView(view) } catch (e: Exception) { L.e(TAG, "Erro ao remover overlay existente: ${e.message}", e) }
            overlayView = null
        }
        showCard(ride, isDemo)
    }

    private fun showCard(ride: RideData, isDemo: Boolean) {
        val rideHash = "${ride.value}_${ride.distanceKm}_${ride.timeMin}_${ride.rating}"

        // Debounce — mover para FORA do try para não travar isProcessingCard
        val now = System.currentTimeMillis()
        if (rideHash == lastRideHash && (now - lastRideTime) < 10000L) {
            L.d(TAG, "Mesmo card detectado em menos de 10s — ignorando (já exibindo)")
            return
        }
        if (overlayView != null && rideHash == lastRideHash) {
            L.d(TAG, "Card já está visível, apenas resetando timer")
            handler.postDelayed(dismissRunnable, prefs.getInt(SettingsActivity.KEY_CARD_DURATION, 30) * 1000L)
            return
        }
        lastRideHash = rideHash
        lastRideTime = now

        // Evitar processamento paralelo do mesmo card
        if (isProcessingCard && currentCardRideHash == rideHash) {
            L.d(TAG, "Card já está sendo processado, ignorando")
            return
        }

        isProcessingCard = true
        currentCardRideHash = rideHash

        try {
            L.d(TAG, "=== showCard INICIADO ===")
            L.d(TAG, "isDemo=$isDemo value=${ride.value} km=${ride.distanceKm} time=${ride.timeMin} rating=${ride.rating}")

            // Cancelar qualquer dismiss pendente antes de processar novo card
            handler.removeCallbacks(dismissRunnable)

        if (!::windowManager.isInitialized) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            L.d(TAG, "windowManager inicializado sob demanda")
        }
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val cardLayout = prefs.getString(SettingsActivity.KEY_CARD_LAYOUT, "column")
        val layoutRes = if (cardLayout == "row") R.layout.floating_card_row else R.layout.floating_card
        val view = inflater.inflate(layoutRes, null)

        val cardRoot     = view.findViewById<View>(R.id.cardRoot)
        val bg = GradientDrawable()
        bg.setColor(AppColors.textPrimary)
        bg.cornerRadius = 14f * resources.displayMetrics.density
        cardRoot.background = bg

        val tvApp        = view.findViewById<TextView>(R.id.tvApp)
        val tvValue      = view.findViewById<TextView>(R.id.tvValue)
        val tvServiceType = view.findViewById<TextView>(R.id.tvServiceType)
        val tvBonus      = view.findViewById<TextView>(R.id.tvBonus)
        val tvPriorityBonus = view.findViewById<TextView>(R.id.tvPriorityBonus)
        val tvDynamicBonus = view.findViewById<TextView>(R.id.tvDynamicBonus)
        val tvKm         = view.findViewById<TextView>(R.id.tvKm)
        val tvHour       = view.findViewById<TextView>(R.id.tvHour)
        val tvMinute     = view.findViewById<TextView>(R.id.tvMinute)
        val tvRating     = view.findViewById<TextView>(R.id.tvRating)
        val tvDistance   = view.findViewById<TextView>(R.id.tvDistance)
        val tvTime       = view.findViewById<TextView>(R.id.tvTime)
        val tvDecision   = view.findViewById<TextView>(R.id.tvDecision)
        val tvScore      = view.findViewById<TextView>(R.id.tvScore)
        val tvStops      = view.findViewById<TextView>(R.id.tvStops)
        val kmRow        = view.findViewById<View>(R.id.kmRow)
        val hourRow      = view.findViewById<View>(R.id.hourRow)
        val minuteRow    = view.findViewById<View>(R.id.minuteRow)
        val ratingRow    = view.findViewById<View>(R.id.ratingRow)

        val pricePerKm   = ride.effectivePricePerKm
        val pricePerHour = ride.effectivePricePerHour
        val pricePerMinute = ride.effectivePricePerMinute

        tvApp.text = ride.appName.ifEmpty { "" }
        tvValue.text = ride.value
            ?.let { "R$ %.2f".format(it).replace(".", ",") } ?: "---"

        tvServiceType.text = ride.serviceType ?: ""
        tvServiceType.visibility =
            if (ride.serviceType != null) View.VISIBLE else View.GONE

        tvBonus.visibility = View.GONE

        tvPriorityBonus.text = ride.priorityBonus
            ?.let { "\u26A1 R$ ${"%.2f".format(it).replace(".", ",")}" } ?: ""
        tvPriorityBonus.visibility =
            if (ride.priorityBonus != null) View.VISIBLE else View.GONE

        tvDynamicBonus.text = ride.dynamicBonus
            ?.let { "\uD83D\uDD25 R$ ${"%.2f".format(it).replace(".", ",")}" } ?: ""
        tvDynamicBonus.visibility =
            if (ride.dynamicBonus != null) View.VISIBLE else View.GONE

        tvKm.text = if (pricePerKm != null)
            "%.2f".format(pricePerKm).replace(".", ",")
        else "---"

        tvHour.text = if (pricePerHour != null)
            "%.2f".format(pricePerHour).replace(".", ",")
        else "---"

        tvMinute.text = if (pricePerMinute != null)
            "%.2f".format(pricePerMinute).replace(".", ",")
        else "---"

        tvRating.text = if (ride.rating != null)
            "%.2f".format(ride.rating).replace(".", ",")
        else "---"

        tvDistance.text = ride.distanceKm
            ?.let { "%.1f km".format(it).replace(".", ",") } ?: ""
        tvTime.text = ride.timeMin?.let { "${it} min" } ?: ""

        val result = DecisionEngine.evaluate(
            kmValue     = ride.effectivePricePerKm,
            hourValue   = ride.effectivePricePerHour,
            minValue    = pricePerMinute,
            ratingValue = ride.rating,
            prefs       = prefs
        )

        tvKm.setTextColor(DecisionEngine.stateColor(result.params[0].state))
        tvHour.setTextColor(DecisionEngine.stateColor(result.params[1].state))
        tvMinute.setTextColor(DecisionEngine.stateColor(result.params[2].state))
        tvRating.setTextColor(DecisionEngine.stateColor(result.params[3].state))

        kmRow.visibility = if (prefs.getBoolean(SettingsActivity.KEY_SHOW_KM, true)) View.VISIBLE else View.GONE
        hourRow.visibility = if (prefs.getBoolean(SettingsActivity.KEY_SHOW_HOUR, true)) View.VISIBLE else View.GONE
        minuteRow.visibility = if (prefs.getBoolean(SettingsActivity.KEY_SHOW_MINUTE, true)) View.VISIBLE else View.GONE
        ratingRow.visibility = if (prefs.getBoolean(SettingsActivity.KEY_SHOW_RATING, true)) View.VISIBLE else View.GONE

        val pts = "${result.totalPoints.toInt()}/${result.maxPoints.toInt()} pts"
        val decisionText = "${DecisionEngine.decisionText(result.decision)} ${"%.0f".format(result.scorePercent)}% ($pts)"
        tvDecision.text = decisionText
        val decisionBg = GradientDrawable()
        decisionBg.setColor(DecisionEngine.overlayDecisionColor(result.decision))
        decisionBg.cornerRadius = 6f * resources.displayMetrics.density
        tvDecision.background = decisionBg
        tvDecision.visibility = View.VISIBLE
        tvDecision.isClickable = false

        tvScore.visibility = View.GONE

        val tvProfit = view.findViewById<TextView>(R.id.tvProfit)
        val tvProfitLabel = view.findViewById<TextView>(R.id.tvProfitLabel)
        tvProfit.visibility = View.GONE
        tvProfitLabel.visibility = View.GONE

        val layoutStops = view.findViewById<View>(R.id.layoutStops)
        if (ride.hasMultipleStops) {
            layoutStops.visibility = View.VISIBLE
            L.d(TAG, "Exibindo várias paradas no card")
        } else {
            layoutStops.visibility = View.GONE
        }

        val tvKnownDestination = view.findViewById<TextView>(R.id.tvKnownDestination)
        if (!ride.dropoffAddress.isNullOrEmpty() && !isDemo) {
            serviceScope.launch(Dispatchers.IO) {
                val db = DatabaseHelper(this@FloatingCardService)
                val visitCount = db.getCompletedVisitsCountForAddress(ride.dropoffAddress!!)

                withContext(Dispatchers.Main) {
                    if (visitCount > 0) {
                        val visitText = if (visitCount == 1) "1x" else "${visitCount}x"
                        tvKnownDestination.text = "\uD83D\uDCCD Destino conhecido · $visitText"
                        tvKnownDestination.visibility = View.VISIBLE
                    }
                }
            }
        } else if (isDemo && ride.dropoffAddress != null) {
            tvKnownDestination.text = "\uD83D\uDCCD Destino conhecido · 3x"
            tvKnownDestination.visibility = View.VISIBLE
        }

        // Congela o costPerKm no momento da criação do card
        serviceScope.launch {
            try {
                val costSummary = CostSummaryCache.getCurrentSummary(this@FloatingCardService)
                val frozenCostPerKm = costSummary.totalCostPerKm
                val estimatedProfit = CostCalculator.estimateProfit(
                    ride.value, ride.distanceKm, frozenCostPerKm
                )
                val estimatedProfitPercent = CostCalculator.estimateProfitPercent(
                    ride.value, ride.distanceKm, frozenCostPerKm
                )
                applyDecisionBorder(cardRoot, result.decision)

                if (estimatedProfit != null && estimatedProfitPercent != null) {
                    val profitRange = ProfitRange.fromPercent(estimatedProfitPercent)

                    tvProfitLabel.text = profitRange.label
                    tvProfitLabel.setTextColor(profitRange.color)
                    tvProfitLabel.visibility = View.VISIBLE

                    val profitValue = if (estimatedProfit >= 0) {
                        "R$ ${String.format("%.2f", estimatedProfit).replace(".", ",")} (${String.format("%.0f", estimatedProfitPercent)}%)"
                    } else {
                        "-R$ ${String.format("%.2f", -estimatedProfit).replace(".", ",")} (${String.format("%.0f", estimatedProfitPercent)}%)"
                    }
                    tvProfit.text = profitValue
                    tvProfit.setTextColor(profitRange.color)
                    tvProfit.visibility = View.VISIBLE
                }
            } catch (e: Exception) { L.e(TAG, "Erro ao calcular lucro estimado: ${e.message}", e) }
        }

        @Suppress("DEPRECATION")
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val cardPosition = prefs.getString(
            SettingsActivity.KEY_CARD_POSITION, "left"
        )
        val cardYPercent = prefs.getInt(
            SettingsActivity.KEY_CARD_Y_PERCENT, 30)

        val screenWidth  = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val yOffset      = (screenHeight * cardYPercent / 100.0).toInt()
        val cardWidthPx  = (220 * resources.displayMetrics.density).toInt()
        val xOffset      = when (cardPosition) {
            "right" -> screenWidth - cardWidthPx - 16
            "center" -> (screenWidth - cardWidthPx) / 2
            else -> 16
        }

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
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            when (cardPosition) {
                "right" -> {
                    gravity = Gravity.END or Gravity.TOP
                    x = 16
                }
                "center" -> {
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP  // MUDE AQUI
                    x = 0
                }
                else -> {
                    gravity = Gravity.START or Gravity.TOP
                    x = 16
                }
            }
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
            if (!::windowManager.isInitialized) {
                windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            }
            windowManager.addView(view, params)
            overlayView = view
            L.d(TAG, "Card exibido com sucesso: isDemo=$isDemo decisao=${DecisionEngine.decisionText(result.decision)}")
        } catch (e: WindowManager.BadTokenException) {
            L.e(TAG, "BadTokenException ao adicionar overlay: ${e.message}")
            stopSelf()
            return
        } catch (e: Exception) {
            L.e(TAG, "Falha ao adicionar overlay view: ${e.message}", e)
            stopSelf()
            return
        }

        handler.removeCallbacks(dismissRunnable)
        if (!isDemo) {
            val durationMs = prefs.getInt(SettingsActivity.KEY_CARD_DURATION, 30) * 1000L
            handler.postDelayed(dismissRunnable, durationMs)
        }

        } catch (e: Exception) {
            L.e(TAG, "Erro ao exibir card: ${e.message}", e)
        } finally {
            isProcessingCard = false
        }
    }

    private fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) { L.e(TAG, "Erro ao remover overlay view no dismiss: ${e.message}", e) }
        }
        overlayView = null
        currentCardRideHash = null
        // Não chamar stopSelf() imediatamente - deixar o serviço vivo
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        serviceScope.cancel()
        dismiss()
        stopSelf()
    }

    override fun onDestroy() {
        L.d(TAG, "FloatingCardService destruído")
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        dismiss()
        super.onDestroy()
    }

    private fun applyDecisionBorder(view: View, decision: DecisionEngine.Decision) {
        val borderColor = when (decision) {
            DecisionEngine.Decision.ACEITAR -> "#00A86B"
            DecisionEngine.Decision.ANALISAR -> "#F97316"
            DecisionEngine.Decision.RECUSAR -> "#DC2626"
        }
        val gd = GradientDrawable()
        gd.setColor(AppColors.textPrimary)
        gd.cornerRadius = 14f * resources.displayMetrics.density
        gd.setStroke(3.dpToPx(), Color.parseColor(borderColor))
        view.background = gd
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

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
