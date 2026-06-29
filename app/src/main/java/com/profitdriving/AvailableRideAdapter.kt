package com.profitdriving

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.profitdriving.FormatUtils
import java.text.SimpleDateFormat
import java.util.Locale

class AvailableRideAdapter(
    private var items: List<RideRecord>,
    private val onAddToDay: (RideRecord) -> Unit
) : RecyclerView.Adapter<AvailableRideAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val loadingIds = mutableSetOf<Long>()

    fun updateData(newItems: List<RideRecord>) {
        items = newItems
        loadingIds.clear()
        notifyDataSetChanged()
    }

    fun appendData(newItems: List<RideRecord>) {
        val startPos = items.size
        items = items + newItems
        notifyItemRangeInserted(startPos, newItems.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_available_ride, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

   override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = items[position]
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = record.timestamp }

        holder.tvTime.text = dateFormat.format(cal.time)
        holder.tvService.text = record.serviceType ?: "Corrida"

        val displayValue = record.value ?: 0.0
        holder.tvValue.text = FormatUtils.currency(displayValue)

        val totalDist = (record.pickupDistanceKm ?: 0.0) + (record.tripDistanceKm ?: record.distanceKm ?: 0.0)
        val totalTime = (record.pickupTimeMin ?: 0) + (record.tripTimeMin ?: record.timeMin ?: 0)

        if (totalDist > 0) {
            holder.tvDistance.text = "\uD83D\uDCCD ${FormatUtils.decimal1(totalDist)} km"
            holder.tvDistance.visibility = View.VISIBLE
        } else {
            holder.tvDistance.visibility = View.GONE
        }

        if (totalTime > 0) {
            holder.tvDuration.text = "\u23F1 %d min".format(totalTime)
            holder.tvDuration.visibility = View.VISIBLE
        } else {
            holder.tvDuration.visibility = View.GONE
        }

        val isLoading = loadingIds.contains(record.id)
        holder.btnAdd.isEnabled = !isLoading
        holder.btnAdd.text = if (isLoading) "\u23F3 Adicionando..." else "\u2795 Adicionar"

        holder.btnAdd.setOnClickListener {
            if (loadingIds.contains(record.id)) return@setOnClickListener
            loadingIds.add(record.id)
            holder.btnAdd.isEnabled = false
            holder.btnAdd.text = "\u23F3 Adicionando..."

            onAddToDay(record)
        }

        val maskedDropoff = CardHashGenerator.maskAddress(record.dropoffAddress)
        if (maskedDropoff.isNotEmpty()) {
            holder.tvDestination.text = "\uD83C\uDFC1 $maskedDropoff"
            holder.tvDestination.visibility = View.VISIBLE
        } else {
            holder.tvDestination.visibility = View.GONE
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTime: TextView = itemView.findViewById(R.id.tvAvailTime)
        val tvService: TextView = itemView.findViewById(R.id.tvAvailService)
        val tvValue: TextView = itemView.findViewById(R.id.tvAvailValue)
        val tvDistance: TextView = itemView.findViewById(R.id.tvAvailDistance)
        val tvDuration: TextView = itemView.findViewById(R.id.tvAvailDuration)
        val tvDestination: TextView = itemView.findViewById(R.id.tvAvailDestination)
        val btnAdd: TextView = itemView.findViewById(R.id.btnAddToDay)
    }
}
