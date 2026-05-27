package com.profitdriving

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var etMinMinute: EditText
    private lateinit var etIdealMinute: EditText
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
    private lateinit var btnShowKm: TextView
    private lateinit var btnShowHour: TextView
    private lateinit var btnShowMinute: TextView
    private lateinit var btnShowRating: TextView
    private lateinit var seekWeightKm: SeekBar
    private lateinit var tvWeightKmStars: TextView
    private lateinit var seekWeightHour: SeekBar
    private lateinit var tvWeightHourStars: TextView
    private lateinit var seekWeightMin: SeekBar
    private lateinit var tvWeightMinStars: TextView
    private lateinit var seekWeightRating: SeekBar
    private lateinit var tvWeightRatingStars: TextView
    private lateinit var seekThresholdAceitar: SeekBar
    private lateinit var tvThresholdAceitarLabel: TextView
    private lateinit var seekThresholdAnalisar: SeekBar
    private lateinit var tvThresholdAnalisarLabel: TextView
    private lateinit var pvKm: TextView
    private lateinit var pvHour: TextView
    private lateinit var pvMin: TextView
    private lateinit var pvRating: TextView
    private lateinit var pvDecision: TextView
    private lateinit var pvScore: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        etMinKm = findViewById(R.id.etMinKm)
        etIdealKm = findViewById(R.id.etIdealKm)
        etMinHour = findViewById(R.id.etMinHour)
        etIdealHour = findViewById(R.id.etIdealHour)
        etMinMinute = findViewById(R.id.etMinMinute)
        etIdealMinute = findViewById(R.id.etIdealMinute)
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
        btnShowKm = findViewById(R.id.btnShowKm)
        btnShowHour = findViewById(R.id.btnShowHour)
        btnShowMinute = findViewById(R.id.btnShowMinute)
        btnShowRating = findViewById(R.id.btnShowRating)
        seekWeightKm = findViewById(R.id.seekWeightKm)
        tvWeightKmStars = findViewById(R.id.tvWeightKmStars)
        seekWeightHour = findViewById(R.id.seekWeightHour)
        tvWeightHourStars = findViewById(R.id.tvWeightHourStars)
        seekWeightMin = findViewById(R.id.seekWeightMin)
        tvWeightMinStars = findViewById(R.id.tvWeightMinStars)
        seekWeightRating = findViewById(R.id.seekWeightRating)
        tvWeightRatingStars = findViewById(R.id.tvWeightRatingStars)
        seekThresholdAceitar = findViewById(R.id.seekThresholdAceitar)
        tvThresholdAceitarLabel = findViewById(R.id.tvThresholdAceitarLabel)
        seekThresholdAnalisar = findViewById(R.id.seekThresholdAnalisar)
        tvThresholdAnalisarLabel = findViewById(R.id.tvThresholdAnalisarLabel)
        pvKm = findViewById(R.id.pvKm)
        pvHour = findViewById(R.id.pvHour)
        pvMin = findViewById(R.id.pvMin)
        pvRating = findViewById(R.id.pvRating)
        pvDecision = findViewById(R.id.pvDecision)
        pvScore = findViewById(R.id.pvScore)

        loadValues()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        btnColuna.setOnClickListener { toggleLayout(isColumn = true) }
        btnLinha.setOnClickListener { toggleLayout(isColumn = false) }
        btnEsquerda.setOnClickListener { toggleSide(isLeft = true) }
        btnDireita.setOnClickListener { toggleSide(isLeft = false) }
        btnShowKm.setOnClickListener { toggleMetric(btnShowKm) }
        btnShowHour.setOnClickListener { toggleMetric(btnShowHour) }
        btnShowMinute.setOnClickListener { toggleMetric(btnShowMinute) }
        btnShowRating.setOnClickListener { toggleMetric(btnShowRating) }

        seekY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvYLabel.text = "Altura na tela: $progress%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        fun weightListener(seek: SeekBar, stars: TextView) {
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val w = progress + 1
                    stars.text = "★".repeat(w) + "☆".repeat(5 - w)
                    updatePreview()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        weightListener(seekWeightKm, tvWeightKmStars)
        weightListener(seekWeightHour, tvWeightHourStars)
        weightListener(seekWeightMin, tvWeightMinStars)
        weightListener(seekWeightRating, tvWeightRatingStars)

        seekThresholdAceitar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvThresholdAceitarLabel.text = "ACEITAR se pontuação ≥ ${progress + 50}%"
                if (fromUser && (progress + 20) >= (seekThresholdAceitar.progress + 50)) {
                    seekThresholdAnalisar.progress = (seekThresholdAceitar.progress + 20).coerceAtMost(29) - 20
                }
                updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        seekThresholdAnalisar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvThresholdAnalisarLabel.text = "ANALISAR se pontuação ≥ ${progress + 20}%"
                if (fromUser && (progress + 20) >= (seekThresholdAceitar.progress + 50)) {
                    Toast.makeText(this@SettingsActivity,
                        "O threshold de ANALISAR deve ser menor que o threshold de ACEITAR",
                        Toast.LENGTH_SHORT).show()
                }
                updatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        updatePreview()

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

    private fun toggleMetric(btn: TextView) {
        btn.isSelected = !btn.isSelected
        btn.setBackgroundResource(
            if (btn.isSelected) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btn.setTextColor(if (btn.isSelected) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
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
        etMinMinute.setText(loadFloat(KEY_MIN_MINUTE, 0.5f))
        etIdealMinute.setText(loadFloat(KEY_IDEAL_MINUTE, 1.0f))
        etMinRating.setText(loadFloat(KEY_MIN_RATING, 4.5f))
        etIdealRating.setText(loadFloat(KEY_IDEAL_RATING, 4.9f))

        val layout = prefs.getString(KEY_CARD_LAYOUT, DEFAULT_CARD_LAYOUT)
        toggleLayout(isColumn = layout == "column")

        val side = prefs.getString(KEY_CARD_SIDE, DEFAULT_CARD_SIDE)
        toggleSide(isLeft = side == "left")

        val yPct = prefs.getInt(KEY_CARD_Y_PERCENT, DEFAULT_CARD_Y_PERCENT)
        seekY.progress = yPct
        tvYLabel.text = "Altura na tela: $yPct%"

        val showKm = prefs.getBoolean(KEY_SHOW_KM, true)
        val showHour = prefs.getBoolean(KEY_SHOW_HOUR, true)
        val showMinute = prefs.getBoolean(KEY_SHOW_MINUTE, true)
        val showRating = prefs.getBoolean(KEY_SHOW_RATING, true)
        fun applyMetric(btn: TextView, visible: Boolean) {
            btn.isSelected = visible
            btn.setBackgroundResource(if (visible) R.drawable.pill_selected else R.drawable.pill_unselected)
            btn.setTextColor(if (visible) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        }
        applyMetric(btnShowKm, showKm)
        applyMetric(btnShowHour, showHour)
        applyMetric(btnShowMinute, showMinute)
        applyMetric(btnShowRating, showRating)

        fun loadWeight(seek: SeekBar, stars: TextView, key: String, def: Int) {
            val w = prefs.getInt(key, def)
            seek.progress = w - 1
            stars.text = "★".repeat(w) + "☆".repeat(5 - w)
        }
        loadWeight(seekWeightKm, tvWeightKmStars, KEY_WEIGHT_KM, 5)
        loadWeight(seekWeightHour, tvWeightHourStars, KEY_WEIGHT_HOUR, 4)
        loadWeight(seekWeightMin, tvWeightMinStars, KEY_WEIGHT_MIN, 3)
        loadWeight(seekWeightRating, tvWeightRatingStars, KEY_WEIGHT_RATING, 2)

        val ta = prefs.getInt(KEY_THRESHOLD_ACEITAR, 80)
        seekThresholdAceitar.progress = ta - 50
        tvThresholdAceitarLabel.text = "ACEITAR se pontuação ≥ ${ta}%"
        val an = prefs.getInt(KEY_THRESHOLD_ANALISAR, 50)
        seekThresholdAnalisar.progress = an - 20
        tvThresholdAnalisarLabel.text = "ANALISAR se pontuação ≥ ${an}%"
    }

    private fun saveValues(showToast: Boolean = true) {
        val thresholdAceitar = seekThresholdAceitar.progress + 50
        val thresholdAnalisar = seekThresholdAnalisar.progress + 20
        if (thresholdAnalisar >= thresholdAceitar) {
            Toast.makeText(this,
                "O threshold de ANALISAR deve ser menor que o threshold de ACEITAR",
                Toast.LENGTH_LONG).show()
            return
        }

        val minKm = parseBr(etMinKm.text.toString())
        val idealKm = parseBr(etIdealKm.text.toString())
        val minHour = parseBr(etMinHour.text.toString())
        val idealHour = parseBr(etIdealHour.text.toString())
        val minMinute = parseBr(etMinMinute.text.toString())
        val idealMinute = parseBr(etIdealMinute.text.toString())
        val minRating = parseBr(etMinRating.text.toString())
        val idealRating = parseBr(etIdealRating.text.toString())

        if (minKm != null && (minKm < 0.5f || minKm > 50f)) {
            etMinKm.error = "Digite um valor entre 0,50 e 50,00"
            return
        }
        if (minHour != null && (minHour < 5f || minHour > 500f)) {
            etMinHour.error = "Digite um valor entre 5,00 e 500,00"
            return
        }
        if (minRating != null && (minRating < 1f || minRating > 5f)) {
            etMinRating.error = "Digite um valor entre 1,00 e 5,00"
            return
        }

        prefs.edit().apply {
            putFloat(KEY_MIN_KM, minKm ?: 2.5f)
            putFloat(KEY_IDEAL_KM, idealKm ?: 4.0f)
            putFloat(KEY_MIN_HOUR, minHour ?: 30f)
            putFloat(KEY_IDEAL_HOUR, idealHour ?: 60f)
            putFloat(KEY_MIN_MINUTE, minMinute ?: 0.5f)
            putFloat(KEY_IDEAL_MINUTE, idealMinute ?: 1.0f)
            putFloat(KEY_MIN_RATING, minRating ?: 4.85f)
            putFloat(KEY_IDEAL_RATING, idealRating ?: 4.93f)
            putString(KEY_CARD_LAYOUT, if (btnColuna.isSelected) "column" else "row")
            putString(KEY_CARD_SIDE, if (btnEsquerda.isSelected) "left" else "right")
            putInt(KEY_CARD_Y_PERCENT, seekY.progress)
            putBoolean(KEY_SHOW_KM, btnShowKm.isSelected)
            putBoolean(KEY_SHOW_HOUR, btnShowHour.isSelected)
            putBoolean(KEY_SHOW_MINUTE, btnShowMinute.isSelected)
            putBoolean(KEY_SHOW_RATING, btnShowRating.isSelected)
            putInt(KEY_WEIGHT_KM, seekWeightKm.progress + 1)
            putInt(KEY_WEIGHT_HOUR, seekWeightHour.progress + 1)
            putInt(KEY_WEIGHT_MIN, seekWeightMin.progress + 1)
            putInt(KEY_WEIGHT_RATING, seekWeightRating.progress + 1)
            putInt(KEY_THRESHOLD_ACEITAR, seekThresholdAceitar.progress + 50)
            putInt(KEY_THRESHOLD_ANALISAR, seekThresholdAnalisar.progress + 20)
            apply()
        }

        if (showToast) {
            btnSave.text = "✓ Salvo!"
            btnSave.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 800)
        }
    }

    private fun updatePreview() {
        prefs.edit().apply {
            putFloat(KEY_MIN_KM, parseBr(etMinKm.text.toString()) ?: 2.5f)
            putFloat(KEY_IDEAL_KM, parseBr(etIdealKm.text.toString()) ?: 4.0f)
            putFloat(KEY_MIN_HOUR, parseBr(etMinHour.text.toString()) ?: 30f)
            putFloat(KEY_IDEAL_HOUR, parseBr(etIdealHour.text.toString()) ?: 60f)
            putFloat(KEY_MIN_MINUTE, parseBr(etMinMinute.text.toString()) ?: 0.5f)
            putFloat(KEY_IDEAL_MINUTE, parseBr(etIdealMinute.text.toString()) ?: 1.0f)
            putFloat(KEY_MIN_RATING, parseBr(etMinRating.text.toString()) ?: 4.85f)
            putFloat(KEY_IDEAL_RATING, parseBr(etIdealRating.text.toString()) ?: 4.93f)
            putInt(KEY_WEIGHT_KM, seekWeightKm.progress + 1)
            putInt(KEY_WEIGHT_HOUR, seekWeightHour.progress + 1)
            putInt(KEY_WEIGHT_MIN, seekWeightMin.progress + 1)
            putInt(KEY_WEIGHT_RATING, seekWeightRating.progress + 1)
            putInt(KEY_THRESHOLD_ACEITAR, seekThresholdAceitar.progress + 50)
            putInt(KEY_THRESHOLD_ANALISAR, seekThresholdAnalisar.progress + 20)
            apply()
        }

        val result = DecisionEngine.evaluate(
            kmValue     = 3.20,
            hourValue   = 45.00,
            minValue    = 0.75,
            ratingValue = 4.70,
            prefs       = prefs
        )

        pvKm.text = "R$/km 3,20"
        pvKm.setTextColor(DecisionEngine.stateColor(result.params[0].state))
        pvHour.text = "R$/h 45,00"
        pvHour.setTextColor(DecisionEngine.stateColor(result.params[1].state))
        pvMin.text = "R$/min 0,75"
        pvMin.setTextColor(DecisionEngine.stateColor(result.params[2].state))
        pvRating.text = "Nota 4,70"
        pvRating.setTextColor(DecisionEngine.stateColor(result.params[3].state))

        pvDecision.text = DecisionEngine.decisionText(result.decision)
        pvDecision.setTextColor(DecisionEngine.decisionColor(result.decision))
        pvScore.text = "Pontuação: ${"%.0f".format(result.scorePercent)}% (${result.totalPoints.toInt()}/${result.maxPoints.toInt()} pts)"
    }

    private fun parseBr(value: String): Float? {
        val cleaned = value.trim().replace(",", ".")
        return cleaned.toFloatOrNull()
    }

    companion object {
        const val PREF_NAME = "profit_driving_prefs"
        const val KEY_MIN_KM = "min_km"
        const val KEY_IDEAL_KM = "ideal_km"
        const val KEY_MIN_HOUR = "min_hour"
        const val KEY_IDEAL_HOUR = "ideal_hour"
        const val KEY_MIN_RATING = "min_rating"
        const val KEY_IDEAL_RATING = "ideal_rating"
        const val KEY_MIN_MINUTE = "min_minute"
        const val KEY_IDEAL_MINUTE = "ideal_minute"

        const val KEY_LAST_VALUE = "last_value"
        const val KEY_LAST_DISTANCE = "last_distance"
        const val KEY_LAST_TIME = "last_time"
        const val KEY_LAST_RATING = "last_rating"
        const val KEY_LAST_APP = "last_app"
        const val KEY_LAST_TIMESTAMP = "last_timestamp"

        const val KEY_CARD_LAYOUT = "card_layout"
        const val KEY_CARD_SIDE = "card_side"
        const val KEY_CARD_Y_PERCENT = "card_y_percent"
        const val KEY_SHOW_KM = "show_km"
        const val KEY_SHOW_HOUR = "show_hour"
        const val KEY_SHOW_MINUTE = "show_minute"
        const val KEY_SHOW_RATING = "show_rating"
        const val KEY_WEIGHT_KM     = "weight_km"
        const val KEY_WEIGHT_HOUR   = "weight_hour"
        const val KEY_WEIGHT_MIN    = "weight_min"
        const val KEY_WEIGHT_RATING = "weight_rating"

        const val KEY_THRESHOLD_ACEITAR  = "threshold_aceitar"
        const val KEY_THRESHOLD_ANALISAR = "threshold_analisar"

        const val DEFAULT_CARD_LAYOUT = "column"
        const val DEFAULT_CARD_SIDE = "left"
        const val DEFAULT_CARD_Y_PERCENT = 30
    }
}
