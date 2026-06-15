package com.profitdriving

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class MyDayActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var rvCompleted: RecyclerView
    private lateinit var rvAvailable: RecyclerView
    private lateinit var emptyCompleted: TextView
    private lateinit var emptyAvailable: TextView
    private lateinit var btnAddManualRide: LinearLayout
    private lateinit var filterCategoryContainer: WrapContentFlowLayout
    private lateinit var btnClearFilters: TextView
    private lateinit var tvCompletedCount: TextView
    private lateinit var tvHistoryInfo: TextView
    private lateinit var btnLoadMoreAvailable: TextView
    private lateinit var progressAvailable: ProgressBar

    // Period navigation views
    private lateinit var btnModeDay: TextView
    private lateinit var btnModeWeek: TextView
    private lateinit var btnModeMonth: TextView
    private lateinit var tvPeriodTitle: TextView
    private lateinit var btnPrevPeriod: TextView
    private lateinit var btnNextPeriod: TextView
    private lateinit var btnToday: TextView

    // Summary views
    private lateinit var tvRideCount: TextView
    private lateinit var tvTotalDistance: TextView
    private lateinit var tvTotalDuration: TextView
    private lateinit var tvGrossRevenue: TextView
    private lateinit var tvNetProfit: TextView
    private lateinit var tvProfitPercent: TextView
    private lateinit var tvRevenuePerKm: TextView
    private lateinit var tvRevenuePerHour: TextView
    private lateinit var tvPeriodBadge: TextView

    // Cost breakdown views
    private lateinit var btnToggleCostDetails: TextView
    private lateinit var layoutCostDetails: LinearLayout
    private var costBreakdownItems = listOf<CostBreakdownItem>()
    private var costDetailsExpanded = false

    // Filter toggle views
    private lateinit var btnToggleFilters: LinearLayout
    private lateinit var filterContainer: LinearLayout
    private lateinit var tvFilterToggleIcon: TextView

    private lateinit var filterManager: FilterManager
    private lateinit var etSearchRides: EditText
    private lateinit var btnClearSearch: TextView

    private lateinit var completedAdapter: MyDayRideAdapter
    private lateinit var availableAdapter: AvailableRideAdapter

    private val allDailyRides = CopyOnWriteArrayList<DailyRide>()
    private val allRideRecords = mutableMapOf<Long, RideRecord>()
    private var availableRidesList = mutableListOf<RideRecord>()

    // Period navigation state
    private var currentMode = ViewMode.DAY
    private var referenceCalendar = Calendar.getInstance()

    // Pagination state
    private var availableRidesPage = 0
    private val availableRidesPageSize = 20
    private var hasMoreAvailableRides = false
    private var totalAvailableRides = 0
    private var isLoadingMore = false
    private var costPerKm = 0.0
    private var fuelCostPerKm = 0.0

    // Filter state
    private var selectedTimeFilter: Int? = null
    private var selectedValueFilter: Int? = null
    private var selectedCategory: String? = null
    private var searchQuery = ""

    private val timeRanges = listOf(
        6 to 12,
        12 to 18,
        18 to 24
    )

    private val valueRanges = listOf(
        0.0 to 15.0,
        15.0 to 30.0,
        30.0 to 50.0,
        50.0 to Double.MAX_VALUE
    )

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dayFormatter = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("pt", "BR"))
    private val weekFormatter = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
    private val monthFormatter = SimpleDateFormat("MMMM, yyyy", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_day)
        setupBottomNav(Screen.MY_DAY)

        db = DatabaseHelper(this)
        referenceCalendar = Calendar.getInstance()

        initViews()
        setupPeriodNavigation()
        setupRecyclerViews()
        setupTimeFilters()
        setupValueFilters()
        setupCategoryFilters()
        setupSearchFilter()
        setViewMode(ViewMode.DAY)
    }

    override fun onPause() {
        super.onPause()
        saveData()
    }

    private fun initViews() {
        rvCompleted = findViewById(R.id.rvCompletedRides)
        rvAvailable = findViewById(R.id.rvAvailableRides)
        emptyCompleted = findViewById(R.id.emptyCompleted)
        emptyAvailable = findViewById(R.id.emptyAvailable)
        btnAddManualRide = findViewById(R.id.btnAddManualRide)
        filterCategoryContainer = findViewById(R.id.filterCategoryContainer)
        btnClearFilters = findViewById(R.id.btnClearFilters)
        tvCompletedCount = findViewById(R.id.tvCompletedCount)
        tvHistoryInfo = findViewById(R.id.tvHistoryInfo)
        btnLoadMoreAvailable = findViewById(R.id.btnLoadMoreAvailable)
        progressAvailable = findViewById(R.id.progressAvailable)

        btnModeDay = findViewById(R.id.btnModeDay)
        btnModeWeek = findViewById(R.id.btnModeWeek)
        btnModeMonth = findViewById(R.id.btnModeMonth)
        tvPeriodTitle = findViewById(R.id.tvPeriodTitle)
        btnPrevPeriod = findViewById(R.id.btnPrevPeriod)
        btnNextPeriod = findViewById(R.id.btnNextPeriod)
        btnToday = findViewById(R.id.btnToday)

        btnLoadMoreAvailable.setOnClickListener { loadMoreAvailableRides() }

        tvRideCount = findViewById(R.id.tvRideCount)
        tvTotalDistance = findViewById(R.id.tvTotalDistance)
        tvTotalDuration = findViewById(R.id.tvTotalDuration)
        tvGrossRevenue = findViewById(R.id.tvGrossRevenue)
        tvNetProfit = findViewById(R.id.tvNetProfit)
        tvProfitPercent = findViewById(R.id.tvProfitPercent)
        tvRevenuePerKm = findViewById(R.id.tvRevenuePerKm)
        tvRevenuePerHour = findViewById(R.id.tvRevenuePerHour)
        tvPeriodBadge = findViewById(R.id.tvPeriodBadge)

        btnToggleCostDetails = findViewById(R.id.btnToggleCostDetails)
        layoutCostDetails = findViewById(R.id.layoutCostDetails)
        btnToggleCostDetails.setOnClickListener { toggleCostDetails() }

        btnToggleFilters = findViewById(R.id.btnToggleFilters)
        filterContainer = findViewById(R.id.filterContainer)
        tvFilterToggleIcon = findViewById(R.id.tvFilterToggleIcon)
        btnToggleFilters.setOnClickListener { toggleFilters() }

        etSearchRides = findViewById(R.id.etSearchRides)
        btnClearSearch = findViewById(R.id.btnClearSearch)

        btnAddManualRide.setOnClickListener { showManualRideDialog() }
        btnClearFilters.setOnClickListener {
            selectedTimeFilter = null
            selectedValueFilter = null
            selectedCategory = null
            searchQuery = ""
            etSearchRides.setText("")
            setupTimeFilters()
            setupValueFilters()
            setupCategoryFilters()
            applyFilters()
            loadAvailableRides(reset = true)
        }
    }

    private fun setupRecyclerViews() {
        completedAdapter = MyDayRideAdapter(
            items = emptyList(),
            rideRecords = emptyMap(),
            costPerKm = 0.0,
            onToggleCompleted = { onToggleCompleted(it) },
            onAddTip = { showTipDialog(it) },
            onAdjust = { showAdjustmentDialog(it) },
            onCancelWithFee = { onCancelWithFee(it) }
        )
        rvCompleted.layoutManager = LinearLayoutManager(this)
        rvCompleted.adapter = completedAdapter

        availableAdapter = AvailableRideAdapter(
            items = availableRidesList,
            onAddToDay = { onAddToDay(it) }
        )
        rvAvailable.layoutManager = LinearLayoutManager(this)
        rvAvailable.adapter = availableAdapter
    }

    private fun setupTimeFilters() {
        filterManager = FilterManager(this)
        val container = findViewById<LinearLayout>(R.id.timeFilterContainer)
        filterManager.clearContainer(container)
        val options = listOf(
            FilterManager.FilterOption("0", "06-12h", "🌅"),
            FilterManager.FilterOption("1", "12-18h", "☀️"),
            FilterManager.FilterOption("2", "18-00h", "🌙")
        )
        val selected = selectedTimeFilter?.let { setOf(it.toString()) } ?: emptySet()
        val view = filterManager.createFilterSection(
            parent = container,
            title = "🕐 Horário",
            options = options,
            selectedIds = selected,
            singleSelection = true,
            callback = object : FilterManager.FilterCallback {
                override fun onFilterChanged(filterId: String, isSelected: Boolean) {
                    selectedTimeFilter = if (isSelected) filterId.toIntOrNull() else null
                    applyFilters()
                    loadAvailableRides(reset = true)
                }
                override fun onClearAll() {}
            }
        )
        container.addView(view)
    }

    private fun setupValueFilters() {
        val container = findViewById<LinearLayout>(R.id.valueFilterContainer)
        filterManager.clearContainer(container)
        val options = listOf(
            FilterManager.FilterOption("0", "R\$ 0-15", "💰"),
            FilterManager.FilterOption("1", "R\$ 15-30", "💰"),
            FilterManager.FilterOption("2", "R\$ 30-50", "💰"),
            FilterManager.FilterOption("3", "R\$ 50+", "💰")
        )
        val selected = selectedValueFilter?.let { setOf(it.toString()) } ?: emptySet()
        val view = filterManager.createFilterSection(
            parent = container,
            title = "💰 Valor",
            options = options,
            selectedIds = selected,
            singleSelection = true,
            callback = object : FilterManager.FilterCallback {
                override fun onFilterChanged(filterId: String, isSelected: Boolean) {
                    selectedValueFilter = if (isSelected) filterId.toIntOrNull() else null
                    applyFilters()
                    loadAvailableRides(reset = true)
                }
                override fun onClearAll() {}
            }
        )
        container.addView(view)
    }

    private fun setupCategoryFilters() {
        val categories = db.getDistinctServiceTypes()
        filterCategoryContainer.removeAllViews()
        if (categories.isEmpty()) {
            filterCategoryContainer.visibility = View.GONE
            return
        }
        filterCategoryContainer.visibility = View.VISIBLE

        val allOpt = FilterManager.FilterOption("__all__", "Todas")
        val allPill = filterManager.createPill(allOpt, selectedCategory == null)
        allPill.setOnClickListener {
            selectedCategory = null
            setupCategoryFilters()
            applyFilters()
            loadAvailableRides(reset = true)
        }
        filterCategoryContainer.addView(allPill)

        for (cat in categories) {
            val opt = FilterManager.FilterOption(cat, cat)
            val pill = filterManager.createPill(opt, selectedCategory == cat)
            pill.setOnClickListener {
                selectedCategory = cat
                setupCategoryFilters()
                applyFilters()
                loadAvailableRides(reset = true)
            }
            filterCategoryContainer.addView(pill)
        }
    }

    private fun setupSearchFilter() {
        etSearchRides.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
                loadAvailableRides(reset = true)
            }
        })

        btnClearSearch.setOnClickListener {
            etSearchRides.setText("")
        }
    }

    private fun setupPeriodNavigation() {
        btnModeDay.setOnClickListener { setViewMode(ViewMode.DAY) }
        btnModeWeek.setOnClickListener { setViewMode(ViewMode.WEEK) }
        btnModeMonth.setOnClickListener { setViewMode(ViewMode.MONTH) }
        btnPrevPeriod.setOnClickListener { navigatePeriod(-1) }
        btnNextPeriod.setOnClickListener { navigatePeriod(1) }
        btnToday.setOnClickListener { goToToday() }
    }

    private fun setViewMode(mode: ViewMode) {
        currentMode = mode
        referenceCalendar = when (mode) {
            ViewMode.DAY -> referenceCalendar
            ViewMode.WEEK -> getWeekStart(referenceCalendar)
            ViewMode.MONTH -> {
                val cal = referenceCalendar.clone() as Calendar
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal
            }
        }
        updateModeStyles()
        updatePeriodTitle()
        updateReadOnlyState()
        loadDataForCurrentPeriod()
    }

    private fun updateModeStyles() {
        btnModeDay.setBackgroundResource(if (currentMode == ViewMode.DAY) R.drawable.pill_selected else R.drawable.pill_unselected)
        btnModeWeek.setBackgroundResource(if (currentMode == ViewMode.WEEK) R.drawable.pill_selected else R.drawable.pill_unselected)
        btnModeMonth.setBackgroundResource(if (currentMode == ViewMode.MONTH) R.drawable.pill_selected else R.drawable.pill_unselected)

        btnModeDay.setTextColor(if (currentMode == ViewMode.DAY) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        btnModeWeek.setTextColor(if (currentMode == ViewMode.WEEK) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        btnModeMonth.setTextColor(if (currentMode == ViewMode.MONTH) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
    }

    private fun navigatePeriod(direction: Int) {
        when (currentMode) {
            ViewMode.DAY -> referenceCalendar.add(Calendar.DAY_OF_MONTH, direction)
            ViewMode.WEEK -> referenceCalendar.add(Calendar.DAY_OF_MONTH, direction * 7)
            ViewMode.MONTH -> referenceCalendar.add(Calendar.MONTH, direction)
        }
        updatePeriodTitle()
        updateReadOnlyState()
        loadDataForCurrentPeriod()
    }

    private fun goToToday() {
        referenceCalendar = Calendar.getInstance()
        if (currentMode != ViewMode.DAY) {
            referenceCalendar = when (currentMode) {
                ViewMode.WEEK -> getWeekStart(referenceCalendar)
                ViewMode.MONTH -> {
                    val cal = referenceCalendar.clone() as Calendar
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal
                }
                else -> referenceCalendar
            }
        }
        updatePeriodTitle()
        updateReadOnlyState()
        loadDataForCurrentPeriod()
    }

    private fun updatePeriodTitle() {
        val title = when (currentMode) {
            ViewMode.DAY -> dayFormatter.format(referenceCalendar.time)
            ViewMode.WEEK -> {
                val start = getWeekStart(referenceCalendar)
                val end = getWeekEnd(referenceCalendar)
                "${weekFormatter.format(start.time)} - ${weekFormatter.format(end.time)}"
            }
            ViewMode.MONTH -> monthFormatter.format(referenceCalendar.time)
        }
        tvPeriodTitle.text = title

        tvPeriodBadge.text = when (currentMode) {
            ViewMode.DAY -> "hoje"
            ViewMode.WEEK -> "esta semana"
            ViewMode.MONTH -> "este m\u00EAs"
        }
    }

    private fun getWeekStart(calendar: Calendar): Calendar {
        val cal = calendar.clone() as Calendar
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun getWeekEnd(weekStart: Calendar): Calendar {
        val cal = weekStart.clone() as Calendar
        cal.add(Calendar.DAY_OF_MONTH, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal
    }

    private fun getPeriodTimestamps(): Pair<Long, Long> {
        val start = Calendar.getInstance()
        val end = Calendar.getInstance()
        when (currentMode) {
            ViewMode.DAY -> {
                start.time = referenceCalendar.time
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                end.time = referenceCalendar.time
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }
            ViewMode.WEEK -> {
                start.time = getWeekStart(referenceCalendar).time
                end.time = getWeekEnd(referenceCalendar).time
            }
            ViewMode.MONTH -> {
                start.time = referenceCalendar.time
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                end.time = referenceCalendar.time
                end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }
        }
        return Pair(start.timeInMillis, end.timeInMillis)
    }

    private fun isReadOnly(): Boolean = false

    private fun updateReadOnlyState() {
        val actionText = when (currentMode) {
            ViewMode.DAY -> "\uD83D\uDCBE Salvar dia"
            ViewMode.WEEK -> "\uD83D\uDCBE Salvar semana"
            ViewMode.MONTH -> "\uD83D\uDCBE Salvar m\u00EAs"
        }
        setupToolbar(
            title = "Meu Dia",
            showBack = true,
            actionText = actionText,
            actionListener = { saveData() }
        )
        btnAddManualRide.isEnabled = true
        btnAddManualRide.alpha = 1.0f
    }

    private fun currentDateStr(): String = isoDateFormat.format(referenceCalendar.time)

    private fun rebuildFilterSections() {
        setupTimeFilters()
        setupValueFilters()
        setupCategoryFilters()
    }

    private fun loadDataForCurrentPeriod() {
        val (periodStart, periodEnd) = getPeriodTimestamps()

        val dateStrings = when (currentMode) {
            ViewMode.DAY -> listOf(currentDateStr())
            ViewMode.WEEK, ViewMode.MONTH -> {
                val dates = mutableListOf<String>()
                val cal = Calendar.getInstance().apply { timeInMillis = periodStart }
                while (cal.timeInMillis <= periodEnd) {
                    dates.add(isoDateFormat.format(cal.time))
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
                dates
            }
        }

        allDailyRides.clear()
        allDailyRides.addAll(dateStrings.flatMap { dateStr ->
            db.getDailyRidesByDate(dateStr)
        })

        val allRecords = db.getRidesByDateRange(periodStart, periodEnd)
        allRideRecords.clear()
        for (record in allRecords) {
            allRideRecords[record.id] = record
        }

        loadCostSummary()
        applyFilters()
        loadAvailableRides(reset = true)
    }

    private fun loadCostSummary() {
        lifecycleScope.launch {
            try {
                val summary = CostSummaryCache.getCurrentSummary(this@MyDayActivity)
                costPerKm = summary.totalCostPerKm
                fuelCostPerKm = summary.fuelCostPerKm
            } catch (e: Exception) {
                L.e(TAG, "Erro ao carregar resumo de custos: ${e.message}", e)
                costPerKm = 0.0
                fuelCostPerKm = 0.0
            }
            applyFilters()
        }
    }

    private fun applyFilters() {
        val filteredCompleted = allDailyRides.filter { ride ->
            val record = allRideRecords[ride.rideId]
            matchesFilters(record, ride.originalValue)
        }

        completedAdapter.updateData(filteredCompleted, allRideRecords, costPerKm)

        tvCompletedCount.text = "${filteredCompleted.size}"

        emptyCompleted.visibility = if (filteredCompleted.isEmpty()) View.VISIBLE else View.GONE
        rvCompleted.visibility = if (filteredCompleted.isEmpty()) View.GONE else View.VISIBLE

        updateSummary(filteredCompleted)
    }

    private fun loadAvailableRides(reset: Boolean = true) {
        if (reset) {
            availableRidesPage = 0
            availableRidesList.clear()
            isLoadingMore = false
        }
        if (isLoadingMore) return
        isLoadingMore = true
        if (reset) showProgressAvailable(true)

        val (periodStart, periodEnd) = getPeriodTimestamps()
        val addedRideIds = allDailyRides.map { it.rideId }.toSet()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var allRides = db.getRidesByDateRange(periodStart, periodEnd)
                    .filter { it.serviceType == null || !it.serviceType!!.startsWith("Manual -") }

                allRides = allRides.filter { record ->
                    matchesFilters(record, record.value ?: 0.0)
                }.map { CardHashGenerator.recoverRideFromRawLogs(it, db) }
                    .filter { CardHashGenerator.isValidRide(it) }
                    .let { CardHashGenerator.deduplicateRides(it) }

                val total = allRides.size
                val fromIndex = availableRidesPage * availableRidesPageSize
                val toIndex = minOf(fromIndex + availableRidesPageSize, total)
                val pageRides = if (fromIndex < total) allRides.subList(fromIndex, toIndex) else emptyList()
                val filteredPage = pageRides.filter { it.id !in addedRideIds }

                withContext(Dispatchers.Main) {
                    availableRidesList.addAll(filteredPage)
                    availableAdapter.updateData(availableRidesList)
                    totalAvailableRides = total
                    hasMoreAvailableRides = (availableRidesPage + 1) * availableRidesPageSize < total
                    btnLoadMoreAvailable.visibility = if (hasMoreAvailableRides) View.VISIBLE else View.GONE
                    updateHistoryInfo()
                    showProgressAvailable(false)
                    isLoadingMore = false
                }
            } catch (e: Exception) {
                L.e(TAG, "Erro ao carregar corridas disponíveis: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showProgressAvailable(false)
                    isLoadingMore = false
                }
            }
        }
    }

    private fun loadMoreAvailableRides() {
        if (!hasMoreAvailableRides || isLoadingMore) return
        availableRidesPage++
        loadAvailableRides(reset = false)
    }

    private fun updateHistoryInfo() {
        val dateStr = when (currentMode) {
            ViewMode.DAY -> dayFormatter.format(referenceCalendar.time)
            ViewMode.WEEK -> {
                val start = getWeekStart(referenceCalendar)
                val end = getWeekEnd(referenceCalendar)
                "${weekFormatter.format(start.time)} - ${weekFormatter.format(end.time)}"
            }
            ViewMode.MONTH -> monthFormatter.format(referenceCalendar.time)
        }
        val infoText = if (isFilterActive()) {
            "$dateStr | ${availableRidesList.size} corridas filtradas (de $totalAvailableRides)"
        } else {
            "$dateStr | $totalAvailableRides corridas no total"
        }
        tvHistoryInfo.text = "\uD83D\uDCC5 $infoText"
    }

    private fun isFilterActive(): Boolean {
        return selectedTimeFilter != null || selectedValueFilter != null || selectedCategory != null || searchQuery.isNotEmpty()
    }

    private fun showProgressAvailable(show: Boolean) {
        progressAvailable.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            btnLoadMoreAvailable.visibility = View.GONE
        }
        emptyAvailable.visibility = if (!show && availableRidesList.isEmpty()) View.VISIBLE else View.GONE
        rvAvailable.visibility = if (!show && availableRidesList.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun matchesFilters(record: RideRecord?, rideValue: Double): Boolean {
        if (selectedTimeFilter != null && record != null) {
            val hour = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                .get(Calendar.HOUR_OF_DAY)
            val range = timeRanges[selectedTimeFilter!!]
            if (hour < range.first || hour >= range.second) return false
        }

        if (selectedValueFilter != null) {
            val range = valueRanges[selectedValueFilter!!]
            if (rideValue < range.first || rideValue >= range.second) return false
        }

        if (selectedCategory != null && record != null) {
            val st = record.serviceType ?: return false
            if (!st.equals(selectedCategory, ignoreCase = true)) return false
        }

        if (searchQuery.isNotEmpty()) {
            val st = record?.serviceType ?: ""
            val pa = record?.pickupAddress ?: ""
            val da = record?.dropoffAddress ?: ""
            val an = record?.appName ?: ""
            val valueStr = "R\$ %.2f".format(rideValue).replace(".", ",")
            val hour = if (record != null) Calendar.getInstance().apply { timeInMillis = record.timestamp }
                .get(Calendar.HOUR_OF_DAY) else -1
            val timeDesc = when (hour) {
                in 6 until 12 -> "manhã"
                in 12 until 18 -> "tarde"
                in 18 until 24 -> "noite"
                else -> ""
            }
            val searchLower = searchQuery.lowercase()
            if (!st.contains(searchQuery, ignoreCase = true) &&
                !pa.contains(searchQuery, ignoreCase = true) &&
                !da.contains(searchQuery, ignoreCase = true) &&
                !an.contains(searchQuery, ignoreCase = true) &&
                !valueStr.contains(searchQuery, ignoreCase = true) &&
                !timeDesc.contains(searchLower)) return false
        }

        return true
    }

    private fun updateSummary(rides: List<DailyRide>) {
        val count = rides.size
        var totalKm = 0.0
        var totalDuration = 0
        var grossRevenue = 0.0
        var totalTips = 0.0
        var totalAdjustments = 0.0

        for (ride in rides) {
            val record = allRideRecords[ride.rideId]
            val totalDist = (record?.pickupDistanceKm ?: 0.0) +
                (record?.tripDistanceKm ?: record?.distanceKm ?: 0.0)
            totalKm += totalDist
            val totalTime = (record?.pickupTimeMin ?: 0) +
                (record?.tripTimeMin ?: record?.timeMin ?: 0)
            totalDuration += totalTime
            grossRevenue += ride.finalValue
            totalTips += ride.tipAmount
            totalAdjustments += ride.adjustmentDifference
        }

        val totalCost = totalKm * costPerKm
        val fuelUsed = totalKm * fuelCostPerKm
        val fuelDisbursed = getTotalFuelDisbursed()

        val netProfit = grossRevenue - totalCost
        val profitPercent = if (grossRevenue > 0) (netProfit / grossRevenue) * 100 else 0.0
        val revenuePerKm = if (totalKm > 0) grossRevenue / totalKm else 0.0
        val revenuePerHour = if (totalDuration > 0) grossRevenue / totalDuration * 60 else 0.0
        val avgTip = if (count > 0) totalTips / count else 0.0

        val hours = totalDuration / 60
        val minutes = totalDuration % 60

        tvRideCount.text = "$count"
        tvTotalDistance.text = "%.1f".format(totalKm).replace(".", ",")
        tvTotalDuration.text = "${hours}h ${minutes}min"

        tvGrossRevenue.text = "R$ %.2f".format(grossRevenue).replace(".", ",")

        tvNetProfit.text = "R$ %.2f".format(netProfit).replace(".", ",")
        tvNetProfit.setTextColor(
            if (netProfit >= 0) 0xFF00A86B.toInt() else 0xFFEF4444.toInt()
        )

        tvProfitPercent.text = "%.1f%% de margem".format(profitPercent).replace(".", ",")
        tvProfitPercent.setTextColor(
            if (profitPercent >= 0) 0xFF9CA3AF.toInt() else 0xFFEF4444.toInt()
        )

        tvRevenuePerKm.text = "R\$/km: %.2f".format(revenuePerKm).replace(".", ",")
        tvRevenuePerHour.text = "R\$/h: %.2f".format(revenuePerHour).replace(".", ",")

        updateCostBreakdown(totalKm)
    }

    private fun getTotalFuelDisbursed(): Double {
        val (periodStart, periodEnd) = getPeriodTimestamps()
        return db.getRefuels()
            .filter { it.timestamp in periodStart..periodEnd }
            .sumOf { it.totalValue }
    }

    private fun toggleCostDetails() {
        costDetailsExpanded = !costDetailsExpanded
        layoutCostDetails.visibility = if (costDetailsExpanded) View.VISIBLE else View.GONE
        btnToggleCostDetails.text = if (costDetailsExpanded)
            "\u25B2 Ocultar detalhes dos custos" else "\u25BC Ver detalhes dos custos"
    }

    private fun toggleFilters() {
        val isVisible = filterContainer.visibility == View.VISIBLE
        filterContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
        tvFilterToggleIcon.text = if (isVisible) "\u25BC" else "\u25B2"
    }

    private fun updateCostBreakdown(dayTotalKm: Double) {
        lifecycleScope.launch {
            try {
                val summary = CostSummaryCache.getCurrentSummary(this@MyDayActivity)
                costBreakdownItems = CostSummaryCache.getCostBreakdown(summary)

                layoutCostDetails.removeAllViews()
                val inflater = layoutInflater

                for (item in costBreakdownItems) {
                    val row = inflater.inflate(R.layout.item_cost_breakdown, layoutCostDetails, false)
                    val tvName = row.findViewById<TextView>(R.id.tvBreakdownName)
                    val tvValue = row.findViewById<TextView>(R.id.tvBreakdownValue)
                    val tvDayTotal = row.findViewById<TextView>(R.id.tvBreakdownDayTotal)
                    val tvPercent = row.findViewById<TextView>(R.id.tvBreakdownPercent)
                    val progress = row.findViewById<ProgressBar>(R.id.progressBreakdown)

                    val dayCost = item.costPerKm * dayTotalKm

                    tvName.text = item.name
                    tvValue.text = "R$ %.4f/km".format(item.costPerKm).replace(".", ",")
                    tvDayTotal.text = "R$ %.2f".format(dayCost).replace(".", ",")
                    tvPercent.text = "%d%%".format((item.percentage * 100).toInt())

                    progress.progressDrawable = android.graphics.drawable.ClipDrawable(
                        android.graphics.drawable.ColorDrawable(item.color),
                        Gravity.START,
                        android.graphics.drawable.ClipDrawable.HORIZONTAL
                    )
                    progress.progress = (item.percentage * 1000).toInt()
                    progress.max = 1000

                    layoutCostDetails.addView(row)
                }
            } catch (e: Exception) { L.e(TAG, "Erro ao atualizar detalhamento de custos: ${e.message}", e) }
        }
    }

    private fun onToggleCompleted(ride: DailyRide) {
        if (ride.isCompleted) {
            val idx = allDailyRides.indexOfFirst { it.id == ride.id }
            if (idx < 0) return
            allDailyRides.removeAt(idx)
            db.deleteDailyRide(ride.id)
            loadDataForCurrentPeriod()
            Toast.makeText(this, "Corrida removida e disponível novamente!", Toast.LENGTH_SHORT).show()
        } else {
            val idx = allDailyRides.indexOfFirst { it.id == ride.id }
            if (idx < 0) return
            val updated = ride.copy(
                isCompleted = true,
                updatedAt = System.currentTimeMillis()
            )
            allDailyRides[idx] = updated
            db.updateDailyRide(updated)
            applyFilters()
            Toast.makeText(this, "Corrida marcada como realizada!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onCancelWithFee(ride: DailyRide) {
        val record = allRideRecords[ride.rideId] ?: return
        showCancelFeeDialog(ride, record)
    }

    private fun showCancelFeeDialog(ride: DailyRide, record: RideRecord) {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_cancel_fee, null)
        val etFeeValue = view.findViewById<EditText>(R.id.etFeeValue)

        builder.setView(view)
            .setTitle("Valor da taxa de deslocamento")
            .setMessage("O valor original (R$ ${String.format("%.2f", ride.originalValue).replace(".", ",")}) será substituído pela taxa.\n\nA distância considerada será APENAS o trajeto até o passageiro (${String.format("%.1f", record.pickupDistanceKm ?: record.distanceKm ?: 0.0)} km).")
            .setPositiveButton("Aplicar taxa") { _, _ ->
                val feeValue = parseDecimal(etFeeValue.text.toString()) ?: 0.0

                val idx = allDailyRides.indexOfFirst { it.id == ride.id }
                if (idx >= 0) {
                    // Usar apenas pickup distance para corridas canceladas
                    val cancelDistance = record.pickupDistanceKm ?: record.distanceKm ?: 0.0
                    val cancelTime = record.pickupTimeMin ?: record.timeMin ?: 0

                    val updated = ride.copy(
                        adjustedValue = feeValue,
                        cancelledWithFee = true,
                        notes = "Cancelado com taxa - valor original: R$ ${String.format("%.2f", ride.originalValue)}. Distância considerada: ${String.format("%.1f", cancelDistance)} km",
                        updatedAt = System.currentTimeMillis()
                    )
                    allDailyRides[idx] = updated
                    db.updateDailyRide(updated)

                    val updatedRecord = record.copy(
                        value = feeValue,
                        distanceKm = cancelDistance,
                        timeMin = cancelTime,
                        tripDistanceKm = null,
                        tripTimeMin = null
                    )
                    allRideRecords[record.id] = updatedRecord

                    applyFilters()
                    Toast.makeText(this, "Taxa de deslocamento aplicada! Distância considerada: ${String.format("%.1f", cancelDistance)} km", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun onAddToDay(record: RideRecord) {
        val dailyRide = DailyRide(
            rideId = record.id,
            date = currentDateStr(),
            originalValue = record.value ?: 0.0,
            isCompleted = true
        )
        val id = db.insertDailyRide(dailyRide)
        allDailyRides.add(dailyRide.copy(id = id))
        availableRidesList.removeAll { it.id == record.id }
        availableAdapter.updateData(availableRidesList)
        updateHistoryInfo()
        applyFilters()
        checkForEvent(record.pickupAddress, record.dropoffAddress)
    }

    private fun checkForEvent(pickupAddress: String?, dropoffAddress: String?) {
        val today = currentDateStr()
        val ridesToday = allDailyRides.filter { it.date == today }

        val pickupCount = ridesToday.count { ride ->
            val record = allRideRecords[ride.rideId]
            pickupAddress != null && record?.pickupAddress?.contains(pickupAddress.take(30)) == true
        }
        val dropoffCount = ridesToday.count { ride ->
            val record = allRideRecords[ride.rideId]
            dropoffAddress != null && record?.dropoffAddress?.contains(dropoffAddress.take(30)) == true
        }

        if (pickupCount >= 3) {
            sendNotification("\uD83D\uDCCD Evento pr\u00F3ximo!",
                "Voc\u00EA j\u00E1 pegou $pickupCount corridas saindo de $pickupAddress hoje. Pode ter um evento na regi\u00E3o!")
        }

        if (dropoffCount >= 3) {
            sendNotification("\uD83C\uDFC1 Evento no destino!",
                "Voc\u00EA j\u00E1 deixou $dropoffCount passageiros em $dropoffAddress hoje. Regi\u00E3o movimentada!")
        }
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "event_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Alertas de Eventos", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun showTipDialog(ride: DailyRide) {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_tip, null)
        val tvInfo = view.findViewById<TextView>(R.id.tvTipRideInfo)
        val etValue = view.findViewById<TextView>(R.id.etTipValue)

        val record = allRideRecords[ride.rideId]
        val serviceName = record?.serviceType ?: "Corrida"
        tvInfo.text = "$serviceName - R$ %.2f".format(ride.originalValue).replace(".", ",")

        builder.setView(view)
            .setPositiveButton("Adicionar") { _, _ ->
                val value = parseDecimal(etValue.text.toString())
                if (value != null && value > 0) {
                    val idx = allDailyRides.indexOfFirst { it.id == ride.id }
                    if (idx >= 0) {
                        allDailyRides[idx] = ride.copy(tipAmount = value, updatedAt = System.currentTimeMillis())
                        db.updateDailyRide(allDailyRides[idx])
                        applyFilters()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAdjustmentDialog(ride: DailyRide) {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_adjustment, null)
        val tvInfo = view.findViewById<TextView>(R.id.tvAdjRideInfo)
        val tvDist = view.findViewById<TextView>(R.id.tvAdjDistance)
        val tvTime = view.findViewById<TextView>(R.id.tvAdjTime)
        val etValue = view.findViewById<TextView>(R.id.etAdjValue)
        val etReason = view.findViewById<TextView>(R.id.etAdjReason)

        val record = allRideRecords[ride.rideId]
        tvInfo.text = "Corrida original: R$ %.2f".format(ride.originalValue).replace(".", ",")
        if (record != null) {
            val dist = record.tripDistanceKm ?: record.distanceKm
            val dur = record.tripTimeMin ?: record.timeMin
            if (dist != null) tvDist.text = "Dist\u00E2ncia original: %.1f km".format(dist).replace(".", ",")
            else tvDist.visibility = View.GONE
            if (dur != null) tvTime.text = "Tempo original: %d min".format(dur)
            else tvTime.visibility = View.GONE
        }

        builder.setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                val newValue = parseDecimal(etValue.text.toString())
                if (newValue != null && newValue > 0) {
                    val idx = allDailyRides.indexOfFirst { it.id == ride.id }
                    if (idx >= 0) {
                        val notes = if (etReason.text.isNotEmpty()) etReason.text.toString() else ride.notes
                        allDailyRides[idx] = ride.copy(
                            adjustedValue = newValue,
                            notes = notes,
                            updatedAt = System.currentTimeMillis()
                        )
                        db.updateDailyRide(allDailyRides[idx])
                        applyFilters()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showManualRideDialog() {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_manual_ride, null)
        val etService = view.findViewById<EditText>(R.id.etManualService)
        val etValue = view.findViewById<EditText>(R.id.etManualValue)
        val etDist = view.findViewById<EditText>(R.id.etManualDistance)
        val etDur = view.findViewById<EditText>(R.id.etManualDuration)
        val etTip = view.findViewById<EditText>(R.id.etManualTip)
        val etNotes = view.findViewById<EditText>(R.id.etManualNotes)

        builder.setView(view)
            .setPositiveButton("Adicionar") { _, _ ->
                val service = etService.text.toString().trim().ifEmpty { "Corrida" }
                val value = parseDecimal(etValue.text.toString()) ?: return@setPositiveButton
                val dist = parseDecimal(etDist.text.toString()) ?: 0.0
                val dur = etDur.text.toString().toIntOrNull() ?: 0
                val tip = parseDecimal(etTip.text.toString()) ?: 0.0
                val notes = etNotes.text.toString().trim().ifEmpty { null }

                val rideId = db.insertManualRide(service, value, dist, dur)
                val dailyRide = DailyRide(
                    rideId = rideId,
                    date = currentDateStr(),
                    originalValue = value,
                    tipAmount = tip,
                    isCompleted = true,
                    notes = notes
                )
                val id = db.insertDailyRide(dailyRide)
                dailyRide.copy(id = id).also {
                    allDailyRides.add(it)
                    val cal = Calendar.getInstance()
                    val manualRecord = RideRecord(
                        id = rideId,
                        value = value,
                        distanceKm = dist,
                        timeMin = dur,
                        rating = null,
                        pricePerKm = null,
                        pricePerHour = null,
                        appName = "",
                        timestamp = cal.timeInMillis,
                        tripDistanceKm = dist,
                        tripTimeMin = dur,
                        serviceType = "Manual - $service",
                        status = "COMPLETED"
                    )
                    allRideRecords[rideId] = manualRecord
                }
                applyFilters()
                loadAvailableRides(reset = true)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveData() {
        allDailyRides.forEach { db.updateDailyRide(it) }
    }

    private fun parseDecimal(text: String): Double? {
        val cleaned = text.trim().replace(",", ".")
        return cleaned.toDoubleOrNull()
    }

    companion object {
        private const val TAG = "CorridaCerta"
    }
}
