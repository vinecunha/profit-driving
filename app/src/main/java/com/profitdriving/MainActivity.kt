package com.profitdriving

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private var currentTypeFilter = "all"
    private var currentScoreFilter = "all"
    private var currentPage = 0
    private var pageSize = 100
    private var hasMore = false
    private val handler = Handler(Looper.getMainLooper())

    private val filterViews = mutableListOf<TextView>()
    private var scoreFilterViews = mutableListOf<TextView>()
    private var filtersExpanded = false
    private val serviceTypePills = mutableListOf<TextView>()

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

        filterViews.addAll(listOf(
            findViewById(R.id.btnFilterToday),
            findViewById(R.id.btnFilter7d),
            findViewById(R.id.btnFilter30d),
            findViewById(R.id.btnFilterAll)
        ))

        currentTypeFilter = savedInstanceState?.getString("typeFilter", "all") ?: "all"
        currentScoreFilter = savedInstanceState?.getString("scoreFilter", "all") ?: "all"
        filtersExpanded = savedInstanceState?.getBoolean("filtersExpanded", false) ?: false

        findViewById<TextView>(R.id.btnFilterAllTypes).setOnClickListener { setTypeFilter("all") }

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

        loadServiceTypeFilters()

        val btnFilterAllScores = findViewById<TextView>(R.id.btnFilterAllScores)
        val btnFilterGood = findViewById<TextView>(R.id.btnFilterGood)
        val btnFilterMedium = findViewById<TextView>(R.id.btnFilterMedium)
        val btnFilterBad = findViewById<TextView>(R.id.btnFilterBad)
        scoreFilterViews = mutableListOf(btnFilterAllScores, btnFilterGood, btnFilterMedium, btnFilterBad)

        btnFilterAllScores.setOnClickListener { setScoreFilter("all") }
        btnFilterGood.setOnClickListener { setScoreFilter("good") }
        btnFilterMedium.setOnClickListener { setScoreFilter("medium") }
        btnFilterBad.setOnClickListener { setScoreFilter("bad") }

        tvA11yBadge = findViewById(R.id.tvAccessibilityBadge)
        btnRadar = findViewById(R.id.btnRadar)

        val btnLoadMore = findViewById<TextView>(R.id.btnLoadMore)

        findViewById<TextView>(R.id.btnFilterToday).setOnClickListener { setFilter(0) }
        findViewById<TextView>(R.id.btnFilter7d).setOnClickListener { setFilter(7) }
        findViewById<TextView>(R.id.btnFilter30d).setOnClickListener { setFilter(30) }
        findViewById<TextView>(R.id.btnFilterAll).setOnClickListener { setFilter(-1) }

        btnLoadMore.setOnClickListener { loadNextPage() }

        btnRadar.setOnClickListener {
            openPermissionSettings()
        }

        updateTypeFilterButtons()
        updateScoreFilterButtons()
        setFilter(filterDays)
    }

    override fun onResume() {
        super.onResume()

        pageSize = getSharedPreferences(SettingsActivity.PREF_NAME, 0)
            .getInt(SettingsActivity.KEY_PAGE_SIZE, 100)
        loadServiceTypeFilters()
        loadFilteredHistory()
        updateStatus()

        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true

        if (isAccessibilityServiceEnabled() && overlayOk) {
            FloatingBubbleService.start(this)
        }
    }

    private fun setFilter(days: Int) {
        filterDays = days
        filterViews.forEachIndexed { i, v ->
            val selected = (i == 0 && days == 0) ||
                    (i == 1 && days == 7) ||
                    (i == 2 && days == 30) ||
                    (i == 3 && days == -1)
            v.setBackgroundResource(
                if (selected) R.drawable.pill_selected else R.drawable.pill_unselected
            )
            v.setTextColor(
                if (selected) 0xFFFFFFFF.toInt() else 0xFF8E9AAF.toInt()
            )
        }
        loadFilteredHistory()
    }

    private fun updateTypeFilterButtons() {
        for (btn in serviceTypePills) {
            val value = btn.tag as? String ?: continue
            val selected = currentTypeFilter == value
            btn.setBackgroundResource(if (selected) R.drawable.pill_selected else R.drawable.pill_unselected)
            btn.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF8E9AAF.toInt())
        }
    }

    private fun updateScoreFilterButtons() {
        val options = listOf("all" to 0, "good" to 1, "medium" to 2, "bad" to 3)
        for ((value, index) in options) {
            val selected = currentScoreFilter == value
            val btn = scoreFilterViews[index]
            btn.setBackgroundResource(if (selected) R.drawable.pill_selected else R.drawable.pill_unselected)
            btn.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF8E9AAF.toInt())
        }
    }

    private fun setTypeFilter(type: String) {
        currentTypeFilter = type
        updateTypeFilterButtons()
        loadFilteredHistory()
    }

    private fun setScoreFilter(score: String) {
        currentScoreFilter = score
        updateScoreFilterButtons()
        loadFilteredHistory()
    }

    private fun loadServiceTypeFilters() {
        val container = findViewById<LinearLayout>(R.id.serviceTypeContainer)
        val types = db.getDistinctServiceTypes().sorted()
        serviceTypePills.clear()
        findViewById<TextView>(R.id.btnFilterAllTypes).tag = "all"
        serviceTypePills.add(findViewById(R.id.btnFilterAllTypes))

        var i = container.childCount - 1
        while (i >= 0) {
            val child = container.getChildAt(i)
            if (child.id != R.id.btnFilterAllTypes) {
                container.removeViewAt(i)
            }
            i--
        }

        for (type in types) {
            val pill = LayoutInflater.from(this).inflate(R.layout.item_pill_filter, container, false) as TextView
            pill.text = type
            pill.tag = type
            pill.setOnClickListener { setTypeFilter(type) }
            container.addView(pill)
            serviceTypePills.add(pill)
        }

        updateTypeFilterButtons()
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

            if (currentTypeFilter != "all") {
                allRecords = allRecords.filter { it.serviceType == currentTypeFilter }
            }

            allRecords = when (currentScoreFilter) {
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
                    val prefs = getSharedPreferences(SettingsActivity.PREF_NAME, 0)
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
            val cn = ComponentName(this, RideAccessibilityService::class.java)
            return enabled.contains(cn.flattenToString())
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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        db.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("filterDays", filterDays)
        outState.putString("typeFilter", currentTypeFilter)
        outState.putString("scoreFilter", currentScoreFilter)
        outState.putBoolean("filtersExpanded", filtersExpanded)
    }
}

