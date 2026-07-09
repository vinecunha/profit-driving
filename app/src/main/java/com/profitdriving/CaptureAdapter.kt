package com.profitdriving

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureAdapter(
    private val captureManager: CaptureManager,
    private val onItemClick: (CaptureRecord) -> Unit,
    private val onSaveClick: (CaptureRecord) -> Unit,
    private val onShareClick: (CaptureRecord) -> Unit,
    private val onDeleteClick: (CaptureRecord) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {}
) : ListAdapter<CaptureRecord, CaptureAdapter.ViewHolder>(DiffCallback()) {

    var isSelectionMode = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) selectedIds.clear()
                onSelectionChanged(selectedIds.size)
                notifyDataSetChanged()
            }
        }

    val selectedIds = mutableSetOf<String>()

    fun selectAll() {
        selectedIds.clear()
        for (i in 0 until itemCount) {
            selectedIds.add(getItem(i).id)
        }
        onSelectionChanged(selectedIds.size)
        notifyDataSetChanged()
    }

    fun deselectAll() {
        selectedIds.clear()
        onSelectionChanged(0)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_capture, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = getItem(position)
        holder.bind(record)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        private val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        private val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        private val tvDate: TextView = view.findViewById(R.id.tvDate)
        private val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        private val layoutActions: LinearLayout = view.findViewById(R.id.layoutActions)
        private val btnView: ImageButton = view.findViewById(R.id.btnView)
        private val btnSave: ImageButton = view.findViewById(R.id.btnSave)
        private val btnShare: ImageButton = view.findViewById(R.id.btnShare)
        private val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        fun bind(record: CaptureRecord) {
            tvAppName.text = record.appName
            tvDate.text = dateFormat.format(Date(record.timestamp))

            tvStatus.text = if (record.savedToGallery) "Salvo na galeria" else "N\u00E3o salvo"
            tvStatus.setTextColor(
                if (record.savedToGallery)
                    tvStatus.context.getColor(R.color.success)
                else
                    tvStatus.context.getColor(R.color.text_tertiary)
            )

            val thumb = captureManager.getDecryptedThumbnail(record)
            if (thumb != null) {
                ivThumbnail.setImageDrawable(
                    androidx.core.graphics.drawable.DrawableCompat.wrap(
                        android.graphics.drawable.BitmapDrawable(
                            ivThumbnail.context.resources, thumb
                        )
                    )
                )
            } else {
                ivThumbnail.setImageResource(R.drawable.ic_photo_library)
            }

            if (isSelectionMode) {
                cbSelect.visibility = View.VISIBLE
                layoutActions.visibility = View.GONE
                cbSelect.isChecked = selectedIds.contains(record.id)
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedIds.add(record.id)
                    else selectedIds.remove(record.id)
                    onSelectionChanged(selectedIds.size)
                }
                itemView.setOnClickListener {
                    cbSelect.isChecked = !cbSelect.isChecked
                }
            } else {
                cbSelect.visibility = View.GONE
                layoutActions.visibility = View.VISIBLE
                btnView.setOnClickListener { onItemClick(record) }
                btnSave.setOnClickListener { onSaveClick(record) }
                btnShare.setOnClickListener { onShareClick(record) }
                btnDelete.setOnClickListener { onDeleteClick(record) }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CaptureRecord>() {
        override fun areItemsTheSame(old: CaptureRecord, new: CaptureRecord): Boolean =
            old.id == new.id

        override fun areContentsTheSame(old: CaptureRecord, new: CaptureRecord): Boolean =
            old == new
    }
}
