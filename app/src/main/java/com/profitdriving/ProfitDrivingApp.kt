package com.profitdriving

import android.app.Application
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ProfitDrivingApp : Application() {

    lateinit var databaseHelper: DatabaseHelper
    lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        instance = this
        databaseHelper = DatabaseHelper(this)
        prefs = createEncryptedPrefs()
        if (!isSignatureValid()) {
            L.w(TAG, "Assinatura do APK inválida — app pode ter sido adulterado")
        }
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

    private fun isSignatureValid(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            true
        } catch (e: Exception) {
            false
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