// ============================================================
// HISTORY ADAPTER CORRIGIDO - Usa os thresholds MIN e IDEAL
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
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val records = records.toMutableList()
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    private val expandedItems = mutableSetOf<Int>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: androidx.cardview.widget.CardView = view.findViewById(R.id.cardRoot)
        val tvServiceType: TextView = view.findViewById(R.id.tvServiceType)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvRatingText: TextView = view.findViewById(R.id.tvRatingText)
        val tvPricePerKm: TextView = view.findViewById(R.id.tvPricePerKm)
        val tvPricePerHour: TextView = view.findViewById(R.id.tvPricePerHour)
        val tvPricePerMin: TextView = view.findViewById(R.id.tvPricePerMin)
        val tvKmBadge: TextView = view.findViewById(R.id.tvKmBadge)
        val tvHourBadge: TextView = view.findViewById(R.id.tvHourBadge)
        val tvMinBadge: TextView = view.findViewById(R.id.tvMinBadge)
        val tvPickupDistance: TextView = view.findViewById(R.id.tvPickupDistance)
        val tvPickupTime: TextView = view.findViewById(R.id.tvPickupTime)
        val tvDistanceWithIcon: TextView = view.findViewById(R.id.tvDistanceWithIcon)
        val tvTimeWithIcon: TextView = view.findViewById(R.id.tvTimeWithIcon)
        val tvTotalInfo: TextView = view.findViewById(R.id.tvTotalInfo)
        val tvBonus: TextView = view.findViewById(R.id.tvBonus)
        val tvScore: TextView = view.findViewById(R.id.tvScore)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val btnExpandProfit: TextView = view.findViewById(R.id.btnExpandProfit)
        val expandableProfitDetails: View = view.findViewById(R.id.expandableProfitDetails)
        val tvProfitDetail: TextView = view.findViewById(R.id.tvProfitDetail)
        val layoutAddresses: View = view.findViewById(R.id.layoutAddresses)
        val tvPickupAddress: TextView = view.findViewById(R.id.tvPickupAddress)
        val tvDropoffAddress: TextView = view.findViewById(R.id.tvDropoffAddress)
        val tvStops: TextView = view.findViewById(R.id.tvStops)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val r = records[position]

        holder.tvServiceType.text = r.serviceType ?: r.appName

        holder.tvPrice.text = r.value?.let {
            "R$ %.2f".format(it).replace(".", ",")
        } ?: "---"

        holder.tvRatingText.text = r.rating?.let {
            "%.2f".format(it).replace(".", ",")
        } ?: "---"

        holder.tvPricePerKm.text = r.pricePerKm?.let {
            "%.2f".format(it).replace(".", ",")
        } ?: "---"

        holder.tvPricePerHour.text = r.pricePerHour?.let {
            "%.2f".format(it).replace(".", ",")
        } ?: "---"

        val pricePerMin = if (r.timeMin != null && r.timeMin > 0 && r.value != null) 
            r.value / r.timeMin 
        else null
        holder.tvPricePerMin.text = pricePerMin?.let {
            "%.2f".format(it).replace(".", ",")
        } ?: "---"

        val kmState = evaluateState(r.pricePerKm, minKm, idealKm)
        val hourState = evaluateState(r.pricePerHour, minHour, idealHour)
        val minState = evaluateState(pricePerMin, minMinute, idealMinute)
        val ratingState = evaluateState(r.rating, minRating, idealRating)

        val (kmText, kmColor) = getBadgeTextAndColor(kmState)
        holder.tvKmBadge.text = kmText
        holder.tvKmBadge.setTextColor(kmColor)

        val (hourText, hourColor) = getBadgeTextAndColor(hourState)
        holder.tvHourBadge.text = hourText
        holder.tvHourBadge.setTextColor(hourColor)

        val (minText, minColor) = getBadgeTextAndColor(minState)
        holder.tvMinBadge.text = minText
        holder.tvMinBadge.setTextColor(minColor)

        val (_, ratingColor) = getBadgeTextAndColor(ratingState)
        holder.tvRatingText.setTextColor(ratingColor)

        holder.tvPickupDistance.text = r.pickupDistanceKm?.let {
            "%.1f km".format(it).replace(".", ",")
        } ?: r.distanceKm?.let {
            "%.1f km".format(it).replace(".", ",")
        } ?: ""
        holder.tvPickupTime.text = r.pickupTimeMin?.let { "${it} min" } ?: r.timeMin?.let { "${it} min" } ?: ""

        holder.tvDistanceWithIcon.text = r.tripDistanceKm?.let {
            "%.1f km".format(it).replace(".", ",")
        } ?: r.distanceKm?.let {
            "%.1f km".format(it).replace(".", ",")
        } ?: ""
        holder.tvTimeWithIcon.text = r.tripTimeMin?.let { "${it} min" } ?: r.timeMin?.let { "${it} min" } ?: ""

        val totalParts = mutableListOf<String>()
        r.distanceKm?.let { totalParts.add("%.1f km".format(it).replace(".", ",")) }
        r.timeMin?.let { totalParts.add("${it} min") }
        holder.tvTotalInfo.text = totalParts.joinToString(" · ")

        val scoreText = r.scorePercent?.let { "${"%.0f".format(it)}%" } ?: ""
        holder.tvScore.text = scoreText

        val isKmGood = kmState == 0
        val isHourGood = hourState == 0
        val isRatingGood = ratingState == 0
        val allGood = isKmGood && isHourGood && isRatingGood
        val partial = isKmGood || isHourGood

        val scoreBgColor = when {
            allGood -> Color.parseColor("#E8F5E9")
            partial -> Color.parseColor("#FFF3E0")
            else -> Color.parseColor("#FFEBEE")
        }
        val scoreFgColor = when {
            allGood -> Color.parseColor("#2E7D32")
            partial -> Color.parseColor("#ED6C02")
            else -> Color.parseColor("#D32F2F")
        }
        holder.tvScore.setBackgroundColor(scoreBgColor)
        holder.tvScore.setTextColor(scoreFgColor)

        holder.tvBonus.visibility = View.GONE

        val hasAddress = r.pickupAddress != null || r.dropoffAddress != null
        holder.layoutAddresses.visibility = if (hasAddress) View.VISIBLE else View.GONE
        holder.tvPickupAddress.text = r.pickupAddress ?: ""
        holder.tvDropoffAddress.text = r.dropoffAddress ?: ""

        if (r.stops != null && r.stops > 0) {
            val stopsText = if (r.stops == 1) {
                "\uD83D\uDD04 Várias paradas"
            } else {
                "\uD83D\uDD04 ${r.stops} paradas"
            }
            holder.tvStops.text = stopsText
            holder.tvStops.visibility = View.VISIBLE
        } else {
            holder.tvStops.visibility = View.GONE
        }

        val isExpanded = expandedItems.contains(position)
        holder.expandableProfitDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE

        if (isExpanded) {
            val rideValue = r.value ?: 0.0
            val distance = (r.pickupDistanceKm ?: 0.0) + (r.tripDistanceKm ?: r.distanceKm ?: 0.0)
            val totalCost = distance * costPerKm
            val lucro = rideValue - totalCost
            val lucroPerc = if (rideValue > 0) (lucro / rideValue) * 100 else 0.0

            val detailText = buildString {
                appendLine("\uD83D\uDCCA Detalhamento do Lucro:")
                appendLine("─────────────────────────")
                appendLine("Valor da corrida: R$ %.2f".format(rideValue).replace(".", ","))
                appendLine("Custo por km: R$ %.2f".format(costPerKm).replace(".", ","))
                appendLine("Distância: %.1f km".format(distance).replace(".", ","))
                appendLine("Custo total: R$ %.2f".format(totalCost).replace(".", ","))
                appendLine("─────────────────────────")
                val lucroStr = "R$ %.2f".format(lucro).replace(".", ",")
                val percStr = "%.1f%%".format(lucroPerc).replace(".", ",")
                append("\uD83D\uDCB5 Lucro: $lucroStr ($percStr)")
            }
            holder.tvProfitDetail.text = detailText
        }

        holder.btnExpandProfit.text = if (isExpanded) "- recolher detalhes" else "+ detalhar corrida"

        holder.btnExpandProfit.setOnClickListener {
            val currentlyExpanded = expandedItems.contains(position)
            if (currentlyExpanded) {
                expandedItems.remove(position)
            } else {
                expandedItems.add(position)
            }
            notifyItemChanged(position)
        }

        holder.tvStatus.text = when (r.status) {
            "ACCEPTED" -> "\u2705 ACEITA"
            "DECLINED" -> "\u274C RECUSADA"
            else -> ""
        }

        when (r.status) {
            "ACCEPTED" -> holder.cardRoot.setBackgroundResource(R.drawable.card_bg_accepted)
            "DECLINED" -> holder.cardRoot.setBackgroundResource(R.drawable.card_bg_declined)
            else -> holder.cardRoot.setBackgroundResource(android.R.color.white)
        }

        holder.tvTimestamp.text = dateFormat.format(java.util.Date(r.timestamp))
    }

    fun appendData(newRecords: List<RideRecord>) {
        val startPos = records.size
        records.addAll(newRecords)
        notifyItemRangeInserted(startPos, newRecords.size)
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
