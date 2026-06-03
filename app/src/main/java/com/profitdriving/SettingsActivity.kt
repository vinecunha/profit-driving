package com.profitdriving

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.profitdriving.SecurePreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import androidx.appcompat.app.AlertDialog

class SettingsActivity : BaseActivity() {

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
    private lateinit var btnCentro: TextView
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
    private var debounceJob: Job? = null
    private lateinit var seekThresholdAnalisar: SeekBar
    private lateinit var tvThresholdAnalisarLabel: TextView
    private lateinit var pvKm: TextView
    private lateinit var pvHour: TextView
    private lateinit var pvMin: TextView
    private lateinit var pvRating: TextView
    private lateinit var pvDecision: TextView
    private lateinit var pvScore: TextView
    private lateinit var btnPage25: TextView
    private lateinit var btnPage50: TextView
    private lateinit var btnPage100: TextView
    private lateinit var btnPage200: TextView
    private lateinit var btnPage500: TextView
    private var selectedPageSize = 100
    private lateinit var seekCardDuration: SeekBar
    private lateinit var tvCardDurationLabel: TextView
    private var cardDurationSeconds = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupBottomNav(Screen.PARAMS)
        setupToolbar(title = "Par\u00e2metros", showBack = true, actionText = "\uD83D\uDCBE Salvar", actionListener = { saveValues() })

        prefs = SecurePreferences.get(this)

        etMinKm = findViewById(R.id.etMinKm)
        etIdealKm = findViewById(R.id.etIdealKm)
        etMinHour = findViewById(R.id.etMinHour)
        etIdealHour = findViewById(R.id.etIdealHour)
        etMinMinute = findViewById(R.id.etMinMinute)
        etIdealMinute = findViewById(R.id.etIdealMinute)
        etMinRating = findViewById(R.id.etMinRating)
        etIdealRating = findViewById(R.id.etIdealRating)
        btnSave = findViewById(R.id.btnAction)
        btnColuna = findViewById(R.id.btnColuna)
        btnLinha = findViewById(R.id.btnLinha)
        btnEsquerda = findViewById(R.id.btnEsquerda)
        btnCentro = findViewById(R.id.btnCentro)
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
        btnPage25 = findViewById(R.id.btnPage25)
        btnPage50 = findViewById(R.id.btnPage50)
        btnPage100 = findViewById(R.id.btnPage100)
        btnPage200 = findViewById(R.id.btnPage200)
        btnPage500 = findViewById(R.id.btnPage500)
        seekCardDuration = findViewById(R.id.seekCardDuration)
        tvCardDurationLabel = findViewById(R.id.tvCardDurationLabel)

        seekCardDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                cardDurationSeconds = 5 + (progress * 5)
                tvCardDurationLabel.text = "Dura\u00e7\u00e3o do card: ${cardDurationSeconds}s"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        loadValues()

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { debouncedUpdatePreview() }
        }
        etMinKm.addTextChangedListener(textWatcher)
        etIdealKm.addTextChangedListener(textWatcher)
        etMinHour.addTextChangedListener(textWatcher)
        etIdealHour.addTextChangedListener(textWatcher)
        etMinMinute.addTextChangedListener(textWatcher)
        etIdealMinute.addTextChangedListener(textWatcher)
        etMinRating.addTextChangedListener(textWatcher)
        etIdealRating.addTextChangedListener(textWatcher)

        val validationWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: return
                if (text.isNotEmpty()) {
                    val cleaned = text.trim().replace(",", ".")
                    val v = cleaned.toFloatOrNull()
                    if (v == null || v <= 0) {
                        (s as? EditText)?.error = "Valor inv\u00e1lido"
                    } else {
                        (s as? EditText)?.error = null
                    }
                }
            }
        }
        etMinKm.addTextChangedListener(validationWatcher)
        etIdealKm.addTextChangedListener(validationWatcher)
        etMinHour.addTextChangedListener(validationWatcher)
        etIdealHour.addTextChangedListener(validationWatcher)
        etMinMinute.addTextChangedListener(validationWatcher)
        etIdealMinute.addTextChangedListener(validationWatcher)
        etMinRating.addTextChangedListener(validationWatcher)
        etIdealRating.addTextChangedListener(validationWatcher)

        btnColuna.setOnClickListener { toggleLayout(isColumn = true) }
        btnLinha.setOnClickListener { toggleLayout(isColumn = false) }
        btnEsquerda.setOnClickListener { togglePosition("left") }
        btnCentro.setOnClickListener { togglePosition("center") }
        btnDireita.setOnClickListener { togglePosition("right") }
        btnShowKm.setOnClickListener { toggleMetric(btnShowKm) }
        btnShowHour.setOnClickListener { toggleMetric(btnShowHour) }
        btnShowMinute.setOnClickListener { toggleMetric(btnShowMinute) }
        btnShowRating.setOnClickListener { toggleMetric(btnShowRating) }
        btnPage25.setOnClickListener { selectPageSize(25) }
        btnPage50.setOnClickListener { selectPageSize(50) }
        btnPage100.setOnClickListener { selectPageSize(100) }
        btnPage200.setOnClickListener { selectPageSize(200) }
        btnPage500.setOnClickListener { selectPageSize(500) }

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
                    stars.text = "\u2605".repeat(w) + "\u2606".repeat(5 - w)
                    debouncedUpdatePreview()
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
                tvThresholdAceitarLabel.text = "ACEITAR se pontua\u00e7\u00e3o \u2265 ${progress + 50}%"
                if (fromUser && (progress + 20) >= (seekThresholdAceitar.progress + 50)) {
                    seekThresholdAnalisar.progress = (seekThresholdAceitar.progress + 20).coerceAtMost(29) - 20
                }
                debouncedUpdatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        seekThresholdAnalisar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvThresholdAnalisarLabel.text = "ANALISAR se pontua\u00e7\u00e3o \u2265 ${progress + 20}%"
                if (fromUser && (progress + 20) >= (seekThresholdAceitar.progress + 50)) {
                    Toast.makeText(this@SettingsActivity,
                        "O threshold de ANALISAR deve ser menor que o threshold de ACEITAR",
                        Toast.LENGTH_SHORT).show()
                }
                debouncedUpdatePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        updatePreview()

        btnDemo.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("Permiss\u00e3o necess\u00e1ria")
                    .setMessage("O card flutuante precisa da permiss\u00e3o de desenhar sobre outros aplicativos. Conceda a permiss\u00e3o nas configura\u00e7\u00f5es.")
                    .setPositiveButton("Abrir configura\u00e7\u00f5es") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                return@setOnClickListener
            }

            saveValues(showToast = false)

            val intent = Intent().apply {
                putExtra("isDemo", true)
                putExtra("value", 28.50)
                putExtra("distanceKm", 7.3)
                putExtra("timeMin", 22)
                putExtra("rating", 4.92)
                putExtra("appName", "Uber")
                putExtra("serviceType", "UberX")
                putExtra("priorityBonus", 3.00)
                putExtra("dynamicBonus", 2.50)
                putExtra("hasMultipleStops", true)
                putExtra("pickupAddress", "Av. Paulista, 1000 - Bela Vista, S\u00e3o Paulo - SP")
                putExtra("dropoffAddress", "Rua Augusta, 1500 - Cerqueira C\u00e9sar - S\u00e3o Paulo - SP, 01305-000")
            }

            FloatingCardService.start(this, intent)
            Toast.makeText(this, "Card de demonstra\u00e7\u00e3o exibido!", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.btnSuggestFromCosts).setOnClickListener { showMarginSelector() }
    }

    private fun showMarginSelector() {
        val db = DatabaseHelper(this)
        val refuels = db.getRefuels()
        val expenses = db.getAllExpenses()
        val monthlyKm = db.getMonthlyKm()

        if (refuels.isEmpty() && expenses.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Sem dados de custos")
                .setMessage("Cadastre abastecimentos e despesas na tela de Custos primeiro.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val summary = CostCalculator.calculateCostSummary(refuels, expenses, monthlyKm)
        val costPerKm = summary.totalCostPerKm

        val margin15 = CostCalculator.getRequiredPricePerKm(15, costPerKm)
        val margin50 = CostCalculator.getRequiredPricePerKm(50, costPerKm)
        val margin100 = CostCalculator.getRequiredPricePerKm(100, costPerKm)

        val margins = listOf(
            Triple("15%", margin15, "Margem conservadora"),
            Triple("50%", margin50, "Margem equilibrada"),
            Triple("100%", margin100, "Margem agressiva")
        )

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_margin_selector, null)

        val tvCostInfo = view.findViewById<TextView>(R.id.tvCostInfo)
        val radioGroupMin = view.findViewById<RadioGroup>(R.id.radioGroupMin)
        val radioGroupIdeal = view.findViewById<RadioGroup>(R.id.radioGroupIdeal)
        val tvWarning = view.findViewById<TextView>(R.id.tvWarning)

        tvCostInfo.text = "Seu custo total/km: ${formatCurrency(costPerKm)}"

        for ((index, margin) in margins.withIndex()) {
            val radioMin = RadioButton(this).apply {
                id = View.generateViewId()
                text = "${margin.first} \u2192 ${formatCurrency(margin.second)}/km"
                setPadding(8, 8, 8, 8)
                tag = margin.second
            }
            radioGroupMin.addView(radioMin)

            val radioIdeal = RadioButton(this).apply {
                id = View.generateViewId()
                text = "${margin.first} \u2192 ${formatCurrency(margin.second)}/km"
                setPadding(8, 8, 8, 8)
                tag = margin.second
            }
            radioGroupIdeal.addView(radioIdeal)
        }

        (radioGroupMin.getChildAt(0) as RadioButton).isChecked = true
        (radioGroupIdeal.getChildAt(1) as RadioButton).isChecked = true

        fun validateAndUpdateWarning() {
            val minValue = getSelectedValue(radioGroupMin)
            val idealValue = getSelectedValue(radioGroupIdeal)
            tvWarning.visibility = if (minValue >= idealValue) View.VISIBLE else View.GONE
        }

        radioGroupMin.setOnCheckedChangeListener { _, _ -> validateAndUpdateWarning() }
        radioGroupIdeal.setOnCheckedChangeListener { _, _ -> validateAndUpdateWarning() }

        AlertDialog.Builder(this)
            .setTitle("\uD83D\uDCCA Escolha seus par\u00e2metros")
            .setView(view)
            .setPositiveButton("Aplicar") { _, _ ->
                val minValue = getSelectedValue(radioGroupMin)
                val idealValue = getSelectedValue(radioGroupIdeal)

                if (minValue >= idealValue) {
                    Toast.makeText(this, "O valor M\u00cdNIMO deve ser menor que o IDEAL", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                etMinKm.setText(formatCurrencySimple(minValue))
                etIdealKm.setText(formatCurrencySimple(idealValue))

                val minHour = minValue * 30
                val idealHour = idealValue * 30
                val minMinute = minHour / 60
                val idealMinute = idealHour / 60

                etMinHour.setText(formatCurrencySimple(minHour))
                etIdealHour.setText(formatCurrencySimple(idealHour))
                etMinMinute.setText(formatCurrencySimple(minMinute))
                etIdealMinute.setText(formatCurrencySimple(idealMinute))

                debouncedUpdatePreview()
                Toast.makeText(this, "Par\u00e2metros atualizados! Revise e salve se desejar.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun getSelectedValue(radioGroup: RadioGroup): Double {
        for (i in 0 until radioGroup.childCount) {
            val radio = radioGroup.getChildAt(i) as RadioButton
            if (radio.isChecked) {
                return radio.tag as Double
            }
        }
        return 0.0
    }

    private fun formatCurrency(value: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
    }

    private fun formatCurrencySimple(value: Double): String {
        return String.format("%.2f", value).replace(".", ",")
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

    private fun togglePosition(position: String) {
        val isLeft = position == "left"
        val isCenter = position == "center"
        val isRight = position == "right"
        btnEsquerda.isSelected = isLeft
        btnCentro.isSelected = isCenter
        btnDireita.isSelected = isRight
        btnEsquerda.setBackgroundResource(if (isLeft) R.drawable.pill_selected else R.drawable.pill_unselected)
        btnCentro.setBackgroundResource(if (isCenter) R.drawable.pill_selected else R.drawable.pill_unselected)
        btnDireita.setBackgroundResource(if (isRight) R.drawable.pill_selected else R.drawable.pill_unselected)
        btnEsquerda.setTextColor(if (isLeft) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        btnCentro.setTextColor(if (isCenter) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        btnDireita.setTextColor(if (isRight) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
    }

    private fun selectPageSize(size: Int) {
        selectedPageSize = size
        val buttons = listOf(
            btnPage25 to 25, btnPage50 to 50, btnPage100 to 100,
            btnPage200 to 200, btnPage500 to 500
        )
        for ((btn, value) in buttons) {
            val selected = value == size
            btn.isSelected = selected
            btn.setBackgroundResource(if (selected) R.drawable.pill_selected else R.drawable.pill_unselected)
            btn.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        }
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

        val position = prefs.getString(KEY_CARD_POSITION, DEFAULT_CARD_POSITION) ?: DEFAULT_CARD_POSITION
        togglePosition(position)

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
            stars.text = "\u2605".repeat(w) + "\u2606".repeat(5 - w)
        }
        loadWeight(seekWeightKm, tvWeightKmStars, KEY_WEIGHT_KM, 5)
        loadWeight(seekWeightHour, tvWeightHourStars, KEY_WEIGHT_HOUR, 4)
        loadWeight(seekWeightMin, tvWeightMinStars, KEY_WEIGHT_MIN, 3)
        loadWeight(seekWeightRating, tvWeightRatingStars, KEY_WEIGHT_RATING, 2)

        val ta = prefs.getInt(KEY_THRESHOLD_ACEITAR, 80)
        seekThresholdAceitar.progress = ta - 50
        tvThresholdAceitarLabel.text = "ACEITAR se pontua\u00e7\u00e3o \u2265 ${ta}%"
        val an = prefs.getInt(KEY_THRESHOLD_ANALISAR, 50)
        seekThresholdAnalisar.progress = an - 20
        tvThresholdAnalisarLabel.text = "ANALISAR se pontua\u00e7\u00e3o \u2265 ${an}%"

        val pageSize = prefs.getInt(KEY_PAGE_SIZE, 100)
        selectPageSize(pageSize)

        cardDurationSeconds = prefs.getInt(KEY_CARD_DURATION, 30)
        seekCardDuration.progress = (cardDurationSeconds - 5) / 5
        tvCardDurationLabel.text = "Dura\u00e7\u00e3o do card: ${cardDurationSeconds}s"
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

        val minKm = parseBr(etMinKm.text.toString(), 0.5f, 50f) ?: run {
            etMinKm.error = "Digite um valor entre 0,50 e 50,00"; return
        }
        val idealKm = parseBr(etIdealKm.text.toString(), 0.5f, 50f) ?: run {
            etIdealKm.error = "Digite um valor entre 0,50 e 50,00"; return
        }
        val minHour = parseBr(etMinHour.text.toString(), 5f, 500f) ?: run {
            etMinHour.error = "Digite um valor entre 5,00 e 500,00"; return
        }
        val idealHour = parseBr(etIdealHour.text.toString(), 5f, 500f) ?: run {
            etIdealHour.error = "Digite um valor entre 5,00 e 500,00"; return
        }
        val minMinute = parseBr(etMinMinute.text.toString(), 0.1f, 10f) ?: run {
            etMinMinute.error = "Valor inv\u00e1lido"; return
        }
        val idealMinute = parseBr(etIdealMinute.text.toString(), 0.1f, 10f) ?: run {
            etIdealMinute.error = "Valor inv\u00e1lido"; return
        }
        val minRating = parseBr(etMinRating.text.toString(), 1f, 5f) ?: run {
            etMinRating.error = "Digite um valor entre 1,00 e 5,00"; return
        }
        val idealRating = parseBr(etIdealRating.text.toString(), 1f, 5f) ?: run {
            etIdealRating.error = "Digite um valor entre 1,00 e 5,00"; return
        }

        prefs.edit().apply {
            putFloat(KEY_MIN_KM, minKm)
            putFloat(KEY_IDEAL_KM, idealKm)
            putFloat(KEY_MIN_HOUR, minHour)
            putFloat(KEY_IDEAL_HOUR, idealHour)
            putFloat(KEY_MIN_MINUTE, minMinute)
            putFloat(KEY_IDEAL_MINUTE, idealMinute)
            putFloat(KEY_MIN_RATING, minRating)
            putFloat(KEY_IDEAL_RATING, idealRating)
            putString(KEY_CARD_LAYOUT, if (btnColuna.isSelected) "column" else "row")
            val position = when {
                btnCentro.isSelected -> "center"
                btnDireita.isSelected -> "right"
                else -> "left"
            }
            putString(KEY_CARD_POSITION, position)
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
            putInt(KEY_PAGE_SIZE, selectedPageSize)
            putInt(KEY_CARD_DURATION, cardDurationSeconds)
            apply()
        }

        if (showToast) {
            btnSave.text = "\u2713 Salvo!"
            btnSave.isEnabled = false
            lifecycleScope.launch {
                delay(800)
                if (isFinishing) return@launch
                finish()
            }
        }
    }

    private fun debouncedUpdatePreview() {
        debounceJob?.cancel()
        debounceJob = lifecycleScope.launch {
            delay(300)
            if (isFinishing) return@launch
            updatePreview()
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

        val kmState = result.params[0].state
        val hourState = result.params[1].state
        val minState = result.params[2].state
        val ratingState = result.params[3].state

        pvKm.text = "R$/km: 3,20 \u2014 ${semaphoreEmoji(kmState)} ${stateLabel(kmState)}"
        pvKm.setTextColor(DecisionEngine.stateColor(kmState))
        pvHour.text = "R$/h: 45,00 \u2014 ${semaphoreEmoji(hourState)} ${stateLabel(hourState)}"
        pvHour.setTextColor(DecisionEngine.stateColor(hourState))
        pvMin.text = "R$/min: 0,75 \u2014 ${semaphoreEmoji(minState)} ${stateLabel(minState)}"
        pvMin.setTextColor(DecisionEngine.stateColor(minState))
        pvRating.text = "Nota: 4,70 \u2014 ${semaphoreEmoji(ratingState)} ${stateLabel(ratingState)}"
        pvRating.setTextColor(DecisionEngine.stateColor(ratingState))

        pvDecision.text = "${decisionEmoji(result.decision)} ${DecisionEngine.decisionText(result.decision)}"
        pvDecision.setTextColor(DecisionEngine.decisionColor(result.decision))
        pvScore.text = "Pontua\u00e7\u00e3o: ${"%.0f".format(result.scorePercent)}% (${result.totalPoints.toInt()}/${result.maxPoints.toInt()} pts)"
    }

    private fun semaphoreEmoji(state: DecisionEngine.ParamState): String = when (state) {
        DecisionEngine.ParamState.OK -> "\uD83D\uDFE2"
        DecisionEngine.ParamState.ANALISAR -> "\uD83D\uDFE0"
        else -> "\uD83D\uDD34"
    }

    private fun stateLabel(state: DecisionEngine.ParamState): String = when (state) {
        DecisionEngine.ParamState.OK -> "BOM"
        DecisionEngine.ParamState.ANALISAR -> "M\u00c9DIO"
        else -> "RUIM"
    }

    private fun decisionEmoji(decision: DecisionEngine.Decision): String = when (decision) {
        DecisionEngine.Decision.ACEITAR -> "\u2705"
        DecisionEngine.Decision.ANALISAR -> "\u26A0\uFE0F"
        else -> "\u274C"
    }

    private fun parseBr(value: String, min: Float = 0f, max: Float = Float.MAX_VALUE): Float? {
        if (value.isBlank()) return null
        val cleaned = value.trim().replace(",", ".")
        val parsed = cleaned.toFloatOrNull()
        return if (parsed != null && parsed >= min && parsed <= max) parsed else null
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
        const val KEY_CARD_POSITION = "card_position"
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
        const val KEY_PAGE_SIZE = "page_size"
        const val KEY_CARD_DURATION = "card_duration"

        const val DEFAULT_CARD_LAYOUT = "column"
        const val DEFAULT_CARD_POSITION = "left"
        const val DEFAULT_CARD_Y_PERCENT = 30
    }
}
