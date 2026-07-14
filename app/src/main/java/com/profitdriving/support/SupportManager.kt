package com.profitdriving.support

import android.content.Context
import android.os.Build
import com.profitdriving.DatabaseHelper
import com.profitdriving.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupportManager(private val context: Context) {

    private val db = DatabaseHelper(context)

    fun createReport(
        rawLogId: Long? = null,
        rideId: Long? = null,
        cardHash: String? = null,
        userNotes: String? = null,
        priority: SupportPriority = SupportPriority.NORMAL
    ): Long? {
        try {
            val report = SupportReport(
                rawLogId = rawLogId,
                rideId = rideId,
                cardHash = cardHash,
                userNotes = userNotes,
                priority = priority,
                deviceModel = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                appVersion = getAppVersion()
            )
            return db.insertSupportReport(report)
        } catch (e: Exception) {
            L.e("SupportManager", "Erro ao criar relatório", e)
            return null
        }
    }

    fun getPendingReports(): List<SupportReport> {
        return db.getSupportReports(status = SupportStatus.PENDING)
    }

    fun getReportsByStatus(status: SupportStatus): List<SupportReport> {
        return db.getSupportReports(status = status)
    }

    fun getReport(reportId: Long): SupportReport? {
        return db.getSupportReport(reportId)
    }

    fun updateStatus(reportId: Long, status: SupportStatus, notes: String? = null): Boolean {
        return db.updateSupportReportStatus(reportId, status, notes)
    }

    suspend fun submitReport(reportId: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val report = db.getSupportReport(reportId)
                    ?: return@withContext Result.failure(Exception("Relatório não encontrado"))
                db.updateSupportReportStatus(reportId, SupportStatus.ANALYZING)
                Result.success(true)
            } catch (e: Exception) {
                L.e("SupportManager", "Erro ao enviar relatório", e)
                Result.failure(e)
            }
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
