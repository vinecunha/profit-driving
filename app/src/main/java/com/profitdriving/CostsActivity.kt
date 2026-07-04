package com.profitdriving

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.profitdriving.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CostsActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var etMonthlyKm: EditText
    private lateinit var refuelList: LinearLayout
    private lateinit var normalizedExpenseList: LinearLayout
    private lateinit var progressOverlay: View
    private var allRefuels = listOf<RefuelRecord>()
    private var allExpenses = listOf<Expense>()
    private var monthlyKm = 3000
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    private var currentMode = ViewMode.DAY
    private var referenceCalendar = Calendar.getInstance()
    private lateinit var btnModeDay: TextView
    private lateinit var btnModeWeek: TextView
    private lateinit var btnModeMonth: TextView
    private lateinit var btnPrevPeriod: TextView
    private lateinit var btnNextPeriod: TextView
    private lateinit var tvPeriodTitle: TextView
    private lateinit var btnToday: TextView
    private val dayFormatter = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("pt", "BR"))
    private val weekFormatter = SimpleDateFormat("dd/MM", Locale.getDefault())
    private val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_costs)
        setupBottomNav(Screen.COSTS)
        setupToolbar(title = "Custos", showBack = true)

        db = DatabaseHelper(this)

        progressOverlay = layoutInflater.inflate(R.layout.progress_overlay, null)
        (findViewById<android.view.ViewGroup>(android.R.id.content))?.addView(progressOverlay)
        progressOverlay.visibility = View.GONE

        setupRefuelButton()
        setupExpenseButtons()
        setupMonthlyKm()
        setupPeriodNavigation()

        findViewById<TextView>(R.id.btnViewExpenses).setOnClickListener {
            startActivity(Intent(this, ExpensesActivity::class.java))
        }
        findViewById<TextView>(R.id.btnViewStats).setOnClickListener {
            startActivity(Intent(this, MonthlyStatsActivity::class.java))
        }
        findViewById<TextView>(R.id.btnViewAllRefuels).setOnClickListener {
            val (periodStart, periodEnd) = getPeriodTimestamps()
            startActivity(Intent(this, RefuelsHistoryActivity::class.java).apply {
                putExtra(RefuelsHistoryActivity.EXTRA_PERIOD_START, periodStart)
                putExtra(RefuelsHistoryActivity.EXTRA_PERIOD_END, periodEnd)
            })
        }

        setupVehicleSettings()
        setViewMode(ViewMode.MONTH)
    }

    private fun setupVehicleSettings() {
        val prefs = PreferenceManager(this)

        val etTank = findViewById<EditText>(R.id.etTankCapacity)
        val etCylinder = findViewById<EditText>(R.id.etCylinderCapacity)
        val etBattery = findViewById<EditText>(R.id.etBatteryCapacity)

        val switchTank = findViewById<SwitchCompat>(R.id.switchTank)
        val switchCylinder = findViewById<SwitchCompat>(R.id.switchCylinder)
        val switchBattery = findViewById<SwitchCompat>(R.id.switchBattery)

        val btnGnvHelp = findViewById<ImageButton>(R.id.btnGnvCapacityHelp)

        // Load saved states
        switchTank.isChecked = prefs.isTankEnabled()
        switchCylinder.isChecked = prefs.isCylinderEnabled()
        switchBattery.isChecked = prefs.isBatteryEnabled()

        val tankCap = prefs.getTankCapacity()
        etTank.setText(if (tankCap > 0) tankCap.toString() else "")

        val cylinderCap = prefs.getCylinderCapacity()
        etCylinder.setText(if (cylinderCap > 0) cylinderCap.toString() else "")

        val batteryCap = prefs.getBatteryCapacity()
        etBattery.setText(if (batteryCap > 0) batteryCap.toString() else "")

        // Toggle wiring: enable/disable EditText + save
        fun applyTankToggle(enabled: Boolean) {
            etTank.isEnabled = enabled
            etTank.alpha = if (enabled) 1f else 0.4f
        }
        fun applyCylinderToggle(enabled: Boolean) {
            etCylinder.isEnabled = enabled
            etCylinder.alpha = if (enabled) 1f else 0.4f
            btnGnvHelp.visibility = if (enabled) View.VISIBLE else View.GONE
        }
        fun applyBatteryToggle(enabled: Boolean) {
            etBattery.isEnabled = enabled
            etBattery.alpha = if (enabled) 1f else 0.4f
        }

        applyTankToggle(switchTank.isChecked)
        applyCylinderToggle(switchCylinder.isChecked)
        applyBatteryToggle(switchBattery.isChecked)

        // Also wire the cylinder toggle to show/hide the help icon
        switchCylinder.setOnCheckedChangeListener { _, isChecked ->
            prefs.setCylinderEnabled(isChecked)
            applyCylinderToggle(isChecked)
        }

        switchTank.setOnCheckedChangeListener { _, isChecked ->
            prefs.setTankEnabled(isChecked)
            applyTankToggle(isChecked)
        }

        switchBattery.setOnCheckedChangeListener { _, isChecked ->
            prefs.setBatteryEnabled(isChecked)
            applyBatteryToggle(isChecked)
        }

        // Persist capacity values
        etTank.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.setTankCapacity(s.toString().trim().toIntOrNull() ?: 0)
            }
        })
        etCylinder.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.setCylinderCapacity(s.toString().trim().toIntOrNull() ?: 0)
            }
        })
        etBattery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = (s.toString().trim().toDoubleOrNull() ?: 0.0).toInt()
                prefs.setBatteryCapacity(value)
            }
        })

        // GNV help dialog
        btnGnvHelp.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.gnv_capacity_help_title))
                .setMessage(getString(R.string.gnv_capacity_help_text))
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        try { loadData() } catch (e: Exception) {
            L.e("CostsActivity", "Error loading data", e)
        }
    }

    private fun setupPeriodNavigation() {
        btnModeDay = findViewById(R.id.btnModeDay)
        btnModeWeek = findViewById(R.id.btnModeWeek)
        btnModeMonth = findViewById(R.id.btnModeMonth)
        btnPrevPeriod = findViewById(R.id.btnPrevPeriod)
        btnNextPeriod = findViewById(R.id.btnNextPeriod)
        tvPeriodTitle = findViewById(R.id.tvPeriodTitle)
        btnToday = findViewById(R.id.btnToday)

        btnModeDay.setOnClickListener { setViewMode(ViewMode.DAY) }
        btnModeWeek.setOnClickListener { setViewMode(ViewMode.WEEK) }
        btnModeMonth.setOnClickListener { setViewMode(ViewMode.MONTH) }
        btnPrevPeriod.setOnClickListener { navigatePeriod(-1) }
        btnNextPeriod.setOnClickListener { navigatePeriod(1) }
        btnToday.setOnClickListener { goToToday() }
    }

    private fun setupRefuelButton() {
        findViewById<TextView>(R.id.btnAddRefuel).setOnClickListener {
            AddRefuelDialog(this) { refuel ->
                showSavingFeedback {
                    db.insertRefuel(refuel)
                    loadData()
                }
            }.show()
        }
    }

    private fun setupExpenseButtons() {
        findViewById<TextView>(R.id.btnAddPerKm).setOnClickListener {
            AddExpenseDialog(this, type = CostType.PER_KM, onSave = { expense ->
                showSavingFeedback {
                    db.insertExpenseItem(expense)
                    loadData()
                }
            }).show()
        }
        findViewById<TextView>(R.id.btnAddFixed).setOnClickListener {
            AddExpenseDialog(this, type = CostType.FIXED, onSave = { expense ->
                showSavingFeedback {
                    db.insertExpenseItem(expense)
                    loadData()
                }
            }).show()
        }
        findViewById<TextView>(R.id.btnAddEvent).setOnClickListener {
            AddExpenseDialog(this, type = CostType.EVENT, onSave = { expense ->
                showSavingFeedback {
                    db.insertExpenseItem(expense)
                    loadData()
                }
            }).show()
        }
    }

    private fun setupMonthlyKm() {
        etMonthlyKm = findViewById(R.id.etMonthlyKm)

        etMonthlyKm.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val km = s.toString().toIntOrNull()
                if (km != null && km > 0) {
                    monthlyKm = km
                    showSavingFeedback {
                        db.saveMonthlyKm(km)
                        updateSummary()
                        updateSimulator()
                    }
                }
            }
        })
    }

    private fun getPeriodTimestamps(): Pair<Long, Long> {
        val start: Calendar
        val end: Calendar
        when (currentMode) {
            ViewMode.DAY -> {
                start = referenceCalendar.clone() as Calendar
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                end = start.clone() as Calendar
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }
            ViewMode.WEEK -> {
                start = getWeekStart(referenceCalendar)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                end = getWeekEnd(referenceCalendar)
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }
            ViewMode.MONTH -> {
                start = referenceCalendar.clone() as Calendar
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                end = start.clone() as Calendar
                end.set(Calendar.DAY_OF_MONTH, start.getActualMaximum(Calendar.DAY_OF_MONTH))
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
            }
        }
        return Pair(start.timeInMillis, end.timeInMillis)
    }

    private fun getWeekStart(cal: Calendar): Calendar {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_WEEK, c.firstDayOfWeek)
        return c
    }

    private fun getWeekEnd(cal: Calendar): Calendar {
        val c = getWeekStart(cal)
        c.add(Calendar.DAY_OF_WEEK, 6)
        return c
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
        loadData()
    }

    private fun updateModeStyles() {
        btnModeDay.setBackgroundResource(if (currentMode == ViewMode.DAY) R.drawable.pill_selected else R.drawable.pill_unselected)
        btnModeWeek.setBackgroundResource(if (currentMode == ViewMode.WEEK) R.drawable.pill_selected else R.drawable.pill_unselected)
        btnModeMonth.setBackgroundResource(if (currentMode == ViewMode.MONTH) R.drawable.pill_selected else R.drawable.pill_unselected)

        btnModeDay.setTextColor(if (currentMode == ViewMode.DAY) ctxColor(R.color.text_inverse) else ctxColor(R.color.text_secondary))
        btnModeWeek.setTextColor(if (currentMode == ViewMode.WEEK) ctxColor(R.color.text_inverse) else ctxColor(R.color.text_secondary))
        btnModeMonth.setTextColor(if (currentMode == ViewMode.MONTH) ctxColor(R.color.text_inverse) else ctxColor(R.color.text_secondary))
    }

    private fun navigatePeriod(direction: Int) {
        when (currentMode) {
            ViewMode.DAY -> referenceCalendar.add(Calendar.DAY_OF_MONTH, direction)
            ViewMode.WEEK -> referenceCalendar.add(Calendar.DAY_OF_MONTH, direction * 7)
            ViewMode.MONTH -> referenceCalendar.add(Calendar.MONTH, direction)
        }
        updatePeriodTitle()
        loadData()
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
        loadData()
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
        tvPeriodTitle.text = title.replaceFirstChar { it.uppercaseChar() }
    }

    private fun isCurrentPeriod(): Boolean {
        val now = Calendar.getInstance()
        return when (currentMode) {
            ViewMode.DAY ->
                referenceCalendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                referenceCalendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
            ViewMode.WEEK -> {
                val refStart = getWeekStart(referenceCalendar)
                val todayStart = getWeekStart(now)
                refStart.timeInMillis == todayStart.timeInMillis
            }
            ViewMode.MONTH ->
                referenceCalendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                referenceCalendar.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        }
    }

    private fun filterRefuelsByPeriod(items: List<RefuelRecord>): List<RefuelRecord> {
        val (periodStart, periodEnd) = getPeriodTimestamps()
        return items.filter { it.timestamp in periodStart..periodEnd }
    }

    private fun filterExpensesByPeriod(items: List<Expense>): List<Expense> {
        val (periodStart, periodEnd) = getPeriodTimestamps()
        return items.filter { e ->
            e.createdAt == 0L || e.createdAt in periodStart..periodEnd
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val (loadedRefuels, loadedExpenses, loadedMonthlyKm) = withContext(Dispatchers.IO) {
                    Triple(db.getRefuels(), db.getAllExpenses(), db.getMonthlyKm())
                }
                allRefuels = filterRefuelsByPeriod(loadedRefuels)
                allExpenses = filterExpensesByPeriod(loadedExpenses)
                monthlyKm = loadedMonthlyKm

                etMonthlyKm.setText(loadedMonthlyKm.toString())
                renderRefuelList()
                updateEnergyStats()
                updateFuelStats()
                updateSummary()
                updateSimulator()
            } catch (e: Exception) {
                L.e("CostsActivity", "Erro ao carregar dados", e)
                android.widget.Toast.makeText(this@CostsActivity,
                    "Erro ao carregar dados: ${e.message}",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderRefuelList() {
        refuelList = findViewById(R.id.refuelList)
        refuelList.removeAllViews()

        if (allRefuels.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nenhum abastecimento registrado"
                textSize = 12f
                setTextColor(ctxColor(R.color.text_secondary))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 8)
            }
            refuelList.addView(empty)
            return
        }

        for (refuel in allRefuels.take(4)) {
            val row = layoutInflater.inflate(R.layout.item_refuel_history, refuelList, false)
            val energyType = EnergyType.fromString(refuel.fuelType)

            row.findViewById<TextView>(R.id.tvRefuelDate).text =
                dateFormat.format(Date(refuel.timestamp))

            row.findViewById<TextView>(R.id.tvRefuelType).apply {
                text = energyType.icon
                setTextColor(ctxColor(energyType.colorRes))
            }

            row.findViewById<TextView>(R.id.tvRefuelOdometer).text =
                "%.0f".format(refuel.odometerKm)

            row.findViewById<TextView>(R.id.tvRefuelVolume).text =
                "${FormatUtils.decimal1(refuel.amount)} ${energyType.unit}"

            // Nível de enchimento
            val prefs = PreferenceManager(this)
            val effectiveCapacity = if (refuel.fuelType == "gnv") {
                getRealGnvCapacity(allRefuels, prefs)
            } else {
                getEffectiveCapacity(refuel.fuelType, prefs)
            }
            val fillLevel = refuel.fillLevel
                ?: if (effectiveCapacity > 0) estimateFillLevelAfter(refuel, allRefuels, effectiveCapacity) else null

            val tvLevel = row.findViewById<TextView>(R.id.tvRefuelLevel)
            if (fillLevel != null) {
                val level = fillLevel!!.toInt().coerceIn(0, 100)
                tvLevel.text = "$level%"
                tvLevel.setTextColor(when {
                    level >= 95 -> ctxColor(R.color.success)
                    level >= 40 -> ctxColor(R.color.warning)
                    else -> ctxColor(R.color.error)
                })
            } else {
                tvLevel.text = "--"
            }

            row.findViewById<TextView>(R.id.tvRefuelPrice).text =
                currencyFormat.format(refuel.totalValue)

            val consumption = calculateRefuelConsumption(refuel)
            val consumptionUnit = if (energyType.unit == "kWh") "km/kWh" else "km/${energyType.unit}"
            row.findViewById<TextView>(R.id.tvRefuelConsumption).text =
                if (consumption > 0) "${FormatUtils.decimal1(consumption)} $consumptionUnit" else "--"

            refuelList.addView(row)
        }
    }

    private fun calculateRefuelConsumption(refuel: RefuelRecord): Double {
        val sameType = allRefuels.filter { it.fuelType == refuel.fuelType }.sortedByDescending { it.timestamp }
        val idx = sameType.indexOfFirst { it.id == refuel.id }
        if (idx < 0 || idx >= sameType.size - 1) return 0.0
        val next = sameType[idx + 1]
        val kmDiff = refuel.odometerKm - next.odometerKm
        return if (kmDiff > 0 && refuel.amount > 0) kmDiff / refuel.amount else 0.0
    }

    private fun updateEnergyStats() {
        val container = findViewById<LinearLayout>(R.id.energyStatsContainer)
        container.removeAllViews()

        val energyTypes = EnergyType.entries
        val vehiclePrefs = PreferenceManager(this)

        for (type in energyTypes) {
            val stats = CostCalculator.calculateEnergyStats(allRefuels, type)
            if (stats.count == 0) continue

            val card = layoutInflater.inflate(R.layout.item_energy_stat, container, false)

            val tvIcon = card.findViewById<TextView>(R.id.tvEnergyIcon)
            val tvName = card.findViewById<TextView>(R.id.tvEnergyName)
            val tvConsumption = card.findViewById<TextView>(R.id.tvEnergyConsumption)
            val tvAutonomy = card.findViewById<TextView>(R.id.tvEnergyAutonomy)
            val tvCost = card.findViewById<TextView>(R.id.tvEnergyCost)
            val tvTotal = card.findViewById<TextView>(R.id.tvEnergyTotal)
            val tvLastDate = card.findViewById<TextView>(R.id.tvEnergyLastDate)

            tvIcon.text = type.icon
            tvName.text = type.display

            val consumptionUnit = if (type.unit == "kWh") "km/kWh" else "km/${type.unit}"
            tvConsumption.text = if (stats.avgConsumption > 0) {
                "Consumo: ${FormatUtils.decimal1(stats.avgConsumption)} $consumptionUnit"
            } else {
                "Consumo: -- $consumptionUnit"
            }

            // Autonomia baseada na capacidade do sistema ativo
            val capacity = when (type) {
                EnergyType.GNV -> if (vehiclePrefs.isCylinderEnabled()) vehiclePrefs.getCylinderCapacity() else 0
                EnergyType.ELECTRIC_AC, EnergyType.ELECTRIC_DC -> if (vehiclePrefs.isBatteryEnabled()) vehiclePrefs.getBatteryCapacity() else 0
                else -> if (vehiclePrefs.isTankEnabled()) vehiclePrefs.getTankCapacity() else 0
            }
            if (capacity > 0 && stats.avgConsumption > 0) {
                val autonomy = capacity * stats.avgConsumption
                tvAutonomy.text = "Autonomia: ~${String.format("%.0f", autonomy).replace(".", ",")} km  (${capacity} ${type.unit})"
                tvAutonomy.visibility = View.VISIBLE
            } else {
                tvAutonomy.visibility = View.GONE
            }

            tvCost.text = if (stats.costPerKm > 0) {
                "Custo: ${FormatUtils.currency(stats.costPerKm)}/km"
            } else {
                "Custo: -- R\$/km"
            }

            val totalFormat = if (type.unit == "kWh") "%.0f %s" else "%.1f %s"
            tvTotal.text = "Total: ${String.format(totalFormat, stats.totalAmount, type.unit).replace(".", ",")} \u00B7 ${FormatUtils.currency(stats.totalCost)}"

            val lastDate = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                .format(Date(stats.lastDate))
            tvLastDate.text = "\u00daltimo: $lastDate (${stats.count} registro(s))"

            container.addView(card)
        }

        if (container.childCount == 0) {
            val empty = TextView(this).apply {
                text = "Nenhum dado de abastecimento"
                textSize = 12f
                setTextColor(ctxColor(R.color.text_tertiary))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 16, 0, 16)
            }
            container.addView(empty)
        }
    }

    private fun updateFuelStats() {
        val result = ConsumptionCalculator.calculateConsumption(allRefuels)

        val methodText = when (result.method) {
            ConsumptionCalculator.CalculationMethod.SINGLE_FUEL ->
                "\u2705 Combust\u00EDvel \u00FAnico"
            ConsumptionCalculator.CalculationMethod.PRIMARY_WITH_SECONDARY ->
                "\uD83D\uDD27 Principal + complementar (total)"
            ConsumptionCalculator.CalculationMethod.MULTIPLE_SIMULTANEOUS ->
                "\u26A1 M\u00FAltiplos simult\u00E2neos (total)"
            ConsumptionCalculator.CalculationMethod.INSUFFICIENT_DATA ->
                "\u26A0\uFE0F Dados insuficientes (adicione ao menos 2 abastecimentos)"
            else -> "\u2753 Desconhecido"
        }
        findViewById<TextView>(R.id.tvCalculationMethod)?.text = methodText

        findViewById<TextView>(R.id.tvCostPerKm)?.text =
            "${FormatUtils.currency(result.costPerKm)} / km"

        findViewById<TextView>(R.id.tvConsumption)?.text =
            "${FormatUtils.decimal1(result.consumptionKmPerLiter)} km/l"

        findViewById<TextView>(R.id.tvTotalKm)?.text =
            "${String.format("%.0f", result.totalKm)} km"

        findViewById<TextView>(R.id.tvTotalCost)?.text =
            "${FormatUtils.currency(result.totalCost)}"

        findViewById<TextView>(R.id.tvTotalLiters)?.text =
            "${FormatUtils.decimal1(result.totalLiters)} L"

        val titleView = findViewById<TextView>(R.id.tvDetailsTitle)
        val container = findViewById<LinearLayout>(R.id.layoutFuelDetails)
        if (result.detailsByType.isNotEmpty()) {
            titleView?.visibility = View.VISIBLE
            container?.removeAllViews()

            result.detailsByType.forEach { (type, detail) ->
                val view = layoutInflater.inflate(R.layout.item_fuel_detail, container, false)
                view.findViewById<TextView>(R.id.tvFuelType).text = type.display
                view.findViewById<TextView>(R.id.tvFuelPercent).text =
                    "${FormatUtils.decimal1(detail.percentageOfTotal)}%"
                view.findViewById<TextView>(R.id.tvFuelLiters).text =
                    "${FormatUtils.decimal1(detail.liters)} ${type.unit}"
                view.findViewById<TextView>(R.id.tvFuelCost).text =
                    "${FormatUtils.currency(detail.cost)}"
                container?.addView(view)
            }
        } else {
            titleView?.visibility = View.GONE
        }
    }

    private fun formatExpenseValue(expense: Expense): String {
        return when (expense.costType) {
            CostType.FIXED -> {
                if (expense.periodicity == Periodicity.YEARLY) {
                    val monthly = expense.value / 12
                    "${currencyFormat.format(expense.value)}/ano"
                } else {
                    "${currencyFormat.format(expense.value)}/m\u00EAs"
                }
            }
            CostType.PER_KM -> {
                if (expense.percentageOfProfit != null) "${expense.percentageOfProfit}% do lucro"
                else "${currencyFormat.format(expense.value)}/km"
            }
            CostType.EVENT -> {
                val events = expense.estimatedEventsPerMonth ?: 1
                "${currencyFormat.format(expense.value)}/evento (~${events}x/m\u00EAs)"
            }
        }
    }

    private fun formatExpenseDetail(expense: Expense): String {
        if (expense.costType != CostType.FIXED) return ""

        val sb = StringBuilder()
        if (expense.periodicity == Periodicity.YEARLY) {
            val monthly = expense.value / 12
            sb.append("${currencyFormat.format(monthly)}/m\u00EAs")
        }
        if (expense.installmentTotal > 1) {
            if (sb.isNotEmpty()) sb.append(" \u2022 ")
            sb.append("${expense.installmentCurrent}/${expense.installmentTotal} parcelas pagas")
            val installmentValue = expense.value / expense.installmentTotal
            sb.append(" (${currencyFormat.format(installmentValue)})")
        }
        return sb.toString()
    }

    private fun showSavingFeedback(action: () -> Unit) {
        progressOverlay.visibility = View.VISIBLE
        try {
            action()
        } catch (e: Exception) {
            L.e("CostsActivity", "Error saving", e)
            android.widget.Toast.makeText(this, "Erro: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
        progressOverlay.visibility = View.GONE
    }

    private fun updateSummary() {
        val summary = CostCalculator.calculateCostSummary(allRefuels, allExpenses, monthlyKm, currentFuelType = "gasoline")

        normalizedExpenseList = findViewById(R.id.normalizedExpenseList)
        normalizedExpenseList.removeAllViews()

        if (summary.normalizedExpenses.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nenhuma despesa cadastrada"
                textSize = 11f
                setTextColor(ctxColor(R.color.text_secondary))
            }
            normalizedExpenseList.addView(empty)
        } else {
            // Group by type: variables first, then fixed, then events
            val variableLines = summary.normalizedExpenses.filter {
                it.periodicity == null
            }
            val fixedLines = summary.normalizedExpenses.filter {
                it.periodicity != null
            }

            if (variableLines.isNotEmpty()) {
                normalizedExpenseList.addView(createSectionLabel("Vari\u00E1veis:"))
                for (ne in variableLines.take(5)) {
                    normalizedExpenseList.addView(createExpenseLine(ne))
                }
            }
            if (fixedLines.isNotEmpty()) {
                normalizedExpenseList.addView(createSectionLabel("Fixos:"))
                for (ne in fixedLines.take(5)) {
                    normalizedExpenseList.addView(createExpenseLine(ne))
                }
            }
        }

        findViewById<TextView>(R.id.tvFuelCostKm).text =
            "Combust\u00EDvel/km: ${currencyFormat.format(summary.fuelCostPerKm)}"
        findViewById<TextView>(R.id.tvFixedCostKm).text =
            "Total fixos/km: ${currencyFormat.format(summary.fixedCostPerKm)}"
        findViewById<TextView>(R.id.tvVariableCostKm).text =
            "Vari\u00E1veis/km: ${currencyFormat.format(summary.variableCostPerKm)}"
        findViewById<TextView>(R.id.tvTotalCostKm).text =
            "CUSTO TOTAL/km: ${currencyFormat.format(summary.totalCostPerKm)}"
        findViewById<TextView>(R.id.tvCostPerHour).text =
            "Custo/hora: ${currencyFormat.format(summary.costPerHour)}"
        findViewById<TextView>(R.id.tvCostPerMinute).text =
            "Custo/minuto: ${currencyFormat.format(summary.costPerMinute)}"

        updateSimulator()
    }

    private fun createSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(ctxColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 4, 0, 2)
        }
    }

    private fun createExpenseLine(ne: NormalizedExpense): TextView {
        return TextView(this).apply {
            text = "${ne.name}: ${currencyFormat.format(ne.costPerKm)}/km"
            textSize = 11f
            setTextColor(ctxColor(R.color.text_secondary))
            setPadding(0, 0, 0, 2)
        }
    }

    private fun updateSimulator() {
        val summary = CostCalculator.calculateCostSummary(allRefuels, allExpenses, monthlyKm, currentFuelType = "gasoline")
        val costPerKm = summary.totalCostPerKm

        findViewById<TextView>(R.id.tvYourCost).text =
            "Seu custo/km: ${currencyFormat.format(costPerKm)}"

        val (ruim, aceitavel, bom) = CostCalculator.simulatorRange(costPerKm)
        findViewById<TextView>(R.id.tvRangeRuim).text = currencyFormat.format(ruim)
        findViewById<TextView>(R.id.tvRangeAceitavel).text = currencyFormat.format(aceitavel)
        findViewById<TextView>(R.id.tvRangeBom).text = currencyFormat.format(bom)

        findViewById<TextView>(R.id.tvProfit30).text =
            "Para lucro de 30%: precisa ${currencyFormat.format(CostCalculator.getRequiredPricePerKm(30, costPerKm))}/km"
        findViewById<TextView>(R.id.tvProfit50).text =
            "Para lucro de 50%: precisa ${currencyFormat.format(CostCalculator.getRequiredPricePerKm(50, costPerKm))}/km"
        findViewById<TextView>(R.id.tvProfit100).text =
            "Para lucro de 100%: precisa ${currencyFormat.format(CostCalculator.getRequiredPricePerKm(100, costPerKm))}/km"
    }
}
