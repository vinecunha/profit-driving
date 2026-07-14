package com.profitdriving.backup

import android.app.ProgressDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.profitdriving.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BackupActivity : BaseActivity() {

    private lateinit var backupManager: BackupManager
    private lateinit var adapter: BackupAdapter
    private lateinit var rvBackups: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)
        setupToolbar("Backup", showBack = true)

        backupManager = BackupManager(this)
        setupViews()
        loadData()
        updateUI()
    }

    private fun setupViews() {
        rvBackups = findViewById(R.id.rvBackups)
        rvBackups.layoutManager = LinearLayoutManager(this)
        adapter = BackupAdapter(
            onRestoreClick = { backup -> restoreBackup(backup) },
            onDeleteClick = { backup -> deleteBackup(backup) }
        )
        rvBackups.adapter = adapter

        findViewById<Button>(R.id.btnCreateBackup).setOnClickListener { createManualBackup() }
        findViewById<LinearLayout>(R.id.llSettings).setOnClickListener { showSettingsDialog() }
    }

    private fun loadData() {
        val backups = backupManager.getBackups()
        adapter.submitList(backups)
        findViewById<TextView>(R.id.tvEmpty).visibility = if (backups.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvTotalBackups).text = "${backups.size}"
    }

    private fun updateUI() {
        val stats = backupManager.getBackupStats()
        findViewById<TextView>(R.id.tvTotalBackups).text = "${stats.totalBackups}"
        findViewById<TextView>(R.id.tvTotalSize).text = formatFileSize(stats.totalSize)

        val lastDate = stats.lastBackup?.let {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it.createdAt))
        } ?: "Nunca"
        findViewById<TextView>(R.id.tvLastBackup).text = lastDate

        val config = backupManager.getConfig()
        findViewById<TextView>(R.id.tvFrequency).text = config.frequency.name
        findViewById<TextView>(R.id.tvMaxBackups).text = "${config.maxBackups}"
        findViewById<TextView>(R.id.tvNextBackup).text = config.nextBackupAt?.let {
            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(it))
        } ?: "indefinido"
    }

    private fun createManualBackup() {
        lifecycleScope.launch {
            findViewById<Button>(R.id.btnCreateBackup).isEnabled = false
            findViewById<Button>(R.id.btnCreateBackup).text = "Criando..."
            val backup = backupManager.createBackup(BackupType.MANUAL)
            if (backup != null) {
                Toast.makeText(this@BackupActivity, "Backup criado!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@BackupActivity, "Erro ao criar backup", Toast.LENGTH_SHORT).show()
            }
            findViewById<Button>(R.id.btnCreateBackup).isEnabled = true
            findViewById<Button>(R.id.btnCreateBackup).text = "Criar Backup"
            loadData(); updateUI()
        }
    }

    private fun restoreBackup(backup: Backup) {
        AlertDialog.Builder(this)
            .setTitle("Restaurar backup")
            .setMessage(buildString {
                appendLine("Atenção: restaurar este backup irá SOBRESCREVER todos os dados atuais.\n")
                appendLine("Backup: ${backup.backupName}")
                appendLine("Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(backup.createdAt))}")
                appendLine("Corridas: ${backup.rideCount}")
                appendLine("Abastecimentos: ${backup.fuelCount}")
                appendLine("Despesas: ${backup.expenseCount}")
                append("\nDeseja continuar?")
            })
            .setPositiveButton("Restaurar") { _, _ ->
                lifecycleScope.launch {
                    val pd = ProgressDialog.show(this@BackupActivity, "Restaurando", "Aguarde...", true, false)
                    val ok = backupManager.restoreBackup(backup.id)
                    pd.dismiss()
                    if (ok) {
                        Toast.makeText(this@BackupActivity, "Dados restaurados!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@BackupActivity, "Erro ao restaurar", Toast.LENGTH_SHORT).show()
                    }
                    loadData(); updateUI()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteBackup(backup: Backup) {
        AlertDialog.Builder(this)
            .setTitle("Deletar backup")
            .setMessage("Tem certeza que deseja deletar este backup?")
            .setPositiveButton("Deletar") { _, _ ->
                if (backupManager.deleteBackup(backup.id)) {
                    Toast.makeText(this@BackupActivity, "Backup removido", Toast.LENGTH_SHORT).show()
                    loadData(); updateUI()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSettingsDialog() {
        val config = backupManager.getConfig()
        val dialogView = layoutInflater.inflate(R.layout.dialog_backup_settings, null)
        val switchEnabled = dialogView.findViewById<SwitchCompat>(R.id.switchEnabled)
        val spinnerFrequency = dialogView.findViewById<Spinner>(R.id.spinnerFrequency)
        val spinnerHour = dialogView.findViewById<Spinner>(R.id.spinnerHour)
        val spinnerMaxBackups = dialogView.findViewById<Spinner>(R.id.spinnerMaxBackups)
        val switchEncrypt = dialogView.findViewById<SwitchCompat>(R.id.switchEncrypt)
        val switchAutoRestore = dialogView.findViewById<SwitchCompat>(R.id.switchAutoRestore)

        val frequencies = BackupFrequency.values().map { it.name }
        spinnerFrequency.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, frequencies)
        val hours = (0..23).map { "%02d:00".format(it) }
        spinnerHour.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, hours)
        val maxBackups = (2..20).map { "$it" }
        spinnerMaxBackups.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, maxBackups)

        switchEnabled.isChecked = config.enabled
        spinnerFrequency.setSelection(frequencies.indexOf(config.frequency.name))
        spinnerHour.setSelection(config.hour.coerceIn(0, 23))
        spinnerMaxBackups.setSelection(maxBackups.indexOf(config.maxBackups.toString()).coerceAtLeast(0))
        switchEncrypt.isChecked = config.encrypt
        switchAutoRestore.isChecked = config.autoRestore

        AlertDialog.Builder(this)
            .setTitle("Configurações de Backup")
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val updated = config.copy(
                    enabled = switchEnabled.isChecked,
                    frequency = BackupFrequency.valueOf(spinnerFrequency.selectedItem.toString()),
                    hour = spinnerHour.selectedItemPosition,
                    maxBackups = spinnerMaxBackups.selectedItem.toString().toInt(),
                    encrypt = switchEncrypt.isChecked,
                    autoRestore = switchAutoRestore.isChecked
                )
                if (backupManager.updateConfig(updated)) {
                    Toast.makeText(this@BackupActivity, "Configurações salvas!", Toast.LENGTH_SHORT).show()
                    updateUI()
                    if (updated.enabled) BackupScheduler(this@BackupActivity).schedule()
                    else BackupScheduler(this@BackupActivity).cancel()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun formatFileSize(size: Long): String = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        else -> "%.1f MB".format(size / (1024.0 * 1024.0))
    }
}
