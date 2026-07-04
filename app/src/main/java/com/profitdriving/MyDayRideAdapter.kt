package com.profitdriving

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale
import com.profitdriving.FormatUtils
import com.profitdriving.AddressReputationManager

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
    private var reputationManager: AddressReputationManager? = null

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
        val iconRes = when {
            record?.serviceType == null -> R.drawable.ic_ride_generic
            record?.serviceType?.contains("Moto", ignoreCase = true) == true -> R.drawable.ic_moto
            record?.serviceType?.contains("Black", ignoreCase = true) == true ||
            record?.serviceType?.contains("Bag", ignoreCase = true) == true ||
            record?.serviceType == "99Top" || record?.serviceType == "Top" ||
            record?.serviceType == "99Black" || record?.serviceType == "99VIP" -> R.drawable.ic_car_luxury
            record?.serviceType?.contains("Entrega", ignoreCase = true) == true ||
            record?.serviceType?.contains("Flash", ignoreCase = true) == true ||
            record?.serviceType?.contains("Envios", ignoreCase = true) == true -> R.drawable.ic_delivery
            record?.serviceType?.contains("UberX", ignoreCase = true) == true ||
            record?.serviceType?.contains("99Pop", ignoreCase = true) == true ||
            record?.serviceType?.contains("Pop", ignoreCase = true) == true ||
            record?.serviceType?.contains("Comfort", ignoreCase = true) == true ||
            record?.serviceType?.contains("Juntos", ignoreCase = true) == true ||
            record?.serviceType?.startsWith("Manual - ") == true -> R.drawable.ic_car
            else -> R.drawable.ic_ride_generic
        }
        holder.ivServiceIcon.setImageResource(iconRes)
        holder.tvOriginalValue.text = FormatUtils.currency(ride.originalValue)
        holder.tvFinalValue.text = FormatUtils.currency(ride.finalValue)

        holder.chkCompleted.text = if (ride.isCompleted) "\u2611" else "\u2610"
        holder.chkCompleted.setTextColor(
            if (ride.isCompleted) AppColors.success else AppColors.textSecondary
        )

        if (ride.tipAmount > 0 || ride.adjustmentDifference != 0.0) {
            val parts = mutableListOf<String>()
            if (ride.tipAmount > 0)
                parts.add("Gorjeta: +${FormatUtils.currency(ride.tipAmount)}")
            if (ride.adjustmentDifference != 0.0) {
                val sinal = if (ride.adjustmentDifference > 0) "+" else ""
                parts.add("Reajuste: $sinal" + FormatUtils.currency(ride.adjustmentDifference))
            }
            holder.tvExtras.text = parts.joinToString(" | ")
            holder.tvExtras.visibility = View.VISIBLE
        } else {
            holder.tvExtras.visibility = View.GONE
        }

        // Destino
        val maskedDropoff = CardHashGenerator.maskAddress(record?.dropoffAddress)
        if (maskedDropoff.isNotEmpty()) {
            holder.tvDestination.text = "\uD83D\uDCCD $maskedDropoff"
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
        holder.tvDetailBaseValue.text = FormatUtils.currency(ride.originalValue)

        // Gorjeta
        if (ride.tipAmount > 0) {
            holder.layoutDetailTip.visibility = View.VISIBLE
            holder.tvDetailTip.text = "+${FormatUtils.currency(ride.tipAmount)}"
        } else {
            holder.layoutDetailTip.visibility = View.GONE
        }

        // Reajuste
        if (ride.adjustmentDifference != 0.0) {
            holder.layoutDetailAdjust.visibility = View.VISIBLE
            val sinal = if (ride.adjustmentDifference > 0) "+" else ""
            holder.tvDetailAdjust.text = "$sinal" + FormatUtils.currency(ride.adjustmentDifference)
        } else {
            holder.layoutDetailAdjust.visibility = View.GONE
        }

        // Total corrida
        holder.tvDetailFinalValue.text = FormatUtils.currency(ride.finalValue)

        // Custo (using total distance)
        val rideCost = totalDist * costPerKm
        if (totalDist > 0 && costPerKm > 0) {
            holder.tvDetailCost.text = "-${FormatUtils.currency(rideCost)} (${FormatUtils.decimal(costPerKm)}/km)"
        } else {
            holder.tvDetailCost.text = "-${FormatUtils.currency(rideCost)}"
        }

        // Lucro
        val profit = ride.finalValue - rideCost
        holder.tvDetailProfit.text = FormatUtils.currency(profit)
        holder.tvDetailProfit.setTextColor(
            if (profit >= 0) AppColors.success else AppColors.error
        )

        if (ride.finalValue > 0) {
            val pct = (profit / ride.finalValue) * 100
            holder.tvDetailProfitPercent.text = "${FormatUtils.decimal1(pct)}% de margem"
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
            val actions = mutableListOf<() -> Unit>()

            if (!ride.cancelledWithFee) {
                actions.add { onCancelWithFee(ride) }
            }
            actions.add { onAddTip(ride) }
            actions.add { onAdjust(ride) }
            actions.add { onToggleCompleted(ride) }

            val labels = mutableListOf<String>()
            if (!ride.cancelledWithFee) labels.add("\uD83D\uDEAB Cancelar com taxa")
            labels.add("\uD83C\uDF6F Adicionar gorjeta")
            labels.add("\u21BB Reajustar valor")
            labels.add("\u21BA Remover da lista")

            val view = LayoutInflater.from(context).inflate(R.layout.dialog_actions, null)
            val btn1 = view.findViewById<TextView>(R.id.btnAction1)
            val btn2 = view.findViewById<TextView>(R.id.btnAction2)
            val btn3 = view.findViewById<TextView>(R.id.btnAction3)
            val btn4 = view.findViewById<TextView>(R.id.btnAction4)
            val divider1 = view.findViewById<View>(R.id.divider1)
            val btn6 = view.findViewById<TextView>(R.id.btnAction6)
            val btn7 = view.findViewById<TextView>(R.id.btnAction7)

            var offset = 0
            if (!ride.cancelledWithFee) {
                btn1.text = labels[0]
                btn1.visibility = View.VISIBLE
                divider1.visibility = View.VISIBLE
                offset = 1
            } else {
                btn1.visibility = View.GONE
                divider1.visibility = View.GONE
            }

            btn2.text = labels[offset]
            btn2.visibility = View.VISIBLE
            btn3.text = labels[offset + 1]
            btn3.visibility = View.VISIBLE
            btn4.text = labels[offset + 2]
            btn4.visibility = View.VISIBLE

            btn6.text = "\uD83D\uDCCD Embarque"
            btn6.visibility = View.VISIBLE
            btn7.text = "\uD83C\uDFF5\uFE0F Destino"
            btn7.visibility = View.VISIBLE

            val dialog = AlertDialog.Builder(context)
                .setTitle("A\u00E7\u00F5es")
                .setView(view)
                .setNegativeButton("Cancelar", null)
                .show()
            btn1.setOnClickListener {
                dialog.dismiss()
                actions[0].invoke()
            }
            btn2.setOnClickListener {
                dialog.dismiss()
                actions[offset].invoke()
            }
            btn3.setOnClickListener {
                dialog.dismiss()
                actions[offset + 1].invoke()
            }
            btn4.setOnClickListener {
                dialog.dismiss()
                actions[offset + 2].invoke()
            }
            btn6.setOnClickListener {
                dialog.dismiss()
                showAddressReputationDialog(context, "pickup", ride, rideRecords[ride.rideId])
            }
            btn7.setOnClickListener {
                dialog.dismiss()
                showAddressReputationDialog(context, "dropoff", ride, rideRecords[ride.rideId])
            }
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

    private fun showAddressReputationDialog(context: Context, tag: String, ride: DailyRide, record: RideRecord?) {
        if (record == null) return
        val rm = reputationManager ?: AddressReputationManager(context).also { reputationManager = it }
        val address = if (tag == "pickup") record.pickupAddress else record.dropoffAddress
        val current = rm.getReputation(address)
        val items = arrayOf("\u2705 Bom", "\u274C Evitar", "\uD83D\uDDD1\uFE0F Limpar")
        val values = arrayOf(
            AddressReputationManager.Reputation.GREEN,
            AddressReputationManager.Reputation.BLACK,
            AddressReputationManager.Reputation.NONE
        )
        val checkedItem = when (current) {
            AddressReputationManager.Reputation.GREEN -> 0
            AddressReputationManager.Reputation.BLACK -> 1
            AddressReputationManager.Reputation.NONE -> 2
        }
        val holder = arrayOf(checkedItem)
        AlertDialog.Builder(context)
            .setTitle(if (tag == "pickup") "\uD83D\uDCCD Embarque" else "\uD83C\uDFF5\uFE0F Destino")
            .setSingleChoiceItems(items, checkedItem) { _, which ->
                holder[0] = which
            }
            .setPositiveButton("OK") { _, _ ->
                rm.setReputation(address, values[holder[0]])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val root: LinearLayout = itemView.findViewById(R.id.rootRideCard)
        val chkCompleted: TextView = itemView.findViewById(R.id.chkCompleted)
        val tvTime: TextView = itemView.findViewById(R.id.tvRideTime)
        val ivServiceIcon: ImageView = itemView.findViewById(R.id.ivServiceIcon)
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
