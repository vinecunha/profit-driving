package com.profitdriving

import android.app.Application
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.profitdriving.backup.BackupScheduler

class ProfitDrivingApp : Application() {

    lateinit var databaseHelper: DatabaseHelper
    lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppColors.init(this)
        databaseHelper = DatabaseHelper(this)
        prefs = createEncryptedPrefs()
        databaseHelper.pruneOldRawLogs()
        BackupScheduler(this).schedule()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                this,
                SettingsActivity.PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            L.e(TAG, "Falha ao criar SharedPreferences criptografadas", e)
            getSharedPreferences(SettingsActivity.PREF_NAME, MODE_PRIVATE)
        }
    }

    companion object {
        private const val TAG = "CorridaCerta"

        @Volatile
        private var instance: ProfitDrivingApp? = null

        fun getInstance(): ProfitDrivingApp =
            instance ?: throw IllegalStateException("ProfitDrivingApp não inicializada")
    }
}