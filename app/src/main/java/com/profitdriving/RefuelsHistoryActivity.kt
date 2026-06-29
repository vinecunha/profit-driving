package com.profitdriving

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import com.profitdriving.FormatUtils
import java.util.Date
import java.util.Locale

class RefuelsHistoryActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var refuelList: LinearLayout
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private var refuels = listOf<RefuelRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_refuels_history)
        setupBottomNav(Screen.COSTS)
        setupToolbar(title = "Abastecimentos", showBack = true)

        db = DatabaseHelper(this)
        refuelList = findViewById(R.id.refuelList)

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        refuels = db.getRefuels().sortedByDescending { it.timestamp }
        renderRefuelList()
    }

    private fun renderRefuelList() {
        refuelList.removeAllViews()

        if (refuels.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nenhum abastecimento registrado"
                textSize = 12f
                setTextColor(AppColors.textSecondary)
                gravity = android.view.Gravity.CENTER
                setPadding(0, 24, 0, 8)
            }
            refuelList.addView(empty)
            return
        }

        for (refuel in refuels) {
            val row = layoutInflater.inflate(R.layout.item_refuel_history, refuelList, false)

            row.findViewById<TextView>(R.id.tvRefuelDate).text =
                dateFormat.format(Date(refuel.timestamp))

            row.findViewById<TextView>(R.id.tvRefuelOdometer).text =
                "%.0f".format(refuel.odometerKm)

            row.findViewById<TextView>(R.id.tvRefuelLiters).text =
                FormatUtils.decimal1(refuel.amount)

            row.findViewById<TextView>(R.id.tvRefuelPrice).text =
                currencyFormat.format(refuel.totalValue)

            val consumption = calculateRefuelConsumption(refuel)
            row.findViewById<TextView>(R.id.tvRefuelConsumption).text =
                if (consumption > 0) FormatUtils.decimal1(consumption) else "--"

            row.setOnLongClickListener {
                showRefuelOptionsDialog(refuel)
                true
            }

            val btnEdit = row.findViewById<TextView>(R.id.btnEditRefuel)
            val btnDelete = row.findViewById<TextView>(R.id.btnDeleteRefuel)
            btnEdit?.setOnClickListener { showEditRefuelDialog(refuel) }
            btnDelete?.setOnClickListener { showDeleteConfirmDialog(refuel) }

            refuelList.addView(row)
        }
    }

    private fun showRefuelOptionsDialog(refuel: RefuelRecord) {
        AlertDialog.Builder(this)
            .setTitle("Abastecimento")
            .setItems(arrayOf("\u270F\uFE0F Editar", "\uD83D\uDDD1\uFE0F Excluir")) { _, which ->
                when (which) {
                    0 -> showEditRefuelDialog(refuel)
                    1 -> showDeleteConfirmDialog(refuel)
                }
            }
            .show()
    }

    private fun showEditRefuelDialog(refuel: RefuelRecord) {
        AddRefuelDialog(this, refuel = refuel, onSave = { updated ->
            db.deleteRefuel(refuel.id)
            db.insertRefuel(updated)
            loadData()
        }).show()
    }

    private fun showDeleteConfirmDialog(refuel: RefuelRecord) {
        AlertDialog.Builder(this)
            .setTitle("Excluir abastecimento")
            .setMessage("Remover abastecimento de ${currencyFormat.format(refuel.totalValue)}?")
            .setPositiveButton("Excluir") { _, _ ->
                db.deleteRefuel(refuel.id)
                loadData()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun calculateRefuelConsumption(refuel: RefuelRecord): Double {
        val sorted = refuels.sortedBy { it.timestamp }
        val idx = sorted.indexOfFirst { it.id == refuel.id }
        if (idx <= 0) return 0.0
        val previous = sorted[idx - 1]
        val kmDiff = refuel.odometerKm - previous.odometerKm
        return if (kmDiff > 0 && refuel.amount > 0) kmDiff / refuel.amount else 0.0
    }
}
