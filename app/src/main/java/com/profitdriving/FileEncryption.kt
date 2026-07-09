package com.profitdriving

import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object FileEncryption {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val SALT_LENGTH = 16
    private const val KEY_LENGTH = 256
    private const val ITERATIONS = 100_000

    // ─── Base64 wrapper (for JSON/text export) ───

    fun encrypt(plaintext: ByteArray, password: String): String {
        val salted = encryptRaw(plaintext, password)
        return Base64.encodeToString(salted, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String, password: String): ByteArray {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        return decryptRaw(data, password)
    }

    // ─── Raw byte format (for file encryption) ───

    fun encryptRaw(plaintext: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

        val ciphertext = cipher.doFinal(plaintext)
        val result = ByteArray(SALT_LENGTH + IV_LENGTH + ciphertext.size)
        System.arraycopy(salt, 0, result, 0, SALT_LENGTH)
        System.arraycopy(iv, 0, result, SALT_LENGTH, IV_LENGTH)
        System.arraycopy(ciphertext, 0, result, SALT_LENGTH + IV_LENGTH, ciphertext.size)
        return result
    }

    fun decryptRaw(data: ByteArray, password: String): ByteArray {
        if (data.size < SALT_LENGTH + IV_LENGTH + 1) {
            throw IllegalArgumentException("Dados criptografados inválidos")
        }
        val salt = data.copyOfRange(0, SALT_LENGTH)
        val iv = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = data.copyOfRange(SALT_LENGTH + IV_LENGTH, data.size)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

        return cipher.doFinal(ciphertext)
    }

    fun encryptFile(file: File, password: String) {
        val plaintext = file.readBytes()
        val encrypted = encryptRaw(plaintext, password)
        file.writeBytes(encrypted)
    }

    fun decryptFile(file: File, password: String): ByteArray {
        val data = file.readBytes()
        return decryptRaw(data, password)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
