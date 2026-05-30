package com.profitdriving

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MonthlyStatsAdapter(
    private var items: List<MonthlyStat> = emptyList(),
    private val maxKm: Double = 0.0
) : RecyclerView.Adapter<MonthlyStatsAdapter.ViewHolder>() {

    private val monthNames = arrayOf(
        "", "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
        "Jul", "Ago", "Set", "Out", "Nov", "Dez"
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_monthly_stat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stat = items[position]
        val label = "${monthNames[stat.month]}/${stat.year}"
        holder.month.text = label

        val progress = if (maxKm > 0) ((stat.totalKm / maxKm) * 100).toInt() else 0
        holder.progressBar.progress = progress.coerceIn(0, 100)
        holder.value.text = "${stat.totalKm.toInt()} km"
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<MonthlyStat>, newMax: Double) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val month: TextView = view.findViewById(R.id.tvStatMonth)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val value: TextView = view.findViewById(R.id.tvStatValue)
    }
}
