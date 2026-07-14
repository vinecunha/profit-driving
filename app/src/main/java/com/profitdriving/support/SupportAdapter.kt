package com.profitdriving.support

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.profitdriving.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SupportAdapter(
    private val onItemClick: (SupportReport) -> Unit,
    private val onSendClick: (SupportReport) -> Unit,
    private val onDeleteClick: (SupportReport) -> Unit
) : RecyclerView.Adapter<SupportAdapter.ViewHolder>() {

    private var items = listOf<SupportReport>()

    fun submitList(list: List<SupportReport>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_support_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvApp = itemView.findViewById<TextView>(R.id.tvApp)
        private val tvDate = itemView.findViewById<TextView>(R.id.tvDate)
        private val tvStatus = itemView.findViewById<TextView>(R.id.tvStatus)
        private val tvNotes = itemView.findViewById<TextView>(R.id.tvNotes)
        private val tvPriority = itemView.findViewById<TextView>(R.id.tvPriority)
        private val btnSend = itemView.findViewById<TextView>(R.id.btnSend)
        private val btnDelete = itemView.findViewById<TextView>(R.id.btnDelete)

        fun bind(report: SupportReport) {
            tvApp.text = "📱 ${report.rawLogId?.let { "Raw Log #$it" } ?: "Manual"}"
            tvDate.text = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(report.createdAt))
            tvStatus.text = report.status.name
            tvStatus.setTextColor(getStatusColor(report.status))
            tvNotes.text = report.userNotes ?: "Sem descrição"
            tvPriority.text = report.priority.name
            tvPriority.setTextColor(getPriorityColor(report.priority))
            btnSend.setOnClickListener { onSendClick(report) }
            btnDelete.setOnClickListener { onDeleteClick(report) }
            itemView.setOnClickListener { onItemClick(report) }
        }

        private fun getStatusColor(status: SupportStatus): Int = itemView.context.run {
            when (status) {
                SupportStatus.PENDING -> getColor(R.color.warning)
                SupportStatus.ANALYZING -> getColor(R.color.accent)
                SupportStatus.RESOLVED -> getColor(R.color.success)
                SupportStatus.REJECTED -> getColor(R.color.error)
                SupportStatus.NEEDS_INFO -> getColor(R.color.warning)
            }
        }

        private fun getPriorityColor(priority: SupportPriority): Int = itemView.context.run {
            when (priority) {
                SupportPriority.NORMAL -> getColor(R.color.text_secondary)
                SupportPriority.HIGH -> getColor(R.color.warning)
                SupportPriority.CRITICAL -> getColor(R.color.error)
            }
        }
    }
}
