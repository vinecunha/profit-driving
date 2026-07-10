package com.profitdriving

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AnalysisActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var cardsContainer: LinearLayout
    private lateinit var serviceTypeContainer: LinearLayout
    private lateinit var skeletonContainer: LinearLayout
    private lateinit var scrollView: View
    private lateinit var btnModeDay: TextView
    private lateinit var btnModeWeek: TextView
    private lateinit var btnModeMonth: TextView
    private lateinit var btnPrevPeriod: TextView
    private lateinit var btnNextPeriod: TextView
    private lateinit var btnToday: TextView
    private lateinit var tvPeriodTitle: TextView
    private lateinit var btnTabGeral: LinearLayout
    private lateinit var btnTabServico: LinearLayout
    private lateinit var tabGeralLine: View
    private lateinit var tabServicoLine: View
    private lateinit var tabGeralText: TextView
    private lateinit var tabServicoText: TextView
    private var ridesByApp: Map<String, List<RideRecord>> = emptyMap()
    private var currentServiceTypeTab = 0 // 0=Geral, 1=Por Tipo
    private var currentResult: AnalysisResultV2? = null
    private var currentAppFilter = "all" // "all", "Uber", "99"
    private lateinit var btnFilterAll: TextView
    private lateinit var btnFilterUber: TextView
    private lateinit var btnFilter99: TextView
    private lateinit var filterContainer: android.widget.LinearLayout

    private var currentMode = ViewMode.DAY
    private var referenceCalendar = Calendar.getInstance()

    private val dayFormatter = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("pt", "BR"))
    private val weekFormatter = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
    private val monthFormatter = SimpleDateFormat("MMMM, yyyy", Locale("pt", "BR"))

    private val GREEN by lazy { ctxColor(R.color.success) }
    private val ORANGE by lazy { ctxColor(R.color.warning) }
    private val RED by lazy { ctxColor(R.color.error) }
    private val BLUE by lazy { ctxColor(R.color.accent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)
        setupBottomNav(Screen.ANALYSIS)
        setupToolbar(title = "An\u00E1lise", showBack = true)

        cardsContainer = findViewById(R.id.cardsContainer)
        serviceTypeContainer = findViewById(R.id.serviceTypeContainer)
        skeletonContainer = findViewById(R.id.skeletonContainer)
        scrollView = findViewById(R.id.scrollView)
        db = DatabaseHelper(this)

        btnModeDay = findViewById(R.id.btnModeDay)
        btnModeWeek = findViewById(R.id.btnModeWeek)
        btnModeMonth = findViewById(R.id.btnModeMonth)
        btnPrevPeriod = findViewById(R.id.btnPrevPeriod)
        btnNextPeriod = findViewById(R.id.btnNextPeriod)
        btnToday = findViewById(R.id.btnToday)
        tvPeriodTitle = findViewById(R.id.tvPeriodTitle)

        btnTabGeral = findViewById(R.id.btnTabGeral)
        btnTabServico = findViewById(R.id.btnTabServico)
        tabGeralLine = findViewById(R.id.tabGeralLine)
        tabServicoLine = findViewById(R.id.tabServicoLine)
        tabGeralText = findViewById(R.id.tabGeralText)
        tabServicoText = findViewById(R.id.tabServicoText)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterUber = findViewById(R.id.btnFilterUber)
        btnFilter99 = findViewById(R.id.btnFilter99)
        filterContainer = findViewById(R.id.filterContainer)

        setupPeriodNavigation()
        setupTabs()
        setupAppFilter()
        setViewMode(ViewMode.DAY)
    }

    private fun loadDataForCurrentPeriod() {
        showSkeleton()
        val (periodStart, periodEnd) = getPeriodTimestamps()
        val (prevStart, prevEnd) = getPreviousPeriodTimestamps()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val allRecords = db.getFiltered(periodStart, null, 0, periodEnd)
                        .let { CardHashGenerator.recoverRidesFromRawLogs(it, db) }
                    val records = if (currentAppFilter == "all") allRecords
                        else allRecords.filter { it.appName.equals(currentAppFilter, ignoreCase = true) }
                    val dailyRides = db.getDailyRidesByDateRange(periodStart, periodEnd)
                    val costPerKm = CostSummaryCache.getCurrentSummary(this@AnalysisActivity).totalCostPerKm
                    val analysisResult = AnalysisHelperV2.calculate(records, dailyRides, costPerKm)

                    val prevDaily = db.getDailyRidesByDateRange(prevStart, prevEnd)
                    val prevEarnings = prevDaily.filter { it.isCompleted }.sumOf { it.finalValue }
                    val prevRides = prevDaily.count { it.isCompleted }

                    val dailyTarget = PreferenceManager(this@AnalysisActivity).getDailyTarget()
                    ridesByApp = records.groupBy { it.appName.ifEmpty { "Uber" } }
                    analysisResult.copy(
                        dailyProjection = analysisResult.dailyProjection.copy(targetDay = dailyTarget),
                        previousPeriodEarnings = prevEarnings,
                        previousPeriodRides = prevRides
                    )
                }
                hideSkeleton()
                buildCards(result)
            } catch (e: Exception) {
                hideSkeleton()
                L.e("AnalysisActivity", "Erro ao carregar dados", e)
                findViewById<TextView>(R.id.tvPeriodTitle)?.text = "Erro ao carregar"
            }
        }
    }

    private fun showSkeleton() {
        skeletonContainer.removeAllViews()
        for (i in 0..2) {
            val skeleton = layoutInflater.inflate(R.layout.skeleton_card, skeletonContainer, false)
            skeletonContainer.addView(skeleton)
        }
        skeletonContainer.visibility = View.VISIBLE
        cardsContainer.visibility = View.GONE
        scrollView.visibility = View.GONE
    }

    private fun hideSkeleton() {
        skeletonContainer.visibility = View.GONE
        cardsContainer.visibility = View.VISIBLE
        scrollView.visibility = View.VISIBLE
        skeletonContainer.removeAllViews()
    }

    private fun setupPeriodNavigation() {
        btnModeDay.setOnClickListener { setViewMode(ViewMode.DAY) }
        btnModeWeek.setOnClickListener { setViewMode(ViewMode.WEEK) }
        btnModeMonth.setOnClickListener { setViewMode(ViewMode.MONTH) }
        btnPrevPeriod.setOnClickListener { navigatePeriod(-1) }
        btnNextPeriod.setOnClickListener { navigatePeriod(1) }
        btnToday.setOnClickListener { goToToday() }
    }

    private fun setupAppFilter() {
        val prefs = SecurePreferences.get(this)
        val uberOn = prefs.getBoolean(SettingsActivity.KEY_READ_UBER, true)
        val ninetyNineOn = prefs.getBoolean(SettingsActivity.KEY_READ_APP99, true) && Build.VERSION.SDK_INT >= 34
        if (!uberOn || !ninetyNineOn) {
            filterContainer.visibility = View.GONE
        } else {
            filterContainer.visibility = View.VISIBLE
            btnFilterAll.setOnClickListener { setAppFilter("all") }
            btnFilterUber.setOnClickListener { setAppFilter("Uber") }
            btnFilter99.setOnClickListener { setAppFilter("99") }
        }
    }

    private fun setAppFilter(filter: String) {
        currentAppFilter = filter
        btnFilterAll.setBackgroundResource(if (filter == "all") R.drawable.pill_selected else R.drawable.pill_unselected)
        btnFilterUber.setBackgroundResource(if (filter == "Uber") R.drawable.pill_selected else R.drawable.pill_unselected)
        btnFilter99.setBackgroundResource(if (filter == "99") R.drawable.pill_selected else R.drawable.pill_unselected)
        btnFilterAll.setTextColor(if (filter == "all") ctxColor(R.color.text_inverse) else ctxColor(R.color.text_secondary))
        btnFilterUber.setTextColor(if (filter == "Uber") ctxColor(R.color.text_inverse) else ctxColor(R.color.text_secondary))
        btnFilter99.setTextColor(if (filter == "99") ctxColor(R.color.text_inverse) else ctxColor(R.color.text_secondary))
        loadDataForCurrentPeriod()
    }

    private fun setupTabs() {
        btnTabGeral.setOnClickListener { setServiceTypeTab(0) }
        btnTabServico.setOnClickListener { setServiceTypeTab(1) }
    }

    private fun setServiceTypeTab(tab: Int) {
        currentServiceTypeTab = tab
        val accent = ctxColor(R.color.accent)
        val border = ctxColor(R.color.border)
        val primary = ctxColor(R.color.text_primary)
        val secondary = ctxColor(R.color.text_secondary)

        tabGeralLine.setBackgroundColor(if (tab == 0) accent else border)
        tabServicoLine.setBackgroundColor(if (tab == 1) accent else border)
        tabGeralText.setTextColor(if (tab == 0) primary else secondary)
        tabServicoText.setTextColor(if (tab == 1) primary else secondary)
        tabGeralText.setTypeface(null, if (tab == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tabServicoText.setTypeface(null, if (tab == 1) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        updateCardVisibility()
    }

    private fun updateCardVisibility() {
        val showGeral = currentServiceTypeTab == 0
        cardsContainer.visibility = if (showGeral) View.VISIBLE else View.GONE
        serviceTypeContainer.visibility = if (showGeral) View.GONE else View.VISIBLE
    }

    private fun setViewMode(mode: ViewMode) {
        currentMode = mode
        referenceCalendar = when (mode) {
            ViewMode.DAY -> referenceCalendar
            ViewMode.WEEK -> getWeekStart(referenceCalendar)
            ViewMode.MONTH -> {
                val cal = referenceCalendar.clone() as Calendar
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal
            }
        }
        updateModeStyles()
        updatePeriodTitle()
        loadDataForCurrentPeriod()
    }

    private fun updateModeStyles() {
        btnModeDay.setBackgroundResource(if (currentMode == ViewMode.DAY) R.drawable.pill_selected else R.drawable.pill_unselected)
        btnModeWeek.setBackgroundResource(if (currentMode == ViewMode.WEEK) R.drawable.pill_selected else R.drawable.pill_unselected)
        btnModeMonth.setBackgroundResource(if (currentMode == ViewMode.MONTH) R.drawable.pill_selected else R.drawable.pill_unselected)

        btnModeDay.setTextColor(if (currentMode == ViewMode.DAY) ctxColor(R.color.text_inverse) else ctxColor(R.color.text_secondary))
        btnModeWeek.setTextColor(if (currentMode == ViewMode.WEEK) ctxColor(R.color.text_inverse) else ctxColor(R.color.text_secondary))
        btnModeMonth.setTextColor(if (currentMode == ViewMode.MONTH) ctxColor(R.color.text_inverse) else ctxColor(R.color.text_secondary))
    }

    private fun updatePeriodTitle() {
        tvPeriodTitle.text = when (currentMode) {
            ViewMode.DAY -> dayFormatter.format(referenceCalendar.time)
            ViewMode.WEEK -> {
                val start = getWeekStart(referenceCalendar)
                val end = getWeekEnd(referenceCalendar)
                "${weekFormatter.format(start.time)} - ${weekFormatter.format(end.time)}"
            }
            ViewMode.MONTH -> monthFormatter.format(referenceCalendar.time)
        }
    }

    private fun getWeekStart(calendar: Calendar): Calendar {
        val cal = calendar.clone() as Calendar
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    private fun getWeekEnd(weekStart: Calendar): Calendar {
        val cal = weekStart.clone() as Calendar
        cal.add(Calendar.DAY_OF_MONTH, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal
    }

    private fun navigatePeriod(direction: Int) {
        when (currentMode) {
            ViewMode.DAY -> referenceCalendar.add(Calendar.DAY_OF_MONTH, direction)
            ViewMode.WEEK -> referenceCalendar.add(Calendar.DAY_OF_MONTH, direction * 7)
            ViewMode.MONTH -> referenceCalendar.add(Calendar.MONTH, direction)
        }
        updatePeriodTitle()
        loadDataForCurrentPeriod()
    }

    private fun goToToday() {
        referenceCalendar = Calendar.getInstance()
        if (currentMode != ViewMode.DAY) {
            referenceCalendar = when (currentMode) {
                ViewMode.WEEK -> getWeekStart(referenceCalendar)
                ViewMode.MONTH -> {
                    val cal = referenceCalendar.clone() as Calendar
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal
                }
                else -> referenceCalendar
            }
        }
        updatePeriodTitle()
        loadDataForCurrentPeriod()
    }

    private fun getPeriodTimestamps(): Pair<Long, Long> {
        return when (currentMode) {
            ViewMode.DAY -> {
                val start = referenceCalendar.clone() as Calendar
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                val end = start.clone() as Calendar
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
                Pair(start.timeInMillis, end.timeInMillis)
            }
            ViewMode.WEEK -> {
                val start = getWeekStart(referenceCalendar)
                val end = getWeekEnd(start)
                Pair(start.timeInMillis, end.timeInMillis)
            }
            ViewMode.MONTH -> {
                val start = referenceCalendar.clone() as Calendar
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                val end = start.clone() as Calendar
                end.set(Calendar.DAY_OF_MONTH, start.getActualMaximum(Calendar.DAY_OF_MONTH))
                end.set(Calendar.HOUR_OF_DAY, 23)
                end.set(Calendar.MINUTE, 59)
                end.set(Calendar.SECOND, 59)
                end.set(Calendar.MILLISECOND, 999)
                Pair(start.timeInMillis, end.timeInMillis)
            }
        }
    }

    private fun getPreviousPeriodTimestamps(): Pair<Long, Long> {
        val (start, end) = getPeriodTimestamps()
        val duration = end - start + 1
        return Pair(start - duration, end - duration)
    }

    private fun buildCards(r: AnalysisResultV2) {
        currentResult = r
        cardsContainer.removeAllViews()
        serviceTypeContainer.removeAllViews()
        cardIndex = 0

        buildDriverRating(r)

        var section = buildExpandableSection("\uD83D\uDCCA", "Vis\u00E3o Geral", cardsContainer)
        buildVisaoGeral(r, section)
        buildScoreDistribution(r, section)
        buildLostRides(r, section)
        buildAcceptanceByValue(r, section)

        section = buildExpandableSection("\uD83D\uDCB0", "Financeiro", cardsContainer)
        buildResumoFinanceiro(r, section)
        buildBreakevenAnalysis(r, section)
        buildComparativoPeriodo(r, section)
        buildBonusImpact(r, section)

        section = buildExpandableSection("\u23F0", "Desempenho", cardsContainer)
        buildMelhorHorario(r, section)
        buildDinamicaHorario(r, section)
        buildScoreTrend(r, section)
        buildCidades(r, section)
        buildBairros(r, section)
        buildHourlyForecast(r, section)
        buildDynamicTrend(r, section)
        buildWeekdayRanking(r, section)

        section = buildExpandableSection("\uD83D\uDCAF", "Corridas", cardsContainer)
        buildMultipleStopsImpact(r, section)
        buildRejectionPatterns(r, section)

        section = buildExpandableSection("\uD83D\uDE80", "Otimiza\u00E7\u00E3o", cardsContainer)
        buildDailyProjection(r, section)
        buildFloorSimulation(r, section)
        buildInsights(r, section)

        buildServiceTypeCards(r)
        updateCardVisibility()
    }

    private fun addSpacer() {
        cardsContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 16)
        })
    }

    private var cardIndex = 0

    private val cardAccentColors by lazy {
        listOf(
            ctxColor(R.color.accent), ctxColor(R.color.success), ctxColor(R.color.warning),
            ctxColor(R.color.metric_good), ctxColor(R.color.metric_bad), ctxColor(R.color.warning)
        )
    }

    private fun createCard(title: String): LinearLayout {
        val accent = cardAccentColors[cardIndex % cardAccentColors.size]
        cardIndex++
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            contentDescription = "Card de análise: $title"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            orientation = VERTICAL
            setPadding(16, 12, 16, 16)
            setBackgroundResource(R.drawable.card_bg)
            elevation = 2 * density

            addView(View(this@AnalysisActivity).apply {
                layoutParams = LinearLayout.LayoutParams(48, 4).apply {
                    setMargins(0, 0, 0, 10)
                }
                setBackgroundColor(accent)
            })

            addView(TextView(this@AnalysisActivity).apply {
                contentDescription = "Título: $title"
                layoutParams = LinearLayout.LayoutParams(
                    MATCH_PARENT, WRAP_CONTENT
                )
                text = title
                textSize = 16f
                setTextColor(ctxColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
        }
    }

    private fun addText(
        card: LinearLayout, text: String, size: Float = 13f,
        color: Int = ctxColor(R.color.text_primary), bold: Boolean = false
    ) {
        card.addView(TextView(this).apply {
            contentDescription = text
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { setMargins(0, 2, 0, 2) }
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        })
    }

    private fun addBar(
        container: LinearLayout, label: String, valueText: String,
        fraction: Float, barColor: Int = GREEN
    ) {
        val row = LinearLayout(this).apply {
            contentDescription = "Barra de $label: $valueText"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 3, 0, 3)
        }

        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.25f)
            text = label
            textSize = 11f
            setTextColor(ctxColor(R.color.text_secondary))
        })

        val barOuter = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 14, 0.55f)
            val g = GradientDrawable().apply {
                setColor(ctxColor(R.color.border))
                cornerRadius = 7 * resources.displayMetrics.density
            }
            background = g
        }
        row.addView(barOuter)

        barOuter.post {
            val w = (barOuter.width * fraction.coerceIn(0f, 1f)).toInt()
            if (w > 0) {
                barOuter.addView(View(this@AnalysisActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(w, MATCH_PARENT)
                    val g = GradientDrawable().apply {
                        setColor(barColor)
                        cornerRadius = 7 * resources.displayMetrics.density
                    }
                    background = g
                })
            }
        }

        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.2f)
            text = valueText
            textSize = 11f
            setTextColor(ctxColor(R.color.text_primary))
            gravity = Gravity.END
        })

        container.addView(row)
    }

    private fun addBarSimple(
        container: LinearLayout, label: String, valueText: String,
        fraction: Float, barColor: Int = GREEN
    ) {
        val row = LinearLayout(this).apply {
            contentDescription = "Barra de $label: $valueText"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 3, 0, 3)
        }

        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.25f)
            text = label
            textSize = 11f
            setTextColor(ctxColor(R.color.text_secondary))
        })

        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.55f)
            text = "\u2588".repeat((fraction.coerceIn(0f, 1f) * 20).toInt().coerceAtLeast(1))
            textSize = 11f
            setTextColor(barColor)
            maxLines = 1
        })

        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.2f)
            text = valueText
            textSize = 11f
            setTextColor(ctxColor(R.color.text_primary))
            gravity = Gravity.END
        })

        container.addView(row)
    }

    private fun addDivider(card: LinearLayout) {
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
                .apply { setMargins(0, 6, 0, 6) }
            setBackgroundColor(ctxColor(R.color.border))
        })
    }

    private fun buildExpandableSection(
        icon: String,
        title: String,
        container: LinearLayout,
        initiallyExpanded: Boolean = true
    ): LinearLayout {
        val sectionWrapper = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { bottomMargin = 12 }
            orientation = VERTICAL
            val bg = GradientDrawable().apply {
                setColor(ctxColor(R.color.bg_screen))
                cornerRadius = 12 * resources.displayMetrics.density
            }
            background = bg
            elevation = 2 * resources.displayMetrics.density
        }

        val arrowIcon = TextView(this).apply {
            text = if (initiallyExpanded) "\u25BC" else "\u25B6"
            textSize = 10f
            setTextColor(ctxColor(R.color.text_tertiary))
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                .apply { rightMargin = 8 }
        }

        val header = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 14, 16, 14)
            isClickable = true
            isFocusable = true
            addView(arrowIcon)
            addView(TextView(this@AnalysisActivity).apply {
                text = "$icon $title"
                textSize = 16f
                setTextColor(ctxColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })
        }

        sectionWrapper.addView(header)

        val content = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = VERTICAL
            setPadding(12, 0, 12, 12)
            visibility = if (initiallyExpanded) View.VISIBLE else View.GONE
        }
        sectionWrapper.addView(content)

        header.setOnClickListener {
            val isVisible = content.visibility == View.VISIBLE
            arrowIcon.text = if (isVisible) "\u25B6" else "\u25BC"
            content.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        container.addView(sectionWrapper)
        return content
    }

    // ─── CARD: Classificação do Motorista ───
    private fun buildDriverRating(r: AnalysisResultV2) {
        val rating = r.driverRating ?: return
        val density = resources.displayMetrics.density

        val card = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { setMargins(0, 0, 0, 12) }
            orientation = VERTICAL
            setPadding(20, 20, 20, 20)
            elevation = 4 * density
            val bg = android.graphics.drawable.GradientDrawable().apply {
                val bgColor = when {
                    rating.score >= 90 -> ctxColor(R.color.success_bg)
                    rating.score >= 75 -> ctxColor(R.color.primary_light)
                    rating.score >= 50 -> ctxColor(R.color.accent_light)
                    else -> ctxColor(R.color.bg_surface)
                }
                setColor(bgColor)
                cornerRadius = 16 * density
            }
            background = bg
        }

        // Title
        card.addView(TextView(this).apply {
            text = "\uD83C\uDFC6  Classifica\u00E7\u00E3o do Motorista"
            textSize = 14f
            setTextColor(ctxColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { bottomMargin = 12 }
        })

        // Stars row + score
        val starRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val starStr = (1..5).joinToString("") { if (it <= rating.stars) "\u2605" else "\u2606" }
        starRow.addView(TextView(this).apply {
            text = starStr
            textSize = 24f
            setTextColor(ctxColor(R.color.highlight))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        starRow.addView(TextView(this).apply {
            text = "  ${rating.score}/100"
            textSize = 18f
            setTextColor(ctxColor(R.color.text_secondary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(starRow)

        // Level name
        card.addView(TextView(this).apply {
            text = rating.level
            textSize = 22f
            setTextColor(ctxColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { topMargin = 4; bottomMargin = 8 }
        })

        // Score bar
        val barColor = when {
            rating.score >= 90 -> ctxColor(R.color.success)
            rating.score >= 75 -> ctxColor(R.color.primary)
            rating.score >= 50 -> ctxColor(R.color.warning)
            else -> ctxColor(R.color.error)
        }
        val barContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (10 * density).toInt())
                .apply { bottomMargin = 12 }
            orientation = HORIZONTAL
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(ctxColor(R.color.bg_surface))
                cornerRadius = 6 * density
            }
            background = bg
        }
        barContainer.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, rating.score.toFloat())
            val fg = android.graphics.drawable.GradientDrawable().apply {
                setColor(barColor)
                cornerRadius = 6 * density
            }
            background = fg
        })
        barContainer.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, (100 - rating.score).toFloat())
        })
        card.addView(barContainer)

        // Description
        card.addView(TextView(this).apply {
            text = rating.description
            textSize = 13f
            setTextColor(ctxColor(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        })

        cardsContainer.addView(card)
    }

    // ─── CARD 1: Visão Geral ───
    private fun buildVisaoGeral(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        val card = createCard("\uD83D\uDCCA Vis\u00E3o Geral")

        addText(card, "\uD83D\uDCCB Corridas ofertadas: ${r.offeredCount}", 13f, ctxColor(R.color.text_primary))
        addText(card, "\u2705 Corridas aceitas: ${r.acceptedCount} (${"%.1f".format(r.acceptanceRate)}%)", 13f, GREEN)
        addText(card, "\uD83D\uDCB0 Faturamento bruto: R\$ ${FormatUtils.decimal(r.totalEarnings)}", 13f, ctxColor(R.color.text_primary))
        addText(card, "\uD83D\uDEE3\uFE0F Km total: ${FormatUtils.decimal1(r.totalKm)} km", 13f, ctxColor(R.color.text_primary))
        addText(card, "\u23F1\uFE0F Tempo total: ${AnalysisHelperV2.hoursMinutes(r.totalMinutes)}", 13f, ctxColor(R.color.text_primary))

        addDivider(card)
        addText(card, "\uD83D\uDCCA M\u00E9dias", 13f, ctxColor(R.color.text_primary), true)
        addText(card, "R\$/km m\u00E9dio: R\$ ${FormatUtils.decimal(r.avgPricePerKm)}", 12f, ctxColor(R.color.text_secondary))
        addText(card, "R\$/h m\u00E9dio: R\$ ${FormatUtils.decimal(r.avgPricePerHour)}", 12f, ctxColor(R.color.text_secondary))
        addText(card, "Nota m\u00E9dia: ${"%.2f".format(r.avgRating)}", 12f, ctxColor(R.color.text_secondary))

        if (ridesByApp.size > 1) {
            addDivider(card)
            addText(card, "\uD83D\uDCF1 Por aplicativo", 13f, ctxColor(R.color.text_primary), true)
            for ((app, rides) in ridesByApp) {
                val count = rides.size
                val totalEarnings = rides.sumOf { it.value ?: 0.0 }
                val color = when {
                    app.equals("99", ignoreCase = true) -> ctxColor(R.color.app_99)
                    else -> ctxColor(R.color.text_secondary)
                }
                addText(card, "$app: $count corridas | R\$ ${FormatUtils.decimal(totalEarnings)}", 12f, color)
            }
        }

        container.addView(card)
    }

    // ─── CARD 2: Impacto dos Bônus ───
    private fun buildBonusImpact(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        val card = createCard("\uD83D\uDCB0 Impacto dos B\u00F4nus")
        val MIN_DATA = 3

        addText(card, "Comparativo de corridas com e sem b\u00F4nus", 12f, ctxColor(R.color.text_secondary))

        addDivider(card)

        val p = r.priorityImpact
        addText(card, "\u2B06 Prioridade", 13f, ctxColor(R.color.text_primary), true)
        if (p.count >= MIN_DATA) {
            val diffP = maxOf(p.avgPricePerKm - p.avgPricePerKmWithout, 0.0)
            val diffPctP = if (p.avgPricePerKmWithout > 0) (diffP / p.avgPricePerKmWithout * 100) else 0.0
            val goodDiffP = maxOf(p.goodRatePercent - r.goodPercent, 0.0)
            addText(card, buildString {
                append("Presente em ${"%.0f".format(p.percentage)}% das corridas")
            }, 12f, ctxColor(R.color.text_secondary))
            addText(card, buildString {
                append("R\$/km m\u00E9dio: R\$ ${FormatUtils.decimal(p.avgPricePerKm)}")
                append("  (sem: R\$ ${FormatUtils.decimal(p.avgPricePerKmWithout)})")
            }, 12f, ctxColor(R.color.text_secondary))
            addText(card, buildString {
                append("\u2B06 ${"%.0f".format(diffPctP)}% melhor R\$/km  |  ${"%.0f".format(goodDiffP)}% mais corridas \"boas\"")
            }, 12f, GREEN, true)
        } else {
            addText(card, "Dados insuficientes (m\u00EDn. $MIN_DATA corridas com prioridade)", 12f, ctxColor(R.color.text_tertiary))
        }

        addDivider(card)

        val d = r.dynamicImpact
        addText(card, "\u26A1 Din\u00E2mica", 13f, ctxColor(R.color.text_primary), true)
        if (d.count >= MIN_DATA) {
            val diffD = maxOf(d.avgPricePerKm - d.avgPricePerKmWithout, 0.0)
            val diffPctD = if (d.avgPricePerKmWithout > 0) (diffD / d.avgPricePerKmWithout * 100) else 0.0
            val goodDiffD = maxOf(d.goodRatePercent - r.goodPercent, 0.0)
            addText(card, buildString {
                append("Presente em ${"%.0f".format(d.percentage)}% das corridas")
            }, 12f, ctxColor(R.color.text_secondary))
            addText(card, buildString {
                append("R\$/km m\u00E9dio: R\$ ${FormatUtils.decimal(d.avgPricePerKm)}")
                append("  (sem: R\$ ${FormatUtils.decimal(d.avgPricePerKmWithout)})")
            }, 12f, ctxColor(R.color.text_secondary))
            addText(card, buildString {
                append("\u2B06 ${"%.0f".format(diffPctD)}% melhor R\$/km  |  ${"%.0f".format(goodDiffD)}% mais corridas \"boas\"")
            }, 12f, GREEN, true)
        } else {
            addText(card, "Dados insuficientes (m\u00EDn. $MIN_DATA corridas com din\u00E2mica)", 12f, ctxColor(R.color.text_tertiary))
        }

        addDivider(card)
        addText(card, "\uD83D\uDCA1 O que significa? B\u00F4nus de prioridade s\u00E3o oferecidos pela Uber para incentivar aceita\u00E7\u00E3o em \u00E1reas de alta demanda. O indicador mostra o impacto real no ganho por km \u2014 quanto maior a diferen\u00E7a, mais vale a pena priorizar essas corridas.",
            11f, ctxColor(R.color.text_tertiary))

        container.addView(card)
    }

    // ─── CARD 3: Melhor Horário ───
    private fun buildMelhorHorario(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        val card = createCard("\u23F0 Melhor hor\u00E1rio para aceitar")
        val bestData = r.hourlyData.find { it.hour == r.bestHourToAccept }

        if (bestData != null && bestData.rideCount > 0) {
            addText(card, buildString {
                append("\u2B50 Melhor hor\u00E1rio: ${bestData.hour}h \u00E0s ${(bestData.hour + 1) % 24}h")
                append("  |  R\$ ${FormatUtils.decimal(bestData.avgPricePerKm)}/km")
                append("  |  ${bestData.rideCount} corridas")
            }, 12f, GREEN, true)
        }

        addDivider(card)

        val topHours = r.hourlyData
            .filter { it.rideCount > 0 }
            .sortedByDescending { it.avgPricePerKm }
            .take(5)

        if (topHours.isNotEmpty()) {
            val maxVal = topHours.first().avgPricePerKm
            val hContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = VERTICAL
                setPadding(0, 4, 0, 0)
            }
            for (h in topHours) {
                addBar(hContainer, "${h.hour}h", "R\$ ${FormatUtils.decimal(h.avgPricePerKm)}",
                    (h.avgPricePerKm / maxVal).toFloat())
            }
            card.addView(hContainer)
        } else {
            addText(card, "Nenhuma corrida aceita no per\u00EDodo", 12f, ctxColor(R.color.text_tertiary))
        }

        container.addView(card)
    }

    // ─── CARD 4: Dinâmica por Horário ───
    private fun buildDinamicaHorario(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        val card = createCard("\u26A1 Din\u00E2mica por hor\u00E1rio")
        val peakData = r.hourlyData.find { it.hour == r.peakDynamicHour }

        if (peakData != null && peakData.rideCount > 0) {
            addText(card, buildString {
                append("\u26A1 Pico de din\u00E2mica: ${peakData.hour}h")
                append("  |  ${"%.0f".format(peakData.dynamicPercentage)}% com din\u00E2mica")
            }, 12f, ORANGE, true)
        }

        addDivider(card)

        val topDynamic = r.hourlyData
            .filter { it.rideCount > 0 }
            .sortedByDescending { it.dynamicPercentage }
            .take(5)

        if (topDynamic.isNotEmpty()) {
            val maxVal = topDynamic.first().dynamicPercentage
            val dContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = VERTICAL
                setPadding(0, 4, 0, 0)
            }
            for (h in topDynamic) {
                addBar(dContainer, "${h.hour}h", "${"%.0f".format(h.dynamicPercentage)}%",
                    (h.dynamicPercentage / maxVal).toFloat(), ORANGE)
            }
            card.addView(dContainer)
        } else {
            addText(card, "Nenhuma corrida com din\u00E2mica no per\u00EDodo", 12f, ctxColor(R.color.text_tertiary))
        }

        container.addView(card)
    }

    // ─── CARD 5: Melhores Cidades ───
    private fun buildCidades(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        if (r.topCities.isEmpty()) return
        val card = createCard("\uD83D\uDCCD Melhores cidades")

        for (c in r.topCities.take(6)) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = VERTICAL
                setPadding(8, 4, 8, 4)
            }

            val header = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                text = c.city
                textSize = 13f
                setTextColor(ctxColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            header.addView(TextView(this).apply {
                text = "${c.rideCount} corridas"
                textSize = 11f
                setTextColor(ctxColor(R.color.text_tertiary))
            })
            row.addView(header)

            addText(row, buildString {
                append("R\$/km: R\$ ${FormatUtils.decimal(c.avgPricePerKm)}")
                append("  |  Din\u00E2mica: ${"%.0f".format(c.dynamicPercentage)}%")
                append("  |  Melhor hor\u00E1rio: ${c.bestHour}h")
            }, 11f, ctxColor(R.color.text_secondary))

            if (c != r.topCities.take(6).last()) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
                        .apply { setMargins(0, 4, 0, 4) }
                    setBackgroundColor(ctxColor(R.color.bg_surface))
                })
            }

            card.addView(row)
        }

        container.addView(card)
    }

    // ─── CARD 6: Melhores Bairros ───
    private fun buildBairros(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        if (r.topNeighborhoods.isEmpty()) return
        val card = createCard("\uD83C\uDFE0 Melhores bairros")

        card.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 8)
            }
            text = "\uD83D\uDCCA R\$/km = m\u00E9dia nas corridas aceitas | \uD83D\uDD25 % = corridas com din\u00E2mica no total ofertado"
            textSize = 10f
            setTextColor(ctxColor(R.color.text_tertiary))
        })

        for (n in r.topNeighborhoods.take(6)) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
            }

            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                text = n.neighborhood
                textSize = 12f
                setTextColor(ctxColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                text = "R\$ ${FormatUtils.decimal(n.avgPricePerKm)} | ${"%.0f".format(n.dynamicPercentage)}%"
                textSize = 11f
                setTextColor(ctxColor(R.color.text_secondary))
            })

            card.addView(row)
        }

        container.addView(card)
    }

    // ─── CARD 7: Previsão de Ganhos por Horário ───
    private fun buildHourlyForecast(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        if (r.hourlyForecast.isEmpty()) return

        val card = createCard("\uD83D\uDCCA PREVIS\u00C3O DE GANHOS POR HOR\u00C1RIO")
        val maxGanho = r.hourlyForecast.maxOfOrNull { it.avgEarningsPerHour } ?: 1.0

        for (hour in r.hourlyForecast) {
            val hourContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val label = hour.slotLabel.replace("-", " \u00E0s ")

            hourContainer.addView(TextView(this).apply {
                text = "\uD83D\uDCC5 $label"
                textSize = 14f
                setTextColor(ctxColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })

            addDetailRow(hourContainer, "\uD83D\uDCB0", "Ganho m\u00E9dio",
                "R\$ ${FormatUtils.decimal(hour.avgEarningsPerHour)}/hora", ctxColor(R.color.text_primary))
            addDetailRow(hourContainer, "\uD83D\uDE97", "Corridas", "${hour.rideCount}", ctxColor(R.color.text_primary))

            val dinamicaColor = if (hour.dynamicPercent >= 30) ctxColor(R.color.success) else ctxColor(R.color.warning)
            addDetailRow(hourContainer, "\u26A1", "Din\u00E2mica",
                "${String.format("%.0f", hour.dynamicPercent)}%", dinamicaColor)

            val fraction = (hour.avgEarningsPerHour / maxGanho).toFloat()
            val progressContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 8)
                    .apply { topMargin = 8 }
                val bg = GradientDrawable()
                bg.setColor(ctxColor(R.color.border))
                bg.cornerRadius = (4 * resources.displayMetrics.density)
                background = bg
            }
            hourContainer.addView(progressContainer)

            progressContainer.post {
                val fillWidth = (progressContainer.width * fraction).toInt()
                if (fillWidth > 0) {
                    val fillView = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(fillWidth, MATCH_PARENT)
                        val fillBg = GradientDrawable()
                        fillBg.setColor(ctxColor(R.color.success))
                        fillBg.cornerRadius = (4 * resources.displayMetrics.density)
                        background = fillBg
                    }
                    progressContainer.addView(fillView)
                }
            }

            if (hour != r.hourlyForecast.last()) {
                hourContainer.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
                        .apply { setMargins(0, 8, 0, 0) }
                    setBackgroundColor(ctxColor(R.color.bg_surface))
                })
            }

            card.addView(hourContainer)
        }

        container.addView(card)
    }

    // ─── CARD 8: Análise de Aceitação por Valor ───
    private fun buildAcceptanceByValue(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        if (r.acceptanceByValue.isEmpty()) return
        val card = createCard("\u2705 An\u00E1lise de aceita\u00E7\u00E3o por valor")

        val maxFrac = r.acceptanceByValue.maxOfOrNull { it.barFraction }?.coerceAtLeast(0.01f) ?: 1f
        for (v in r.acceptanceByValue) {
            val line = "${v.rangeLabel}  ${v.offered} ofertadas \u2192 ${v.accepted} aceitas (${"%.0f".format(v.acceptanceRate)}%)"
            addBarSimple(card, v.rangeLabel,
                "${v.offered} ofertadas \u2192 ${v.accepted} aceitas (${"%.0f".format(v.acceptanceRate)}%)",
                v.barFraction / maxFrac, BLUE)
        }

        container.addView(card)
    }

    // ─── CARD 9: Corridas Perdidas ───
    private fun buildLostRides(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        val lost = r.lostRides
        if (lost.lostCount == 0) return
        val card = createCard("\u274C Corridas perdidas (n\u00E3o aceitas)")

        addText(card, "\uD83D\uDCB0 Valor m\u00E9dio perdido: R\$ ${FormatUtils.decimal(lost.avgLostValue)}")
        addText(card, "\uD83D\uDCCA R\$/km m\u00E9dio perdido: R\$ ${FormatUtils.decimal(lost.avgLostPricePerKm)}")
        addText(card, "\u23F0 Hor\u00E1rio com mais perdas: ${lost.peakLossHour} (${"%.0f".format(lost.peakLossPercent)}%)")
        addText(card, "\uD83D\uDE97 Categoria: ${lost.topLostCategory} (${"%.0f".format(lost.topLostCategoryPercent)}% das perdas)")
        addText(card, "\uD83D\uDCCD Cidade: ${lost.topLostCity} (${"%.0f".format(lost.topLostCityPercent)}% das perdas)")

        container.addView(card)
    }

    // ─── CARD 10: Tendência de Dinâmica ───
    private fun buildDynamicTrend(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        if (r.dynamicTrend.size < 2) return
        val card = createCard("\uD83D\uDCC8 Tend\u00EAncia de din\u00E2mica (7 dias)")

        val maxFrac = r.dynamicTrend.maxOfOrNull { it.barFraction }?.coerceAtLeast(0.01f) ?: 1f
        for (d in r.dynamicTrend) {
            val arrow = if (d.isUp) "\uD83D\uDD3C" else "\uD83D\uDD3B"
            addBarSimple(card, d.dayLabel,
                "${"%.0f".format(d.dynamicPercent)}% $arrow",
                d.barFraction / maxFrac, if (d.isUp) GREEN else RED)
        }

        container.addView(card)
    }

    // ─── CARD 11: Projeção do Dia ───
    private fun buildDailyProjection(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        val p = r.dailyProjection
        if (p.completedRides == 0) return
        val card = createCard("\uD83C\uDFAF Proje\u00E7\u00E3o do dia")

        addText(card, "\uD83D\uDCB0 Ganho atual: R\$ ${FormatUtils.decimal(p.currentEarnings)} (${p.completedRides} corridas)")
        val timeRange = if (p.firstRideTime.isNotEmpty() && p.lastRideTime.isNotEmpty()) {
            " (${p.firstRideTime} \u00E0s ${p.lastRideTime})"
        } else ""
        addText(card, "\u23F1\uFE0F Horas trabalhadas: ${"%.1f".format(p.hoursWorked)}h$timeRange")
        addText(card, "\uD83D\uDCC8 Ganho m\u00E9dio/h: R\$ ${FormatUtils.decimal(p.avgPerHour)}")

        addDivider(card)

        val targetRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
            isClickable = true
            isFocusable = true
            setOnClickListener { showTargetDialog(card, r) }
        }
        targetRow.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            text = "\uD83C\uDFAF Meta do dia"
            textSize = 13f
            setTextColor(ctxColor(R.color.text_secondary))
        })
        targetRow.addView(TextView(this).apply {
            text = "R\$ ${FormatUtils.decimal(p.targetDay)}  \u270F\uFE0F"
            textSize = 13f
            setTextColor(ctxColor(R.color.accent))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(targetRow)

        addText(card, "\uD83D\uDCCA Proje\u00E7\u00E3o (${"%.0f".format(p.targetHours)}h): R\$ ${FormatUtils.decimal(p.projectedWithTargetHours)}")

        val remainingColor = if (p.remaining <= 0) GREEN else ORANGE
        val remainingText = if (p.remaining <= 0) {
            "\u2705 Meta atingida! Excedente: R\$ ${FormatUtils.decimal(-p.remaining)}"
        } else {
            "\u26A0\uFE0F Faltam R\$ ${FormatUtils.decimal(p.remaining)} para bater a meta"
        }
        addText(card, remainingText, 12f, remainingColor, true)

        container.addView(card)
    }

    private fun showTargetDialog(card: LinearLayout, r: AnalysisResultV2) {
        val currentTarget = r.dailyProjection.targetDay.toInt()
        val input = android.widget.EditText(this).apply {
            setText(currentTarget.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSelection(length())
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Definir meta do dia")
            .setMessage("Quanto voc\u00EA quer ganhar hoje?")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val value = input.text.toString().toDoubleOrNull()
                if (value != null && value > 0) {
                    PreferenceManager(this).setDailyTarget(value)
                    loadDataForCurrentPeriod()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ─── CARD 12: Dias Mais Lucrativos ───
    private fun buildWeekdayRanking(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        if (r.weekdayRanking.isEmpty()) return

        val card = createCard("\uD83C\uDFC6 DIAS MAIS LUCRATIVOS")
        val maxProfit = r.weekdayRanking.maxOfOrNull { it.avgEarnings } ?: 1.0

        for ((index, day) in r.weekdayRanking.withIndex()) {
            val dayContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = VERTICAL
                setPadding(0, 8, 0, 8)
            }

            dayContainer.addView(TextView(this).apply {
                text = "${index + 1}. ${getDayName(day.dayOfWeek)}"
                textSize = 14f
                setTextColor(ctxColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })

            addDetailRow(dayContainer, "\uD83D\uDCB0", "Lucro m\u00E9dio",
                "R\$ ${FormatUtils.decimal(day.avgEarnings)}", day.avgEarnings > 0)
            addDetailRow(dayContainer, "\uD83D\uDE97", "Corridas", "${day.rideCount}", true)
            addDetailRow(dayContainer, "\uD83D\uDCCA", "R\$/km m\u00E9dio",
                "R\$ ${FormatUtils.decimal(day.avgPricePerKm)}", true)

            val fraction = (day.avgEarnings / maxProfit).toFloat()
            val progressContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 8)
                    .apply { topMargin = 8 }
                val bg = GradientDrawable()
                bg.setColor(ctxColor(R.color.border))
                bg.cornerRadius = (4 * resources.displayMetrics.density)
                background = bg
            }
            dayContainer.addView(progressContainer)

            progressContainer.post {
                val fillWidth = (progressContainer.width * fraction).toInt()
                if (fillWidth > 0) {
                    val fillView = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(fillWidth, MATCH_PARENT)
                        val fillBg = GradientDrawable()
                        fillBg.setColor(ctxColor(R.color.success))
                        fillBg.cornerRadius = (4 * resources.displayMetrics.density)
                        background = fillBg
                    }
                    progressContainer.addView(fillView)
                }
            }

            if (index < r.weekdayRanking.size - 1) {
                dayContainer.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
                        .apply { setMargins(0, 8, 0, 0) }
                    setBackgroundColor(ctxColor(R.color.bg_surface))
                })
            }

            card.addView(dayContainer)
        }

        container.addView(card)
    }

    // ─── CARD: Tendência de Score ───
    private fun buildScoreTrend(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        if (r.scoreTrend.size < 2) return
        val card = createCard("📈 Tendência do Score")

        addText(card, "Evolução diária do score médio das corridas aceitas", 12f, ctxColor(R.color.text_secondary))

        addDivider(card)

        val maxFrac = r.scoreTrend.maxOfOrNull { it.barFraction }?.coerceAtLeast(0.01f) ?: 1f
        for (d in r.scoreTrend) {
            val label = "${d.dayLabel} (${d.rideCount})"
            val value = "${"%.0f".format(d.avgScore)}%"
            addBarSimple(card, label, value, d.barFraction / maxFrac,
                if (d.avgScore >= 80) GREEN else if (d.avgScore >= 50) ORANGE else RED)
        }

        addDivider(card)
        val totalAverage = r.scoreTrend.map { it.avgScore }.average()
        val trend = if (r.scoreTrend.size >= 2) {
            val first = r.scoreTrend.first().avgScore
            val last = r.scoreTrend.last().avgScore
            last - first
        } else 0.0
        addText(card, "Média do período: ${"%.0f".format(totalAverage)}% | Tendência: ${if (trend >= 0) "+" else ""}${"%.0f".format(trend)}%",
            12f, if (trend >= 0) GREEN else RED, true)

        container.addView(card)
    }

    private fun buildMultipleStopsImpact(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        val m = r.multipleStopsImpact ?: return
        val card = createCard("🔄 Impacto de paradas múltiplas")

        addText(card, "Comparativo entre corridas com e sem paradas", 12f, ctxColor(R.color.text_secondary))

        addDivider(card)

        addText(card, "✅ Sem paradas (${m.withoutStops.count} corridas)", 13f, GREEN, true)
        addInfoRow(card, "Valor médio", "R$ ${FormatUtils.decimal(m.withoutStops.avgValue)}", ctxColor(R.color.text_primary))
        addInfoRow(card, "R$/km médio", "R$ ${FormatUtils.decimal(m.withoutStops.avgPricePerKm)}", ctxColor(R.color.text_primary))
        addInfoRow(card, "R$/h médio", "R$ ${FormatUtils.decimal(m.withoutStops.avgPricePerHour)}", ctxColor(R.color.text_secondary))
        addInfoRow(card, "Km médio", "${FormatUtils.decimal1(m.withoutStops.avgDistance)} km", ctxColor(R.color.text_secondary))
        addInfoRow(card, "Tempo médio", "${m.withoutStops.avgTime} min", ctxColor(R.color.text_secondary))

        addDivider(card)

        addText(card, "🔄 Com paradas (${m.withStops.count} corridas)", 13f, ORANGE, true)
        addInfoRow(card, "Valor médio", "R$ ${FormatUtils.decimal(m.withStops.avgValue)}", ctxColor(R.color.text_primary))
        addInfoRow(card, "R$/km médio", "R$ ${FormatUtils.decimal(m.withStops.avgPricePerKm)}", ctxColor(R.color.text_primary))
        addInfoRow(card, "R$/h médio", "R$ ${FormatUtils.decimal(m.withStops.avgPricePerHour)}", ctxColor(R.color.text_secondary))
        addInfoRow(card, "Km médio", "${FormatUtils.decimal1(m.withStops.avgDistance)} km", ctxColor(R.color.text_secondary))
        addInfoRow(card, "Tempo médio", "${m.withStops.avgTime} min", ctxColor(R.color.text_secondary))

        addDivider(card)

        val diffPpk = m.withStops.avgPricePerKm - m.withoutStops.avgPricePerKm
        val diffPph = m.withStops.avgPricePerHour - m.withoutStops.avgPricePerHour
        addText(card, "Diferença R$/km: ${if (diffPpk >= 0) "+" else ""}R$ ${FormatUtils.decimal(diffPpk)}",
            12f, if (diffPpk >= 0) GREEN else RED)
        addText(card, "Diferença R$/h: ${if (diffPph >= 0) "+" else ""}R$ ${FormatUtils.decimal(diffPph)}",
            12f, if (diffPph >= 0) GREEN else RED)

        container.addView(card)
    }

    private fun buildRejectionPatterns(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        val p = r.rejectionPatterns ?: return
        if (p.totalLost == 0) return
        val card = createCard("❌ Padrões de rejeição")

        addText(card, "${p.totalLost} corridas não aceitas | R$ ${FormatUtils.decimal(p.totalLostValue)} perdidos",
            12f, ctxColor(R.color.text_primary), true)

        addDivider(card)

        addText(card, "Por faixa de valor", 13f, ctxColor(R.color.text_primary), true)
        for (v in p.byValue) {
            addBarSimple(card, v.label, "${v.count} (${"%.0f".format(v.percentOfTotal)}%)",
                (v.percentOfTotal / 100).toFloat(), BLUE)
        }

        addDivider(card)

        addText(card, "Por distância", 13f, ctxColor(R.color.text_primary), true)
        for (d in p.byDistance) {
            addBarSimple(card, d.label, "${d.count} (${"%.0f".format(d.percentOfTotal)}%)",
                (d.percentOfTotal / 100).toFloat(), ORANGE)
        }

        addDivider(card)

        addText(card, "Por período", 13f, ctxColor(R.color.text_primary), true)
        for (h in p.byHour) {
            addBarSimple(card, h.label, "${h.count} (${"%.0f".format(h.percentOfTotal)}%)",
                (h.percentOfTotal / 100).toFloat(), if (h.label == "Noite") GREEN else ctxColor(R.color.text_secondary))
        }

        addDivider(card)
        addText(card, "💡 Você recusou ${"%.0f".format(p.byValue.firstOrNull()?.percentOfTotal ?: 0.0)}% das corridas na faixa de menor valor. Reveja seu floor de aceitação.",
            11f, ctxColor(R.color.text_tertiary))

        container.addView(card)
    }

    // ─── SERVICE TYPE COLORS ───
    private fun serviceTypeColor(type: String): Int {
        return when {
            type.contains("UberX", ignoreCase = true) -> ctxColor(R.color.service_uberx)
            type.contains("Comfort", ignoreCase = true) || type.contains("Confort", ignoreCase = true) -> ctxColor(R.color.service_comfort)
            type.contains("Black", ignoreCase = true) || type.contains("VIP", ignoreCase = true) || type.contains("Top", ignoreCase = true) -> ctxColor(R.color.service_black)
            type.contains("Moto", ignoreCase = true) -> ctxColor(R.color.service_moto)
            type.contains("Entrega", ignoreCase = true) || type.contains("Flash", ignoreCase = true) || type.contains("Envios", ignoreCase = true) -> ctxColor(R.color.service_entrega)
            type.contains("Pop", ignoreCase = true) || type.contains("Juntos", ignoreCase = true) -> ctxColor(R.color.service_pop)
            else -> ctxColor(R.color.service_outros)
        }
    }

    private fun serviceTypeIcon(type: String): String {
        return when {
            type.contains("UberX", ignoreCase = true) -> "\uD83D\uDE97"
            type.contains("Comfort", ignoreCase = true) || type.contains("Confort", ignoreCase = true) -> "\uD83D\uDE98"
            type.contains("Black", ignoreCase = true) || type.contains("VIP", ignoreCase = true) -> "\uD83D\uDE94"
            type.contains("Moto", ignoreCase = true) -> "\uD83C\uDFCD\uFE0F"
            type.contains("Entrega", ignoreCase = true) || type.contains("Flash", ignoreCase = true) -> "\uD83D\uDCE6"
            type.contains("Pop", ignoreCase = true) -> "\uD83D\uDE95"
            else -> "\uD83D\uDE97"
        }
    }

    // ─── SERVICE TYPE CARDS ───
    private fun buildServiceTypeCards(r: AnalysisResultV2) {
        if (r.serviceTypes.isEmpty()) {
            serviceTypeContainer.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    .apply { setMargins(0, 24, 0, 0) }
                text = "Nenhum dado de tipo de serviço disponível"
                textSize = 14f
                setTextColor(ctxColor(R.color.text_tertiary))
                gravity = Gravity.CENTER
            })
            return
        }

        val sorted = r.serviceTypes.sortedByDescending { it.totalEarnings }

        val section = buildExpandableSection("\uD83D\uDE97", "Comparativo por Tipo de Serviço", serviceTypeContainer)
        buildServiceTypeDistribution(sorted, section)
        buildServiceTypeComparison(sorted, r, section)

        for (st in sorted) {
            val card = createServiceTypeCard(st, r)
            serviceTypeContainer.addView(card)
        }
    }

    private fun buildServiceTypeDistribution(list: List<ServiceTypeStats>, container: LinearLayout) {
        val total = list.sumOf { it.totalEarnings }.coerceAtLeast(0.01)
        val card = createCard("\uD83D\uDCC8 Distribui\u00E7\u00E3o do faturamento")

        val barHeight = 24
        val density = resources.displayMetrics.density
        val barContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, barHeight * density.toInt())
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val radiusPx = 6 * density
        for ((i, st) in list.withIndex()) {
            val fraction = (st.totalEarnings / total).toFloat().coerceIn(0.01f, 1f)
            val color = serviceTypeColor(st.serviceType)
            barContainer.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, barHeight * density.toInt(), fraction).apply {
                    if (i > 0) setMargins(2, 0, 0, 0)
                }
                val g = android.graphics.drawable.GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = radiusPx
                }
                background = g
            })
        }

        card.addView(barContainer)

        // Legend
        for (st in list) {
            val color = serviceTypeColor(st.serviceType)
            val icon = serviceTypeIcon(st.serviceType)
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 2, 0, 2)
            }
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(10, 10).apply { rightMargin = 6 }
                val dotBg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 2 * density
                }
                background = dotBg
            })
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                text = "$icon ${st.serviceType}"
                textSize = 11f
                setTextColor(ctxColor(R.color.text_secondary))
            })
            row.addView(TextView(this).apply {
                text = "R\$ ${FormatUtils.decimal(st.totalEarnings)} (${"%.0f".format(st.earningsPercentOfTotal)}%)"
                textSize = 11f
                setTextColor(ctxColor(R.color.text_primary))
            })
            card.addView(row)
        }

        container.addView(card)
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 12)
        })
    }

    private fun buildServiceTypeComparison(list: List<ServiceTypeStats>, r: AnalysisResultV2, container: LinearLayout) {
        if (list.size < 2) return
        val card = createCard("\uD83D\uDCCA Comparativo entre tipos")
        val maxEarnings = list.first().totalEarnings.coerceAtLeast(0.01)

        for (st in list) {
            val color = serviceTypeColor(st.serviceType)
            val icon = serviceTypeIcon(st.serviceType)
            val label = "$icon ${st.serviceType}"
            val fraction = (st.totalEarnings / maxEarnings).toFloat()
            val valueText = "R\$ ${FormatUtils.decimal(st.totalEarnings)} (${"%.0f".format(st.earningsPercentOfTotal)}%)"
            addBarSimple(card, label, valueText, fraction, color)
        }

        container.addView(card)
        container.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 16)
        })
    }

    private fun createServiceTypeCard(st: ServiceTypeStats, r: AnalysisResultV2): LinearLayout {
        val color = serviceTypeColor(st.serviceType)
        val icon = serviceTypeIcon(st.serviceType)
        val density = resources.displayMetrics.density

        val card = LinearLayout(this).apply {
            contentDescription = "Card de ${st.serviceType}"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { setMargins(0, 0, 0, 12) }
            orientation = VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundResource(R.drawable.card_bg)
            elevation = 2 * density
            clipToOutline = true
        }

        // Header with service type color bar and name
        card.addView(LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 14, 16, 14)
            setBackgroundColor(color)

            addView(TextView(this@AnalysisActivity).apply {
                text = "$icon ${st.serviceType}"
                textSize = 16f
                setTextColor(if (color == ctxColor(R.color.service_black) || color == ctxColor(R.color.service_uberx)) ctxColor(R.color.text_inverse) else ctxColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })

            addView(TextView(this@AnalysisActivity).apply {
                text = "${st.acceptedCount}/${st.offeredCount}"
                textSize = 13f
                setTextColor(if (color == ctxColor(R.color.service_black) || color == ctxColor(R.color.service_uberx)) ctxColor(R.color.text_inverse) else ctxColor(R.color.text_primary))
            })
        })

        // Body
        val body = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = VERTICAL
            setPadding(16, 12, 16, 16)
        }
        card.addView(body)

        // Earnings row
        addInfoRowService(body, "\uD83D\uDCB0 Faturamento", "R\$ ${FormatUtils.decimal(st.totalEarnings)}",
            ctxColor(R.color.text_primary), true)

        addInfoRowService(body, "\uD83D\uDCCA Aceitação", "${"%.1f".format(st.acceptanceRate)}%",
            if (st.acceptanceRate >= 70) ctxColor(R.color.success) else if (st.acceptanceRate >= 40) ctxColor(R.color.warning) else ctxColor(R.color.error))

        addInfoRowService(body, "\uD83D\uDCC8 R\$/km", "R\$ ${FormatUtils.decimal(st.avgPricePerKm)}",
            ctxColor(R.color.text_primary))

        addInfoRowService(body, "\u23F0 R\$/h", "R\$ ${FormatUtils.decimal(st.avgPricePerHour)}",
            ctxColor(R.color.text_secondary))

        // Comparison with overall average
        val diffPpk = st.avgPricePerKm - r.avgPricePerKm
        val diffPpkPct = if (r.avgPricePerKm > 0) (diffPpk / r.avgPricePerKm * 100) else 0.0
        val arrowPpk = if (diffPpk >= 0) "\u2191" else "\u2193"
        val diffColorPpk = if (diffPpk >= 0) ctxColor(R.color.success) else ctxColor(R.color.error)
        addInfoRowService(body, "vs. m\u00E9dia (R\$/km)", "$arrowPpk ${"%.1f".format(diffPpkPct)}%",
            diffColorPpk)

        addDivider(body)

        // Dynamic & Priority
        addInfoRowService(body, "\u26A1 Din\u00E2mica", "${"%.0f".format(st.dynamicPercent)}%",
            if (st.dynamicPercent >= 30) ctxColor(R.color.success) else ctxColor(R.color.text_secondary))
        addInfoRowService(body, "\u2B06 Prioridade", "${"%.0f".format(st.priorityPercent)}%",
            if (st.priorityPercent >= 30) ctxColor(R.color.success) else ctxColor(R.color.text_secondary))

        addDivider(body)

        // Score
        addInfoRowService(body, "\u2B50 Score bom", "${"%.0f".format(st.goodPercent)}%",
            if (st.goodPercent >= 60) ctxColor(R.color.success) else ctxColor(R.color.warning))

        // Lost count
        if (st.lostCount > 0) {
            addInfoRowService(body, "\u274C Perdidas", "${st.lostCount} (m\u00E9dia R\$ ${FormatUtils.decimal(st.avgLostValue)})",
                ctxColor(R.color.error))
        }

        // Best hour
        addInfoRowService(body, "\uD83C\uDFC6 Melhor hor\u00E1rio", st.bestHour,
            ctxColor(R.color.text_primary), true)

        return card
    }

    private fun addInfoRowService(container: LinearLayout, label: String, value: String, color: Int, bold: Boolean = false) {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 3, 0, 3)
        }
        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            text = label
            textSize = 12f
            setTextColor(ctxColor(R.color.text_secondary))
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        })
        container.addView(row)
    }

    // ─── CARD: Resumo Financeiro ───
    private fun buildResumoFinanceiro(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        val card = createCard("💰 RESUMO FINANCEIRO")

        val netProfit = r.totalEarnings - r.totalCost

        addInfoRow(card, "Faturamento bruto", "R$ ${FormatUtils.decimal(r.totalEarnings)}", ctxColor(R.color.text_primary))
        addInfoRow(card, "Custo total", "-R$ ${FormatUtils.decimal(r.totalCost)}", ctxColor(R.color.error))
        addDivider(card)
        addInfoRow(card, "Lucro líquido", "R$ ${FormatUtils.decimal(netProfit)}",
            if (netProfit >= 0) ctxColor(R.color.success) else ctxColor(R.color.error), bold = true)
        addInfoRow(card, "Custo por km", "R$ ${FormatUtils.decimal(r.totalCostPerKm)}", ctxColor(R.color.text_secondary))
        addInfoRow(card, "Margem líquida", "${"%.1f".format(r.profitMargin)}%",
            if (r.profitMargin >= 20) ctxColor(R.color.success) else if (r.profitMargin >= 0) ctxColor(R.color.warning) else ctxColor(R.color.error))

        container.addView(card)
    }

    private fun addInfoRow(container: LinearLayout, label: String, value: String, color: Int, bold: Boolean = false) {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
        }
        row.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            text = label
            textSize = 13f
            setTextColor(ctxColor(R.color.text_secondary))
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        })
        container.addView(row)
    }

    // ─── CARD: Distribuição de Scores ───
    private fun buildScoreDistribution(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        val card = createCard("🎯 DISTRIBUIÇÃO DAS OFERTAS")

        addText(card, "Total de ofertas: ${r.offeredCount}", 13f, ctxColor(R.color.text_primary))

        addDivider(card)

        val scoreContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = VERTICAL
        }

        addBar(scoreContainer, "✅ Boa (≥80%)", "${"%.0f".format(r.goodPercent)}%",
            (r.goodPercent / 100).toFloat(), ctxColor(R.color.success))
        addBar(scoreContainer, "⚠️ Média (50-79%)", "${"%.0f".format(r.mediumPercent)}%",
            (r.mediumPercent / 100).toFloat(), ctxColor(R.color.warning))
        addBar(scoreContainer, "❌ Ruim (<50%)", "${"%.0f".format(r.badPercent)}%",
            (r.badPercent / 100).toFloat(), ctxColor(R.color.error))

        card.addView(scoreContainer)

        addDivider(card)

        addText(card, "📊 Taxa de aceitação: ${"%.1f".format(r.acceptanceRate)}%", 13f,
            if (r.acceptanceRate >= 70) ctxColor(R.color.success) else ctxColor(R.color.warning), true)

        addText(card, buildString {
            append("→ Você aceitou ${r.acceptedCount} de ${r.offeredCount} corridas")
            if (r.offeredCount - r.acceptedCount > 0) {
                append("\n→ ${r.offeredCount - r.acceptedCount} corridas foram recusadas ou expiraram")
            }
        }, 11f, ctxColor(R.color.text_secondary))

        container.addView(card)
    }

    // ─── CARD: Comparativo com Período Anterior ───
    private fun buildComparativoPeriodo(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        if (r.previousPeriodEarnings <= 0 && r.previousPeriodRides <= 0) return

        val card = createCard("📈 COMPARATIVO COM PERÍODO ANTERIOR")

        val earningsDiff = r.totalEarnings - r.previousPeriodEarnings
        val earningsPct = if (r.previousPeriodEarnings > 0)
            (earningsDiff / r.previousPeriodEarnings) * 100 else 0.0

        val ridesDiff = r.acceptedCount - r.previousPeriodRides
        val ridesPct = if (r.previousPeriodRides > 0)
            (ridesDiff.toDouble() / r.previousPeriodRides) * 100 else 0.0

        addComparisonRow(card, "Faturamento",
            "R$ ${FormatUtils.decimal(r.totalEarnings)}",
            "R$ ${FormatUtils.decimal(r.previousPeriodEarnings)}",
            earningsDiff, earningsPct)

        addComparisonRow(card, "Corridas",
            "${r.acceptedCount}",
            "${r.previousPeriodRides}",
            ridesDiff.toDouble(), ridesPct)

        container.addView(card)
    }

    private fun addComparisonRow(container: LinearLayout, label: String, current: String, previous: String, diff: Double, pct: Double) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        headerRow.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.4f)
            text = label
            textSize = 12f
            setTextColor(ctxColor(R.color.text_secondary))
        })
        headerRow.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.6f)
            text = "Atual: $current  |  Anterior: $previous"
            textSize = 11f
            setTextColor(ctxColor(R.color.text_tertiary))
        })
        row.addView(headerRow)

        val arrowIcon = if (diff > 0) "▲" else if (diff < 0) "▼" else "●"
        val arrowColor = if (diff > 0) ctxColor(R.color.success) else if (diff < 0) ctxColor(R.color.error) else ctxColor(R.color.metric_absent)

        addText(row, "$arrowIcon ${"%.1f".format(Math.abs(pct))}% (${if (diff > 0) "+" else ""}${FormatUtils.decimal(diff)})",
            11f, arrowColor)

        container.addView(row)
    }

    // ─── CARD: Insights e Recomendações ───
    private fun buildInsights(r: AnalysisResultV2, container: LinearLayout = cardsContainer) {
        val insights = mutableListOf<String>()

        if (r.acceptanceRate < 50) {
            insights.add("⚠️ Sua taxa de aceitação está baixa (${"%.0f".format(r.acceptanceRate)}%). Reveja seus critérios de recusa.")
        } else if (r.acceptanceRate > 85) {
            insights.add("✅ Ótima taxa de aceitação! Continue assim.")
        }

        if (r.profitMargin < 20 && r.profitMargin >= 0) {
            insights.add("💰 Sua margem está abaixo do ideal (${"%.0f".format(r.profitMargin)}%). Busque corridas com R$/km mais alto.")
        } else if (r.profitMargin < 0) {
            insights.add("🔴 Você está tendo prejuízo! Revise seus custos ou aceite corridas com valor mais alto.")
        }

        val bestHour = r.hourlyData.maxByOrNull { it.avgPricePerKm }
        if (bestHour != null && bestHour.rideCount >= 3) {
            insights.add("⏰ Melhor horário para trabalhar: ${bestHour.hour}h (R$ ${FormatUtils.decimal(bestHour.avgPricePerKm)}/km)")
        }

        if (r.priorityImpact.count > 0 && r.priorityImpact.avgPricePerKm > r.priorityImpact.avgPricePerKmWithout) {
            val diff = r.priorityImpact.avgPricePerKm - r.priorityImpact.avgPricePerKmWithout
            insights.add("✨ Prioridade aumenta seu R$/km em R$ ${FormatUtils.decimal(diff)}. Vale a pena priorizar!")
        }

        if (r.lostRides.lostCount > 0 && r.lostRides.avgLostPricePerKm > 0) {
            insights.add("📉 Você deixou de ganhar R$ ${FormatUtils.decimal(r.lostRides.avgLostValue)} em média por corrida não aceita.")
        }

        if (insights.isEmpty()) {
            insights.add("📊 Continue monitorando. Nenhum insight crítico no momento.")
        }

        val card = createCard("💡 INSIGHTS E RECOMENDAÇÕES")

        for (insight in insights.take(5)) {
            addText(card, insight, 12f,
                if (insight.startsWith("✅") || insight.startsWith("✨")) ctxColor(R.color.success)
                else if (insight.startsWith("⚠️") || insight.startsWith("📉")) ctxColor(R.color.warning)
                else if (insight.startsWith("🔴")) ctxColor(R.color.error)
                else ctxColor(R.color.text_primary))

            if (insight != insights.last()) {
                addDivider(card)
            }
        }

        container.addView(card)
    }

    private fun addDetailRow(
        container: LinearLayout,
        icon: String,
        label: String,
        value: String,
        color: Any = false
    ) {
        val row = LinearLayout(this).apply {
            contentDescription = "$icon $label: $value"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 2, 16, 2)
        }

        row.addView(TextView(this).apply {
            contentDescription = null
            text = icon
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(28, WRAP_CONTENT)
        })

        row.addView(TextView(this).apply {
            contentDescription = null
            text = "$label: "
            textSize = 12f
            setTextColor(ctxColor(R.color.text_tertiary))
        })

        val textColor = when (color) {
            is Int -> color
            is Boolean -> if (color) ctxColor(R.color.text_primary) else ctxColor(R.color.text_tertiary)
            else -> ctxColor(R.color.text_primary)
        }

        row.addView(TextView(this).apply {
            contentDescription = null
            text = value
            textSize = 12f
            setTextColor(textColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        container.addView(row)
    }

    // ─── CARD: Simulador de Floor de Aceitação ───
    private fun buildFloorSimulation(r: AnalysisResultV2, container: LinearLayout) {
        val sim = r.floorSimulation ?: return
        if (sim.scenarios.isEmpty()) return

        val rec = sim.scenarios.firstOrNull { it.thresholdPerKm == sim.recommendedThreshold }

        val card = createCard("🎯 Simulador de Aceitação")

        // 1. Pergunta principal
        addText(card,
            "Qual valor mínimo você deve aceitar por km para ganhar mais?",
            spFromRes(R.dimen.text_size_14), ctxColor(R.color.text_primary), bold = true)

        addText(card,
            "Baseado em ${sim.totalRides} corridas recebidas, respeitando janela de hor\u00E1rio de cada dia",
            spFromRes(R.dimen.text_size_10), ctxColor(R.color.text_secondary))

        addDivider(card)

        // 2. Cenário recomendado (destaque)
        if (rec != null) {
            val highlight = createHighlightCard(
                icon = "✅",
                title = "Melhor para voc\u00EA: aceitar acima de R\$ ${FormatUtils.decimal(rec.thresholdPerKm)}/km",
                metrics = listOf(
                    Triple("\uD83D\uDCCA", "${rec.acceptedCount} de ${sim.totalRides}", "corridas vi\u00E1veis"),
                    Triple("\uD83D\uDCB0", "R\$ ${FormatUtils.decimal(rec.totalEarnings)}", "faturamento"),
                    Triple("\u23F1\uFE0F", "${formatNetValue(rec.effectivePerHour)}/h", "l\u00EDquido/hora")
                ),
                delta = rec.netGainVsActual
            )
            card.addView(highlight)
            addDivider(card)
        }

        // Congratulatory or tip message comparing user's actual avg to recommended
        if (r.avgPricePerKm >= sim.recommendedThreshold) {
            addText(card,
                "\uD83C\uDF89 Parab\u00E9ns! Sua m\u00E9dia de R\$ ${FormatUtils.decimal(r.avgPricePerKm)}/km j\u00E1 est\u00E1 alinhada com a melhor estrat\u00E9gia. Continue selecionando bem as corridas!",
                spFromRes(R.dimen.text_size_11), ctxColor(R.color.success_text), bold = false)
        } else {
            addText(card,
                "\uD83D\uDCA1 Sua m\u00E9dia atual \u00E9 de R\$ ${FormatUtils.decimal(r.avgPricePerKm)}/km. Tente filtrar corridas abaixo de R\$ ${FormatUtils.decimal(sim.recommendedThreshold)}/km para melhorar seus ganhos.",
                spFromRes(R.dimen.text_size_11), ctxColor(R.color.text_secondary), bold = false)
        }
        addDivider(card)

        // 3. Tabela comparativa
        val density2 = resources.displayMetrics.density
        val rowPadH2 = dimenPx(R.dimen.card_padding).toInt() / 4
        val rowPadV2 = dimenPx(R.dimen.card_padding).toInt() / 3

        val weights = listOf(0.18f, 0.15f, 0.20f, 0.20f, 0.27f)
        val headers = listOf("S\u00F3 aceito\nacima de", "Corridas\nvi\u00E1veis", "Ganho m\u00E9dio\npor km", "Ganho\npor hora", "Diferen\u00E7a")

        val headerRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = HORIZONTAL
            setPadding(rowPadH2, rowPadV2, rowPadH2, rowPadV2)
            val bg = GradientDrawable().apply {
                setColor(ctxColor(R.color.bg_surface))
                cornerRadius = 6 * density2
            }
            background = bg
        }
        headers.forEachIndexed { i, label ->
            headerRow.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, weights[i])
                text = label
                textSize = spFromRes(R.dimen.text_size_9)
                setTextColor(ctxColor(R.color.text_tertiary))
                if (i > 0) gravity = Gravity.END
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 2
            })
        }
        card.addView(headerRow)

        for (s in sim.scenarios) {
            val isRecommended = s.thresholdPerKm == sim.recommendedThreshold
            val isBelowBreakeven = s.thresholdPerKm < sim.breakEvenKm
            val vals = listOf(
                "R$ ${FormatUtils.decimal(s.thresholdPerKm)}" to getRowColor(isRecommended, isBelowBreakeven),
                "${s.acceptedCount}"                            to ctxColor(R.color.text_secondary),
                "R$ ${FormatUtils.decimal(s.avgPricePerKm)}"   to ctxColor(R.color.text_primary),
                formatNetValue(s.effectivePerHour)              to getDeltaColor(s.effectivePerHour),
                formatDelta(s.netGainVsActual)                  to getDeltaColor(s.netGainVsActual)
            )

            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = HORIZONTAL
                setPadding(rowPadH2, rowPadV2, rowPadH2, rowPadV2)
                if (isRecommended || isBelowBreakeven) {
                    val bg = GradientDrawable().apply {
                        setColor(if (isRecommended) ctxColor(R.color.success_bg) else ctxColor(R.color.error_bg))
                        cornerRadius = 6 * density2
                    }
                    background = bg
                }
            }
            vals.forEachIndexed { i, (text, color) ->
                row.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, weights[i])
                    this.text = text
                    textSize = spFromRes(R.dimen.text_size_11)
                    setTextColor(color)
                    if (isRecommended) setTypeface(null, android.graphics.Typeface.BOLD)
                    if (i > 0) gravity = Gravity.END
                })
            }
            card.addView(row)
        }

        // 4. Legenda explicativa
        addDivider(card)

        addText(card, "\uD83D\uDCA1 Como funciona:",
            spFromRes(R.dimen.text_size_11), ctxColor(R.color.text_secondary), bold = true)

        addText(card, "Em cada dia, o simulador calcula quantas corridas cabem na sua janela de trabalho (primeira \u00E0 \u00FAltima oferta do dia, desconsiderando pausas). Depois, para cada cen\u00E1rio, seleciona as melhores corridas (maior R\$/km) que voc\u00EA conseguiria fazer dentro desse tempo.",
            spFromRes(R.dimen.text_size_10), ctxColor(R.color.text_tertiary))

        addDivider(card)

        addText(card, "\uD83D\uDCCB Entendendo a tabela:",
            spFromRes(R.dimen.text_size_11), ctxColor(R.color.text_secondary), bold = true)

        val legendTerms = listOf(
            "\uD83D\uDD39 \"S\u00F3 aceito acima de\" \u2192 O valor m\u00EDnimo que voc\u00EA decide aceitar por km",
            "\uD83D\uDD39 \"Corridas vi\u00E1veis\" \u2192 Quantas corridas acima desse valor caberiam no seu hor\u00E1rio (respeitando o tempo m\u00E9dio de cada uma)",
            "\uD83D\uDD39 \"Ganho m\u00E9dio por km\" \u2192 A m\u00E9dia real do que voc\u00EA ganharia por km nessas corridas",
            "\uD83D\uDD39 \"Ganho por hora\" \u2192 Quanto sobraria por hora trabalhada j\u00E1 descontando o custo do carro",
            "\uD83D\uDD39 \"Diferen\u00E7a\" \u2192 Quanto voc\u00EA ganharia a mais ou a menos comparado \u00E0 m\u00E9dia geral"
        )
        for (item in legendTerms) {
            addText(card, item, spFromRes(R.dimen.text_size_10), ctxColor(R.color.text_tertiary))
        }

        // 5. Legenda de cores
        addDivider(card)

        fun addColorLegend(color: Int, label: String) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 2, 0, 2)
            }
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams((4 * density2).toInt(), (4 * density2).toInt())
                    .apply { rightMargin = 6 }
                val bg = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 2 * density2
                }
                background = bg
            })
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                text = label
                textSize = spFromRes(R.dimen.text_size_10)
                setTextColor(ctxColor(R.color.text_secondary))
            })
            card.addView(row)
        }

        addColorLegend(ctxColor(R.color.success_bg), "✅ Verde = melhor opção para você")
        addColorLegend(ctxColor(R.color.error_bg), "⚠️ Vermelho = abaixo do custo do carro (prejuízo)")
        addColorLegend(ctxColor(R.color.text_tertiary), "🔘 Cinza = opções intermediárias")

        // 6. Footer
        addDivider(card)

        addText(card,
            "💰 Custo do seu carro por km: R$ ${FormatUtils.decimal(sim.breakEvenKm)}/km",
            spFromRes(R.dimen.text_size_10), ctxColor(R.color.text_tertiary))

        addText(card,
            "⚠️ Aceitar abaixo disso = você está perdendo dinheiro",
            spFromRes(R.dimen.text_size_10), ctxColor(R.color.warning))

        // 7. Projeção financeira do cenário recomendado (atual vs projetado)
        if (rec != null) {
            addDivider(card)

            val currentEarnings = r.totalEarnings
            val currentKm = r.totalKm
            val currentCost = currentKm * sim.breakEvenKm
            val currentNet = currentEarnings - currentCost

            val recEarnings = rec.totalEarnings
            val recKm = rec.totalKm
            val recCost = recKm * sim.breakEvenKm
            val recNet = recEarnings - recCost

            val additionalNet = recNet - currentNet

            val density = resources.displayMetrics.density
            val padH = dimenPx(R.dimen.card_padding).toInt() / 3
            val padV = dimenPx(R.dimen.card_padding).toInt() / 3

            val financeBox = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = VERTICAL
                setPadding(padH, padV, padH, padV)
                val bg = GradientDrawable().apply {
                    setColor(ctxColor(R.color.bg_surface))
                    cornerRadius = 6 * density
                }
                background = bg
            }

            financeBox.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                text = "📊 Projeção — Cenário recomendado"
                textSize = spFromRes(R.dimen.text_size_11)
                setTextColor(ctxColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            val colW = listOf(0.35f, 0.22f, 0.22f, 0.21f)

            val fHeaderRow = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = HORIZONTAL
                setPadding(0, 3, 0, 3)
            }
            listOf("", "Atual", "Projetado", "Δ").forEachIndexed { i, label ->
                fHeaderRow.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, colW[i])
                    text = label
                    textSize = spFromRes(R.dimen.text_size_9)
                    setTextColor(ctxColor(R.color.text_tertiary))
                    if (i > 0) gravity = Gravity.END
                })
            }
            financeBox.addView(fHeaderRow)

            fun addProjectionRow(label: String, current: Double, projected: Double, negate: Boolean = false) {
                val cv = if (negate) -current else current
                val pv = if (negate) -projected else projected
                val delta = pv - cv
                val row = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    orientation = HORIZONTAL
                    setPadding(0, 3, 0, 3)
                }
                row.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, colW[0])
                    text = label
                    textSize = spFromRes(R.dimen.text_size_10)
                    setTextColor(ctxColor(R.color.text_secondary))
                })
                row.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, colW[1])
                    text = "R\$ ${FormatUtils.decimal(cv)}"
                    textSize = spFromRes(R.dimen.text_size_10)
                    setTextColor(if (cv >= 0) ctxColor(R.color.text_primary) else ctxColor(R.color.error))
                    gravity = Gravity.END
                })
                row.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, colW[2])
                    text = "R\$ ${FormatUtils.decimal(pv)}"
                    textSize = spFromRes(R.dimen.text_size_10)
                    setTextColor(if (pv >= 0) ctxColor(R.color.success) else ctxColor(R.color.error))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.END
                })
                row.addView(TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, colW[3])
                    text = if (delta >= 0) "+R\$ ${FormatUtils.decimal(delta)}" else "-R\$ ${FormatUtils.decimal(-delta)}"
                    textSize = spFromRes(R.dimen.text_size_9)
                    setTextColor(if (delta >= 0) ctxColor(R.color.success) else ctxColor(R.color.error))
                    gravity = Gravity.END
                })
                financeBox.addView(row)
            }

            addProjectionRow("Receita bruta", currentEarnings, recEarnings)
            addProjectionRow("Custo total (km)", currentCost, recCost, negate = true)
            addProjectionRow("Lucro líquido", currentNet, recNet)

            addDivider(financeBox)

            val deltaRow = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = HORIZONTAL
                setPadding(0, 3, 0, 3)
            }
            deltaRow.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, colW[0])
                text = "A mais de lucro"
                textSize = spFromRes(R.dimen.text_size_10)
                setTextColor(ctxColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            deltaRow.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, colW[1] + colW[2])
                text = ""
            })
            deltaRow.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, colW[3])
                text = if (additionalNet >= 0) "+R\$ ${FormatUtils.decimal(additionalNet)}" else "-R\$ ${FormatUtils.decimal(-additionalNet)}"
                textSize = spFromRes(R.dimen.text_size_10)
                setTextColor(if (additionalNet >= 0) ctxColor(R.color.success) else ctxColor(R.color.error))
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
            })
            financeBox.addView(deltaRow)

            card.addView(financeBox)
        }

        container.addView(card)
    }

    private fun createHighlightCard(
        icon: String,
        title: String,
        metrics: List<Triple<String, String, String>>,
        delta: Double
    ): LinearLayout {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = VERTICAL
            setPadding(12, 12, 12, 12)
            val bg = GradientDrawable().apply {
                setColor(ctxColor(R.color.success_bg))
                cornerRadius = 12 * density
                setStroke((2 * density).toInt(), ctxColor(R.color.success))
            }
            background = bg

            addView(TextView(this@AnalysisActivity).apply {
                text = "$icon $title"
                textSize = 15f
                setTextColor(ctxColor(R.color.success_text))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    .apply { setMargins(0, 0, 0, 4) }
            })

            val metricsRow = LinearLayout(this@AnalysisActivity).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }
            for ((icon, value, label) in metrics) {
                val item = LinearLayout(this@AnalysisActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                }
                item.addView(TextView(this@AnalysisActivity).apply {
                    text = icon
                    textSize = 18f
                })
                item.addView(TextView(this@AnalysisActivity).apply {
                    text = value
                    textSize = 16f
                    setTextColor(ctxColor(R.color.success_text))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                item.addView(TextView(this@AnalysisActivity).apply {
                    text = label
                    textSize = spFromRes(R.dimen.text_size_10)
                    setTextColor(ctxColor(R.color.text_secondary))
                })
                metricsRow.addView(item)
            }
            addView(metricsRow)

            val deltaText = formatDelta(delta)
            val deltaColor = getDeltaColor(delta)
            addView(TextView(this@AnalysisActivity).apply {
                text = "$deltaText por dia vs sua média atual"
                textSize = spFromRes(R.dimen.text_size_11)
                setTextColor(deltaColor)
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    .apply { topMargin = 4 }
            })
        }
    }

    private fun getRowColor(isRecommended: Boolean, isBelowBreakeven: Boolean): Int {
        return when {
            isRecommended -> ctxColor(R.color.success_text)
            isBelowBreakeven -> ctxColor(R.color.error)
            else -> ctxColor(R.color.text_primary)
        }
    }

    private fun formatDelta(value: Double): String {
        return if (value >= 0) "+R\$ ${FormatUtils.decimal(value)}"
        else "-R\$ ${FormatUtils.decimal(-value)}"
    }

    private fun formatNetValue(value: Double): String {
        return if (value >= 0) "R\$ ${FormatUtils.decimal(value)}"
        else "-R\$ ${FormatUtils.decimal(-value)}"
    }

    private fun getDeltaColor(value: Double): Int {
        return when {
            value > 0 -> ctxColor(R.color.success)
            value < 0 -> ctxColor(R.color.error)
            else -> ctxColor(R.color.text_tertiary)
        }
    }

    // ─── CARD: Breakeven Dinâmico por Corrida ───
    private fun buildBreakevenAnalysis(r: AnalysisResultV2, container: LinearLayout) {
        val be = r.breakevenAnalysis ?: return
        if (be.aboveBreakeven.isEmpty() && be.belowBreakeven.isEmpty()) return

        val card = createCard("Breakeven por corrida")

        addInfoRow(card, "Custo do veículo",
            "R\$ ${FormatUtils.decimal(be.costPerKm)}/km",
            ctxColor(R.color.text_secondary))

        addInfoRow(card,
            "Corridas acima do custo",
            "${be.aboveBreakeven.size} (${"%.0f".format(be.pctAbove)}%)",
            ctxColor(R.color.success))

        addInfoRow(card,
            "Corridas abaixo do custo",
            "${be.belowBreakeven.size}",
            if (be.belowBreakeven.isEmpty()) ctxColor(R.color.text_tertiary) else ctxColor(R.color.error))

        addDivider(card)

        addInfoRow(card, "Lucro líquido (corridas boas)",
            "+R\$ ${FormatUtils.decimal(be.totalNetProfit)}",
            ctxColor(R.color.success), bold = true)

        if (be.totalNetLoss > 0) {
            addInfoRow(card, "Prejuízo acumulado (corridas ruins)",
                "-R\$ ${FormatUtils.decimal(be.totalNetLoss)}",
                ctxColor(R.color.error), bold = true)
        }

        addInfoRow(card, "Saldo real do período",
            "${if (be.netBalance >= 0) "+" else ""}R\$ ${FormatUtils.decimal(be.netBalance)}",
            if (be.netBalance >= 0) ctxColor(R.color.success) else ctxColor(R.color.error),
            bold = true)

        if (be.belowBreakeven.isNotEmpty()) {
            addDivider(card)
            addText(card, "Corridas que custaram mais do que renderam:",
                spFromRes(R.dimen.text_size_12), ctxColor(R.color.text_primary), bold = true)

            for (ride in be.belowBreakeven.take(5)) {
                val label = buildString {
                    append("R\$ ${FormatUtils.decimal(ride.pricePerKm)}/km")
                    if (ride.origin.isNotBlank()) append(" · ${ride.origin}")
                    if (ride.hour >= 0) append(" · ${ride.hour}h")
                }
                addInfoRow(card, label,
                    "-R\$ ${FormatUtils.decimal(-ride.netResult)}",
                    ctxColor(R.color.error))
            }

            if (be.belowBreakeven.size > 5) {
                addText(card, "+ ${be.belowBreakeven.size - 5} corridas não exibidas",
                    spFromRes(R.dimen.text_size_10), ctxColor(R.color.text_tertiary))
            }

            if (be.worstHour >= 0) {
                addText(card, "Horário com mais corridas no prejuízo: ${be.worstHour}h",
                    spFromRes(R.dimen.text_size_11), ctxColor(R.color.warning))
            }
        }

        if (be.bestHour >= 0) {
            addText(card,
                "Melhor horário: ${be.bestHour}h (margem média +R\$ ${FormatUtils.decimal(be.avgMarginAbove)}/km)",
                spFromRes(R.dimen.text_size_11), ctxColor(R.color.success))
        }

        addDivider(card)
        addText(card,
            "Custo/km vem dos seus custos cadastrados (fixos + por km + por evento).",
            spFromRes(R.dimen.text_size_10), ctxColor(R.color.text_tertiary))

        container.addView(card)
    }

    private fun getDayName(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.MONDAY -> "Segunda-feira"
        Calendar.TUESDAY -> "Ter\u00E7a-feira"
        Calendar.WEDNESDAY -> "Quarta-feira"
        Calendar.THURSDAY -> "Quinta-feira"
        Calendar.FRIDAY -> "Sexta-feira"
        Calendar.SATURDAY -> "S\u00E1bado"
        Calendar.SUNDAY -> "Domingo"
        else -> "?"
    }

}
