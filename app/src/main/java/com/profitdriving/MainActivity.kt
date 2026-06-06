package com.profitdriving

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.profitdriving.SecurePreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var progressBar: View
    private lateinit var tvA11yBadge: TextView
    private lateinit var btnRadar: View
    private var adapter: HistoryAdapter? = null
    private var filterDays = 0
    private var selectedPeriodFilter: String? = null
    private var selectedTypeFilter = "all"
    private var selectedScoreFilter = "all"
    private var currentPage = 0
    private var pageSize = 100
    private var hasMore = false
    private val handler = Handler(Looper.getMainLooper())

    private val dataUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "NEW_RIDE_SAVED") {
                loadFilteredHistory()
            }
        }
    }

    private lateinit var filterManager: FilterManager
    private var filtersExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupBottomNav(Screen.HOME)
        setupToolbar(
            showLogo = true,
            actionText = "\uD83D\uDDD1\uFE0F",
            actionListener = {
                val count = db.getAll().size
                val deletedRecords = db.getAll()
                AlertDialog.Builder(this)
                    .setTitle("Limpar histórico")
                    .setMessage("$count corridas serão apagadas permanentemente.\nEsta ação não pode ser desfeita.")
                    .setPositiveButton("Apagar tudo") { _, _ ->
                        db.deleteAll()
                        loadFilteredHistory()
                        Snackbar.make(findViewById(android.R.id.content), "Histórico apagado", Snackbar.LENGTH_LONG)
                            .setAction("Desfazer") {
                                for (r in deletedRecords) {
                                    db.insert(r)
                                }
                                loadFilteredHistory()
                            }
                            .setActionTextColor(Color.parseColor("#FFD700"))
                            .show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )
        filterDays = savedInstanceState?.getInt("filterDays", 0) ?: 0

        db = DatabaseHelper(this)
        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)

        selectedTypeFilter = savedInstanceState?.getString("typeFilter", "all") ?: "all"
        selectedScoreFilter = savedInstanceState?.getString("scoreFilter", "all") ?: "all"
        filtersExpanded = savedInstanceState?.getBoolean("filtersExpanded", false) ?: false

        tvA11yBadge = findViewById(R.id.tvAccessibilityBadge)
        btnRadar = findViewById(R.id.btnRadar)

        val btnLoadMore = findViewById<TextView>(R.id.btnLoadMore)
        btnLoadMore.setOnClickListener { loadNextPage() }

        btnRadar.setOnClickListener {
            openPermissionSettings()
        }

        filterManager = FilterManager(this)
        setupPeriodFilter()
        setupTypeFilter()
        setupScoreFilter()

        val filterToggle = findViewById<LinearLayout>(R.id.btnFilterToggle)
        val filterContainer = findViewById<LinearLayout>(R.id.filterContainer)
        val tvFilterToggleIcon = findViewById<TextView>(R.id.tvFilterToggleIcon)

        filterContainer.visibility = if (filtersExpanded) View.VISIBLE else View.GONE
        tvFilterToggleIcon.text = if (filtersExpanded) "\u25B2" else "\u25BC"

        filterToggle.setOnClickListener {
            filtersExpanded = !filtersExpanded
            filterContainer.visibility = if (filtersExpanded) View.VISIBLE else View.GONE
            tvFilterToggleIcon.text = if (filtersExpanded) "\u25B2" else "\u25BC"
        }

        findViewById<TextView>(R.id.btnClearFilters).setOnClickListener {
            filterDays = 0
            selectedPeriodFilter = null
            selectedTypeFilter = "all"
            selectedScoreFilter = "all"
            setupPeriodFilter()
            setupTypeFilter()
            setupScoreFilter()
            loadFilteredHistory()
        }
    }

    override fun onResume() {
        super.onResume()

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(dataUpdateReceiver, IntentFilter("NEW_RIDE_SAVED"))

        pageSize = SecurePreferences.get(this)
            .getInt(SettingsActivity.KEY_PAGE_SIZE, 100)
        setupTypeFilter()
        loadFilteredHistory()
        updateStatus()

        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true

        if (isAccessibilityServiceEnabled() && overlayOk) {
            FloatingBubbleService.start(this)
        }
    }

    override fun onPause() {
        super.onPause()
        try { LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(dataUpdateReceiver) } catch (_: Exception) {}
    }

    private fun loadFilteredHistory() {
        currentPage = 0
        loadPage(reset = true)
    }

    private fun loadPage(reset: Boolean) {
        val btnLoadMore = findViewById<TextView>(R.id.btnLoadMore)
        if (reset) {
            progressBar.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.GONE
            btnLoadMore.visibility = View.GONE
        }
        Thread {
            val sinceMs = if (filterDays >= 0) {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -filterDays) }
                cal.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else null

            var allRecords = if (sinceMs != null) db.getFiltered(sinceMs) else db.getAll()

            if (selectedTypeFilter != "all") {
                allRecords = allRecords.filter { it.serviceType == selectedTypeFilter }
            }

            allRecords = when (selectedScoreFilter) {
                "good" -> allRecords.filter { it.scorePercent != null && it.scorePercent >= 80.0 }
                "medium" -> allRecords.filter { it.scorePercent != null && it.scorePercent >= 50.0 && it.scorePercent < 80.0 }
                "bad" -> allRecords.filter { it.scorePercent != null && it.scorePercent < 50.0 }
                else -> allRecords
            }

            val totalCount = allRecords.size
            val fromIndex = currentPage * pageSize
            val toIndex = minOf(fromIndex + pageSize, totalCount)
            val records = if (fromIndex < totalCount) allRecords.subList(fromIndex, toIndex) else emptyList()
            handler.post {
                if (reset) progressBar.visibility = View.GONE
                if (records.isEmpty() && reset) {
                    recyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    btnLoadMore.visibility = View.GONE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                    val prefs = SecurePreferences.get(this)
                    if (reset || adapter == null) {
                        val costSummary = CostCalculator.calculateCostSummary(
                            db.getRefuels(), db.getAllExpenses(), db.getMonthlyKm()
                        )
                        adapter = HistoryAdapter(
                            records,
                            minKm = prefs.getFloat(SettingsActivity.KEY_MIN_KM, 0f),
                            idealKm = prefs.getFloat(SettingsActivity.KEY_IDEAL_KM, 0f),
                            minHour = prefs.getFloat(SettingsActivity.KEY_MIN_HOUR, 0f),
                            idealHour = prefs.getFloat(SettingsActivity.KEY_IDEAL_HOUR, 0f),
                            minMinute = prefs.getFloat(SettingsActivity.KEY_MIN_MINUTE, 0f),
                            idealMinute = prefs.getFloat(SettingsActivity.KEY_IDEAL_MINUTE, 0f),
                            minRating = prefs.getFloat(SettingsActivity.KEY_MIN_RATING, 0f),
                            idealRating = prefs.getFloat(SettingsActivity.KEY_IDEAL_RATING, 0f),
                            costPerKm = costSummary.totalCostPerKm
                        )
                        recyclerView.layoutManager = LinearLayoutManager(this)
                        recyclerView.adapter = adapter
                    } else {
                        adapter!!.appendData(records)
                    }
                    val loadedSoFar = (currentPage + 1) * pageSize
                    hasMore = loadedSoFar < totalCount
                    btnLoadMore.visibility = if (hasMore) View.VISIBLE else View.GONE
                    btnLoadMore.text = if (hasMore)
                        "Ver mais (${totalCount - loadedSoFar} restantes)" else ""
                }
            }
        }.start()
    }

    private fun loadNextPage() {
        currentPage++
        loadPage(reset = false)
    }

    private fun updateStatus() {
        val a11yOk = isAccessibilityServiceEnabled()
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true
        val allOk = a11yOk && overlayOk
        tvA11yBadge.text = if (allOk)
            "✅ Acessibilidade • Sobreposição ✓"
        else
            "⚠️ Acessibilidade • Sobreposição ✗"
        btnRadar.visibility = if (allOk) View.GONE else View.VISIBLE
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val cnV2 = ComponentName(this, com.profitdriving.accessibility.RideAccessibilityServiceV2::class.java)
            val cnV1 = ComponentName(this, RideAccessibilityService::class.java)
            return enabled.contains(cnV2.flattenToString()) || enabled.contains(cnV1.flattenToString())
        } catch (_: Exception) {
            return false
        }
    }

    private fun openPermissionSettings() {
        val a11yOk = isAccessibilityServiceEnabled()
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true

        if (!overlayOk) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        if (!a11yOk) {
            AlertDialog.Builder(this)
                .setTitle("Ativar Serviço de Acessibilidade")
                .setMessage(
                    "1. Toque em \"Abrir Configurações\"\n" +
                    "2. Toque em \"Corrida Certa\"\n" +
                    "3. Ative a chave \"Permitir\"\n" +
                    "4. Confirme em \"OK\""
                )
                .setPositiveButton("Abrir Configurações") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun setupPeriodFilter() {
        val container = findViewById<ViewGroup>(R.id.periodFilterContainer)
        filterManager.clearContainer(container)
        val options = listOf(
            FilterManager.FilterOption("0", "Hoje"),
            FilterManager.FilterOption("7", "7 dias"),
            FilterManager.FilterOption("30", "30 dias")
        )
        val selected = selectedPeriodFilter?.let { setOf(it) } ?: emptySet()
        val view = filterManager.createFilterSection(
            parent = container,
            title = "",
            options = options,
            selectedIds = selected,
            singleSelection = true,
            callback = object : FilterManager.FilterCallback {
                override fun onFilterChanged(filterId: String, isSelected: Boolean) {
                    selectedPeriodFilter = if (isSelected) filterId else null
                    filterDays = selectedPeriodFilter?.toIntOrNull() ?: 0
                    loadFilteredHistory()
                }
                override fun onClearAll() {}
            }
        )

        val marginTopDp = 16
        val marginTopPx = (marginTopDp * resources.displayMetrics.density).toInt()
        
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams
        params?.topMargin = marginTopPx
        view.layoutParams = params
        
        container.addView(view)
    }
 
    private fun setupTypeFilter() {
        val container = findViewById<ViewGroup>(R.id.typeFilterContainer)
        filterManager.clearContainer(container)
        val types = db.getDistinctServiceTypes()
        val options = listOf(FilterManager.FilterOption("all", "Todas")) +
            types.map { FilterManager.FilterOption(it, it) }
        val selected = if (selectedTypeFilter == "all") emptySet() else setOf(selectedTypeFilter)
        val view = filterManager.createFilterSection(
            parent = container,
            title = "",
            options = options,
            selectedIds = selected,
            singleSelection = true,
            callback = object : FilterManager.FilterCallback {
                override fun onFilterChanged(filterId: String, isSelected: Boolean) {
                    selectedTypeFilter = if (isSelected) filterId else "all"
                    loadFilteredHistory()
                }
                override fun onClearAll() {}
            }
        )
        container.addView(view)
    }

    private fun setupScoreFilter() {
        val container = findViewById<ViewGroup>(R.id.scoreFilterContainer)
        filterManager.clearContainer(container)
        val options = listOf(
            FilterManager.FilterOption("all", "Todas", "⭐"),
            FilterManager.FilterOption("good", "Boas (80%+)", "✅"),
            FilterManager.FilterOption("medium", "Médias (50-80%)", "⚠️"),
            FilterManager.FilterOption("bad", "Ruins (<50%)", "❌")
        )
        val selected = if (selectedScoreFilter == "all") emptySet() else setOf(selectedScoreFilter)
        val view = filterManager.createFilterSection(
            parent = container,
            title = "",
            options = options,
            selectedIds = selected,
            singleSelection = true,
            callback = object : FilterManager.FilterCallback {
                override fun onFilterChanged(filterId: String, isSelected: Boolean) {
                    selectedScoreFilter = if (isSelected) filterId else "all"
                    loadFilteredHistory()
                }
                override fun onClearAll() {}
            }
        )
        container.addView(view)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        db.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("filterDays", filterDays)
        outState.putString("typeFilter", selectedTypeFilter)
        outState.putString("scoreFilter", selectedScoreFilter)
        outState.putBoolean("filtersExpanded", filtersExpanded)
    }
}

// ============================================================
// HISTORY ADAPTER CORRIGIDO - Com funções seguras de formatação
// ============================================================

class HistoryAdapter(
    records: List<RideRecord>,
    private val minKm: Float,
    private val idealKm: Float,
    private val minHour: Float,
    private val idealHour: Float,
    private val minMinute: Float,
    private val idealMinute: Float,
    private val minRating: Float,
    private val idealRating: Float,
    private val costPerKm: Double = 0.0
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val records = records.toMutableList()
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    private val expandedItems = mutableSetOf<Int>()

    companion object {
        private const val TYPE_NORMAL = 0
    }

    // ============================================================
    // FUNÇÕES SEGURAS DE FORMATAÇÃO
    // ============================================================
    
    private fun formatMoney(value: Double?): String {
        return value?.let { "R$ %.2f".format(it).replace(".", ",") } ?: "---"
    }

    private fun formatDecimal(value: Double?): String {
        return value?.let { "%.2f".format(it).replace(".", ",") } ?: "---"
    }

    private fun formatPercent(value: Double?): String {
        return value?.let { "%.0f".format(it) } ?: "0"
    }

    private fun formatDistance(value: Double?): String {
        return value?.let { "%.1f km".format(it).replace(".", ",") } ?: ""
    }

    private fun formatTime(value: Int?): String {
        return value?.let { "${it} min" } ?: ""
    }

    private fun formatBonus(value: Double?): String {
        return value?.let { "%.2f".format(it).replace(".", ",") } ?: "0,00"
    }

    override fun getItemViewType(position: Int): Int {
        return TYPE_NORMAL
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: androidx.cardview.widget.CardView = view.findViewById(R.id.cardRoot)
        val layoutServiceBadge: View = view.findViewById(R.id.layoutServiceBadge)
        val ivServiceTypeIcon: ImageView = view.findViewById(R.id.tvServiceTypeIcon)
        val tvServiceType: TextView = view.findViewById(R.id.tvServiceType)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvRatingText: TextView = view.findViewById(R.id.tvRatingText)
        val tvPricePerKm: TextView = view.findViewById(R.id.tvPricePerKm)
        val tvPricePerHour: TextView = view.findViewById(R.id.tvPricePerHour)
        val tvPricePerMin: TextView = view.findViewById(R.id.tvPricePerMin)
        val tvKmBadge: TextView = view.findViewById(R.id.tvKmBadge)
        val tvHourBadge: TextView = view.findViewById(R.id.tvHourBadge)
        val tvMinBadge: TextView = view.findViewById(R.id.tvMinBadge)
        val tvTotalInfo: TextView = view.findViewById(R.id.tvTotalInfo)
        val tvPriorityBonus: TextView = view.findViewById(R.id.tvPriorityBonus)
        val tvDynamicBonus: TextView = view.findViewById(R.id.tvDynamicBonus)
        val tvScore: TextView = view.findViewById(R.id.tvScore)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val btnExpandProfit: TextView = view.findViewById(R.id.btnExpandProfit)
        val expandableProfitDetails: View = view.findViewById(R.id.expandableProfitDetails)
        val tvProfitDetail: TextView = view.findViewById(R.id.tvProfitDetail)
        val layoutTripInfo: View = view.findViewById(R.id.layoutTripInfo)
        val tvPickupDistance: TextView = view.findViewById(R.id.tvPickupDistance)
        val tvPickupTime: TextView = view.findViewById(R.id.tvPickupTime)
        val tvPickupAddress: TextView = view.findViewById(R.id.tvPickupAddress)
        val tvDropoffDistance: TextView = view.findViewById(R.id.tvDropoffDistance)
        val tvDropoffTime: TextView = view.findViewById(R.id.tvDropoffTime)
        val tvDropoffAddress: TextView = view.findViewById(R.id.tvDropoffAddress)
        val tvDecisionBadge: TextView = view.findViewById(R.id.tvDecisionBadge)
        val ivStops: ImageView = view.findViewById(R.id.ivStops)
        val tvStops: TextView = view.findViewById(R.id.tvStops)
        val tvProfitIcon: TextView = view.findViewById(R.id.tvProfitIcon)
        val tvProfitLabel: TextView = view.findViewById(R.id.tvProfitLabel)
        val tvProfitValue: TextView = view.findViewById(R.id.tvProfitValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ride_card, parent, false)
        return ViewHolder(view)
    }

    private fun evaluateState(value: Double?, min: Float, ideal: Float): Int {
        if (value == null) return 3
        return when {
            value >= ideal -> 0
            value >= min   -> 1
            else           -> 2
        }
    }

    private fun getBadgeTextAndColor(state: Int): Pair<String, Int> {
        return when (state) {
            0 -> Pair("✅ Bom", 0xFF4ADE80.toInt())
            1 -> Pair("⚠️ Médio", 0xFFFB923C.toInt())
            2 -> Pair("❌ Ruim", 0xFFF87171.toInt())
            else -> Pair("—", 0xFF8E9AAF.toInt())
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val r = records[position] as RideRecord
        val vh = holder as ViewHolder

        val serviceType = r.serviceType ?: r.appName
        vh.tvServiceType.text = serviceType

        val iconRes = when {
            r.serviceType == null -> R.drawable.ic_ride_generic
            r.serviceType!!.contains("Moto", ignoreCase = true) -> R.drawable.ic_moto
            r.serviceType!!.contains("Black", ignoreCase = true) ||
            r.serviceType!!.contains("Bag", ignoreCase = true) ||
            r.serviceType == "99Top" || r.serviceType == "Top" ||
            r.serviceType == "99Black" || r.serviceType == "99VIP" -> R.drawable.ic_car_luxury
            r.serviceType!!.contains("Entrega", ignoreCase = true) ||
            r.serviceType!!.contains("Flash", ignoreCase = true) ||
            r.serviceType!!.contains("Envios", ignoreCase = true) -> R.drawable.ic_delivery
            r.serviceType!!.contains("UberX", ignoreCase = true) ||
            r.serviceType!!.contains("99Pop", ignoreCase = true) ||
            r.serviceType!!.contains("Pop", ignoreCase = true) ||
            r.serviceType!!.contains("Comfort", ignoreCase = true) ||
            r.serviceType!!.contains("Juntos", ignoreCase = true) ||
            r.serviceType!!.startsWith("Manual - ") -> R.drawable.ic_car
            else -> R.drawable.ic_ride_generic
        }
        vh.ivServiceTypeIcon.setImageResource(iconRes)

        val (pillColor, textColor, iconColor) = when {
            serviceType.contains("Black", ignoreCase = true) ->
                Triple(Color.parseColor("#1A1A1A"), Color.WHITE, Color.WHITE)
            serviceType.contains("Comfort", ignoreCase = true) || serviceType.contains("Confort", ignoreCase = true) ->
                Triple(Color.parseColor("#2563EB"), Color.WHITE, Color.WHITE)
            serviceType.contains("Moto", ignoreCase = true) || serviceType.contains("Entrega", ignoreCase = true) ->
                Triple(Color.parseColor("#10B981"), Color.WHITE, Color.WHITE)
            else ->
                Triple(Color.parseColor("#F1F5F9"), 0xFF1A2C3E.toInt(), 0xFF5E6F8D.toInt())
        }
        val pillRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 28f, vh.layoutServiceBadge.context.resources.displayMetrics
        )
        vh.layoutServiceBadge.background = GradientDrawable().apply {
            cornerRadius = pillRadius
            setColor(pillColor)
        }
        vh.ivServiceTypeIcon.setColorFilter(iconColor)
        vh.tvServiceType.setTextColor(textColor)

        val decisionText = when {
            r.scorePercent != null && r.scorePercent >= 80 -> "✅ BOA (${formatPercent(r.scorePercent)}%)"
            r.scorePercent != null && r.scorePercent >= 50 -> "⚠️ MÉDIA (${formatPercent(r.scorePercent)}%)"
            else -> "❌ RUIM (${formatPercent(r.scorePercent)}%)"
        }
        val decisionBg = when {
            r.scorePercent != null && r.scorePercent >= 80 -> Color.parseColor("#D1FAE5")
            r.scorePercent != null && r.scorePercent >= 50 -> Color.parseColor("#FEF3C7")
            else -> Color.parseColor("#FEE2E2")
        }
        val decisionTextColor = when {
            r.scorePercent != null && r.scorePercent >= 80 -> Color.parseColor("#065F46")
            r.scorePercent != null && r.scorePercent >= 50 -> Color.parseColor("#92400E")
            else -> Color.parseColor("#991B1B")
        }
        vh.tvDecisionBadge.text = decisionText
        vh.tvDecisionBadge.background = GradientDrawable().apply {
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12f, vh.tvDecisionBadge.context.resources.displayMetrics
            )
            setColor(decisionBg)
        }
        vh.tvDecisionBadge.setTextColor(decisionTextColor)
        vh.tvDecisionBadge.visibility = if (r.scorePercent != null) View.VISIBLE else View.GONE

        vh.tvPrice.text = formatMoney(r.value)
        vh.tvRatingText.text = formatDecimal(r.rating)
        vh.tvPricePerKm.text = formatDecimal(r.pricePerKm)
        vh.tvPricePerHour.text = formatDecimal(r.pricePerHour)

        val pricePerMin = if (r.timeMin != null && r.timeMin > 0 && r.value != null) 
            r.value / r.timeMin 
        else null
        vh.tvPricePerMin.text = formatDecimal(pricePerMin)

        val kmState = evaluateState(r.pricePerKm, minKm, idealKm)
        val hourState = evaluateState(r.pricePerHour, minHour, idealHour)
        val minState = evaluateState(pricePerMin, minMinute, idealMinute)
        val ratingState = evaluateState(r.rating, minRating, idealRating)

        fun setBadge(badge: TextView, state: Int) {
            val radius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12f, badge.context.resources.displayMetrics
            )
            when (state) {
                0 -> {
                    badge.text = "✅ Bom"
                    badge.background = GradientDrawable().apply {
                        cornerRadius = radius
                        setColor(Color.parseColor("#D1FAE5"))
                    }
                    badge.setTextColor(Color.parseColor("#065F46"))
                    badge.visibility = View.VISIBLE
                }
                1 -> {
                    badge.text = "⚠️ Médio"
                    badge.background = GradientDrawable().apply {
                        cornerRadius = radius
                        setColor(Color.parseColor("#FEF3C7"))
                    }
                    badge.setTextColor(Color.parseColor("#92400E"))
                    badge.visibility = View.VISIBLE
                }
                2 -> {
                    badge.text = "❌ Ruim"
                    badge.background = GradientDrawable().apply {
                        cornerRadius = radius
                        setColor(Color.parseColor("#FEE2E2"))
                    }
                    badge.setTextColor(Color.parseColor("#991B1B"))
                    badge.visibility = View.VISIBLE
                }
                else -> badge.visibility = View.GONE
            }
        }
        setBadge(vh.tvKmBadge, kmState)
        setBadge(vh.tvHourBadge, hourState)
        setBadge(vh.tvMinBadge, minState)

        val ratingColor = when (ratingState) {
            0 -> Color.parseColor("#065F46")
            1 -> Color.parseColor("#92400E")
            2 -> Color.parseColor("#991B1B")
            else -> Color.parseColor("#475569")
        }
        vh.tvRatingText.setTextColor(ratingColor)

        val pickupDist = r.pickupDistanceKm
        val tripDist = r.tripDistanceKm
        
        val pickupTime = r.pickupTimeMin
        val tripTime = r.tripTimeMin
        
        val totalDist = (pickupDist ?: 0.0) + (tripDist ?: r.distanceKm ?: 0.0)
        val totalTime = (pickupTime ?: 0) + (tripTime ?: r.timeMin ?: 0)

        vh.tvPickupDistance.text = if (pickupDist != null && pickupDist > 0) 
            formatDistance(pickupDist) else ""
        vh.tvPickupTime.text = if (pickupTime != null && pickupTime > 0) 
            formatTime(pickupTime) else ""
        vh.tvPickupAddress.text = maskAddress(r.pickupAddress)

        vh.tvDropoffDistance.text = if (tripDist != null && tripDist > 0) 
            formatDistance(tripDist) else ""
        vh.tvDropoffTime.text = if (tripTime != null && tripTime > 0) 
            formatTime(tripTime) else ""
        vh.tvDropoffAddress.text = maskAddress(r.dropoffAddress)

        val totalParts = mutableListOf<String>()
        if (totalDist > 0) {
            totalParts.add(formatDistance(totalDist))
        }
        if (totalTime > 0) {
            totalParts.add("${totalTime} min")
        }
        vh.tvTotalInfo.text = totalParts.joinToString(" · ")
        vh.tvTotalInfo.visibility = if (totalParts.isNotEmpty()) View.VISIBLE else View.GONE

        vh.tvScore.visibility = View.GONE

        val priorityBonus = r.priorityBonus
        val dynamicBonus = r.dynamicBonus

        if (priorityBonus != null && priorityBonus > 0) {
            vh.tvPriorityBonus.text = "\u26A1 R$${formatBonus(priorityBonus)} prioridade"
            vh.tvPriorityBonus.visibility = View.VISIBLE
        } else {
            vh.tvPriorityBonus.visibility = View.GONE
        }

        if (dynamicBonus != null && dynamicBonus > 0) {
            vh.tvDynamicBonus.text = "\uD83D\uDD25 R$${formatBonus(dynamicBonus)} din\u00E2mica"
            vh.tvDynamicBonus.visibility = View.VISIBLE
        } else {
            vh.tvDynamicBonus.visibility = View.GONE
        }

        val profitRange = getProfitRangeForRide(r)
        val profitValue = formatProfitValue(r)
        vh.tvProfitIcon.text = profitRange.icon
        vh.tvProfitIcon.visibility = View.VISIBLE
        vh.tvProfitLabel.text = profitRange.label
        vh.tvProfitLabel.setTextColor(profitRange.color)
        vh.tvProfitLabel.visibility = View.VISIBLE
        vh.tvProfitValue.text = profitValue
        vh.tvProfitValue.setTextColor(profitRange.color)
        vh.tvProfitValue.visibility = View.VISIBLE

        if (r.hasMultipleStops) {
            vh.tvStops.text = "V\u00E1rias paradas"
            vh.tvStops.visibility = View.VISIBLE
            vh.ivStops.visibility = View.VISIBLE
        } else {
            vh.tvStops.visibility = View.GONE
            vh.ivStops.visibility = View.GONE
        }

        val isExpanded = expandedItems.contains(position)
        vh.expandableProfitDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE

        if (isExpanded) {
            val rideValue = r.value ?: 0.0
            val distance = totalDist
            val usedCostPerKm = r.costPerKmAtTime ?: costPerKm
            val totalCost = distance * usedCostPerKm
            val lucro = rideValue - totalCost
            val lucroPerc = if (rideValue > 0) (lucro / rideValue) * 100 else 0.0

            val detailText = buildString {
                appendLine("\uD83D\uDCCA Detalhamento do Lucro:")
                appendLine("─────────────────────────")
                appendLine("Valor da corrida: ${formatMoney(rideValue)}")
                appendLine("Custo por km: ${formatDecimal(usedCostPerKm)}")
                appendLine("Distância: ${formatDistance(distance)}")
                appendLine("Custo total: ${formatMoney(totalCost)}")
                appendLine("─────────────────────────")
                val lucroStr = formatMoney(lucro)
                val percStr = formatDecimal(lucroPerc)
                append("\uD83D\uDCB5 Lucro: $lucroStr ($percStr%)")
            }
            vh.tvProfitDetail.text = detailText
        }

        vh.btnExpandProfit.text = if (isExpanded) "- recolher detalhes" else "+ detalhar corrida"

        vh.btnExpandProfit.setOnClickListener {
            val currentlyExpanded = expandedItems.contains(position)
            if (currentlyExpanded) {
                expandedItems.remove(position)
            } else {
                expandedItems.add(position)
            }
            notifyItemChanged(position)
        }

        vh.tvStatus.text = when (r.status) {
            "ACCEPTED" -> "\u2705 ACEITA"
            "DECLINED" -> "\u274C RECUSADA"
            else -> ""
        }

        when (r.status) {
            "ACCEPTED" -> vh.cardRoot.setBackgroundResource(R.drawable.card_bg_accepted)
            "DECLINED" -> vh.cardRoot.setBackgroundResource(R.drawable.card_bg_declined)
            else -> {
                vh.cardRoot.setBackgroundResource(R.drawable.card_bg_neutral)
                vh.cardRoot.cardElevation = 0f
                }
            }

            vh.tvTimestamp.text = dateFormat.format(java.util.Date(r.timestamp))
        }

    private fun maskAddress(fullAddress: String?): String {
        if (fullAddress.isNullOrBlank()) return ""

        var masked = fullAddress

        // 1. Remover número da casa e a vírgula anterior
        masked = masked.replace(Regex(""",?\s*\d+(?:-\d+)?\s*[,]?"""), " ")
        masked = masked.replace(Regex("""\s+nº\s*\d+""", RegexOption.IGNORE_CASE), " ")

        // 2. Normalizar hífens
        masked = masked.replace(Regex("""\s*-\s*"""), " - ")

        // 3. Mascarar CEP (manter apenas 3 últimos dígitos)
        masked = masked.replace(Regex("""(\d{5})[-]?(\d{3})""")) { match ->
            val suffix = match.groupValues[2]
            "XXXXX-$suffix"
        }

        // 4. Limpar múltiplos espaços e vírgulas
        masked = masked
            .replace(Regex("\\s+"), " ")
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("\\s*,\\s*"), ", ")
            .trim()

        // 5. Remover vírgula no início ou final
        masked = masked.replace(Regex("^,|,$"), "")

        // 6. Limitar tamanho
        if (masked.length > 100) {
            masked = masked.take(100) + "..."
        }

        return masked
    }

    private fun getProfitRangeForRide(ride: RideRecord): ProfitRange {
        val rideValue = ride.value ?: 0.0
        val distance = (ride.pickupDistanceKm ?: 0.0) + (ride.tripDistanceKm ?: ride.distanceKm ?: 0.0)
        val usedCostPerKm = ride.costPerKmAtTime ?: costPerKm
        val totalCost = distance * usedCostPerKm
        val profit = rideValue - totalCost
        val profitPercent = if (rideValue > 0) (profit / rideValue) * 100 else 0.0
        return ProfitRange.fromPercent(profitPercent)
    }

    private fun formatProfitValue(ride: RideRecord): String {
        val rideValue = ride.value ?: 0.0
        val distance = (ride.pickupDistanceKm ?: 0.0) + (ride.tripDistanceKm ?: ride.distanceKm ?: 0.0)
        val usedCostPerKm = ride.costPerKmAtTime ?: costPerKm
        val totalCost = distance * usedCostPerKm
        val profit = rideValue - totalCost
        val profitPercent = if (rideValue > 0) (profit / rideValue) * 100 else 0.0
        
        val profitFormatted = formatMoney(profit)
        val percentFormatted = formatDecimal(profitPercent)
        
        return if (profit >= 0) {
            "$profitFormatted ($percentFormatted%)"
        } else {
            "-${profitFormatted.replace("R$", "").trim()} ($percentFormatted%)"
        }
    }

    fun appendData(newRecords: List<RideRecord>) {
        records.addAll(newRecords)
        notifyItemRangeInserted(records.size - newRecords.size, newRecords.size)
    }

    fun updateData(newRecords: List<RideRecord>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = records.size
            override fun getNewListSize() = newRecords.size
            override fun areItemsTheSame(o: Int, n: Int) =
                records[o].id == newRecords[n].id
            override fun areContentsTheSame(o: Int, n: Int) =
                records[o] == newRecords[n]
        })
        records.clear()
        records.addAll(newRecords)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = records.size
}