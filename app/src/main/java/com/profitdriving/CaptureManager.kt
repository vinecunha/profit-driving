package com.profitdriving

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class CaptureManager(private val context: Context) {

    private val captureDir = File(context.cacheDir, "captures")
    private val thumbDir = File(context.cacheDir, "captures/thumbs")
    private val prefs = context.getSharedPreferences("captures", Context.MODE_PRIVATE)
    private val keyList = "capture_ids"

    init {
        if (!captureDir.exists()) captureDir.mkdirs()
        if (!thumbDir.exists()) thumbDir.mkdirs()
    }

    fun saveCapture(bitmap: Bitmap, appName: String, rideHash: String? = null): CaptureRecord? {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val fileName = "$id.enc"
        val file = File(captureDir, fileName)

        return try {
            val fullBytes = java.io.ByteArrayOutputStream().also { bos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            }.toByteArray()
            file.writeBytes(FileEncryption.encryptRaw(fullBytes, EncryptionConfig.EXPORT_PASSWORD))

            val thumb = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
            val thumbFile = File(thumbDir, fileName)
            val thumbBytes = java.io.ByteArrayOutputStream().also { bos ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 70, bos)
            }.toByteArray()
            thumbFile.writeBytes(FileEncryption.encryptRaw(thumbBytes, EncryptionConfig.EXPORT_PASSWORD))
            thumb.recycle()
            bitmap.recycle()

            val record = CaptureRecord(
                id = id,
                timestamp = timestamp,
                appName = appName,
                filePath = file.absolutePath,
                thumbnailPath = thumbFile.absolutePath,
                rideHash = rideHash
            )

            saveMetadata(record)
            record
        } catch (e: Exception) {
            L.e("CaptureManager", "Erro ao salvar captura", e)
            null
        }
    }

    fun getCaptures(limit: Int = 50): List<CaptureRecord> {
        return loadMetadata().sortedByDescending { it.timestamp }.take(limit)
    }

    fun getCapture(id: String): CaptureRecord? {
        val ids = getIds()
        if (id !in ids) return null
        return readRecord(id)
    }

    fun deleteCapture(id: String): Boolean {
        val record = getCapture(id) ?: return false
        File(record.filePath).delete()
        record.thumbnailPath?.let { File(it).delete() }
        deleteMetadata(id)
        return true
    }

    fun deleteAll() {
        val ids = getIds().toList()
        for (id in ids) deleteCapture(id)
    }

    fun cleanupOldCaptures() {
        val cutoff = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        val oldIds = getIds().filter { id ->
            val ts = prefs.getLong("${id}_ts", -1L)
            ts in 0 until cutoff
        }
        for (id in oldIds) deleteCapture(id)
    }

    fun getDecryptedBitmap(record: CaptureRecord): Bitmap? {
        return try {
            val file = File(record.filePath)
            if (!file.exists()) return null
            val decrypted = FileEncryption.decryptFile(file, EncryptionConfig.EXPORT_PASSWORD)
            BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
        } catch (e: Exception) {
            L.e("CaptureManager", "Erro ao decriptar captura", e)
            null
        }
    }

    fun getDecryptedThumbnail(record: CaptureRecord): Bitmap? {
        val thumbPath = record.thumbnailPath ?: return null
        return try {
            val file = File(thumbPath)
            if (!file.exists()) return null
            val decrypted = FileEncryption.decryptFile(file, EncryptionConfig.EXPORT_PASSWORD)
            BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
        } catch (e: Exception) {
            L.e("CaptureManager", "Erro ao decriptar thumbnail", e)
            null
        }
    }

    fun saveToGallery(id: String): Boolean {
        val record = getCapture(id) ?: return false
        return try {
            val bitmap = getDecryptedBitmap(record) ?: return false

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "capture_$id.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/ProfitDriving"
                )
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return false

            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()

            markAsSaved(id)
            true
        } catch (e: Exception) {
            L.e("CaptureManager", "Erro ao salvar na galeria", e)
            false
        }
    }

    fun shareCapture(id: String): Boolean {
        val record = getCapture(id) ?: return false
        return try {
            val bitmap = getDecryptedBitmap(record) ?: return false

            // Write decrypted temp file for sharing
            val tempDir = File(context.cacheDir, "share_tmp")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempFile = File(tempDir, "${id}_share.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Compartilhar captura"))
            true
        } catch (e: Exception) {
            L.e("CaptureManager", "Erro ao compartilhar", e)
            false
        }
    }

    // ─── Métodos auxiliares ───

    private fun saveMetadata(record: CaptureRecord) {
        val editor = prefs.edit()
        val ids = getIds().toMutableList()
        if (record.id !in ids) {
            ids.add(0, record.id)
            editor.putString(keyList, ids.joinToString(","))
        }
        editor.putLong("${record.id}_ts", record.timestamp)
            .putString("${record.id}_app", record.appName)
            .putString("${record.id}_file", record.filePath)
            .putString("${record.id}_thumb", record.thumbnailPath)
            .putString("${record.id}_hash", record.rideHash)
            .putBoolean("${record.id}_saved", record.savedToGallery)
            .apply()
    }

    private fun loadMetadata(): List<CaptureRecord> {
        return getIds().mapNotNull { readRecord(it) }
    }

    private fun readRecord(id: String): CaptureRecord? {
        val timestamp = prefs.getLong("${id}_ts", -1L)
        if (timestamp == -1L) return null
        return CaptureRecord(
            id = id,
            timestamp = timestamp,
            appName = prefs.getString("${id}_app", "") ?: "",
            filePath = prefs.getString("${id}_file", "") ?: "",
            thumbnailPath = prefs.getString("${id}_thumb", null),
            rideHash = prefs.getString("${id}_hash", null),
            savedToGallery = prefs.getBoolean("${id}_saved", false)
        )
    }

    private fun deleteMetadata(id: String) {
        val editor = prefs.edit()
        val ids = getIds().toMutableList()
        ids.remove(id)
        editor.putString(keyList, ids.joinToString(","))
            .remove("${id}_ts")
            .remove("${id}_app")
            .remove("${id}_file")
            .remove("${id}_thumb")
            .remove("${id}_hash")
            .remove("${id}_saved")
            .apply()
    }

    private fun markAsSaved(id: String) {
        prefs.edit().putBoolean("${id}_saved", true).apply()
    }

    private fun getIds(): List<String> {
        return prefs.getString(keyList, "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
}
