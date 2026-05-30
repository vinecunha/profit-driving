package com.profitdriving

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ExpenseAdapter(
    private var items: MutableList<Expense> = mutableListOf(),
    private val onEdit: (Expense, Int) -> Unit,
    private val onDelete: (Expense, Int) -> Unit
) : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val expense = items[position]
        holder.icon.text = expense.category.icon
        holder.name.text = expense.name

        val detail = when (expense.costType) {
            CostType.FIXED -> {
                val per = if (expense.periodicity == Periodicity.YEARLY) "anual" else "mensal"
                "R\$ ${"%.2f".format(expense.value)}/$per"
            }
            CostType.PER_KM -> {
                if (expense.percentageOfProfit != null) "${expense.percentageOfProfit}% do lucro"
                else "R\$ ${"%.4f".format(expense.value)}/km"
            }
            CostType.EVENT -> {
                val events = expense.estimatedEventsPerMonth ?: 1
                "R\$ ${"%.2f".format(expense.value)}/evento (~${events}x/m\u00EAs)"
            }
        }
        holder.detail.text = detail

        val monthlyCost = when (expense.costType) {
            CostType.FIXED -> CostCalculator.calculateMonthlyFixedCost(
                expense.value, expense.periodicity ?: Periodicity.MONTHLY, expense.usefulLifeMonths
            )
            CostType.PER_KM -> expense.value * 3000
            CostType.EVENT -> expense.value * (expense.estimatedEventsPerMonth ?: 1)
        }
        holder.monthly.text = "R\$ ${"%.2f".format(monthlyCost)}/m\u00EAs"
        holder.perKm.text = "R\$ ${"%.4f".format(monthlyCost / 3000)}/km"

        holder.btnEdit.setOnClickListener { onEdit(expense, position) }
        holder.btnDelete.setOnClickListener { onDelete(expense, position) }
    }

    override fun getItemCount(): Int = items.size

    fun getItem(position: Int): Expense? {
        return if (position in items.indices) items[position] else null
    }

    fun updateData(newItems: List<Expense>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.tvExpenseIcon)
        val name: TextView = view.findViewById(R.id.tvExpenseName)
        val detail: TextView = view.findViewById(R.id.tvExpenseDetail)
        val monthly: TextView = view.findViewById(R.id.tvExpenseMonthly)
        val perKm: TextView = view.findViewById(R.id.tvExpensePerKm)
        val btnEdit: TextView = view.findViewById(R.id.btnEditExpense)
        val btnDelete: TextView = view.findViewById(R.id.btnDeleteExpense)
    }
}
