package com.profitdriving

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MonthlyStatsActivity : BaseActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var kmGraphContainer: LinearLayout
    private lateinit var fuelGraphContainer: LinearLayout
    private lateinit var statsTableContainer: LinearLayout
    private lateinit var tvSuggestedGoal: TextView
    private lateinit var tvGoalDetail: TextView

    private val monthNames = arrayOf(
        "", "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
        "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monthly_stats)
        setupBottomNav(Screen.HOME)
        setupToolbar(title = "Estat\u00edsticas Mensais", showBack = true, actionText = "\uD83D\uDCC5 Recalcular", actionListener = { recalculateStats() })

        dbHelper = DatabaseHelper(this)

        kmGraphContainer = findViewById(R.id.kmGraphContainer)
        fuelGraphContainer = findViewById(R.id.fuelGraphContainer)
        statsTableContainer = findViewById(R.id.statsTableContainer)
        tvSuggestedGoal = findViewById(R.id.tvSuggestedGoal)
        tvGoalDetail = findViewById(R.id.tvGoalDetail)

        loadData()
    }

    private fun loadData() {
        val refuels = dbHelper.getRefuels()
        val stats = dbHelper.getMonthlyStats()

        val kmData = CostCalculator.calculateMonthlyKm(refuels)
        val fuelData = CostCalculator.calculateMonthlyFuelCost(refuels)
        val consumptionData = CostCalculator.calculateMonthlyConsumption(refuels)

        val goal = CostCalculator.suggestMonthlyKmGoal(kmData)
        tvSuggestedGoal.text = "${"%.0f".format(goal)} km/m\u00EAs"
        tvGoalDetail.text = "Baseado na m\u00E9dia dos \u00FAltimos 3 meses"

        // Km graph
        kmGraphContainer.removeAllViews()
        if (kmData.isNotEmpty()) {
            val maxKm = kmData.values.maxOrNull() ?: 1.0
            val sortedKm = kmData.entries.sortedBy {
                it.key.first * 12 + it.key.second
            }
            for ((period, km) in sortedKm) {
                kmGraphContainer.addView(
                    createBarRow(
                        label = "${monthNames[period.second]}/${period.first}",
                        value = km,
                        maxValue = maxKm,
                        unit = " km",
                        color = "#00A86B"
                    )
                )
            }
        }

        // Fuel cost graph
        fuelGraphContainer.removeAllViews()
        if (fuelData.isNotEmpty()) {
            val maxFuel = fuelData.values.maxOrNull() ?: 1.0
            val sortedFuel = fuelData.entries.sortedBy {
                it.key.first * 12 + it.key.second
            }
            for ((period, cost) in sortedFuel) {
                fuelGraphContainer.addView(
                    createBarRow(
                        label = "${monthNames[period.second]}/${period.first}",
                        value = cost,
                        maxValue = maxFuel,
                        unit = " R\$",
                        color = "#F97316",
                        format = "%.0f"
                    )
                )
            }
        }

        // Stats table
        statsTableContainer.removeAllViews()
        if (stats.isNotEmpty()) {
            val sorted = stats.sortedBy { it.year * 12 + it.month }
            for (stat in sorted) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.setPadding(0, 8, 0, 8)
                row.setBackgroundResource(
                    if ((stats.indexOf(stat) % 2) == 0) android.R.color.white
                    else android.R.color.transparent
                )

                row.addView(createTableCell("${monthNames[stat.month]}/${stat.year}", 1f, gravity = 0))
                row.addView(createTableCell("${stat.totalKm.toInt()} km", 1f, gravity = 1))
                row.addView(createTableCell("R\$ ${"%.0f".format(stat.totalFuelCost)}", 1f, gravity = 1))
                row.addView(createTableCell("${"%.1f".format(stat.avgConsumption)} km/l", 0.8f, gravity = 2))

                statsTableContainer.addView(row)
            }
        }

        saveStatsToDb(stats, kmData, fuelData, consumptionData)
    }

    private fun createBarRow(
        label: String,
        value: Double,
        maxValue: Double,
        unit: String,
        color: String,
        format: String = "%.0f"
    ): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        row.setPadding(0, 4, 0, 4)

        val labelView = TextView(this)
        labelView.text = label
        labelView.textSize = 11f
        labelView.setTextColor(0xFF475569.toInt())
        labelView.layoutParams = LinearLayout.LayoutParams(220, LinearLayout.LayoutParams.WRAP_CONTENT)

        val progress = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.max = 100
        progress.progress = ((value / maxValue) * 100).toInt().coerceIn(0, 100)
        progress.progressTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(color)
        )
        progress.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
            0xFFE2E8F0.toInt()
        )
        progress.layoutParams = LinearLayout.LayoutParams(0, 12, 1f)

        val valueView = TextView(this)
        valueView.text = "$unit ${format.format(value)}".trimStart()
        valueView.textSize = 11f
        valueView.setTextColor(0xFF475569.toInt())
        valueView.gravity = android.view.Gravity.END
        valueView.layoutParams = LinearLayout.LayoutParams(260, LinearLayout.LayoutParams.WRAP_CONTENT)

        row.addView(labelView)
        row.addView(progress)
        row.addView(valueView)

        return row
    }

    private fun createTableCell(
        text: String,
        weight: Float,
        gravity: Int
    ): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 11f
        tv.setTextColor(0xFF475569.toInt())
        tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        when (gravity) {
            1 -> tv.gravity = android.view.Gravity.CENTER
            2 -> tv.gravity = android.view.Gravity.END
        }
        return tv
    }

    private fun recalculateStats() {
        val refuels = dbHelper.getRefuels()
        val kmData = CostCalculator.calculateMonthlyKm(refuels)
        val fuelData = CostCalculator.calculateMonthlyFuelCost(refuels)
        val consumptionData = CostCalculator.calculateMonthlyConsumption(refuels)

        val allPeriods = (kmData.keys + fuelData.keys + consumptionData.keys).toSet()
        val now = java.util.Calendar.getInstance()

        for (period in allPeriods) {
            val totalKm = kmData[period] ?: 0.0
            val totalFuelCost = fuelData[period] ?: 0.0
            val avgConsumption = consumptionData[period] ?: 0.0
            if (totalKm > 0 || totalFuelCost > 0) {
                dbHelper.saveMonthlyStat(
                    year = period.first,
                    month = period.second,
                    totalKm = totalKm,
                    totalFuelCost = totalFuelCost,
                    avgConsumption = avgConsumption
                )
            }
        }

        loadData()
    }

    private fun saveStatsToDb(
        currentStats: List<MonthlyStat>,
        kmData: Map<Pair<Int, Int>, Double>,
        fuelData: Map<Pair<Int, Int>, Double>,
        consumptionData: Map<Pair<Int, Int>, Double>
    ) {
        val existingKeys = currentStats.map { Pair(it.year, it.month) }.toSet()
        val allPeriods = (kmData.keys + fuelData.keys + consumptionData.keys).toSet()
        val missing = allPeriods - existingKeys

        if (missing.isNotEmpty()) {
            for (period in missing) {
                dbHelper.saveMonthlyStat(
                    year = period.first,
                    month = period.second,
                    totalKm = kmData[period] ?: 0.0,
                    totalFuelCost = fuelData[period] ?: 0.0,
                    avgConsumption = consumptionData[period] ?: 0.0
                )
            }
        }
    }
}
