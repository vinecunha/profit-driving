package com.profitdriving

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class AddRefuelDialog(
    private val context: Context,
    private val refuel: RefuelRecord? = null,
    private val onSave: (RefuelRecord) -> Unit
) {
    private var selectedFuel = refuel?.fuelType ?: "gasoline"
    private var selectedCharger = refuel?.chargerType ?: "AC"
    private var isFullTank = refuel?.isFullTank ?: true

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_refuel, null)
        val etOdometer = view.findViewById<EditText>(R.id.etOdometer)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val etPricePerUnit = view.findViewById<EditText>(R.id.etPricePerUnit)
        val etTotalValue = view.findViewById<EditText>(R.id.etTotalValue)
        val etPercentageStart = view.findViewById<EditText>(R.id.etPercentageStart)
        val etPercentageEnd = view.findViewById<EditText>(R.id.etPercentageEnd)
        val chargerGroup = view.findViewById<View>(R.id.chargerGroup)
        val percentageGroup = view.findViewById<View>(R.id.percentageGroup)

        val btnGasoline = view.findViewById<TextView>(R.id.btnFuelGasoline)
        val btnEthanol = view.findViewById<TextView>(R.id.btnFuelEthanol)
        val btnDiesel = view.findViewById<TextView>(R.id.btnFuelDiesel)
        val btnGNV = view.findViewById<TextView>(R.id.btnFuelGNV)
        val btnElectricAC = view.findViewById<TextView>(R.id.btnFuelElectricAC)
        val btnElectricDC = view.findViewById<TextView>(R.id.btnFuelElectricDC)
        val btnHybrid = view.findViewById<TextView>(R.id.btnFuelHybrid)
        val btnChargerAC = view.findViewById<TextView>(R.id.btnChargerAC)
        val btnChargerDC = view.findViewById<TextView>(R.id.btnChargerDC)
        val btnChargerHome = view.findViewById<TextView>(R.id.btnChargerHome)
        val btnFullYes = view.findViewById<TextView>(R.id.btnFullTankYes)
        val btnFullNo = view.findViewById<TextView>(R.id.btnFullTankNo)
        val btnConfirm = view.findViewById<TextView>(R.id.btnConfirmRefuel)

        val allFuelButtons = listOf(
            btnGasoline to "gasoline",
            btnEthanol to "ethanol",
            btnDiesel to "diesel",
            btnGNV to "gnv",
            btnElectricAC to "ELECTRIC_AC",
            btnElectricDC to "ELECTRIC_DC",
            btnHybrid to "HYBRID_CHARGE"
        )

        val chargerButtons = listOf(
            btnChargerAC to "AC",
            btnChargerDC to "DC",
            btnChargerHome to "home"
        )

        fun updateFieldsForFuelType(type: String) {
            val isElectric = type in listOf("ELECTRIC_AC", "ELECTRIC_DC", "HYBRID_CHARGE")
            val energyType = EnergyType.fromString(type)
            val hint = "Quantidade (${energyType.unit}) - calculado automaticamente"
            etAmount.hint = hint
            chargerGroup.visibility = if (isElectric) View.VISIBLE else View.GONE
            percentageGroup.visibility = if (isElectric) View.VISIBLE else View.GONE
        }

        fun selectFuel(type: String) {
            selectedFuel = type
            for ((btn, value) in allFuelButtons) {
                val selected = value == type
                btn.isSelected = selected
                btn.setBackgroundResource(if (selected) R.drawable.pill_selected else R.drawable.pill_unselected)
                btn.setTextColor(if (selected) AppColors.textInverse else AppColors.textSecondary)
            }
            updateFieldsForFuelType(type)
        }

        fun selectCharger(type: String) {
            selectedCharger = type
            for ((btn, value) in chargerButtons) {
                val selected = value == type
                btn.isSelected = selected
                btn.setBackgroundResource(if (selected) R.drawable.pill_selected else R.drawable.pill_unselected)
                btn.setTextColor(if (selected) AppColors.textInverse else AppColors.textSecondary)
            }
        }

        allFuelButtons.forEach { (btn, value) ->
            btn.setOnClickListener { selectFuel(value) }
        }
        chargerButtons.forEach { (btn, value) ->
            btn.setOnClickListener { selectCharger(value) }
        }
        selectFuel(selectedFuel)
        selectCharger(selectedCharger)

        fun toggleFullTank(yes: Boolean) {
            isFullTank = yes
            btnFullYes.isSelected = yes
            btnFullYes.setBackgroundResource(if (yes) R.drawable.pill_selected else R.drawable.pill_unselected)
            btnFullYes.setTextColor(if (yes) AppColors.textInverse else AppColors.textSecondary)
            btnFullNo.isSelected = !yes
            btnFullNo.setBackgroundResource(if (!yes) R.drawable.pill_selected else R.drawable.pill_unselected)
            btnFullNo.setTextColor(if (!yes) AppColors.textInverse else AppColors.textSecondary)
        }

        if (refuel != null) {
            etOdometer.setText("%.0f".format(refuel.odometerKm).replace(".", ","))
            etPricePerUnit.setText("%.2f".format(refuel.pricePerUnit).replace(".", ","))
            etTotalValue.setText("%.2f".format(refuel.totalValue).replace(".", ","))
            etAmount.setText("%.2f".format(refuel.amount).replace(".", ","))
            refuel.percentageStart?.let { etPercentageStart.setText(it.toString()) }
            refuel.percentageEnd?.let { etPercentageEnd.setText(it.toString()) }
            toggleFullTank(refuel.isFullTank)
        }

        fun updateAmount() {
            val total = etTotalValue.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val price = etPricePerUnit.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val amount = if (price > 0) total / price else 0.0
            etAmount.setText(if (amount > 0) "%.2f".format(amount).replace(".", ",") else "")
        }

        val amountWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateAmount() }
        }
        etTotalValue.addTextChangedListener(amountWatcher)
        etPricePerUnit.addTextChangedListener(amountWatcher)

        btnFullYes.setOnClickListener { toggleFullTank(true) }
        btnFullNo.setOnClickListener { toggleFullTank(false) }
        if (refuel == null) toggleFullTank(true)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        btnConfirm.setOnClickListener {
            val odometer = etOdometer.text.toString().replace(",", ".").toDoubleOrNull()
            val total = etTotalValue.text.toString().replace(",", ".").toDoubleOrNull()
            val price = etPricePerUnit.text.toString().replace(",", ".").toDoubleOrNull()
            val pctStart = etPercentageStart.text.toString().toIntOrNull()
            val pctEnd = etPercentageEnd.text.toString().toIntOrNull()

            if (odometer == null || odometer <= 0) {
                etOdometer.error = "Informe o hodômetro"
                return@setOnClickListener
            }
            if (total == null || total <= 0) {
                etTotalValue.error = "Informe o valor total"
                return@setOnClickListener
            }
            if (price == null || price <= 0) {
                etPricePerUnit.error = "Informe o preço"
                return@setOnClickListener
            }

            val energyType = EnergyType.fromString(selectedFuel)
            val amount = total / price

            onSave(RefuelRecord(
                timestamp = System.currentTimeMillis(),
                odometerKm = odometer,
                amount = amount,
                unitType = energyType.unit,
                pricePerUnit = price,
                totalValue = total,
                isFullTank = isFullTank,
                fuelType = selectedFuel,
                chargerType = if (energyType.isElectric) selectedCharger else null,
                percentageStart = if (energyType.isElectric) pctStart else null,
                percentageEnd = if (energyType.isElectric) pctEnd else null
            ))
            dialog.dismiss()
        }

        dialog.show()
    }
}
