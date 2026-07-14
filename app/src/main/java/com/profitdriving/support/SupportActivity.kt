package com.profitdriving.support

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.profitdriving.*
import com.profitdriving.DatabaseHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SupportActivity : BaseActivity() {

    private lateinit var supportManager: SupportManager
    private lateinit var adapter: SupportAdapter
    private lateinit var rvReports: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support)
        setupToolbar("Suporte", showBack = true)

        supportManager = SupportManager(this)
        setupViews()
        loadReports()
    }

    private fun setupViews() {
        rvReports = findViewById(R.id.rvReports)
        rvReports.layoutManager = LinearLayoutManager(this)
        adapter = SupportAdapter(
            onItemClick = { report -> showReportDetail(report) },
            onSendClick = { report -> submitReport(report) },
            onDeleteClick = { report -> deleteReport(report) }
        )
        rvReports.adapter = adapter

        findViewById<Button>(R.id.btnNewReport).setOnClickListener { showNewReportDialog() }
    }

    private fun loadReports() {
        val reports = supportManager.getPendingReports()
        adapter.submitList(reports)
        findViewById<TextView>(R.id.tvEmpty).visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvCount).text = "${reports.size} pendentes"
    }

    private fun showNewReportDialog() {
        val db = DatabaseHelper(this)
        val problemLogs = try {
            db.getRawLogsByStatus("error", 20)
        } catch (_: Exception) { emptyList<Any>() }

        if (problemLogs.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Nenhum card problemático")
                .setMessage("No momento não há cards com erro de leitura. Se você identificou um card que não foi reconhecido corretamente, ele aparecerá aqui após a detecção.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val items = problemLogs.map { log ->
            val rawLog = log as? com.profitdriving.RawCardLog
            val ts = if (rawLog != null) formatDate(rawLog.timestamp) else "?"
            val pkg = rawLog?.packageName ?: "?"
            "$ts - $pkg"
        }

        AlertDialog.Builder(this)
            .setTitle("Selecione o card problemático")
            .setItems(items.toTypedArray()) { _, which ->
                val selectedLog = problemLogs[which] as? com.profitdriving.RawCardLog
                if (selectedLog != null) showReportForm(selectedLog)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showReportForm(rawLog: com.profitdriving.RawCardLog) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_support_report, null)
        val etNotes = dialogView.findViewById<EditText>(R.id.etNotes)
        val spinnerPriority = dialogView.findViewById<Spinner>(R.id.spinnerPriority)
        val priorities = SupportPriority.values().map { it.name }
        spinnerPriority.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, priorities)

        AlertDialog.Builder(this)
            .setTitle("Detalhes do problema")
            .setView(dialogView)
            .setPositiveButton("Enviar") { _, _ ->
                val notes = etNotes.text.toString()
                val priority = SupportPriority.values()[spinnerPriority.selectedItemPosition]
                val reportId = supportManager.createReport(
                    rawLogId = rawLog.id,
                    userNotes = notes.ifEmpty { null },
                    priority = priority
                )
                if (reportId != null) {
                    Toast.makeText(this, "Relatório criado!", Toast.LENGTH_SHORT).show()
                    loadReports()
                } else {
                    Toast.makeText(this, "Erro ao criar relatório", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showReportDetail(report: SupportReport) {
        AlertDialog.Builder(this)
            .setTitle("Relatório #${report.id}")
            .setMessage(buildString {
                appendLine("Status: ${report.status.name}")
                appendLine("Prioridade: ${report.priority.name}")
                if (!report.userNotes.isNullOrBlank()) appendLine("Notas: ${report.userNotes}")
                if (!report.deviceModel.isNullOrBlank()) appendLine("Dispositivo: ${report.deviceModel}")
                if (!report.androidVersion.isNullOrBlank()) appendLine("Android: ${report.androidVersion}")
                appendLine("Criado: ${formatDate(report.createdAt)}")
            })
            .setPositiveButton("OK", null)
            .show()
    }

    private fun submitReport(report: SupportReport) {
        lifecycleScope.launch {
            val result = supportManager.submitReport(report.id)
            if (result.isSuccess) {
                Toast.makeText(this@SupportActivity, "Enviado para análise!", Toast.LENGTH_SHORT).show()
                loadReports()
            } else {
                Toast.makeText(this@SupportActivity, "Erro ao enviar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteReport(report: SupportReport) {
        AlertDialog.Builder(this)
            .setTitle("Remover relatório")
            .setMessage("Tem certeza que deseja remover este relatório?")
            .setPositiveButton("Remover") { _, _ ->
                val db = DatabaseHelper(this)
                db.deleteSupportReport(report.id)
                loadReports()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
