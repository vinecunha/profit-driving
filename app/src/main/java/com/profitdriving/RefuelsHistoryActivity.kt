package com.profitdriving

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class RefuelsHistoryActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var refuelList: LinearLayout
    private lateinit var btnLoadMore: TextView
    private lateinit var tvLegend: TextView
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    private var allRefuels = listOf<RefuelRecord>()
    private var displayedCount = 0
    private val pageSize = 20
    private var periodStart: Long? = null
    private var periodEnd: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_refuels_history)
        setupBottomNav(Screen.COSTS)
        setupToolbar(title = "Abastecimentos", showBack = true)

        db = DatabaseHelper(this)
        refuelList = findViewById(R.id.refuelList)
        btnLoadMore = findViewById(R.id.btnLoadMore)
        tvLegend = findViewById(R.id.tvLegend)

        periodStart = intent?.getLongExtra(EXTRA_PERIOD_START, -1L)?.takeIf { it >= 0 }
        periodEnd = intent?.getLongExtra(EXTRA_PERIOD_END, -1L)?.takeIf { it >= 0 }

        btnLoadMore.setOnClickListener { loadMore() }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        allRefuels = db.getRefuels()
            .filter { r ->
                (periodStart == null || r.timestamp >= periodStart!!) &&
                (periodEnd == null || r.timestamp <= periodEnd!!)
            }
            .sortedByDescending { it.timestamp }
        displayedCount = 0
        renderRefuelList()
    }

    private fun renderRefuelList() {
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
            btnLoadMore.visibility = View.GONE
            return
        }

        loadMore()
    }

    private fun loadMore() {
        val nextBatch = allRefuels.drop(displayedCount).take(pageSize)
        for (refuel in nextBatch) {
            val row = layoutInflater.inflate(R.layout.item_refuel_history, refuelList, false)
            val energyType = EnergyType.fromString(refuel.fuelType)

            row.findViewById<TextView>(R.id.tvRefuelDate).text =
                dateFormat.format(Date(refuel.timestamp))

            row.findViewById<TextView>(R.id.tvRefuelType).apply {
                text = energyType.icon
                setTextColor(ctxColor(energyType.colorRes))
                isClickable = false
                isFocusable = false
            }

            row.findViewById<TextView>(R.id.tvRefuelOdometer).text =
                "%.0f".format(refuel.odometerKm)

            row.findViewById<TextView>(R.id.tvRefuelVolume).text =
                "${FormatUtils.decimal1(refuel.amount)} ${energyType.unit}"

            row.findViewById<TextView>(R.id.tvRefuelPrice).text =
                currencyFormat.format(refuel.totalValue)

            val consumption = calculateRefuelConsumption(refuel)
            val consumptionUnit = if (energyType.unit == "kWh") "km/kWh" else "km/${energyType.unit}"
            row.findViewById<TextView>(R.id.tvRefuelConsumption).text =
                if (consumption > 0) "${FormatUtils.decimal1(consumption)} $consumptionUnit" else "--"

            setupSwipeDetection(row, refuel)

            refuelList.addView(row)
        }
        displayedCount += nextBatch.size

        btnLoadMore.visibility = if (displayedCount < allRefuels.size) View.VISIBLE else View.GONE
        updateLegend()
    }

    private fun updateLegend() {
        val types = allRefuels.map { EnergyType.fromString(it.fuelType) }.distinct()
        if (types.isEmpty()) {
            tvLegend.visibility = View.GONE
            return
        }
        val text = types.joinToString("  ") { "${it.icon} ${it.display} (${it.unit})" }
        tvLegend.text = text
        tvLegend.visibility = View.VISIBLE
    }

    private fun setupSwipeDetection(row: View, refuel: RefuelRecord) {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                showRefuelOptionsDialog(refuel)
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > 120 && abs(diffX) > abs(diffY) * 2) {
                    if (diffX > 0) {
                        showEditRefuelDialog(refuel)
                    } else {
                        showDeleteConfirmDialog(refuel)
                    }
                    return true
                }
                return false
            }
        })

        row.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> row.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> row.parent.requestDisallowInterceptTouchEvent(false)
            }
            detector.onTouchEvent(event)
            false
        }
        row.isLongClickable = false
    }

    private fun showRefuelOptionsDialog(refuel: RefuelRecord) {
        val energyType = EnergyType.fromString(refuel.fuelType)
        AlertDialog.Builder(this)
            .setTitle("${energyType.icon} Abastecimento")
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
        val sameType = allRefuels.filter { it.fuelType == refuel.fuelType }.sortedBy { it.timestamp }
        val idx = sameType.indexOfFirst { it.id == refuel.id }
        if (idx <= 0) return 0.0
        val previous = sameType[idx - 1]
        val kmDiff = refuel.odometerKm - previous.odometerKm
        return if (kmDiff > 0 && refuel.amount > 0) kmDiff / refuel.amount else 0.0
    }

    companion object {
        const val EXTRA_PERIOD_START = "period_start"
        const val EXTRA_PERIOD_END = "period_end"
    }
}
