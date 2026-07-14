package com.profitdriving.utils

import android.content.Context
import android.provider.Settings
import com.profitdriving.L
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256

    private fun getSecretKey(context: Context): SecretKeySpec {
        val userId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "default_key_2024"
        return SecretKeySpec(userId.toByteArray().copyOf(32), ALGORITHM)
    }

    fun encrypt(context: Context, data: ByteArray): ByteArray {
        return try {
            val key = getSecretKey(context)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(data)
            iv + encrypted
        } catch (e: Exception) {
            L.e("CryptoUtils", "Erro ao criptografar", e)
            data
        }
    }

    fun decrypt(context: Context, encrypted: ByteArray): ByteArray {
        return try {
            val key = getSecretKey(context)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = encrypted.copyOfRange(0, 12)
            val data = encrypted.copyOfRange(12, encrypted.size)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            L.e("CryptoUtils", "Erro ao descriptografar", e)
            encrypted
        }
    }
}
