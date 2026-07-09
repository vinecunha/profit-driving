package com.profitdriving

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.profitdriving.FormatUtils
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
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
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
    private val reputationManager by lazy { AddressReputationManager(this) }

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

        val action = intent.getStringExtra("ACTION")
        if (action == "SHOW_LAST_RIDE") {
            val value = intent.getDoubleExtra("value", 0.0).let { if (it <= 0) null else it }
            val distanceKm = intent.getDoubleExtra("distanceKm", 0.0).let { if (it <= 0) null else it }
            val timeMin = intent.getIntExtra("timeMin", -1).let { if (it < 0) null else it }
            val rating = intent.getDoubleExtra("rating", -1.0).let { if (it < 0) null else it }
            val appName = intent.getStringExtra("appName") ?: ""
            val serviceType = intent.getStringExtra("serviceType")
            val pickupAddress = intent.getStringExtra("pickupAddress")
            val dropoffAddress = intent.getStringExtra("dropoffAddress")

            val ride = RideData(
                value = value,
                distanceKm = distanceKm,
                timeMin = timeMin,
                rating = rating,
                appName = appName,
                serviceType = serviceType,
                pickupAddress = pickupAddress,
                dropoffAddress = dropoffAddress
            )
            showOverlay(ride, false)
            return START_STICKY
        }

        val isDemo = intent.getBooleanExtra("isDemo", false)
        val isReScan = intent.getBooleanExtra("reScan", false)
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

        L.d(TAG, "Card: valor=$value km=$distanceKm tempo=$timeMin nota=$rating demo=$isDemo reScan=$isReScan")
        showOverlay(ride, isDemo, isReScan)

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

    fun isCardVisible(): Boolean {
        return overlayView != null && overlayView?.visibility == View.VISIBLE
    }

    private fun showOverlay(ride: RideData, isDemo: Boolean = false, isReScan: Boolean = false) {
        overlayView?.let { view ->
            try { windowManager.removeView(view) } catch (e: Exception) { L.e(TAG, "Erro ao remover overlay existente: ${e.message}", e) }
            overlayView = null
        }
        showCard(ride, isDemo, isReScan)
    }

    private fun showCard(ride: RideData, isDemo: Boolean, isReScan: Boolean = false) {
        val rideHash = "${ride.value}_${ride.distanceKm}_${ride.timeMin}_${ride.rating}"

        if (!isReScan) {
            val now = System.currentTimeMillis()
            if (rideHash == lastRideHash && (now - lastRideTime) < 10000L) {
                L.d(TAG, "Mesmo card detectado em menos de 10s — ignorando (já exibindo)")
                return
            }
            if (overlayView != null && rideHash == lastRideHash) {
                L.d(TAG, "Card já está visível, apenas resetando timer")
handler.postDelayed(dismissRunnable, prefs.getInt(SettingsActivity.KEY_CARD_DURATION, 15) * 1000L)
            return
        }
    } else {
        L.d(TAG, "Re-scan: ignorando debounce, recriando card")
    }
    lastRideHash = rideHash
    lastRideTime = System.currentTimeMillis()

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
        val layoutRes = when {
            ride.appName == "99" && cardLayout == "row" -> R.layout.floating_card_row_99
            ride.appName == "99"                        -> R.layout.floating_card_99
            cardLayout == "row"                         -> R.layout.floating_card_row
            else                                        -> R.layout.floating_card
        }
        val view = inflater.inflate(layoutRes, null)

        val cardRoot     = view.findViewById<View>(R.id.cardRoot)
        val bg = GradientDrawable()
        bg.setColor(AppColors.overlayBg)
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

        val llPickupReputation   = view.findViewById<android.view.ViewGroup>(R.id.llPickupReputation)
        val dotPickupReputation  = view.findViewById<View>(R.id.dotPickupReputation)
        val tvPickupReputation   = view.findViewById<TextView>(R.id.tvPickupReputation)

        val llDropoffReputation  = view.findViewById<android.view.ViewGroup>(R.id.llDropoffReputation)
        val dotDropoffReputation = view.findViewById<View>(R.id.dotDropoffReputation)
        val tvDropoffReputation  = view.findViewById<TextView>(R.id.tvDropoffReputation)

        val llReputationActions  = view.findViewById<android.view.ViewGroup>(R.id.llReputationActions)
        val btnGreenPickup       = view.findViewById<TextView>(R.id.btnGreenPickup)
        val btnBlackPickup       = view.findViewById<TextView>(R.id.btnBlackPickup)
        val btnGreenDropoff      = view.findViewById<TextView>(R.id.btnGreenDropoff)
        val btnBlackDropoff      = view.findViewById<TextView>(R.id.btnBlackDropoff)

        // IDs exclusivos da 99 — null em layouts Uber, sem crash
        val ll99Block           = view.findViewById<View?>(R.id.ll99Block)
        val tvDynamicMultiplier = view.findViewById<TextView?>(R.id.tvDynamicMultiplier)
        val tvBaseRate          = view.findViewById<TextView?>(R.id.tvBaseRate)
        val llNegotiateOptions  = view.findViewById<View?>(R.id.llNegotiateOptions)
        val tvNegOpt1           = view.findViewById<TextView?>(R.id.tvNegOpt1)
        val tvNegOpt2           = view.findViewById<TextView?>(R.id.tvNegOpt2)
        val tvNegOpt3           = view.findViewById<TextView?>(R.id.tvNegOpt3)

        val density = resources.displayMetrics.density
        val cornerRadius = resources.getDimension(R.dimen.badge_corner_radius)

        fun styleActionBtn(tv: TextView, bgColorRes: Int, textColorRes: Int) {
            val gd = GradientDrawable()
            gd.setColor(getColor(bgColorRes))
            gd.cornerRadius = cornerRadius
            tv.background = gd
            tv.setTextColor(getColor(textColorRes))
        }
        styleActionBtn(btnGreenPickup,  R.color.success_bg,  R.color.success_text)
        styleActionBtn(btnBlackPickup,  R.color.error_bg,    R.color.error_text)
        styleActionBtn(btnGreenDropoff, R.color.success_bg,  R.color.success_text)
        styleActionBtn(btnBlackDropoff, R.color.error_bg,    R.color.error_text)

        val negCorner = resources.getDimension(R.dimen.badge_corner_radius)
        fun styleNegBtn(tv: TextView?) {
            tv ?: return
            val gd = GradientDrawable()
            gd.setColor(getColor(R.color.overlay_btn_action))
            gd.cornerRadius = negCorner
            gd.setStroke(1, getColor(R.color.overlay_border))
            tv.background = gd
        }
        styleNegBtn(tvNegOpt1)
        styleNegBtn(tvNegOpt2)
        styleNegBtn(tvNegOpt3)

        val pricePerKm   = ride.effectivePricePerKm
        val pricePerHour = ride.effectivePricePerHour
        val pricePerMinute = ride.effectivePricePerMinute

        tvApp.text = ride.appName.ifEmpty { "" }
        tvValue.text = ride.value
            ?.let { FormatUtils.currency(it) } ?: "---"

        tvServiceType.text = ride.serviceType ?: ""
        tvServiceType.visibility =
            if (ride.serviceType != null) View.VISIBLE else View.GONE

        tvBonus.visibility = View.GONE

        tvPriorityBonus.text = ride.priorityBonus
            ?.let { "\u26A1 R$ ${FormatUtils.decimal(it)}" } ?: ""
        tvPriorityBonus.visibility =
            if (ride.priorityBonus != null) View.VISIBLE else View.GONE

        tvDynamicBonus.text = ride.dynamicBonus
            ?.let { "\uD83D\uDD25 R$ ${FormatUtils.decimal(it)}" } ?: ""
        tvDynamicBonus.visibility =
            if (ride.dynamicBonus != null) View.VISIBLE else View.GONE

        // ── Bloco exclusivo da 99 ──────────────────────────────────────
        if (ride.appName == "99") {
            ll99Block?.visibility = View.VISIBLE

            val multiplier = ride.dynamicMultiplier
            if (multiplier != null) {
                tvDynamicMultiplier?.text = "⚡ ${FormatUtils.decimal(multiplier)}x"
                tvDynamicMultiplier?.visibility = View.VISIBLE
            } else {
                tvDynamicMultiplier?.visibility = View.GONE
            }

            val base = ride.baseRate
            if (base != null) {
                tvBaseRate?.text = "R\$ ${FormatUtils.decimal(base)} tarifa base incl."
                tvBaseRate?.visibility = View.VISIBLE
            } else {
                tvBaseRate?.visibility = View.GONE
            }

            val opts = ride.negotiateOptions
            if (ride.isNegotiate && opts != null && opts.size >= 2) {
                tvNegOpt1?.text = opts.getOrNull(0)?.let { "R\$ ${FormatUtils.decimal(it)}" } ?: ""
                tvNegOpt2?.text = opts.getOrNull(1)?.let { "R\$ ${FormatUtils.decimal(it)}" } ?: ""
                tvNegOpt3?.text = opts.getOrNull(2)?.let { "R\$ ${FormatUtils.decimal(it)}" } ?: ""
                llNegotiateOptions?.visibility = View.VISIBLE
            } else {
                llNegotiateOptions?.visibility = View.GONE
            }
        }

        tvKm.text = if (pricePerKm != null)
            FormatUtils.decimal(pricePerKm)
        else "---"

        tvHour.text = if (pricePerHour != null)
            FormatUtils.decimal(pricePerHour)
        else "---"

        tvMinute.text = if (pricePerMinute != null)
            FormatUtils.decimal(pricePerMinute)
        else "---"

        tvRating.text = if (ride.rating != null)
            FormatUtils.decimal(ride.rating)
        else "---"

        tvDistance.text = ride.distanceKm
                ?.let { FormatUtils.distance(it) } ?: ""
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
        val ivKnownDestination = view.findViewById<ImageView>(R.id.ivKnownDestination)
        val llKnownDestination = if (cardLayout != "row") view.findViewById<View>(R.id.llKnownDestination) else null
        if (!ride.dropoffAddress.isNullOrEmpty() && !isDemo) {
            serviceScope.launch(Dispatchers.IO) {
                val db = DatabaseHelper(this@FloatingCardService)
                val visitCount = db.getCompletedVisitsCountForAddress(ride.dropoffAddress!!)

                withContext(Dispatchers.Main) {
                    if (visitCount > 0) {
                        val visitText = if (visitCount == 1) "1x" else "${visitCount}x"
                        tvKnownDestination.text = "Destino conhecido · $visitText"
                        tvKnownDestination.visibility = View.VISIBLE
                        ivKnownDestination.visibility = View.VISIBLE
                        llKnownDestination?.visibility = View.VISIBLE
                    }
                }
            }
        } else if (isDemo && ride.dropoffAddress != null) {
            tvKnownDestination.text = "Destino conhecido · 3x"
            tvKnownDestination.visibility = View.VISIBLE
            ivKnownDestination.visibility = View.VISIBLE
            llKnownDestination?.visibility = View.VISIBLE
        }

        // ── Reputação de endereço (greenlist / blacklist) ──────────────
        if (!isDemo) {
            serviceScope.launch(Dispatchers.IO) {
                val pickupRep  = reputationManager.getReputation(ride.pickupAddress)
                val dropoffRep = reputationManager.getReputation(ride.dropoffAddress)

                withContext(Dispatchers.Main) {
                    if (pickupRep != AddressReputationManager.Reputation.NONE) {
                        val (dotColor, label) = when (pickupRep) {
                            AddressReputationManager.Reputation.GREEN ->
                                Pair(R.color.overlay_success, "Embarque bom")
                            AddressReputationManager.Reputation.BLACK ->
                                Pair(R.color.overlay_error, "Embarque — evitar")
                            else -> null
                        } ?: return@withContext

                        val gd = GradientDrawable()
                        gd.shape = GradientDrawable.OVAL
                        gd.setColor(getColor(dotColor))
                        dotPickupReputation.background = gd

                        tvPickupReputation.text = label
                        tvPickupReputation.setTextColor(getColor(dotColor))
                        llPickupReputation.visibility = View.VISIBLE
                    }

                    if (dropoffRep != AddressReputationManager.Reputation.NONE) {
                        val (dotColor, label) = when (dropoffRep) {
                            AddressReputationManager.Reputation.GREEN ->
                                Pair(R.color.overlay_success, "Destino bom")
                            AddressReputationManager.Reputation.BLACK ->
                                Pair(R.color.overlay_error, "Destino — evitar")
                            else -> null
                        } ?: return@withContext

                        val gd = GradientDrawable()
                        gd.shape = GradientDrawable.OVAL
                        gd.setColor(getColor(dotColor))
                        dotDropoffReputation.background = gd

                        tvDropoffReputation.text = label
                        tvDropoffReputation.setTextColor(getColor(dotColor))
                        llDropoffReputation.visibility = View.VISIBLE
                    }

                    if (ride.pickupAddress.isNullOrEmpty()) {
                        btnGreenPickup.visibility  = View.GONE
                        btnBlackPickup.visibility  = View.GONE
                    }
                    if (ride.dropoffAddress.isNullOrEmpty()) {
                        btnGreenDropoff.visibility = View.GONE
                        btnBlackDropoff.visibility = View.GONE
                    }
                }
            }
        }

        // ── Botões de ação do painel de reputação ─────────────────────
        fun saveReputation(
            address: String?,
            reputation: AddressReputationManager.Reputation,
            reputationView: android.view.ViewGroup,
            dotView: View,
            tvView: TextView,
        ) {
            serviceScope.launch(Dispatchers.IO) {
                reputationManager.setReputation(address, reputation)
                withContext(Dispatchers.Main) {
                    llReputationActions.visibility = View.GONE

                    if (reputation != AddressReputationManager.Reputation.NONE) {
                        val (dotColor, label) = when (reputation) {
                            AddressReputationManager.Reputation.GREEN ->
                                Pair(R.color.overlay_success,
                                     if (address == ride.pickupAddress) "Embarque bom" else "Destino bom")
                            AddressReputationManager.Reputation.BLACK ->
                                Pair(R.color.overlay_error,
                                     if (address == ride.pickupAddress) "Embarque — evitar" else "Destino — evitar")
                            else -> return@withContext
                        }
                        val gd = GradientDrawable()
                        gd.shape = GradientDrawable.OVAL
                        gd.setColor(getColor(dotColor))
                        dotView.background = gd
                        tvView.text = label
                        tvView.setTextColor(getColor(dotColor))
                        reputationView.visibility = View.VISIBLE
                    } else {
                        reputationView.visibility = View.GONE
                    }

                    handler.removeCallbacks(dismissRunnable)
                    val durationMs = prefs.getInt(SettingsActivity.KEY_CARD_DURATION, 15) * 1000L
                    handler.postDelayed(dismissRunnable, durationMs)
                }
            }
        }

        btnGreenPickup.setOnClickListener {
            saveReputation(ride.pickupAddress, AddressReputationManager.Reputation.GREEN,
                           llPickupReputation, dotPickupReputation, tvPickupReputation)
        }
        btnBlackPickup.setOnClickListener {
            saveReputation(ride.pickupAddress, AddressReputationManager.Reputation.BLACK,
                           llPickupReputation, dotPickupReputation, tvPickupReputation)
        }
        btnGreenDropoff.setOnClickListener {
            saveReputation(ride.dropoffAddress, AddressReputationManager.Reputation.GREEN,
                           llDropoffReputation, dotDropoffReputation, tvDropoffReputation)
        }
        btnBlackDropoff.setOnClickListener {
            saveReputation(ride.dropoffAddress, AddressReputationManager.Reputation.BLACK,
                           llDropoffReputation, dotDropoffReputation, tvDropoffReputation)
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
                        "${FormatUtils.currency(estimatedProfit)} (${FormatUtils.integer(estimatedProfitPercent.toInt())}%)"
                    } else {
                        "-${FormatUtils.currency(-estimatedProfit)} (${FormatUtils.integer(estimatedProfitPercent.toInt())}%)"
                    }
                    tvProfit.text = profitValue
                    tvProfit.setTextColor(profitRange.color)
                    tvProfit.visibility = View.VISIBLE
                }
            } catch (e: Exception) { L.e(TAG, "Erro ao calcular lucro estimado: ${e.message}", e) }
        }

        val cardPosition = prefs.getString(
            SettingsActivity.KEY_CARD_POSITION, "left"
        )
        val cardYPercent = prefs.getInt(
            SettingsActivity.KEY_CARD_Y_PERCENT, 30)

        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
            android.graphics.Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        val screenWidth  = bounds.width()
        val screenHeight = bounds.height()
        val yOffset      = ((screenHeight * cardYPercent / 100.0).toInt())
            .coerceIn(0, screenHeight - 200)
        val displayDensity = resources.displayMetrics.density
        val minCardPx  = (220 * displayDensity).toInt()
        val maxCardPx  = (320 * displayDensity).toInt()
        val cardWidthPx = (screenWidth * 0.28).toInt().coerceIn(minCardPx, maxCardPx)

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
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            when (cardPosition) {
                "right" -> {
                    gravity = Gravity.END or Gravity.TOP
                    x = 16
                }
                "center" -> {
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    x = 0
                }
                else -> {
                    gravity = Gravity.START or Gravity.TOP
                    x = 16
                }
            }
            y = yOffset
        }

        var longPressRunnable: Runnable? = null
        var touchDownX = 0f
        var touchDownY = 0f
        val longPressThresholdMs = 500L
        val touchSlopPx = 12f * resources.displayMetrics.density

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.rawX
                    touchDownY = event.rawY

                    if (llReputationActions.visibility == View.VISIBLE) {
                        return@setOnTouchListener false
                    }

                    val runnable = Runnable {
                        longPressRunnable = null
                        handler.removeCallbacks(dismissRunnable)
                        llReputationActions.visibility = View.VISIBLE
                    }
                    longPressRunnable = runnable
                    handler.postDelayed(runnable, longPressThresholdMs)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - touchDownX)
                    val dy = Math.abs(event.rawY - touchDownY)
                    if (dx > touchSlopPx || dy > touchSlopPx) {
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        longPressRunnable = null
                    }
                    false
                }

                MotionEvent.ACTION_UP -> {
                    val pending = longPressRunnable
                    if (pending != null) {
                        handler.removeCallbacks(pending)
                        longPressRunnable = null

                        if (llReputationActions.visibility == View.GONE) {
                            dismiss()
                        }
                    }
                    true
                }

                else -> false
            }
        }

        val animation = PreferenceManager(this).getCardAnimation()

        try {
            if (!::windowManager.isInitialized) {
                windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            }
            windowManager.addView(view, params)
            overlayView = view

            when (animation) {
                AnimationConstants.ANIMATION_FADE,
                AnimationConstants.ANIMATION_FADE_SLIDE -> view.alpha = 0f
                AnimationConstants.ANIMATION_SLIDE_RIGHT -> view.translationX = 200f
                AnimationConstants.ANIMATION_SLIDE_LEFT -> view.translationX = -200f
                AnimationConstants.ANIMATION_FADE_SLIDE -> view.translationY = 80f
                AnimationConstants.ANIMATION_NONE -> {}
            }

            when (animation) {
                AnimationConstants.ANIMATION_NONE -> {}
                AnimationConstants.ANIMATION_FADE -> {
                    view.animate()
                        .alpha(1f)
                        .setDuration(350)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                AnimationConstants.ANIMATION_SLIDE_RIGHT,
                AnimationConstants.ANIMATION_SLIDE_LEFT -> {
                    view.animate()
                        .translationX(0f)
                        .setDuration(400)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                AnimationConstants.ANIMATION_FADE_SLIDE -> {
                    view.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(350)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }

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
            val durationMs = prefs.getInt(SettingsActivity.KEY_CARD_DURATION, 15) * 1000L
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
            val animation = PreferenceManager(this).getCardAnimation()
            when (animation) {
                AnimationConstants.ANIMATION_NONE -> {
                    try { windowManager.removeView(view) } catch (e: Exception) { L.e(TAG, "Erro ao remover overlay view no dismiss: ${e.message}", e) }
                    overlayView = null
                    currentCardRideHash = null
                }
                AnimationConstants.ANIMATION_FADE,
                AnimationConstants.ANIMATION_FADE_SLIDE -> {
                    view.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            try { windowManager.removeView(view) } catch (e: Exception) { L.e(TAG, "Erro ao remover overlay view no dismiss: ${e.message}", e) }
                            overlayView = null
                            currentCardRideHash = null
                        }
                        .start()
                }
                AnimationConstants.ANIMATION_SLIDE_RIGHT -> {
                    view.animate()
                        .translationX(200f)
                        .setDuration(250)
                        .withEndAction {
                            try { windowManager.removeView(view) } catch (e: Exception) { L.e(TAG, "Erro ao remover overlay view no dismiss: ${e.message}", e) }
                            overlayView = null
                            currentCardRideHash = null
                        }
                        .start()
                }
                AnimationConstants.ANIMATION_SLIDE_LEFT -> {
                    view.animate()
                        .translationX(-200f)
                        .setDuration(250)
                        .withEndAction {
                            try { windowManager.removeView(view) } catch (e: Exception) { L.e(TAG, "Erro ao remover overlay view no dismiss: ${e.message}", e) }
                            overlayView = null
                            currentCardRideHash = null
                        }
                        .start()
                }
            }
        } ?: run {
            overlayView = null
            currentCardRideHash = null
        }
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
            DecisionEngine.Decision.ACEITAR -> AppColors.success
            DecisionEngine.Decision.ANALISAR -> AppColors.warning
            DecisionEngine.Decision.RECUSAR -> AppColors.error
        }
        val gd = GradientDrawable()
        gd.setColor(AppColors.overlayBg)
        gd.cornerRadius = 14f * resources.displayMetrics.density
        gd.setStroke(3.dpToPx(), borderColor)
        view.background = gd
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

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingCardService::class.java))
        }
    }
}
