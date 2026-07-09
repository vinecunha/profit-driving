package com.profitdriving

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CaptureHistoryActivity : BaseActivity() {

    private lateinit var captureManager: CaptureManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CaptureAdapter
    private lateinit var emptyState: View
    private lateinit var tvInfo: TextView
    private lateinit var btnActionDelete: TextView
    private var captures = listOf<CaptureRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture_history)
        setupToolbar(title = "Hist\u00F3rico de Capturas", showBack = true)

        captureManager = CaptureManager(this)
        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        tvInfo = findViewById(R.id.tvInfo)
        btnActionDelete = findViewById(R.id.btnActionDelete)

        setupRecyclerView()
        loadCaptures()
    }

    override fun onResume() {
        super.onResume()
        loadCaptures()
    }

    private fun setupRecyclerView() {
        adapter = CaptureAdapter(
            captureManager = captureManager,
            onItemClick = { record -> viewCapture(record) },
            onSaveClick = { record -> saveCapture(record) },
            onShareClick = { record -> shareCapture(record) },
            onDeleteClick = { record -> deleteCapture(record) },
            onSelectionChanged = { count -> updateSelectionToolbar(count) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadCaptures() {
        captureManager.cleanupOldCaptures()
        captures = captureManager.getCaptures()

        if (captures.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            tvInfo.visibility = View.GONE
            adapter.isSelectionMode = false
            return
        }
        emptyState.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        tvInfo.visibility = View.VISIBLE
        adapter.submitList(captures)

        setupToolbar(
            title = "Hist\u00F3rico de Capturas",
            showBack = true,
            actionText = "Selecionar",
            actionListener = { enterSelectionMode() }
        )
        btnActionDelete.visibility = View.GONE
    }

    private fun enterSelectionMode() {
        adapter.isSelectionMode = true
        setupToolbar(
            title = "Selecionar capturas",
            showBack = true,
            backListener = { exitSelectionMode() }
        )
        btnActionDelete.apply {
            visibility = View.VISIBLE
            setOnClickListener { deleteSelected() }
        }
    }

    private fun exitSelectionMode() {
        adapter.isSelectionMode = false
        btnActionDelete.visibility = View.GONE
        setupToolbar(
            title = "Hist\u00F3rico de Capturas",
            showBack = true,
            actionText = "Selecionar",
            actionListener = { enterSelectionMode() }
        )
    }

    private fun updateSelectionToolbar(count: Int) {
        btnActionDelete.text = "Excluir ($count)"
        btnActionDelete.isEnabled = count > 0
        btnActionDelete.alpha = if (count > 0) 1.0f else 0.4f
    }

    private fun deleteSelected() {
        val ids = adapter.selectedIds.toList()
        if (ids.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("Excluir capturas")
            .setMessage("Deseja excluir ${ids.size} captura(s)?")
            .setPositiveButton("Excluir") { _, _ ->
                var deleted = 0
                for (id in ids) {
                    if (captureManager.deleteCapture(id)) deleted++
                }
                Toast.makeText(this, "$deleted captura(s) exclu\u00EDda(s)", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                loadCaptures()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun viewCapture(record: CaptureRecord) {
        val intent = android.content.Intent(this, CaptureViewerActivity::class.java)
        intent.putExtra("capture_id", record.id)
        startActivity(intent)
    }

    private fun saveCapture(record: CaptureRecord) {
        if (captureManager.saveToGallery(record.id)) {
            Toast.makeText(this, "Imagem salva na galeria", Toast.LENGTH_SHORT).show()
            loadCaptures()
        } else {
            Toast.makeText(this, "Erro ao salvar imagem", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCapture(record: CaptureRecord) {
        captureManager.shareCapture(record.id)
    }

    private fun deleteCapture(record: CaptureRecord) {
        AlertDialog.Builder(this)
            .setTitle("Excluir captura")
            .setMessage("Deseja excluir esta captura?")
            .setPositiveButton("Excluir") { _, _ ->
                if (captureManager.deleteCapture(record.id)) {
                    Toast.makeText(this, "Captura exclu\u00EDda", Toast.LENGTH_SHORT).show()
                    loadCaptures()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
