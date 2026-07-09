package com.profitdriving

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureViewerActivity : BaseActivity() {

    private lateinit var captureManager: CaptureManager
    private var captureId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture_viewer)
        setupToolbar(title = "Visualizar Captura", showBack = true)

        captureManager = CaptureManager(this)
        captureId = intent.getStringExtra("capture_id")

        val ivCaptureFull = findViewById<ImageView>(R.id.ivCaptureFull)
        val btnSaveGallery = findViewById<Button>(R.id.btnSaveGallery)
        val btnShareCapture = findViewById<Button>(R.id.btnShareCapture)
        val tvCaptureInfo = findViewById<TextView>(R.id.tvCaptureInfo)

        val record = captureId?.let { captureManager.getCapture(it) }
        if (record == null) {
            tvCaptureInfo.text = "Captura n\u00E3o encontrada"
            return
        }

        val bitmap = captureManager.getDecryptedBitmap(record)
        if (bitmap != null) {
            ivCaptureFull.setImageBitmap(bitmap)
        } else {
            ivCaptureFull.setImageResource(R.drawable.ic_photo_library)
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val info = StringBuilder()
        info.append(record.appName).append(" \u2022 ")
        info.append(dateFormat.format(Date(record.timestamp)))
        if (record.savedToGallery) {
            info.append(" \u2022 Salvo na galeria")
        }
        tvCaptureInfo.text = info.toString()

        btnSaveGallery.setOnClickListener {
            if (captureManager.saveToGallery(record.id)) {
                Toast.makeText(this, "Imagem salva na galeria", Toast.LENGTH_SHORT).show()
                btnSaveGallery.isEnabled = false
                btnSaveGallery.text = "Salvo"
            } else {
                Toast.makeText(this, "Erro ao salvar", Toast.LENGTH_SHORT).show()
            }
        }

        btnShareCapture.setOnClickListener {
            captureManager.shareCapture(record.id)
        }
    }
}
