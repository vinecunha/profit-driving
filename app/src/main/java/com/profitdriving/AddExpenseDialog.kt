package com.profitdriving

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.app.AlertDialog
import com.profitdriving.FormatUtils

class AddExpenseDialog(
    private val context: Context,
    private val existing: Expense? = null,
    private val onSave: (Expense) -> Unit
) {

    fun show() {
        if (existing != null) {
            showTypeSpecific(existing.costType)
        } else {
            showTypePicker()
        }
    }

    private fun showTypePicker() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_expense_type, null)
        val dialog = AlertDialog.Builder(context).setView(view).create()

        view.findViewById<TextView>(R.id.btnTypePerKm).setOnClickListener {
            dialog.dismiss()
            showTypeSpecific(CostType.PER_KM)
        }
        view.findViewById<TextView>(R.id.btnTypeFixed).setOnClickListener {
            dialog.dismiss()
            showTypeSpecific(CostType.FIXED)
        }
        view.findViewById<TextView>(R.id.btnTypeEvent).setOnClickListener {
            dialog.dismiss()
            showTypeSpecific(CostType.EVENT)
        }

        dialog.show()
    }

    private fun showTypeSpecific(type: CostType) {
        when (type) {
            CostType.PER_KM -> showPerKmDialog()
            CostType.FIXED -> showFixedDialog()
            CostType.EVENT -> showEventDialog()
        }
    }

    private fun mapCategory(name: String): ExpenseCategory {
        return when {
            name.contains("Manuten\u00E7\u00E3o", ignoreCase = true) -> ExpenseCategory.MAINTENANCE
            name.contains("Pneu", ignoreCase = true) -> ExpenseCategory.TIRES
            name.contains("Combust\u00EDvel", ignoreCase = true) -> ExpenseCategory.FUEL
            name.contains("Lavagem", ignoreCase = true) -> ExpenseCategory.WASH
            name.contains("Seguro", ignoreCase = true) -> ExpenseCategory.INSURANCE
            name.contains("IPVA", ignoreCase = true) -> ExpenseCategory.TAX
            name.contains("Financiamento", ignoreCase = true) -> ExpenseCategory.FINANCING
            name.contains("Estacionamento", ignoreCase = true) -> ExpenseCategory.PARKING
            name.contains("Ped\u00E1gio", ignoreCase = true) -> ExpenseCategory.TOLL
            else -> ExpenseCategory.OTHER
        }
    }

    private fun showPerKmDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_expense_per_km, null)
        val dialog = AlertDialog.Builder(context).setView(view).create()

        val etName = view.findViewById<EditText>(R.id.etPerKmName)
        val etValue = view.findViewById<EditText>(R.id.etPerKmValue)
        var selectedName: String? = null

        fun selectSuggestion(name: String, defaultValue: Double) {
            selectedName = name
            etName.visibility = View.GONE
            etValue.setText(FormatUtils.decimal4(defaultValue))
        }

        view.findViewById<TextView>(R.id.btnPerKmManutencao).setOnClickListener { selectSuggestion("Manuten\u00E7\u00E3o", 0.07) }
        view.findViewById<TextView>(R.id.btnPerKmPneus).setOnClickListener { selectSuggestion("Pneus", 0.03) }
        view.findViewById<TextView>(R.id.btnPerKmCombustivel).setOnClickListener { selectSuggestion("Combust\u00EDvel", 0.25) }
        view.findViewById<TextView>(R.id.btnPerKmLavagem).setOnClickListener { selectSuggestion("Lavagem", 0.008) }
        view.findViewById<TextView>(R.id.btnPerKmOutro).setOnClickListener {
            selectedName = null
            etName.visibility = View.VISIBLE
            etName.requestFocus()
        }

        if (existing?.costType == CostType.PER_KM) {
            selectedName = existing.name
            if (ExpenseSuggestions.perKmSuggestions.any { it.name == existing.name }) {
                etValue.setText(FormatUtils.decimal4(existing.value))
            } else {
                etName.setText(existing.name)
                etName.visibility = View.VISIBLE
                etValue.setText(FormatUtils.decimal4(existing.value))
            }
        }

        view.findViewById<TextView>(R.id.btnCancelPerKm).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnSavePerKm).setOnClickListener {
            val name = selectedName ?: etName.text.toString().trim()
            val valueStr = etValue.text.toString().replace(",", ".").toDoubleOrNull()

            if (name.isEmpty()) {
                etName.error = "Informe o nome"
                return@setOnClickListener
            }
            if (valueStr == null || valueStr <= 0) {
                etValue.error = "Informe um valor v\u00E1lido"
                return@setOnClickListener
            }

            onSave(Expense(
                id = existing?.id ?: 0,
                name = name,
                value = valueStr,
                costType = CostType.PER_KM,
                category = mapCategory(name),
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            ))
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showFixedDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_expense_fixed, null)
        val dialog = AlertDialog.Builder(context).setView(view).create()

        val etName = view.findViewById<EditText>(R.id.etFixedName)
        val etValue = view.findViewById<EditText>(R.id.etFixedValue)
        val etInstallmentTotal = view.findViewById<EditText>(R.id.etInstallmentTotal)
        val etInstallmentCurrent = view.findViewById<EditText>(R.id.etInstallmentCurrent)
        val installGroup = view.findViewById<LinearLayout>(R.id.installmentGroup)
        val btnAvista = view.findViewById<TextView>(R.id.btnPaymentInstallmentNo)
        val btnParcelado = view.findViewById<TextView>(R.id.btnPaymentInstallmentYes)
        var selectedName: String? = null
        var selectedPeriodicity = Periodicity.MONTHLY
        var isInstallment = false

        val btnMonthly = view.findViewById<TextView>(R.id.btnFixedMonthly)
        val btnYearly = view.findViewById<TextView>(R.id.btnFixedYearly)

        fun selectSuggestion(name: String, defaultValue: Double) {
            selectedName = name
            etName.visibility = View.GONE
            etValue.setText(FormatUtils.decimal(defaultValue))
        }

        fun selectPeriodicity(per: Periodicity) {
            selectedPeriodicity = per
            btnMonthly.isSelected = per == Periodicity.MONTHLY
            btnYearly.isSelected = per == Periodicity.YEARLY
            btnMonthly.setBackgroundResource(if (per == Periodicity.MONTHLY) R.drawable.pill_selected else R.drawable.pill_unselected)
            btnYearly.setBackgroundResource(if (per == Periodicity.YEARLY) R.drawable.pill_selected else R.drawable.pill_unselected)
            btnMonthly.setTextColor(if (per == Periodicity.MONTHLY) AppColors.textInverse else AppColors.textSecondary)
            btnYearly.setTextColor(if (per == Periodicity.YEARLY) AppColors.textInverse else AppColors.textSecondary)
        }

        fun selectPaymentType(installment: Boolean) {
            isInstallment = installment
            btnAvista.isSelected = !installment
            btnParcelado.isSelected = installment
            btnAvista.setBackgroundResource(if (!installment) R.drawable.pill_selected else R.drawable.pill_unselected)
            btnParcelado.setBackgroundResource(if (installment) R.drawable.pill_selected else R.drawable.pill_unselected)
            btnAvista.setTextColor(if (!installment) AppColors.textInverse else AppColors.textSecondary)
            btnParcelado.setTextColor(if (installment) AppColors.textInverse else AppColors.textSecondary)
            installGroup.visibility = if (installment) View.VISIBLE else View.GONE
        }

        view.findViewById<TextView>(R.id.btnFixedSeguro).setOnClickListener { selectSuggestion("Seguro", 250.0) }
        view.findViewById<TextView>(R.id.btnFixedIpva).setOnClickListener {
            selectSuggestion("IPVA", 1350.0)
            selectPeriodicity(Periodicity.YEARLY)
        }
        view.findViewById<TextView>(R.id.btnFixedFinanciamento).setOnClickListener { selectSuggestion("Financiamento", 500.0) }
        view.findViewById<TextView>(R.id.btnFixedManutencao).setOnClickListener { selectSuggestion("Manuten\u00E7\u00E3o preventiva", 80.0) }
        view.findViewById<TextView>(R.id.btnFixedOutro).setOnClickListener {
            selectedName = null
            etName.visibility = View.VISIBLE
            etName.requestFocus()
        }

        btnMonthly.setOnClickListener { selectPeriodicity(Periodicity.MONTHLY) }
        btnYearly.setOnClickListener { selectPeriodicity(Periodicity.YEARLY) }

        btnAvista.setOnClickListener { selectPaymentType(false) }
        btnParcelado.setOnClickListener { selectPaymentType(true) }

        if (existing?.costType == CostType.FIXED) {
            selectedName = existing.name
            val displayValue = existing.totalOriginalValue ?: existing.value
            if (ExpenseSuggestions.fixedSuggestions.any { it.name == existing.name }) {
                etValue.setText(FormatUtils.decimal(displayValue))
            } else {
                etName.setText(existing.name)
                etName.visibility = View.VISIBLE
                etValue.setText(FormatUtils.decimal(displayValue))
            }
            selectPeriodicity(existing.periodicity ?: Periodicity.MONTHLY)
            if (existing.installmentTotal > 1) {
                selectPaymentType(true)
                etInstallmentTotal.setText(existing.installmentTotal.toString())
                etInstallmentCurrent.setText(existing.installmentCurrent.toString())
            }
        }

        view.findViewById<TextView>(R.id.btnCancelFixed).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnSaveFixed).setOnClickListener {
            val name = selectedName ?: etName.text.toString().trim()
            val valueStr = etValue.text.toString().replace(",", ".").toDoubleOrNull()

            if (name.isEmpty()) {
                etName.error = "Informe o nome"
                return@setOnClickListener
            }
            if (valueStr == null || valueStr <= 0) {
                etValue.error = "Informe um valor v\u00E1lido"
                return@setOnClickListener
            }

            val installmentTotal = if (isInstallment)
                etInstallmentTotal.text.toString().toIntOrNull()?.takeIf { it >= 1 } ?: 12
            else 1
            val installmentCurrent = if (isInstallment)
                etInstallmentCurrent.text.toString().toIntOrNull()?.takeIf { it >= 0 } ?: 0
            else (if (existing?.installmentTotal ?: 1 > 1) (existing?.installmentCurrent ?: 0) else 0)

            val paymentStatus = if (installmentCurrent >= installmentTotal && installmentTotal > 1) "PAID"
                else if (installmentCurrent > 0) "PARTIAL"
                else "PENDING"

            val paidAmount = if (installmentTotal > 1 && installmentCurrent > 0)
                valueStr / installmentTotal * installmentCurrent
            else 0.0

            onSave(Expense(
                id = existing?.id ?: 0,
                name = name,
                value = valueStr,
                costType = CostType.FIXED,
                periodicity = selectedPeriodicity,
                category = mapCategory(name),
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                totalOriginalValue = valueStr,
                paymentStatus = paymentStatus,
                paidAmount = paidAmount,
                installmentTotal = installmentTotal,
                installmentCurrent = installmentCurrent
            ))
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEventDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_expense_event, null)
        val dialog = AlertDialog.Builder(context).setView(view).create()

        val etName = view.findViewById<EditText>(R.id.etEventName)
        val etValue = view.findViewById<EditText>(R.id.etEventValue)
        val etFreq = view.findViewById<EditText>(R.id.etEventFreq)
        var selectedName: String? = null

        fun selectSuggestion(name: String, defaultValue: Double) {
            selectedName = name
            etName.visibility = View.GONE
            etValue.setText(FormatUtils.decimal(defaultValue))
        }

        view.findViewById<TextView>(R.id.btnEventEstacionamento).setOnClickListener { selectSuggestion("Estacionamento", 20.0) }
        view.findViewById<TextView>(R.id.btnEventPedagio).setOnClickListener { selectSuggestion("Ped\u00E1gio", 15.0) }
        view.findViewById<TextView>(R.id.btnEventLavagem).setOnClickListener { selectSuggestion("Lavagem especial", 60.0) }
        view.findViewById<TextView>(R.id.btnEventOutro).setOnClickListener {
            selectedName = null
            etName.visibility = View.VISIBLE
            etName.requestFocus()
        }

        if (existing?.costType == CostType.EVENT) {
            selectedName = existing.name
            if (ExpenseSuggestions.eventSuggestions.any { it.name == existing.name }) {
                etValue.setText(FormatUtils.decimal(existing.value))
            } else {
                etName.setText(existing.name)
                etName.visibility = View.VISIBLE
                etValue.setText(FormatUtils.decimal(existing.value))
            }
            etFreq.setText((existing.estimatedEventsPerMonth ?: 1).toString())
        }

        view.findViewById<TextView>(R.id.btnCancelEvent).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnSaveEvent).setOnClickListener {
            val name = selectedName ?: etName.text.toString().trim()
            val valueStr = etValue.text.toString().replace(",", ".").toDoubleOrNull()
            val freq = etFreq.text.toString().toIntOrNull()?.takeIf { it >= 1 } ?: 1

            if (name.isEmpty()) {
                etName.error = "Informe o nome"
                return@setOnClickListener
            }
            if (valueStr == null || valueStr <= 0) {
                etValue.error = "Informe um valor v\u00E1lido"
                return@setOnClickListener
            }

            onSave(Expense(
                id = existing?.id ?: 0,
                name = name,
                value = valueStr,
                costType = CostType.EVENT,
                estimatedEventsPerMonth = freq,
                category = mapCategory(name),
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            ))
            dialog.dismiss()
        }

        dialog.show()
    }
}
