package com.profitdriving.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.profitdriving.L
import com.profitdriving.R
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class BackupScheduler(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "backup_channel"
        private const val WORK_NAME = "backup_work"
    }

    fun schedule() {
        val config = BackupManager(context).getConfig()
        if (!config.enabled) { cancel(); return }

        if (config.frequency == BackupFrequency.MANUAL) return

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val intervalHours = when (config.frequency) {
            BackupFrequency.DAILY -> 24L
            BackupFrequency.WEEKLY -> 168L
            BackupFrequency.MONTHLY -> 720L
            else -> 24L
        }

        val initialDelay = config.nextBackupAt?.let {
            maxOf(0L, it - System.currentTimeMillis())
        } ?: 0L

        val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun isScheduled(): Boolean {
        return try {
            val workInfo = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME)
                .get()
            workInfo.isNotEmpty()
        } catch (_: Exception) { false }
    }
}

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val backup = BackupManager(applicationContext).createBackup(BackupType.AUTO)
            if (backup != null) {
                L.d("BackupWorker", "Backup automático criado: ${backup.backupName}")
                Result.success()
            } else {
                L.e("BackupWorker", "Falha ao criar backup automático")
                Result.failure()
            }
        } catch (e: Exception) {
            L.e("BackupWorker", "Erro no backup", e)
            Result.retry()
        }
    }
}
