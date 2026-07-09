package com.profitdriving

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.profitdriving.SecurePreferences
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.AdapterView
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.parser.App99CardParser
import com.profitdriving.parser.DiscoveryCardParser
import com.profitdriving.parser.ExclusiveCardParser
import com.profitdriving.parser.RadarCardParser
import com.profitdriving.parser.ReservationDetailParser
import com.profitdriving.parser.RideDataParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import com.profitdriving.FormatUtils
import com.profitdriving.models.WorkProfile
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate

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
    private lateinit var tvRankKm: TextView
    private lateinit var btnRankKmUp: TextView
    private lateinit var btnRankKmDown: TextView
    private lateinit var tvRankHour: TextView
    private lateinit var btnRankHourUp: TextView
    private lateinit var btnRankHourDown: TextView
    private lateinit var tvRankMin: TextView
    private lateinit var btnRankMinUp: TextView
    private lateinit var btnRankMinDown: TextView
    private lateinit var tvRankRating: TextView
    private lateinit var btnRankRatingUp: TextView
    private lateinit var btnRankRatingDown: TextView
    private lateinit var rankContainer: android.widget.LinearLayout
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
    private lateinit var spinnerCardAnimation: Spinner
    private lateinit var tvAnimationPreview: TextView
    private lateinit var btnProfileBadDay: TextView
    private lateinit var btnProfileNormal: TextView
    private lateinit var btnProfileDynamic: TextView
    private lateinit var btnProfileCustom: TextView
    private lateinit var tvProfileDesc: TextView
    private var activeProfile = WorkProfile.NORMAL
    private var isUpdatingFromProfile = false

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val exporter = DataExporter(this)
        val result = exporter.export(uri)
        Toast.makeText(this, if (result.success) result.message else "Falha: ${result.message}", Toast.LENGTH_LONG).show()
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, takeFlags)

        AlertDialog.Builder(this)
            .setTitle("Importar Dados")
            .setMessage("Deseja limpar os dados existentes antes de importar? Isso removerá todos os registros atuais do banco de dados.\n\nRecomendado apenas em instalação limpa (primeiro uso no aparelho).")
            .setPositiveButton("Importar (manter existentes)") { _, _ ->
                doImport(uri, clearExisting = false)
            }
            .setNeutralButton("Importar (substituir tudo)") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Confirmação")
                    .setMessage("Tem certeza? TODOS os dados atuais serão apagados antes da importação. Essa ação NÃO pode ser desfeita.")
                    .setPositiveButton("Sim, substituir") { _, _ ->
                        doImport(uri, clearExisting = true)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun doImport(uri: Uri, clearExisting: Boolean) {
        val exporter = DataExporter(this)
        val result = exporter.import(uri, clearExisting)
        Toast.makeText(this, if (result.success) result.message else "Falha: ${result.message}", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupBottomNav(Screen.PARAMS)
        setupToolbar(title = "Par\u00e2metros", showBack = true, actionText = "\uD83D\uDCBE Salvar", actionListener = { saveValues() })

        prefs = SecurePreferences.get(this)

        migrateWeightsToRanks()

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
        tvRankKm = findViewById(R.id.tvRankKm)
        btnRankKmUp = findViewById(R.id.btnRankKmUp)
        btnRankKmDown = findViewById(R.id.btnRankKmDown)
        tvRankHour = findViewById(R.id.tvRankHour)
        btnRankHourUp = findViewById(R.id.btnRankHourUp)
        btnRankHourDown = findViewById(R.id.btnRankHourDown)
        tvRankMin = findViewById(R.id.tvRankMin)
        btnRankMinUp = findViewById(R.id.btnRankMinUp)
        btnRankMinDown = findViewById(R.id.btnRankMinDown)
        tvRankRating = findViewById(R.id.tvRankRating)
        btnRankRatingUp = findViewById(R.id.btnRankRatingUp)
        btnRankRatingDown = findViewById(R.id.btnRankRatingDown)
        rankContainer = findViewById(R.id.rankContainer)
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
        spinnerCardAnimation = findViewById(R.id.spinnerCardAnimation)
        tvAnimationPreview = findViewById(R.id.tvAnimationPreview)
        btnProfileBadDay = findViewById(R.id.btnProfileBadDay)
        btnProfileNormal = findViewById(R.id.btnProfileNormal)
        btnProfileDynamic = findViewById(R.id.btnProfileDynamic)
        btnProfileCustom = findViewById(R.id.btnProfileCustom)
        tvProfileDesc = findViewById(R.id.tvProfileDesc)

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
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdatingFromProfile && activeProfile != WorkProfile.CUSTOM) {
                    selectWorkProfile(WorkProfile.CUSTOM)
                }
                debouncedUpdatePreview()
            }
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

        setupRankRow(tvRankKm, btnRankKmUp, btnRankKmDown, KEY_RANK_KM)
        setupRankRow(tvRankHour, btnRankHourUp, btnRankHourDown, KEY_RANK_HOUR)
        setupRankRow(tvRankMin, btnRankMinUp, btnRankMinDown, KEY_RANK_MIN)
        setupRankRow(tvRankRating, btnRankRatingUp, btnRankRatingDown, KEY_RANK_RATING)

        seekThresholdAceitar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvThresholdAceitarLabel.text = "BOA se pontua\u00e7\u00e3o \u2265 ${progress + 50}%"
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
                tvThresholdAnalisarLabel.text = "MÉDIA se pontua\u00e7\u00e3o \u2265 ${progress + 20}%"
                if (fromUser && (progress + 20) >= (seekThresholdAceitar.progress + 50)) {
                    Toast.makeText(this@SettingsActivity,
                        "O threshold de MÉDIA deve ser menor que o threshold de BOA",
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

        findViewById<TextView>(R.id.btnReprocess).setOnClickListener { showReprocessDialog() }

        setupThemePills()
        setupWorkProfileSelector()
        setupAnimationSelector()
        setupEventNotificationsSettings()
        setupAppReadingSettings()
        setupDataExportImport()
    }

    private fun showReprocessDialog() {
        val db = DatabaseHelper(this)
        val failedCount = db.getFailedRawCards().size
        val pendingCount = db.getPendingRawCards().size
        val totalCount = failedCount + pendingCount

        if (totalCount == 0) {
            AlertDialog.Builder(this)
                .setTitle("Reprocessar Cards")
                .setMessage("Nenhum card com falha encontrado.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Reprocessar Cards")
            .setMessage("$failedCount cards com status 'failed' e $pendingCount com status 'pending'. Tentar reprocessar com parsers atualizados?")
            .setPositiveButton("Reprocessar") { _, _ ->
                val parsers: List<RideDataParser> = listOf(
                    ReservationDetailParser(),
                    RadarCardParser(),
                    ExclusiveCardParser(),
                    App99CardParser(),
                    DiscoveryCardParser()
                )
                val reprocessor = RawCardReprocessor(this, parsers)
                reprocessor.reprocessInBackground(limit = 50) { count ->
                    runOnUiThread {
                        Toast.makeText(this, "$count card(s) reprocessados com sucesso!", Toast.LENGTH_SHORT).show()
                    }
                }
                Toast.makeText(this, "Reprocessamento iniciado em background!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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

        tvCostInfo.text = "Seu custo total/km: ${FormatUtils.currency(costPerKm)}"

        for ((index, margin) in margins.withIndex()) {
            val radioMin = RadioButton(this).apply {
                id = View.generateViewId()
                text = "${margin.first} \u2192 ${FormatUtils.currency(margin.second)}/km"
                setPadding(8, 8, 8, 8)
                tag = margin.second
            }
            radioGroupMin.addView(radioMin)

            val radioIdeal = RadioButton(this).apply {
                id = View.generateViewId()
                text = "${margin.first} \u2192 ${FormatUtils.currency(margin.second)}/km"
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

                etMinKm.setText(FormatUtils.decimal(minValue))
                etIdealKm.setText(FormatUtils.decimal(idealValue))

                val minHour = minValue * 30
                val idealHour = idealValue * 30
                val minMinute = minHour / 60
                val idealMinute = idealHour / 60

                etMinHour.setText(FormatUtils.decimal(minHour))
                etIdealHour.setText(FormatUtils.decimal(idealHour))
                etMinMinute.setText(FormatUtils.decimal(minMinute))
                etIdealMinute.setText(FormatUtils.decimal(idealMinute))

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

    private fun toggleLayout(isColumn: Boolean) {
        btnColuna.isSelected = isColumn
        btnLinha.isSelected = !isColumn
        btnColuna.setBackgroundResource(
            if (isColumn) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btnLinha.setBackgroundResource(
            if (!isColumn) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btnColuna.setTextColor(if (isColumn) AppColors.textInverse else AppColors.textSecondary)
        btnLinha.setTextColor(if (!isColumn) AppColors.textInverse else AppColors.textSecondary)
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
        btnEsquerda.setTextColor(if (isLeft) AppColors.textInverse else AppColors.textSecondary)
        btnCentro.setTextColor(if (isCenter) AppColors.textInverse else AppColors.textSecondary)
        btnDireita.setTextColor(if (isRight) AppColors.textInverse else AppColors.textSecondary)
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
            btn.setTextColor(if (selected) AppColors.textInverse else AppColors.textSecondary)
        }
    }

    private fun toggleMetric(btn: TextView) {
        btn.isSelected = !btn.isSelected
        btn.setBackgroundResource(
            if (btn.isSelected) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btn.setTextColor(if (btn.isSelected) AppColors.textInverse else AppColors.textSecondary)
    }

    private fun loadValues() {
        fun loadFloat(key: String, def: Float): String {
            val v = prefs.getFloat(key, def)
            return if (v == 0f) "" else FormatUtils.decimal(v)
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
            btn.setTextColor(if (visible) AppColors.textInverse else AppColors.textSecondary)
        }
        applyMetric(btnShowKm, showKm)
        applyMetric(btnShowHour, showHour)
        applyMetric(btnShowMinute, showMinute)
        applyMetric(btnShowRating, showRating)

        loadRanks()

        val ta = prefs.getInt(KEY_THRESHOLD_ACEITAR, 80)
        seekThresholdAceitar.progress = ta - 50
        tvThresholdAceitarLabel.text = "BOA se pontua\u00e7\u00e3o \u2265 ${ta}%"
        val an = prefs.getInt(KEY_THRESHOLD_ANALISAR, 50)
        seekThresholdAnalisar.progress = an - 20
        tvThresholdAnalisarLabel.text = "MÉDIA se pontua\u00e7\u00e3o \u2265 ${an}%"

        val pageSize = prefs.getInt(KEY_PAGE_SIZE, 100)
        selectPageSize(pageSize)

        cardDurationSeconds = prefs.getInt(KEY_CARD_DURATION, 15)
        seekCardDuration.progress = (cardDurationSeconds - 5) / 5
        tvCardDurationLabel.text = "Dura\u00e7\u00e3o do card: ${cardDurationSeconds}s"

        val profileKey = prefs.getString(KEY_ACTIVE_PROFILE, "normal") ?: "normal"
        activeProfile = WorkProfile.entries.firstOrNull { it.prefKey == profileKey } ?: WorkProfile.NORMAL
        selectWorkProfile(activeProfile)
    }

    private fun saveValues(showToast: Boolean = true) {
        val thresholdAceitar = seekThresholdAceitar.progress + 50
        val thresholdAnalisar = seekThresholdAnalisar.progress + 20
        if (thresholdAnalisar >= thresholdAceitar) {
            Toast.makeText(this,
                "O threshold de MÉDIA deve ser menor que o threshold de BOA",
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
            putInt(KEY_RANK_KM, getRank(KEY_RANK_KM))
            putInt(KEY_RANK_HOUR, getRank(KEY_RANK_HOUR))
            putInt(KEY_RANK_MIN, getRank(KEY_RANK_MIN))
            putInt(KEY_RANK_RATING, getRank(KEY_RANK_RATING))
            putInt(KEY_THRESHOLD_ACEITAR, seekThresholdAceitar.progress + 50)
            putInt(KEY_THRESHOLD_ANALISAR, seekThresholdAnalisar.progress + 20)
            putInt(KEY_PAGE_SIZE, selectedPageSize)
            putInt(KEY_CARD_DURATION, cardDurationSeconds)
            putString(KEY_ACTIVE_PROFILE, activeProfile.prefKey)
            if (activeProfile == WorkProfile.CUSTOM) {
                putFloat(KEY_CUSTOM_MIN_KM, minKm)
                putFloat(KEY_CUSTOM_IDEAL_KM, idealKm)
                putFloat(KEY_CUSTOM_MIN_HOUR, minHour)
                putFloat(KEY_CUSTOM_IDEAL_HOUR, idealHour)
                putFloat(KEY_CUSTOM_MIN_MINUTE, minMinute)
                putFloat(KEY_CUSTOM_IDEAL_MINUTE, idealMinute)
                putFloat(KEY_CUSTOM_MIN_RATING, minRating)
                putFloat(KEY_CUSTOM_IDEAL_RATING, idealRating)
            }
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
            putInt(KEY_RANK_KM, getRank(KEY_RANK_KM))
            putInt(KEY_RANK_HOUR, getRank(KEY_RANK_HOUR))
            putInt(KEY_RANK_MIN, getRank(KEY_RANK_MIN))
            putInt(KEY_RANK_RATING, getRank(KEY_RANK_RATING))
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

        for (i in 0..3) {
            val p = result.params[i]
            val tv = when (i) { 0 -> pvKm; 1 -> pvHour; 2 -> pvMin; else -> pvRating }
            val label = when (i) {
                0 -> "R$/km: 3,20"
                1 -> "R$/h: 45,00"
                2 -> "R$/min: 0,75"
                else -> "Nota: 4,70"
            }
            tv.text = "$label → ${"%.1f".format(p.score)}/10 (${p.rank}º · +${"%.0f".format(p.points)} pts)"
            tv.setTextColor(DecisionEngine.stateColor(p.state))
        }

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

    private fun computeCostPerKm(): Double {
        val db = DatabaseHelper(this)
        val refuels = db.getRefuels()
        val expenses = db.getAllExpenses()
        val monthlyKm = db.getMonthlyKm()
        return CostCalculator.calculateCostSummary(refuels, expenses, monthlyKm).totalCostPerKm
    }

    private fun showProfileWarning(costKm: Double) {
        removeProfileWarning()
        val d = resources.displayMetrics.density
        val warningTv = TextView(this).apply {
            tag = "profile_warning_badge"
            text = "⚠️ Seu semáforo será configurado abaixo do custo/km do seu veículo que é de R\$ ${FormatUtils.decimal(costKm)}. Isso significa PREJUÍZO"
            textSize = spFromRes(R.dimen.text_size_12)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xFFE53935.toInt())
            setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (6 * d).toInt()
            layoutParams = lp
        }
        val parent = tvProfileDesc.parent as android.view.ViewGroup
        parent.addView(warningTv, parent.indexOfChild(tvProfileDesc) + 1)
    }

    private fun removeProfileWarning() {
        val parent = tvProfileDesc.parent as? android.view.ViewGroup ?: return
        parent.findViewWithTag<android.view.View>("profile_warning_badge")?.let { parent.removeView(it) }
    }

    private fun setupThemePills() {
        val btnSystem = findViewById<TextView>(R.id.btnThemeSystem)
        val btnLight = findViewById<TextView>(R.id.btnThemeLight)
        val btnDark = findViewById<TextView>(R.id.btnThemeDark)
        val currentMode = prefs.getInt(KEY_THEME_MODE, THEME_MODE_SYSTEM)

        fun selectTheme(mode: Int) {
            prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
            AppCompatDelegate.setDefaultNightMode(mode)
            recreate()
        }

        fun applyPillStyle(btn: TextView, selected: Boolean) {
            btn.isSelected = selected
            btn.setBackgroundResource(if (selected) R.drawable.pill_selected else R.drawable.pill_unselected)
            btn.setTextColor(if (selected) AppColors.textInverse else AppColors.textSecondary)
        }

        applyPillStyle(btnSystem, currentMode == THEME_MODE_SYSTEM)
        applyPillStyle(btnLight, currentMode == THEME_MODE_LIGHT)
        applyPillStyle(btnDark, currentMode == THEME_MODE_DARK)

        btnSystem.setOnClickListener { selectTheme(THEME_MODE_SYSTEM) }
        btnLight.setOnClickListener { selectTheme(THEME_MODE_LIGHT) }
        btnDark.setOnClickListener { selectTheme(THEME_MODE_DARK) }
    }

    private fun setupWorkProfileSelector() {
        btnProfileBadDay.setOnClickListener { selectWorkProfile(WorkProfile.BAD_DAY) }
        btnProfileNormal.setOnClickListener { selectWorkProfile(WorkProfile.NORMAL) }
        btnProfileDynamic.setOnClickListener { selectWorkProfile(WorkProfile.DYNAMIC) }
        btnProfileCustom.setOnClickListener { selectWorkProfile(WorkProfile.CUSTOM) }
    }

    private fun selectWorkProfile(profile: WorkProfile) {
        activeProfile = profile

        // Update pill visual state for all 4 profiles
        btnProfileBadDay.isSelected = profile == WorkProfile.BAD_DAY
        btnProfileNormal.isSelected = profile == WorkProfile.NORMAL
        btnProfileDynamic.isSelected = profile == WorkProfile.DYNAMIC
        btnProfileCustom.isSelected = profile == WorkProfile.CUSTOM

        btnProfileBadDay.setBackgroundResource(
            if (profile == WorkProfile.BAD_DAY) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btnProfileNormal.setBackgroundResource(
            if (profile == WorkProfile.NORMAL) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btnProfileDynamic.setBackgroundResource(
            if (profile == WorkProfile.DYNAMIC) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btnProfileCustom.setBackgroundResource(
            if (profile == WorkProfile.CUSTOM) R.drawable.pill_selected else R.drawable.pill_unselected
        )

        btnProfileBadDay.setTextColor(
            if (profile == WorkProfile.BAD_DAY) AppColors.textInverse else AppColors.textSecondary
        )
        btnProfileNormal.setTextColor(
            if (profile == WorkProfile.NORMAL) AppColors.textInverse else AppColors.textSecondary
        )
        btnProfileDynamic.setTextColor(
            if (profile == WorkProfile.DYNAMIC) AppColors.textInverse else AppColors.textSecondary
        )
        btnProfileCustom.setTextColor(
            if (profile == WorkProfile.CUSTOM) AppColors.textInverse else AppColors.textSecondary
        )

        // CUSTOM: restore from snapshot or fall back to saved values
        if (profile == WorkProfile.CUSTOM) {
            tvProfileDesc.setText(R.string.profile_custom_desc)
            isUpdatingFromProfile = true
            fun lf(key: String, def: Float): String {
                val v = prefs.getFloat(key, def)
                return if (v == 0f) "" else FormatUtils.decimal(v)
            }
            if (prefs.contains(KEY_CUSTOM_MIN_KM)) {
                etMinKm.setText(lf(KEY_CUSTOM_MIN_KM, 2.5f))
                etIdealKm.setText(lf(KEY_CUSTOM_IDEAL_KM, 4.0f))
                etMinHour.setText(lf(KEY_CUSTOM_MIN_HOUR, 30f))
                etIdealHour.setText(lf(KEY_CUSTOM_IDEAL_HOUR, 60f))
                etMinMinute.setText(lf(KEY_CUSTOM_MIN_MINUTE, 0.5f))
                etIdealMinute.setText(lf(KEY_CUSTOM_IDEAL_MINUTE, 1.0f))
                etMinRating.setText(lf(KEY_CUSTOM_MIN_RATING, 4.5f))
                etIdealRating.setText(lf(KEY_CUSTOM_IDEAL_RATING, 4.9f))
            } else {
                etMinKm.setText(lf(KEY_MIN_KM, 2.5f))
                etIdealKm.setText(lf(KEY_IDEAL_KM, 4.0f))
                etMinHour.setText(lf(KEY_MIN_HOUR, 30f))
                etIdealHour.setText(lf(KEY_IDEAL_HOUR, 60f))
                etMinMinute.setText(lf(KEY_MIN_MINUTE, 0.5f))
                etIdealMinute.setText(lf(KEY_IDEAL_MINUTE, 1.0f))
                etMinRating.setText(lf(KEY_MIN_RATING, 4.5f))
                etIdealRating.setText(lf(KEY_IDEAL_RATING, 4.9f))
            }
            isUpdatingFromProfile = false

            val costKm = computeCostPerKm()
            if (costKm > 0) {
                val minKm = parseBr(etMinKm.text.toString()) ?: 0f
                if (minKm < costKm) {
                    showProfileWarning(costKm)
                } else {
                    removeProfileWarning()
                }
            } else {
                removeProfileWarning()
            }

            debouncedUpdatePreview()
            return
        }

        tvProfileDesc.setText(when (profile) {
            WorkProfile.BAD_DAY -> R.string.profile_bad_day_desc
            WorkProfile.NORMAL -> R.string.profile_normal_desc
            WorkProfile.DYNAMIC -> R.string.profile_dynamic_desc
            else -> R.string.profile_custom_desc
        })

        val db = DatabaseHelper(this)
        val values = WorkProfileCalculator.calculate(profile, db)

        val stats = db.getRideStats(
            Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.timeInMillis
        )
        val usedDefault = stats == null || stats.count < 10
        val toastMsg = if (usedDefault) R.string.profile_applied_default
                       else R.string.profile_applied_stats

        isUpdatingFromProfile = true
        etMinKm.setText(FormatUtils.decimal(values.minKm))
        etIdealKm.setText(FormatUtils.decimal(values.idealKm))
        etMinHour.setText(FormatUtils.decimal(values.minHour))
        etIdealHour.setText(FormatUtils.decimal(values.idealHour))
        etMinMinute.setText(FormatUtils.decimal(values.minMinute))
        etIdealMinute.setText(FormatUtils.decimal(values.idealMinute))
        etMinRating.setText(FormatUtils.decimal(values.minRating))
        etIdealRating.setText(FormatUtils.decimal(values.idealRating))
        isUpdatingFromProfile = false

        val costKm = computeCostPerKm()
        if (costKm > 0) {
            val minKm = parseBr(etMinKm.text.toString()) ?: 0f
            if (minKm < costKm) {
                showProfileWarning(costKm)
            } else {
                removeProfileWarning()
            }
        } else {
            removeProfileWarning()
        }

        debouncedUpdatePreview()
        Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
    }

    private fun setupAnimationSelector() {
        val prefs = PreferenceManager(this)
        val currentAnimation = prefs.getCardAnimation()
        val labels = AnimationConstants.getAnimationLabels()
        val animationNames = AnimationConstants.getAnimationNames()
        val animationLabels = animationNames.map { labels[it] ?: it }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            animationLabels
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCardAnimation.adapter = adapter

        val currentIndex = animationNames.indexOf(currentAnimation)
        spinnerCardAnimation.setSelection(if (currentIndex >= 0) currentIndex else 0)

        spinnerCardAnimation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = animationNames[position]
                prefs.setCardAnimation(selected)
                showAnimationPreview(selected)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showAnimationPreview(animation: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            tvAnimationPreview.visibility = View.GONE
            return
        }

        tvAnimationPreview.visibility = View.VISIBLE
        val animNames = mapOf(
            AnimationConstants.ANIMATION_NONE to "Instantâneo",
            AnimationConstants.ANIMATION_FADE to "Fade In",
            AnimationConstants.ANIMATION_SLIDE_RIGHT to "→ Direita",
            AnimationConstants.ANIMATION_SLIDE_LEFT to "← Esquerda",
            AnimationConstants.ANIMATION_FADE_SLIDE to "Fade + Slide ↑"
        )
        tvAnimationPreview.text = "Preview: ${animNames[animation] ?: animation}"

        tvAnimationPreview.alpha = 0f
        tvAnimationPreview.translationX = 0f
        tvAnimationPreview.translationY = 0f

        when (animation) {
            AnimationConstants.ANIMATION_NONE -> {
                tvAnimationPreview.alpha = 1f
            }
            AnimationConstants.ANIMATION_FADE -> {
                tvAnimationPreview.animate()
                    .alpha(1f)
                    .setDuration(500)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            AnimationConstants.ANIMATION_SLIDE_RIGHT -> {
                tvAnimationPreview.translationX = 100f
                tvAnimationPreview.animate()
                    .translationX(0f)
                    .setDuration(500)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            AnimationConstants.ANIMATION_SLIDE_LEFT -> {
                tvAnimationPreview.translationX = -100f
                tvAnimationPreview.animate()
                    .translationX(0f)
                    .setDuration(500)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            AnimationConstants.ANIMATION_FADE_SLIDE -> {
                tvAnimationPreview.alpha = 0f
                tvAnimationPreview.translationY = 40f
                tvAnimationPreview.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(500)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun setupEventNotificationsSettings() {
        val prefs = PreferenceManager(this)

        val switchEnabled = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchEventNotifications)
        switchEnabled.isChecked = prefs.isEventNotificationsEnabled()
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.setEventNotificationsEnabled(isChecked)
            val statusText = if (isChecked) "Ativado" else "Desativado"
            Toast.makeText(this, "Notificações de eventos: $statusText", Toast.LENGTH_SHORT).show()
            if (!isChecked) {
                EventAlertManager(this).resetCooldown()
            }
            updateEventNotificationsInfo()
        }

        val spinnerThreshold = findViewById<Spinner>(R.id.spinnerEventThreshold)
        val thresholdOptions = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10")
        val thresholdAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, thresholdOptions)
        thresholdAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerThreshold.adapter = thresholdAdapter

        val currentThreshold = prefs.getEventNotificationsThreshold()
        val currentIndex = thresholdOptions.indexOf(currentThreshold.toString())
        spinnerThreshold.setSelection(if (currentIndex >= 0) currentIndex else 3)

        spinnerThreshold.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val threshold = thresholdOptions[position].toInt()
                prefs.setEventNotificationsThreshold(threshold)
                Toast.makeText(this@SettingsActivity, "Limite: $threshold corridas", Toast.LENGTH_SHORT).show()
                updateEventNotificationsInfo()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        updateEventNotificationsInfo()
    }

    private fun updateEventNotificationsInfo() {
        val prefs = PreferenceManager(this)
        val tvInfo = findViewById<TextView>(R.id.tvEventNotificationsInfo)
        val currentThreshold = prefs.getEventNotificationsThreshold()
        val enabled = prefs.isEventNotificationsEnabled()
        tvInfo.text = """
            ⚙️ Como funciona:

            • O app detecta quando há muitas corridas na última hora
            • Isso pode indicar um evento próximo (show, jogo, etc.)
            • Você recebe uma notificação para ficar alerta

            🔹 Limite atual: $currentThreshold corridas
            🔹 Status: ${if (enabled) "✅ Ativado" else "❌ Desativado"}
        """.trimIndent()
    }

    private fun setupAppReadingSettings() {
        val prefs = SecurePreferences.get(this)

        val switchUber = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchReadUber)
        switchUber.isChecked = prefs.getBoolean(KEY_READ_UBER, true)
        switchUber.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_READ_UBER, isChecked).apply()
            Toast.makeText(this, "Leitura Uber: ${if (isChecked) "Ativada" else "Desativada"}", Toast.LENGTH_SHORT).show()
        }

        val switchApp99 = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchReadApp99)
        val tv99Subtitle = findViewById<TextView>(R.id.tvReadApp99Subtitle)
        val apiOk = Build.VERSION.SDK_INT >= 34
        if (!apiOk) {
            switchApp99.isChecked = false
            switchApp99.isEnabled = false
            tv99Subtitle.text = "Indisponível (requer Android 14+)"
            tv99Subtitle.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            prefs.edit().putBoolean(KEY_READ_APP99, false).apply()
        } else {
            switchApp99.isChecked = prefs.getBoolean(KEY_READ_APP99, true)
            switchApp99.isEnabled = true
            switchApp99.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(KEY_READ_APP99, isChecked).apply()
                Toast.makeText(this, "Leitura 99: ${if (isChecked) "Ativada" else "Desativada"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDataExportImport() {
        findViewById<TextView>(R.id.btnExportData).setOnClickListener {
            exportLauncher.launch("profit_driving_backup.json")
        }
        findViewById<TextView>(R.id.btnImportData).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }

    private fun migrateWeightsToRanks() {
        if (!prefs.contains(KEY_RANK_KM) && prefs.contains(KEY_WEIGHT_KM)) {
            val metrics = listOf(
                KEY_RANK_KM to prefs.getInt(KEY_WEIGHT_KM, 5),
                KEY_RANK_HOUR to prefs.getInt(KEY_WEIGHT_HOUR, 4),
                KEY_RANK_MIN to prefs.getInt(KEY_WEIGHT_MIN, 3),
                KEY_RANK_RATING to prefs.getInt(KEY_WEIGHT_RATING, 2)
            )
            val sorted = metrics.sortedByDescending { it.second }
            prefs.edit().apply {
                sorted.forEachIndexed { i, (key, _) -> putInt(key, i + 1) }
                apply()
            }
        }
        val rankKeys = listOf(KEY_RANK_KM, KEY_RANK_HOUR, KEY_RANK_MIN, KEY_RANK_RATING)
        for (rk in rankKeys) {
            if (!prefs.contains(rk)) {
                val defRank = when (rk) {
                    KEY_RANK_KM -> 1; KEY_RANK_HOUR -> 2; KEY_RANK_MIN -> 3; else -> 4
                }
                prefs.edit().putInt(rk, defRank).apply()
            }
        }
    }

    private fun getRank(key: String): Int {
        val tv = when (key) {
            KEY_RANK_KM -> tvRankKm; KEY_RANK_HOUR -> tvRankHour
            KEY_RANK_MIN -> tvRankMin; else -> tvRankRating
        }
        return tv.text.toString().replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 1
    }

    private fun loadRanks() {
        val rankKeys = listOf(KEY_RANK_KM, KEY_RANK_HOUR, KEY_RANK_MIN, KEY_RANK_RATING)
        val views = listOf(tvRankKm, tvRankHour, tvRankMin, tvRankRating)
        val upBts = listOf(btnRankKmUp, btnRankHourUp, btnRankMinUp, btnRankRatingUp)
        val dnBts = listOf(btnRankKmDown, btnRankHourDown, btnRankMinDown, btnRankRatingDown)
        val tags = listOf("km", "hour", "min", "rating")

        for (i in 0..3) {
            val r = prefs.getInt(rankKeys[i], i + 1)
            views[i].text = "${r}º"
            upBts[i].isEnabled = r > 1
            dnBts[i].isEnabled = r < 4
        }

        // Reorder rows by rank (1º first, 4º last)
        val children = (0 until rankContainer.childCount).map { rankContainer.getChildAt(it) }
        val sorted = children.sortedBy { child ->
            val tag = child.tag?.toString() ?: ""
            val idx = tags.indexOf(tag)
            if (idx < 0) 99 else prefs.getInt(rankKeys[idx], idx + 1)
        }
        for (pos in sorted.indices) {
            if (rankContainer.getChildAt(pos) !== sorted[pos]) {
                rankContainer.removeView(sorted[pos])
                rankContainer.addView(sorted[pos], pos)
            }
        }
    }

    private fun setupRankRow(
        tvRank: TextView, btnUp: TextView, btnDown: TextView, key: String
    ) {
        btnUp.setOnClickListener {
            val current = prefs.getInt(key, 1)
            if (current > 1) swapRanks(key, current, current - 1)
        }
        btnDown.setOnClickListener {
            val current = prefs.getInt(key, 1)
            if (current < 4) swapRanks(key, current, current + 1)
        }
    }

    private fun swapRanks(changedKey: String, oldRank: Int, newRank: Int) {
        val rankKeys = listOf(KEY_RANK_KM, KEY_RANK_HOUR, KEY_RANK_MIN, KEY_RANK_RATING)
        val swappedKey = rankKeys.firstOrNull { prefs.getInt(it, 0) == newRank } ?: return
        prefs.edit().apply {
            putInt(changedKey, newRank)
            putInt(swappedKey, oldRank)
            apply()
        }
        loadRanks()
        debouncedUpdatePreview()
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

        const val KEY_CUSTOM_MIN_KM = "custom_min_km"
        const val KEY_CUSTOM_IDEAL_KM = "custom_ideal_km"
        const val KEY_CUSTOM_MIN_HOUR = "custom_min_hour"
        const val KEY_CUSTOM_IDEAL_HOUR = "custom_ideal_hour"
        const val KEY_CUSTOM_MIN_MINUTE = "custom_min_minute"
        const val KEY_CUSTOM_IDEAL_MINUTE = "custom_ideal_minute"
        const val KEY_CUSTOM_MIN_RATING = "custom_min_rating"
        const val KEY_CUSTOM_IDEAL_RATING = "custom_ideal_rating"

        const val KEY_LAST_VALUE = "last_value"
        const val KEY_LAST_DISTANCE = "last_distance"
        const val KEY_LAST_TIME = "last_time"
        const val KEY_LAST_RATING = "last_rating"
        const val KEY_LAST_APP = "last_app"
        const val KEY_LAST_TIMESTAMP = "last_timestamp"
        const val KEY_LAST_SERVICE_TYPE = "last_service_type"

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

        const val KEY_RANK_KM     = "rank_km"
        const val KEY_RANK_HOUR   = "rank_hour"
        const val KEY_RANK_MIN    = "rank_min"
        const val KEY_RANK_RATING = "rank_rating"

        const val KEY_THRESHOLD_ACEITAR  = "threshold_aceitar"
        const val KEY_THRESHOLD_ANALISAR = "threshold_analisar"
        const val KEY_PAGE_SIZE = "page_size"
        const val KEY_CARD_DURATION = "card_duration"
        const val KEY_ACTIVE_PROFILE = "active_work_profile"

        const val DEFAULT_CARD_LAYOUT = "column"
        const val DEFAULT_CARD_POSITION = "left"
        const val DEFAULT_CARD_Y_PERCENT = 30

        const val KEY_READ_UBER = "read_uber"
        const val KEY_READ_APP99 = "read_app99"
    }
}
