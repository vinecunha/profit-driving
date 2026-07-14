package com.profitdriving.backup

import com.profitdriving.DailyRide
import com.profitdriving.Expense
import com.profitdriving.RefuelRecord
import com.profitdriving.RideRecord

enum class BackupType { AUTO, MANUAL }

enum class BackupStatus { COMPLETE, PARTIAL, FAILED }

enum class BackupFrequency { DAILY, WEEKLY, MONTHLY, MANUAL }

data class Backup(
    val id: Long = 0,
    val backupName: String,
    val backupType: BackupType,
    val backupPath: String,
    val fileSize: Long,
    val rideCount: Int = 0,
    val fuelCount: Int = 0,
    val expenseCount: Int = 0,
    val status: BackupStatus = BackupStatus.COMPLETE,
    val createdAt: Long = System.currentTimeMillis(),
    val restoredAt: Long? = null,
    val notes: String? = null
)

data class BackupConfig(
    val id: Long = 1,
    val enabled: Boolean = true,
    val frequency: BackupFrequency = BackupFrequency.DAILY,
    val hour: Int = 22,
    val maxBackups: Int = 10,
    val includeScreenshots: Boolean = false,
    val encrypt: Boolean = true,
    val autoRestore: Boolean = true,
    val lastBackupAt: Long? = null,
    val nextBackupAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class BackupStats(
    val totalBackups: Int,
    val totalSize: Long,
    val lastBackup: Backup?,
    val oldestBackup: Backup?,
    val avgRideCount: Int
)

data class BackupData(
    val version: String,
    val exportedAt: Long,
    val rides: List<RideRecord>,
    val fuelRefuels: List<RefuelRecord>,
    val expenses: List<Expense>,
    val dailyRides: List<DailyRide>,
    val addressReputation: List<Pair<String, Int>>
)
