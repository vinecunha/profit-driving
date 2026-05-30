package com.profitdriving

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView


class ExpensesActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper

    private lateinit var tvTotalCostPerKm: TextView
    private lateinit var tvFixedMonthly: TextView
    private lateinit var emptyState: View
    private lateinit var expensesContent: View

    private lateinit var perKmList: LinearLayout
    private lateinit var fixedList: LinearLayout
    private lateinit var eventList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expenses)
        setupBottomNav(Screen.COSTS)
        setupToolbar(title = "Despesas", showBack = true)

        db = DatabaseHelper(this)

        tvTotalCostPerKm = findViewById(R.id.tvTotalCostPerKm)
        tvFixedMonthly = findViewById(R.id.tvFixedMonthlyCost)
        emptyState = findViewById(R.id.emptyState)
        expensesContent = findViewById(R.id.expensesContent)
        perKmList = findViewById(R.id.perKmList)
        fixedList = findViewById(R.id.fixedList)
        eventList = findViewById(R.id.eventList)

        findViewById<TextView>(R.id.btnAddExpense).setOnClickListener {
            AddExpenseDialog(this) { expense ->
                db.insertExpenseItem(expense)
                loadData()
            }.show()
        }

        findViewById<TextView>(R.id.btnAddPerKm).setOnClickListener {
            AddExpenseDialog(this) { expense ->
                db.insertExpenseItem(expense)
                loadData()
            }.show()
        }

        findViewById<TextView>(R.id.btnAddFixed).setOnClickListener {
            AddExpenseDialog(this) { expense ->
                db.insertExpenseItem(expense)
                loadData()
            }.show()
        }

        findViewById<TextView>(R.id.btnAddEvent).setOnClickListener {
            AddExpenseDialog(this) { expense ->
                db.insertExpenseItem(expense)
                loadData()
            }.show()
        }
    }

    override fun onResume() {
        super.onResume()
        try { loadData() } catch (e: Exception) {
            android.util.Log.e("ExpensesActivity", "Error loading data", e)
        }
    }

    private fun loadData() {
        val allExpenses = db.getAllExpenses()

        emptyState.visibility = if (allExpenses.isEmpty()) View.VISIBLE else View.GONE
        expensesContent.visibility = if (allExpenses.isEmpty()) View.GONE else View.VISIBLE

        renderExpenseLists(allExpenses)
        updateSummary(allExpenses)
    }

    private fun renderExpenseLists(allExpenses: List<Expense>) {
        perKmList.removeAllViews()
        fixedList.removeAllViews()
        eventList.removeAllViews()

        val perKmExpenses = allExpenses.filter { it.costType == CostType.PER_KM }
        val fixedExpenses = allExpenses.filter { it.costType == CostType.FIXED }
        val eventExpenses = allExpenses.filter { it.costType == CostType.EVENT }

        renderList(perKmList, perKmExpenses) { e, _ -> "R\$ ${"%.4f".format(e.value)}/km" }
        renderList(fixedList, fixedExpenses) { e, p ->
            val monthly = when (e.periodicity ?: Periodicity.MONTHLY) {
                Periodicity.YEARLY -> e.value / 12
                Periodicity.MONTHLY -> e.value
            }
            "R\$ ${"%.2f".format(monthly)}/m\u00EAs"
        }
        renderList(eventList, eventExpenses) { e, _ ->
            "R\$ ${"%.2f".format(e.value)}/evento"
        }
    }

    private fun renderList(
        container: LinearLayout,
        items: List<Expense>,
        formatValue: (Expense, Int) -> String
    ) {
        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nenhuma despesa cadastrada"
                textSize = 12f
                setTextColor(0xFF9CA3AF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 16)
            }
            container.addView(empty)
            return
        }

        for ((index, expense) in items.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14, 10, 14, 10)
                if (index < items.size - 1) {
                    setBackgroundResource(R.drawable.toggle_unselected)
                    setPadding(14, 10, 14, 6)
                }
            }

            val separator = if (index < items.size - 1) {
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { setMargins(14, 0, 14, 0) }
                    setBackgroundColor(0xFFF0F2F8.toInt())
                }
            } else null

            val iconText = TextView(this).apply {
                text = expense.category.icon
                textSize = 16f
                setPadding(0, 0, 8, 0)
            }

            val nameText = TextView(this).apply {
                text = expense.name
                textSize = 13f
                setTextColor(0xFF1A2C3E.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val valueText = TextView(this).apply {
                text = formatValue(expense, index)
                textSize = 12f
                setTextColor(0xFF5E6F8D.toInt())
                setPadding(8, 0, 8, 0)
            }

            val btnEdit = TextView(this).apply {
                text = "\u270F\uFE0F"
                textSize = 14f
                setPadding(4, 0, 4, 0)
                setOnClickListener {
                    AddExpenseDialog(this@ExpensesActivity, expense) { updated ->
                        db.updateExpenseItem(updated)
                        loadData()
                    }.show()
                }
            }

            val btnDelete = TextView(this).apply {
                text = "\uD83D\uDDD1\uFE0F"
                textSize = 14f
                setPadding(4, 0, 0, 0)
                setOnClickListener {
                    showDeleteConfirm(expense)
                }
            }

            row.addView(iconText)
            row.addView(nameText)
            row.addView(valueText)
            row.addView(btnEdit)
            row.addView(btnDelete)
            container.addView(row)
            if (separator != null) container.addView(separator)
        }
    }

    private fun updateSummary(allExpenses: List<Expense>) {
        val monthlyKm = 3000

        val fixedMonthly = allExpenses.filter { it.costType == CostType.FIXED }.sumOf { e ->
            when (e.periodicity ?: Periodicity.MONTHLY) {
                Periodicity.YEARLY -> e.value / 12
                Periodicity.MONTHLY -> e.value
            }
        }

        val perKmSum = allExpenses.filter { it.costType == CostType.PER_KM }.sumOf { it.value }

        val eventMonthly = allExpenses.filter { it.costType == CostType.EVENT }.sumOf { e ->
            e.value * (e.estimatedEventsPerMonth ?: 1)
        }

        val totalMonthly = fixedMonthly + perKmSum * monthlyKm + eventMonthly
        val totalPerKm = if (monthlyKm > 0) totalMonthly / monthlyKm else 0.0

        tvTotalCostPerKm.text = "Custo total/km: R\$ ${"%.2f".format(totalPerKm)}"
        tvFixedMonthly.text = "Custo fixo mensal: R\$ ${"%.2f".format(fixedMonthly)}"
    }

    private fun showDeleteConfirm(expense: Expense) {
        AlertDialog.Builder(this)
            .setTitle("Excluir despesa")
            .setMessage("Tem certeza que deseja excluir \"${expense.name}\"?")
            .setPositiveButton("Excluir") { _, _ ->
                db.deleteExpenseItem(expense.id)
                loadData()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
