package com.profitdriving

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class MyDayRideAdapter(
    private var items: List<DailyRide>,
    private var rideRecords: Map<Long, RideRecord>,
    private var costPerKm: Double = 0.0,
    private val onToggleCompleted: (DailyRide) -> Unit,
    private val onAddTip: (DailyRide) -> Unit,
    private val onAdjust: (DailyRide) -> Unit,
    private val onCancelWithFee: (DailyRide) -> Unit
) : RecyclerView.Adapter<MyDayRideAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var expandedPosition = -1

    fun updateData(newItems: List<DailyRide>, records: Map<Long, RideRecord>, cpk: Double) {
        items = newItems
        rideRecords = records
        costPerKm = cpk
        expandedPosition = -1
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_day_ride, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ride = items[position]
        val record = rideRecords[ride.rideId]
        val isExpanded = expandedPosition == position

        holder.tvTime.text = if (record != null)
            dateFormat.format(java.util.Date(record.timestamp)) else "--:--"
        holder.tvService.text = record?.serviceType ?: "Corrida"
        holder.tvOriginalValue.text = "R$ %.2f".format(ride.originalValue).replace(".", ",")
        holder.tvFinalValue.text = "R$ %.2f".format(ride.finalValue).replace(".", ",")

        holder.chkCompleted.text = if (ride.isCompleted) "\u2611" else "\u2610"
        holder.chkCompleted.setTextColor(
            if (ride.isCompleted) 0xFF00A86B.toInt() else 0xFF5E6F8D.toInt()
        )

        if (ride.tipAmount > 0 || ride.adjustmentDifference != 0.0) {
            val parts = mutableListOf<String>()
            if (ride.tipAmount > 0)
                parts.add("Gorjeta: +R$ %.2f".format(ride.tipAmount).replace(".", ","))
            if (ride.adjustmentDifference != 0.0) {
                val sinal = if (ride.adjustmentDifference > 0) "+" else ""
                parts.add("Reajuste: $sinal" + "R$ %.2f".format(ride.adjustmentDifference).replace(".", ","))
            }
            holder.tvExtras.text = parts.joinToString(" | ")
            holder.tvExtras.visibility = View.VISIBLE
        } else {
            holder.tvExtras.visibility = View.GONE
        }

        // Destino
        val maskedDropoff = CardHashGenerator.maskAddress(record?.dropoffAddress)
        if (maskedDropoff.isNotEmpty()) {
            holder.tvDestination.text = "\uD83C\uDFC1 $maskedDropoff"
            holder.tvDestination.visibility = View.VISIBLE
        } else {
            holder.tvDestination.visibility = View.GONE
        }

        holder.tvExpandHint.text = if (isExpanded) "\u25B2" else "\u25BC"
        holder.layoutDetail.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.btnActions.visibility = if (ride.isCompleted) View.VISIBLE else View.GONE

        // Total distance/time = pickup + trip
        val pickupDist = record?.pickupDistanceKm ?: 0.0
        val tripDist = record?.tripDistanceKm ?: record?.distanceKm ?: 0.0
        val totalDist = pickupDist + tripDist

        val pickupTime = record?.pickupTimeMin ?: 0
        val tripTime = record?.tripTimeMin ?: record?.timeMin ?: 0
        val totalTime = pickupTime + tripTime

        // Pickup
        if (pickupDist > 0 || pickupTime > 0) {
            holder.tvDetailPickup.text = "%.1fkm \u23F1 %dmin".format(pickupDist, pickupTime)
                .replace(".", ",")
            holder.tvDetailPickup.visibility = View.VISIBLE
        } else {
            holder.tvDetailPickup.visibility = View.GONE
        }

        // Trip
        if (tripDist > 0 || tripTime > 0) {
            holder.tvDetailTrip.text = "%.1fkm \u23F1 %dmin".format(tripDist, tripTime)
                .replace(".", ",")
            holder.tvDetailTrip.visibility = View.VISIBLE
        } else {
            holder.tvDetailTrip.visibility = View.GONE
        }

        // Total
        holder.tvDetailTotal.text = "%.1fkm \u23F1 %dmin".format(totalDist, totalTime)
            .replace(".", ",")

        // Valor base
        holder.tvDetailBaseValue.text = "R$ %.2f".format(ride.originalValue).replace(".", ",")

        // Gorjeta
        if (ride.tipAmount > 0) {
            holder.layoutDetailTip.visibility = View.VISIBLE
            holder.tvDetailTip.text = "+R$ %.2f".format(ride.tipAmount).replace(".", ",")
        } else {
            holder.layoutDetailTip.visibility = View.GONE
        }

        // Reajuste
        if (ride.adjustmentDifference != 0.0) {
            holder.layoutDetailAdjust.visibility = View.VISIBLE
            val sinal = if (ride.adjustmentDifference > 0) "+" else ""
            holder.tvDetailAdjust.text = "$sinal" + "R$ %.2f".format(ride.adjustmentDifference).replace(".", ",")
        } else {
            holder.layoutDetailAdjust.visibility = View.GONE
        }

        // Total corrida
        holder.tvDetailFinalValue.text = "R$ %.2f".format(ride.finalValue).replace(".", ",")

        // Custo (using total distance)
        val rideCost = totalDist * costPerKm
        if (totalDist > 0 && costPerKm > 0) {
            holder.tvDetailCost.text = "-R$ %.2f (%.2f/km)".format(rideCost, costPerKm)
                .replace(".", ",")
        } else {
            holder.tvDetailCost.text = "-R$ %.2f".format(rideCost).replace(".", ",")
        }

        // Lucro
        val profit = ride.finalValue - rideCost
        holder.tvDetailProfit.text = "R$ %.2f".format(profit).replace(".", ",")
        holder.tvDetailProfit.setTextColor(
            if (profit >= 0) 0xFF00A86B.toInt() else 0xFFEF4444.toInt()
        )

        if (ride.finalValue > 0) {
            val pct = (profit / ride.finalValue) * 100
            holder.tvDetailProfitPercent.text = "%.1f%% de margem".format(pct).replace(".", ",")
            holder.tvDetailProfitPercent.visibility = View.VISIBLE
        } else {
            holder.tvDetailProfitPercent.visibility = View.GONE
        }

        // Long-press on checkbox for undo confirmation
        holder.chkCompleted.setOnLongClickListener {
            if (ride.isCompleted) {
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("Remover corrida")
                    .setMessage("Esta corrida voltará para a lista de disponíveis e não será mais contabilizada no resumo do dia.\n\nDeseja continuar?")
                    .setPositiveButton("Sim, remover") { _, _ -> onToggleCompleted(ride) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            true
        }

        // Single action button with dialog
        holder.chkCompleted.setOnClickListener { onToggleCompleted(ride) }
        holder.btnActions.setOnClickListener {
            val context = holder.itemView.context
            val options = mutableListOf<String>()
            val actions = mutableListOf<() -> Unit>()

            if (!ride.cancelledWithFee) {
                options.add("\uD83D\uDEAB Cancelar com taxa")
                actions.add { onCancelWithFee(ride) }
            }
            options.add("\uD83C\uDF6F Adicionar gorjeta")
            actions.add { onAddTip(ride) }
            options.add("\u21BB Reajustar valor")
            actions.add { onAdjust(ride) }
            options.add("\u21BA Remover da lista")
            actions.add { onToggleCompleted(ride) }

            AlertDialog.Builder(context)
                .setTitle("A\u00E7\u00F5es")
                .setItems(options.toTypedArray()) { _, which ->
                    actions.getOrNull(which)?.invoke()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        holder.root.setOnClickListener {
            if (expandedPosition == position) {
                expandedPosition = -1
                notifyItemChanged(position)
            } else {
                val prev = expandedPosition
                expandedPosition = position
                if (prev >= 0) notifyItemChanged(prev)
                notifyItemChanged(position)
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: LinearLayout = itemView.findViewById(R.id.rootRideCard)
        val chkCompleted: TextView = itemView.findViewById(R.id.chkCompleted)
        val tvTime: TextView = itemView.findViewById(R.id.tvRideTime)
        val tvService: TextView = itemView.findViewById(R.id.tvRideService)
        val tvOriginalValue: TextView = itemView.findViewById(R.id.tvRideOriginalValue)
        val tvExtras: TextView = itemView.findViewById(R.id.tvRideExtras)
        val tvDestination: TextView = itemView.findViewById(R.id.tvRideDestination)
        val tvFinalValue: TextView = itemView.findViewById(R.id.tvRideFinalValue)
        val tvExpandHint: TextView = itemView.findViewById(R.id.tvExpandHint)
        val layoutDetail: LinearLayout = itemView.findViewById(R.id.layoutRideDetail)
        val tvDetailPickup: TextView = itemView.findViewById(R.id.tvDetailPickup)
        val tvDetailTrip: TextView = itemView.findViewById(R.id.tvDetailTrip)
        val tvDetailTotal: TextView = itemView.findViewById(R.id.tvDetailTotal)
        val tvDetailBaseValue: TextView = itemView.findViewById(R.id.tvDetailBaseValue)
        val layoutDetailTip: LinearLayout = itemView.findViewById(R.id.layoutDetailTip)
        val tvDetailTip: TextView = itemView.findViewById(R.id.tvDetailTip)
        val layoutDetailAdjust: LinearLayout = itemView.findViewById(R.id.layoutDetailAdjust)
        val tvDetailAdjust: TextView = itemView.findViewById(R.id.tvDetailAdjust)
        val tvDetailFinalValue: TextView = itemView.findViewById(R.id.tvDetailFinalValue)
        val tvDetailCost: TextView = itemView.findViewById(R.id.tvDetailCost)
        val tvDetailProfit: TextView = itemView.findViewById(R.id.tvDetailProfit)
        val tvDetailProfitPercent: TextView = itemView.findViewById(R.id.tvDetailProfitPercent)
        val btnActions: TextView = itemView.findViewById(R.id.btnActions)
    }
}
