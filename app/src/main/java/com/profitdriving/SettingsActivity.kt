package com.profitdriving

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var etMinKm: EditText
    private lateinit var etIdealKm: EditText
    private lateinit var etMinHour: EditText
    private lateinit var etIdealHour: EditText
    private lateinit var etMinRating: EditText
    private lateinit var etIdealRating: EditText
    private lateinit var btnSave: TextView
    private lateinit var btnColuna: TextView
    private lateinit var btnLinha: TextView
    private lateinit var btnEsquerda: TextView
    private lateinit var btnDireita: TextView
    private lateinit var seekY: SeekBar
    private lateinit var tvYLabel: TextView
    private lateinit var btnDemo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        etMinKm = findViewById(R.id.etMinKm)
        etIdealKm = findViewById(R.id.etIdealKm)
        etMinHour = findViewById(R.id.etMinHour)
        etIdealHour = findViewById(R.id.etIdealHour)
        etMinRating = findViewById(R.id.etMinRating)
        etIdealRating = findViewById(R.id.etIdealRating)
        btnSave = findViewById(R.id.btnSave)
        btnColuna = findViewById(R.id.btnColuna)
        btnLinha = findViewById(R.id.btnLinha)
        btnEsquerda = findViewById(R.id.btnEsquerda)
        btnDireita = findViewById(R.id.btnDireita)
        seekY = findViewById(R.id.seekY)
        tvYLabel = findViewById(R.id.tvYLabel)
        btnDemo = findViewById(R.id.btnDemo)

        loadValues()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        btnColuna.setOnClickListener { toggleLayout(isColumn = true) }
        btnLinha.setOnClickListener { toggleLayout(isColumn = false) }
        btnEsquerda.setOnClickListener { toggleSide(isLeft = true) }
        btnDireita.setOnClickListener { toggleSide(isLeft = false) }

        seekY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvYLabel.text = "Altura na tela: $progress%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnDemo.setOnClickListener {
            saveValues(showToast = false)
            val intent = Intent().apply {
                putExtra("isDemo", true)
                putExtra("value", 18.50)
                putExtra("distanceKm", 3.7)
                putExtra("timeMin", 15)
                putExtra("rating", 4.87)
                putExtra("appName", "Uber")
                putExtra("serviceType", "UberX")
                putExtra("bonusAmount", -1.0)
            }
            FloatingCardService.start(this, intent)
            Toast.makeText(this, "Card de exemplo exibido!", Toast.LENGTH_SHORT).show()
        }

        btnSave.setOnClickListener { saveValues() }
    }

    private fun toggleLayout(isColumn: Boolean) {
        btnColuna.isSelected = isColumn
        btnLinha.isSelected = !isColumn
        btnColuna.setBackgroundResource(
            if (isColumn) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btnLinha.setBackgroundResource(
            if (!isColumn) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btnColuna.setTextColor(if (isColumn) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        btnLinha.setTextColor(if (!isColumn) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
    }

    private fun toggleSide(isLeft: Boolean) {
        btnEsquerda.isSelected = isLeft
        btnDireita.isSelected = !isLeft
        btnEsquerda.setBackgroundResource(
            if (isLeft) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btnDireita.setBackgroundResource(
            if (!isLeft) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btnEsquerda.setTextColor(if (isLeft) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        btnDireita.setTextColor(if (!isLeft) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
    }

    private fun loadValues() {
        fun loadFloat(key: String, def: Float): String {
            val v = prefs.getFloat(key, def)
            return if (v == 0f) "" else "%.2f".format(v).replace(".", ",")
        }
        etMinKm.setText(loadFloat(KEY_MIN_KM, 2.5f))
        etIdealKm.setText(loadFloat(KEY_IDEAL_KM, 4.0f))
        etMinHour.setText(loadFloat(KEY_MIN_HOUR, 30f))
        etIdealHour.setText(loadFloat(KEY_IDEAL_HOUR, 60f))
        etMinRating.setText(loadFloat(KEY_MIN_RATING, 4.5f))
        etIdealRating.setText(loadFloat(KEY_IDEAL_RATING, 4.9f))

        val layout = prefs.getString(KEY_CARD_LAYOUT, DEFAULT_CARD_LAYOUT)
        toggleLayout(isColumn = layout == "column")

        val side = prefs.getString(KEY_CARD_SIDE, DEFAULT_CARD_SIDE)
        toggleSide(isLeft = side == "left")

        val yPct = prefs.getInt(KEY_CARD_Y_PERCENT, DEFAULT_CARD_Y_PERCENT)
        seekY.progress = yPct
        tvYLabel.text = "Altura na tela: $yPct%"
    }

    private fun saveValues(showToast: Boolean = true) {
        val minKm = parseBr(etMinKm.text.toString())
        val idealKm = parseBr(etIdealKm.text.toString())
        val minHour = parseBr(etMinHour.text.toString())
        val idealHour = parseBr(etIdealHour.text.toString())
        val minRating = parseBr(etMinRating.text.toString())
        val idealRating = parseBr(etIdealRating.text.toString())

        prefs.edit().apply {
            putFloat(KEY_MIN_KM, minKm ?: 2.5f)
            putFloat(KEY_IDEAL_KM, idealKm ?: 4.0f)
            putFloat(KEY_MIN_HOUR, minHour ?: 30f)
            putFloat(KEY_IDEAL_HOUR, idealHour ?: 60f)
            putFloat(KEY_MIN_RATING, minRating ?: 4.5f)
            putFloat(KEY_IDEAL_RATING, idealRating ?: 4.9f)
            putString(KEY_CARD_LAYOUT, if (btnColuna.isSelected) "column" else "row")
            putString(KEY_CARD_SIDE, if (btnEsquerda.isSelected) "left" else "right")
            putInt(KEY_CARD_Y_PERCENT, seekY.progress)
            apply()
        }

        if (showToast) {
            Toast.makeText(this, "Parâmetros salvos!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun parseBr(value: String): Float? {
        val cleaned = value.trim().replace(",", ".")
        return cleaned.toFloatOrNull()
    }

    enum class ParameterStatus { RECUSAR, ANALISAR, ACEITAR }

    companion object {
        const val PREF_NAME = "profit_driving_prefs"
        const val KEY_MIN_KM = "min_km"
        const val KEY_IDEAL_KM = "ideal_km"
        const val KEY_MIN_HOUR = "min_hour"
        const val KEY_IDEAL_HOUR = "ideal_hour"
        const val KEY_MIN_RATING = "min_rating"
        const val KEY_IDEAL_RATING = "ideal_rating"

        const val KEY_LAST_VALUE = "last_value"
        const val KEY_LAST_DISTANCE = "last_distance"
        const val KEY_LAST_TIME = "last_time"
        const val KEY_LAST_RATING = "last_rating"
        const val KEY_LAST_APP = "last_app"
        const val KEY_LAST_TIMESTAMP = "last_timestamp"

        const val KEY_CARD_LAYOUT = "card_layout"
        const val KEY_CARD_SIDE = "card_side"
        const val KEY_CARD_Y_PERCENT = "card_y_percent"

        const val DEFAULT_CARD_LAYOUT = "column"
        const val DEFAULT_CARD_SIDE = "left"
        const val DEFAULT_CARD_Y_PERCENT = 30

        fun evaluateParameter(value: Double?, min: Float, ideal: Float): ParameterStatus {
            if (value == null) return ParameterStatus.ACEITAR
            val v = value.toFloat()
            return when {
                v < min -> ParameterStatus.RECUSAR
                v < ideal -> ParameterStatus.ANALISAR
                else -> ParameterStatus.ACEITAR
            }
        }

        fun getOverallStatus(
            kmValue: Double?,
            hourValue: Double?,
            ratingValue: Double?,
            prefs: SharedPreferences
        ): ParameterStatus {
            val minKm = prefs.getFloat(KEY_MIN_KM, 2.5f)
            val idealKm = prefs.getFloat(KEY_IDEAL_KM, 4.0f)
            val minHour = prefs.getFloat(KEY_MIN_HOUR, 30f)
            val idealHour = prefs.getFloat(KEY_IDEAL_HOUR, 60f)
            val minRating = prefs.getFloat(KEY_MIN_RATING, 4.5f)
            val idealRating = prefs.getFloat(KEY_IDEAL_RATING, 4.9f)

            val statuses = listOf(
                evaluateParameter(kmValue, minKm, idealKm),
                evaluateParameter(hourValue, minHour, idealHour),
                evaluateParameter(ratingValue, minRating, idealRating)
            )

            return when {
                statuses.contains(ParameterStatus.RECUSAR) -> ParameterStatus.RECUSAR
                statuses.contains(ParameterStatus.ANALISAR) -> ParameterStatus.ANALISAR
                else -> ParameterStatus.ACEITAR
            }
        }

        @JvmStatic
        fun getStatusColor(status: ParameterStatus): Int {
            return when (status) {
                ParameterStatus.RECUSAR -> 0xFFF87171.toInt()
                ParameterStatus.ANALISAR -> 0xFFFB923C.toInt()
                ParameterStatus.ACEITAR -> 0xFF4ADE80.toInt()
            }
        }

        @JvmStatic
        fun getDecisionText(status: ParameterStatus): String {
            return when (status) {
                ParameterStatus.RECUSAR -> "❌ RECUSAR"
                ParameterStatus.ANALISAR -> "⚠️ ANALISAR"
                ParameterStatus.ACEITAR -> "✅ ACEITAR"
            }
        }

        @JvmStatic
        fun getDecisionBgColor(status: ParameterStatus): Int {
            return when (status) {
                ParameterStatus.RECUSAR -> 0xFFDC2626.toInt()
                ParameterStatus.ANALISAR -> 0xFFF97316.toInt()
                ParameterStatus.ACEITAR -> 0xFF00A86B.toInt()
            }
        }
    }
}
