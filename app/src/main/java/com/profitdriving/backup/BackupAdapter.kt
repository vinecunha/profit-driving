package com.profitdriving.backup

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.profitdriving.R
import java.text.SimpleDateFormat
import java.util.*

class BackupAdapter(
    private val onRestoreClick: (Backup) -> Unit,
    private val onDeleteClick: (Backup) -> Unit
) : RecyclerView.Adapter<BackupAdapter.ViewHolder>() {

    private var items = listOf<Backup>()

    fun submitList(list: List<Backup>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_backup, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName = itemView.findViewById<TextView>(R.id.tvBackupName)
        private val tvDate = itemView.findViewById<TextView>(R.id.tvBackupDate)
        private val tvSize = itemView.findViewById<TextView>(R.id.tvBackupSize)
        private val tvRides = itemView.findViewById<TextView>(R.id.tvBackupRides)
        private val btnRestore = itemView.findViewById<TextView>(R.id.btnRestore)
        private val btnDelete = itemView.findViewById<TextView>(R.id.btnDelete)

        fun bind(backup: Backup) {
            tvName.text = backup.backupName
            tvDate.text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(backup.createdAt))
            tvSize.text = formatFileSize(backup.fileSize)
            tvRides.text = "${backup.rideCount} corridas"
            btnRestore.setOnClickListener { onRestoreClick(backup) }
            btnDelete.setOnClickListener { onDeleteClick(backup) }
        }

        private fun formatFileSize(size: Long): String = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
            else -> "%.1f MB".format(size / (1024.0 * 1024.0))
        }
    }
}
