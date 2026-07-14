package com.profitdriving.backup

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.profitdriving.DatabaseHelper
import com.profitdriving.L
import com.profitdriving.utils.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

class BackupManager(private val context: Context) {

    private val db = DatabaseHelper(context)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    companion object {
        private const val BACKUP_DIR = "backups"
        private const val FILE_EXTENSION = ".crb"
    }

    suspend fun createBackup(type: BackupType = BackupType.MANUAL): Backup? {
        return withContext(Dispatchers.IO) {
            try {
                val backupData = exportAllData()
                val json = gson.toJson(backupData)
                val jsonBytes = json.toByteArray()

                val encrypted = if (getConfig().encrypt) {
                    CryptoUtils.encrypt(context, jsonBytes)
                } else jsonBytes

                val backupPath = saveToDisk(encrypted, type) ?: return@withContext null

                val backup = Backup(
                    backupName = generateBackupName(),
                    backupType = type,
                    backupPath = backupPath,
                    fileSize = encrypted.size.toLong(),
                    rideCount = backupData.rides.size,
                    fuelCount = backupData.fuelRefuels.size,
                    expenseCount = backupData.expenses.size,
                    status = BackupStatus.COMPLETE,
                    createdAt = System.currentTimeMillis()
                )

                val id = db.insertBackup(backup)
                if (id > 0) {
                    updateConfigAfterBackup()
                    cleanOldBackups()
                    return@withContext backup.copy(id = id)
                }
                null
            } catch (e: Exception) {
                L.e("BackupManager", "Erro ao criar backup", e)
                null
            }
        }
    }

    suspend fun restoreBackup(backupId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val backup = db.getBackup(backupId) ?: return@withContext false
                val encrypted = loadFromDisk(backup.backupPath) ?: return@withContext false

                val jsonBytes = if (getConfig().encrypt) {
                    CryptoUtils.decrypt(context, encrypted)
                } else encrypted

                val json = String(jsonBytes)
                val backupData = gson.fromJson(json, BackupData::class.java)
                restoreAllData(backupData)
                db.updateBackupRestored(backupId, System.currentTimeMillis())
                true
            } catch (e: Exception) {
                L.e("BackupManager", "Erro ao restaurar backup", e)
                false
            }
        }
    }

    private suspend fun exportAllData(): BackupData {
        return withContext(Dispatchers.IO) {
            BackupData(
                version = getAppVersion(),
                exportedAt = System.currentTimeMillis(),
                rides = db.getAll(),
                fuelRefuels = db.getRefuels(),
                expenses = db.getAllExpenses(),
                dailyRides = db.getAllDailyRides(),
                addressReputation = db.getAllAddressReputations()
            )
        }
    }

    private suspend fun restoreAllData(data: BackupData) {
        withContext(Dispatchers.IO) {
            db.clearAllData()
            for (ride in data.rides) db.insertRideRecord(ride)
            for (refuel in data.fuelRefuels) db.insertRefuel(refuel)
            for (expense in data.expenses) db.insertExpenseItem(expense)
            for (dr in data.dailyRides) db.insertDailyRide(dr)
            for ((addr, rep) in data.addressReputation) db.insertAddressReputation(addr, rep)
        }
    }

    private fun saveToDisk(data: ByteArray, type: BackupType): String? {
        return try {
            val dir = File(context.filesDir, BACKUP_DIR)
            if (!dir.exists()) dir.mkdirs()
            val fileName = "${System.currentTimeMillis()}_${type.name}.$FILE_EXTENSION"
            val file = File(dir, fileName)
            file.writeBytes(data)
            file.absolutePath
        } catch (e: Exception) {
            L.e("BackupManager", "Erro ao salvar no disco", e)
            null
        }
    }

    private fun loadFromDisk(path: String): ByteArray? {
        return try {
            val file = File(path)
            if (!file.exists()) null else file.readBytes()
        } catch (e: Exception) {
            L.e("BackupManager", "Erro ao carregar do disco", e)
            null
        }
    }

    fun getBackups(): List<Backup> = db.getAllBackups()
    fun getBackup(id: Long): Backup? = db.getBackup(id)

    fun deleteBackup(id: Long): Boolean {
        val backup = db.getBackup(id) ?: return false
        try { File(backup.backupPath).delete() } catch (_: Exception) { }
        return db.deleteBackup(id)
    }

    fun getBackupStats(): BackupStats {
        val backups = db.getAllBackups()
        return if (backups.isEmpty()) {
            BackupStats(0, 0, null, null, 0)
        } else {
            BackupStats(
                totalBackups = backups.size,
                totalSize = backups.sumOf { it.fileSize },
                lastBackup = backups.firstOrNull(),
                oldestBackup = backups.lastOrNull(),
                avgRideCount = if (backups.isNotEmpty()) backups.sumOf { it.rideCount } / backups.size else 0
            )
        }
    }

    fun getConfig(): BackupConfig = db.getBackupConfig() ?: BackupConfig()

    fun updateConfig(config: BackupConfig): Boolean {
        val updated = config.copy(
            updatedAt = System.currentTimeMillis(),
            nextBackupAt = calculateNextBackup(config)
        )
        return db.updateBackupConfig(updated)
    }

    private fun updateConfigAfterBackup() {
        val config = getConfig()
        val updated = config.copy(
            lastBackupAt = System.currentTimeMillis(),
            nextBackupAt = calculateNextBackup(config)
        )
        db.updateBackupConfig(updated)
    }

    private fun calculateNextBackup(config: BackupConfig): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        return when (config.frequency) {
            BackupFrequency.DAILY -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, config.hour)
                calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
            BackupFrequency.WEEKLY -> {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, config.hour)
                calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
            BackupFrequency.MONTHLY -> {
                calendar.add(Calendar.MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, config.hour)
                calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
            BackupFrequency.MANUAL -> Long.MAX_VALUE
        }
    }

    private fun cleanOldBackups() {
        val config = getConfig()
        val backups = db.getAllBackups()
        if (backups.size > config.maxBackups) {
            for (b in backups.drop(config.maxBackups)) deleteBackup(b.id)
        }
    }

    private fun generateBackupName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        return "Backup_$date"
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) { "1.0" }
    }
}
