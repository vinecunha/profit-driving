package com.profitdriving

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class AvailableRideAdapter(
    private var items: List<RideRecord>,
    private val onAddToDay: (RideRecord) -> Unit
) : RecyclerView.Adapter<AvailableRideAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun updateData(newItems: List<RideRecord>) {
        items = newItems
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
        holder.tvValue.text = "R$ %.2f".format(displayValue).replace(".", ",")

        val dist = record.tripDistanceKm ?: record.distanceKm
        val dur = record.tripTimeMin ?: record.timeMin

        if (dist != null) {
            holder.tvDistance.text = "\uD83D\uDCCD %.1f km".format(dist).replace(".", ",")
            holder.tvDistance.visibility = View.VISIBLE
        } else {
            holder.tvDistance.visibility = View.GONE
        }

        if (dur != null) {
            holder.tvDuration.text = "\u23F1 %d min".format(dur)
            holder.tvDuration.visibility = View.VISIBLE
        } else {
            holder.tvDuration.visibility = View.GONE
        }

        holder.btnAdd.setOnClickListener { onAddToDay(record) }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTime: TextView = itemView.findViewById(R.id.tvAvailTime)
        val tvService: TextView = itemView.findViewById(R.id.tvAvailService)
        val tvValue: TextView = itemView.findViewById(R.id.tvAvailValue)
        val tvDistance: TextView = itemView.findViewById(R.id.tvAvailDistance)
        val tvDuration: TextView = itemView.findViewById(R.id.tvAvailDuration)
        val btnAdd: TextView = itemView.findViewById(R.id.btnAddToDay)
    }
}
