package com.profitdriving

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CostsActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var etMonthlyKm: EditText
    private lateinit var refuelList: LinearLayout
    private lateinit var perKmList: LinearLayout
    private lateinit var fixedList: LinearLayout
    private lateinit var eventList: LinearLayout
    private lateinit var normalizedExpenseList: LinearLayout
    private var selectedFuel = "gasoline"
    private var refuels = listOf<RefuelRecord>()
    private var allExpenses = listOf<Expense>()
    private var monthlyKm = 3000
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_costs)
        setupBottomNav(Screen.COSTS)
        setupToolbar(title = "Custos", showBack = true)

        db = DatabaseHelper(this)

        setupFuelSelector()
        setupRefuelButton()
        setupExpenseButtons()
        setupMonthlyKm()

        findViewById<TextView>(R.id.btnViewExpenses).setOnClickListener {
            startActivity(Intent(this, ExpensesActivity::class.java))
        }
        findViewById<TextView>(R.id.btnViewStats).setOnClickListener {
            startActivity(Intent(this, MonthlyStatsActivity::class.java))
        }
        findViewById<TextView>(R.id.btnViewAllRefuels).setOnClickListener {
            startActivity(Intent(this, RefuelsHistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        try { loadData() } catch (e: Exception) {
            android.util.Log.e("CostsActivity", "Error loading data", e)
        }
    }

    private fun setupFuelSelector() {
        val fuelButtons = mapOf(
            R.id.btnFuelGasoline to "gasoline",
            R.id.btnFuelEthanol to "ethanol",
            R.id.btnFuelDiesel to "diesel",
            R.id.btnFuelGNV to "gnv"
        )

        fun selectFuel(type: String) {
            selectedFuel = type
            for ((id, value) in fuelButtons) {
                val btn = findViewById<TextView>(id)
                val selected = value == type
                btn.isSelected = selected
                btn.setBackgroundResource(if (selected) R.drawable.pill_selected else R.drawable.pill_unselected)
                btn.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
            }
        }

        for ((id, value) in fuelButtons) {
            findViewById<TextView>(id).setOnClickListener { selectFuel(value) }
        }
        selectFuel("gasoline")
    }

    private fun setupRefuelButton() {
        findViewById<TextView>(R.id.btnAddRefuel).setOnClickListener {
            AddRefuelDialog(this) { refuel ->
                db.insertRefuel(refuel)
                loadData()
            }.show()
        }
    }

    private fun setupExpenseButtons() {
        findViewById<TextView>(R.id.btnAddPerKm).setOnClickListener {
            AddExpenseDialog(this, onSave = { expense ->
                db.insertExpenseItem(expense)
                loadData()
            }).show()
        }
        findViewById<TextView>(R.id.btnAddFixed).setOnClickListener {
            AddExpenseDialog(this, onSave = { expense ->
                db.insertExpenseItem(expense)
                loadData()
            }).show()
        }
        findViewById<TextView>(R.id.btnAddEvent).setOnClickListener {
            AddExpenseDialog(this, onSave = { expense ->
                db.insertExpenseItem(expense)
                loadData()
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
                    db.saveMonthlyKm(km)
                    updateSummary()
                    updateSimulator()
                }
            }
        })
    }

    private fun loadData() {
        refuels = db.getRefuels()
        allExpenses = db.getAllExpenses()
        monthlyKm = db.getMonthlyKm()

        etMonthlyKm.setText(monthlyKm.toString())
        renderRefuelList()
        renderExpenseLists()
        updateSummary()
        updateSimulator()
    }

    private fun renderRefuelList() {
        refuelList = findViewById(R.id.refuelList)
        refuelList.removeAllViews()

        if (refuels.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nenhum abastecimento registrado"
                textSize = 12f
                setTextColor(0xFF9CA3AF.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 8)
            }
            refuelList.addView(empty)
            return
        }

        for (refuel in refuels.take(4)) {
            val row = layoutInflater.inflate(R.layout.item_refuel_history, refuelList, false)

            row.findViewById<TextView>(R.id.tvRefuelDate).text =
                dateFormat.format(Date(refuel.timestamp))

            row.findViewById<TextView>(R.id.tvRefuelOdometer).text =
                "%.0f".format(refuel.odometerKm)

            row.findViewById<TextView>(R.id.tvRefuelLiters).text =
                "%.1f".format(refuel.liters).replace(".", ",")

            row.findViewById<TextView>(R.id.tvRefuelPrice).text =
                currencyFormat.format(refuel.totalValue)

            val consumption = calculateRefuelConsumption(refuel)
            row.findViewById<TextView>(R.id.tvRefuelConsumption).text =
                if (consumption > 0) "%.1f".format(consumption).replace(".", ",") else "--"

            refuelList.addView(row)
        }
    }

    private fun calculateRefuelConsumption(refuel: RefuelRecord): Double {
        val allRefuels = refuels.sortedByDescending { it.timestamp }
        val idx = allRefuels.indexOfFirst { it.id == refuel.id }
        if (idx < 0 || idx >= allRefuels.size - 1) return 0.0
        val next = allRefuels[idx + 1]
        val kmDiff = refuel.odometerKm - next.odometerKm
        return if (kmDiff > 0 && refuel.liters > 0) kmDiff / refuel.liters else 0.0
    }

    private fun renderExpenseLists() {
        perKmList = findViewById(R.id.perKmList)
        fixedList = findViewById(R.id.fixedList)
        eventList = findViewById(R.id.eventList)

        renderExpenseList(perKmList, allExpenses.filter { it.costType == CostType.PER_KM }, "nenhum custo vari\u00E1vel")
        renderExpenseList(fixedList, allExpenses.filter { it.costType == CostType.FIXED }, "nenhum custo fixo")
        renderExpenseList(eventList, allExpenses.filter { it.costType == CostType.EVENT }, "nenhum custo por evento")
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

    private fun renderExpenseList(container: LinearLayout, expenses: List<Expense>, emptyMessage: String) {
        container.removeAllViews()

        if (expenses.isEmpty()) {
            val empty = TextView(this).apply {
                text = emptyMessage
                textSize = 11f
                setTextColor(0xFF9CA3AF.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(0, 6, 0, 6)
            }
            container.addView(empty)
            return
        }

        for (expense in expenses) {
            val row = layoutInflater.inflate(R.layout.item_expense, container, false)

            row.findViewById<TextView>(R.id.tvExpenseIcon).text = expense.category.icon
            row.findViewById<TextView>(R.id.tvExpenseName).text = expense.name

            val statusText = when (expense.paymentStatus) {
                "PAID" -> if (expense.installmentTotal > 1)
                    "\u2705 Quitado (${expense.installmentCurrent}/${expense.installmentTotal})"
                    else "\u2705 Quitado"
                "PARTIAL" -> "\u23F3 Pago parcialmente (${expense.installmentCurrent}/${expense.installmentTotal})"
                else -> "\u23F1 Pendente"
            }
            val statusColor = when (expense.paymentStatus) {
                "PAID" -> 0xFF16A34A.toInt()
                "PARTIAL" -> 0xFFF59E0B.toInt()
                else -> 0xFF6B7280.toInt()
            }
            row.findViewById<TextView>(R.id.tvExpenseStatus).text = statusText
            row.findViewById<TextView>(R.id.tvExpenseStatus).setTextColor(statusColor)

            row.findViewById<TextView>(R.id.tvExpenseValue).text = formatExpenseValue(expense)

            val detail = formatExpenseDetail(expense)
            row.findViewById<TextView>(R.id.tvExpenseDetail).text = detail

            // Payment action buttons
            val btnPay = row.findViewById<TextView>(R.id.btnPayPartial)
            val btnQuit = row.findViewById<TextView>(R.id.btnQuit)
            if (expense.paymentStatus != "PAID") {
                btnPay.visibility = View.VISIBLE
                btnQuit.visibility = View.VISIBLE
                btnPay.setOnClickListener {
                    showPaymentDialog(expense)
                }
                btnQuit.setOnClickListener {
                    db.updateExpenseItem(expense.copy(
                        paymentStatus = "PAID",
                        installmentCurrent = expense.installmentTotal,
                        paidAmount = expense.totalOriginalValue ?: expense.value,
                        lastPaymentDate = System.currentTimeMillis()
                    ))
                    loadData()
                }
            }

            row.findViewById<TextView>(R.id.btnEditExpense).setOnClickListener {
                AddExpenseDialog(this, expense) { updated ->
                    db.updateExpenseItem(updated)
                    loadData()
                }.show()
            }

            row.findViewById<TextView>(R.id.btnDeleteExpense).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Excluir despesa")
                    .setMessage("Remover \"${expense.name}\"?")
                    .setPositiveButton("Excluir") { _, _ ->
                        db.deleteExpenseItem(expense.id)
                        loadData()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }

            container.addView(row)
        }
    }

    private fun showPaymentDialog(expense: Expense) {
        val remaining = (expense.totalOriginalValue ?: expense.value) - expense.paidAmount
        if (remaining <= 0) {
            db.updateExpenseItem(expense.copy(
                paymentStatus = "PAID",
                installmentCurrent = expense.installmentTotal,
                paidAmount = expense.totalOriginalValue ?: expense.value,
                lastPaymentDate = System.currentTimeMillis()
            ))
            loadData()
            return
        }

        val installmentValue = (expense.totalOriginalValue ?: expense.value) / expense.installmentTotal
        val remainingInstallments = expense.installmentTotal - expense.installmentCurrent

        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Valor a pagar (R\$ ${"%.2f".format(installmentValue).replace(".", ",")})"
            setText(String.format("%.2f", installmentValue).replace(".", ","))
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("\uD83D\uDCB0 Registrar pagamento")
            .setMessage("Restam ${remainingInstallments}x de ${currencyFormat.format(installmentValue)}")
            .setView(input)
            .setPositiveButton("Pagar") { _, _ ->
                val payValue = input.text.toString().replace(",", ".").toDoubleOrNull() ?: installmentValue
                val newPaidAmount = expense.paidAmount + payValue
                val newInstallmentCurrent = (newPaidAmount / installmentValue).toInt()
                    .coerceAtMost(expense.installmentTotal)
                val totalValue = expense.totalOriginalValue ?: expense.value
                val newStatus = if (newPaidAmount >= totalValue) "PAID" else "PARTIAL"

                db.updateExpenseItem(expense.copy(
                    paymentStatus = newStatus,
                    paidAmount = newPaidAmount,
                    installmentCurrent = newInstallmentCurrent,
                    lastPaymentDate = System.currentTimeMillis()
                ))
                loadData()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateSummary() {
        val summary = CostCalculator.calculateCostSummary(refuels, allExpenses, monthlyKm)

        normalizedExpenseList = findViewById(R.id.normalizedExpenseList)
        normalizedExpenseList.removeAllViews()

        if (summary.normalizedExpenses.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nenhuma despesa cadastrada"
                textSize = 11f
                setTextColor(0xFF9CA3AF.toInt())
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
            "Custo/min: ${currencyFormat.format(summary.costPerMinute)}"

        findViewById<TextView>(R.id.tvAvgConsumption).text =
            "Consumo m\u00E9dio: ${"%.1f".format(summary.avgConsumption).replace(".", ",")} km/L"
        findViewById<TextView>(R.id.tvAvgFuelCost).text =
            "Custo combust\u00EDvel: ${currencyFormat.format(summary.fuelCostPerKm)}/km"

        updateSimulator()
    }

    private fun createSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(0xFF1A2C3E.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 4, 0, 2)
        }
    }

    private fun createExpenseLine(ne: NormalizedExpense): TextView {
        return TextView(this).apply {
            text = "${ne.name}: ${currencyFormat.format(ne.costPerKm)}/km"
            textSize = 11f
            setTextColor(0xFF5E6F8D.toInt())
            setPadding(0, 0, 0, 2)
        }
    }

    private fun updateSimulator() {
        val summary = CostCalculator.calculateCostSummary(refuels, allExpenses, monthlyKm)
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
