package com.profitdriving

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class AddRefuelDialog(
    private val context: Context,
    private val refuel: RefuelRecord? = null,
    private val onSave: (RefuelRecord) -> Unit
) {
    private var selectedFuel = refuel?.fuelType ?: "gasoline"
    private var isFullTank = refuel?.isFullTank ?: true

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_refuel, null)
        val etOdometer = view.findViewById<EditText>(R.id.etOdometer)
        val etLiters = view.findViewById<EditText>(R.id.etLiters)
        val etPricePerLiter = view.findViewById<EditText>(R.id.etPricePerLiter)
        val etTotalValue = view.findViewById<EditText>(R.id.etTotalValue)
        val btnGasoline = view.findViewById<TextView>(R.id.btnFuelGasoline)
        val btnEthanol = view.findViewById<TextView>(R.id.btnFuelEthanol)
        val btnDiesel = view.findViewById<TextView>(R.id.btnFuelDiesel)
        val btnGNV = view.findViewById<TextView>(R.id.btnFuelGNV)
        val btnFullYes = view.findViewById<TextView>(R.id.btnFullTankYes)
        val btnFullNo = view.findViewById<TextView>(R.id.btnFullTankNo)
        val btnConfirm = view.findViewById<TextView>(R.id.btnConfirmRefuel)

        val fuelButtons = listOf(
            btnGasoline to "gasoline",
            btnEthanol to "ethanol",
            btnDiesel to "diesel",
            btnGNV to "gnv"
        )

        fun selectFuel(type: String) {
            selectedFuel = type
            for ((btn, value) in fuelButtons) {
                val selected = value == type
                btn.isSelected = selected
                btn.setBackgroundResource(if (selected) R.drawable.pill_selected else R.drawable.pill_unselected)
                btn.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
            }
        }

        fuelButtons.forEach { (btn, value) ->
            btn.setOnClickListener { selectFuel(value) }
        }
        selectFuel(selectedFuel)

        fun toggleFullTank(yes: Boolean) {
            isFullTank = yes
            btnFullYes.isSelected = yes
            btnFullYes.setBackgroundResource(if (yes) R.drawable.pill_selected else R.drawable.pill_unselected)
            btnFullYes.setTextColor(if (yes) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
            btnFullNo.isSelected = !yes
            btnFullNo.setBackgroundResource(if (!yes) R.drawable.pill_selected else R.drawable.pill_unselected)
            btnFullNo.setTextColor(if (!yes) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
        }

        if (refuel != null) {
            etOdometer.setText("%.0f".format(refuel.odometerKm).replace(".", ","))
            etLiters.setText("%.1f".format(refuel.liters).replace(".", ","))
            etPricePerLiter.setText("%.2f".format(refuel.pricePerLiter).replace(".", ","))
            etTotalValue.setText("%.2f".format(refuel.totalValue).replace(".", ","))
            toggleFullTank(refuel.isFullTank)
        }

        fun updateTotal() {
            val liters = etLiters.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val price = etPricePerLiter.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val total = liters * price
            etTotalValue.setText(if (total > 0) "%.2f".format(total).replace(".", ",") else "")
        }

        val totalWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateTotal() }
        }
        etLiters.addTextChangedListener(totalWatcher)
        etPricePerLiter.addTextChangedListener(totalWatcher)

        btnFullYes.setOnClickListener { toggleFullTank(true) }
        btnFullNo.setOnClickListener { toggleFullTank(false) }
        if (refuel == null) toggleFullTank(true)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        btnConfirm.setOnClickListener {
            val odometer = etOdometer.text.toString().replace(",", ".").toDoubleOrNull()
            val liters = etLiters.text.toString().replace(",", ".").toDoubleOrNull()
            val price = etPricePerLiter.text.toString().replace(",", ".").toDoubleOrNull()

            if (odometer == null || odometer <= 0) {
                etOdometer.error = "Informe o hodômetro"
                return@setOnClickListener
            }
            if (liters == null || liters <= 0) {
                etLiters.error = "Informe os litros"
                return@setOnClickListener
            }
            if (price == null || price <= 0) {
                etPricePerLiter.error = "Informe o preço"
                return@setOnClickListener
            }

            onSave(RefuelRecord(
                timestamp = System.currentTimeMillis(),
                odometerKm = odometer,
                liters = liters,
                pricePerLiter = price,
                totalValue = liters * price,
                isFullTank = isFullTank,
                fuelType = selectedFuel
            ))
            dialog.dismiss()
        }

        dialog.show()
    }
}
