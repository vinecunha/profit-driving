package com.profitdriving

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AnalysisActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var cardsContainer: LinearLayout
    private lateinit var skeletonContainer: LinearLayout
    private lateinit var scrollView: View
    private lateinit var btnModeDay: TextView
    private lateinit var btnModeWeek: TextView
    private lateinit var btnModeMonth: TextView
    private lateinit var btnPrevPeriod: TextView
    private lateinit var btnNextPeriod: TextView
    private lateinit var btnToday: TextView
    private lateinit var tvPeriodTitle: TextView

    private var currentMode = ViewMode.DAY
    private var referenceCalendar = Calendar.getInstance()
    private val handler = Handler(Looper.getMainLooper())

    private val dayFormatter = SimpleDateFormat("dd 'de' MMMM, yyyy", Locale("pt", "BR"))
    private val weekFormatter = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
    private val monthFormatter = SimpleDateFormat("MMMM, yyyy", Locale("pt", "BR"))

    private val GREEN = Color.parseColor("#00A86B")
    private val ORANGE = Color.parseColor("#F59E0B")
    private val RED = Color.parseColor("#EF4444")
    private val BLUE = Color.parseColor("#3B82F6")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)
        setupBottomNav(Screen.ANALYSIS)
        setupToolbar(title = "An\u00E1lise", showBack = true)

        cardsContainer = findViewById(R.id.cardsContainer)
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

        setupPeriodNavigation()
        setViewMode(ViewMode.DAY)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun loadDataForCurrentPeriod() {
        showSkeleton()
        val (periodStart, periodEnd) = getPeriodTimestamps()

        Thread {
            val records = db.getFiltered(periodStart)
            val dailyRides = db.getDailyRidesByDateRange(periodStart, periodEnd)
            val costPerKm = kotlinx.coroutines.runBlocking {
                CostSummaryCache.getCurrentSummary(this@AnalysisActivity).totalCostPerKm
            }
            val result = AnalysisHelperV2.calculate(records, dailyRides, costPerKm)
            handler.post {
                hideSkeleton()
                buildCards(result)
            }
        }.start()
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

        btnModeDay.setTextColor(if (currentMode == ViewMode.DAY) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        btnModeWeek.setTextColor(if (currentMode == ViewMode.WEEK) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        btnModeMonth.setTextColor(if (currentMode == ViewMode.MONTH) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
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

    private fun buildCards(r: AnalysisResultV2) {
        cardsContainer.removeAllViews()
        cardIndex = 0

        // NÍVEL 1 - VISÃO GERAL
        buildVisaoGeral(r)
        buildResumoFinanceiro(r)
        buildComparativoPeriodo(r)
        addSpacer()

        // NÍVEL 2 - QUALIDADE DAS OFERTAS
        buildScoreDistribution(r)
        buildLostRides(r)
        buildAcceptanceByValue(r)
        addSpacer()

        // NÍVEL 3 - FATORES DE SUCESSO
        buildBonusImpact(r)
        buildMelhorHorario(r)
        buildDinamicaHorario(r)
        buildCidades(r)
        buildBairros(r)
        addSpacer()

        // NÍVEL 4 - PROJEÇÕES
        buildHourlyForecast(r)
        buildDynamicTrend(r)
        buildWeekdayRanking(r)
        addSpacer()

        // NÍVEL 5 - INSIGHTS E OTIMIZAÇÃO
        buildDailyProjection(r)
        buildInsights(r)
    }

    private fun addSpacer() {
        cardsContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 16)
        })
    }

    private var cardIndex = 0

    private val cardBgColors = listOf(
        "#FFFFFF", "#F0FDF4", "#FFF7ED", "#EFF6FF",
        "#FEF2F2", "#F5F3FF", "#FFF9E6"
    )

    private fun createCard(title: String): LinearLayout {
        val bg = cardBgColors[cardIndex % cardBgColors.size]
        cardIndex++
        return LinearLayout(this).apply {
            contentDescription = "Card de análise: $title"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            orientation = VERTICAL
            setPadding(16, 12, 16, 16)
            val d = GradientDrawable().apply {
                setColor(Color.parseColor(bg))
                cornerRadius = 12 * resources.displayMetrics.density
            }
            background = d
            elevation = 2 * resources.displayMetrics.density

            addView(TextView(this@AnalysisActivity).apply {
                contentDescription = "Título: $title"
                layoutParams = LinearLayout.LayoutParams(
                    MATCH_PARENT, WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 10) }
                text = title
                textSize = 16f
                setTextColor(Color.BLACK)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
        }
    }

    private fun addText(
        card: LinearLayout, text: String, size: Float = 13f,
        color: Int = Color.parseColor("#444444"), bold: Boolean = false
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
            setTextColor(Color.parseColor("#555555"))
        })

        val barOuter = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 14, 0.55f)
            val g = GradientDrawable().apply {
                setColor(Color.parseColor("#E0E0E0"))
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
            setTextColor(Color.parseColor("#333333"))
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
            setTextColor(Color.parseColor("#555555"))
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
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.END
        })

        container.addView(row)
    }

    private fun addDivider(card: LinearLayout) {
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
                .apply { setMargins(0, 6, 0, 6) }
            setBackgroundColor(Color.parseColor("#E5E9F0"))
        })
    }

    // ─── CARD 1: Visão Geral ───
    private fun buildVisaoGeral(r: AnalysisResultV2) {
        val card = createCard("\uD83D\uDCCA Vis\u00E3o Geral")

        addText(card, "\uD83D\uDCCB Corridas ofertadas: ${r.offeredCount}", 13f, Color.parseColor("#333333"))
        addText(card, "\u2705 Corridas aceitas: ${r.acceptedCount} (${"%.1f".format(r.acceptanceRate)}%)", 13f, GREEN)
        addText(card, "\uD83D\uDCB0 Faturamento bruto: R\$ ${AnalysisHelperV2.formatBr(r.totalEarnings)}", 13f, Color.parseColor("#333333"))
        addText(card, "\uD83D\uDEE3\uFE0F Km total: ${AnalysisHelperV2.formatBr1(r.totalKm)} km", 13f, Color.parseColor("#333333"))
        addText(card, "\u23F1\uFE0F Tempo total: ${AnalysisHelperV2.hoursMinutes(r.totalMinutes)}", 13f, Color.parseColor("#333333"))

        addDivider(card)
        addText(card, "\uD83D\uDCCA M\u00E9dias", 13f, Color.parseColor("#333333"), true)
        addText(card, "R\$/km m\u00E9dio: R\$ ${AnalysisHelperV2.formatBr(r.avgPricePerKm)}", 12f, Color.parseColor("#555555"))
        addText(card, "R\$/h m\u00E9dio: R\$ ${AnalysisHelperV2.formatBr(r.avgPricePerHour)}", 12f, Color.parseColor("#555555"))
        addText(card, "Nota m\u00E9dia: ${"%.2f".format(r.avgRating)}", 12f, Color.parseColor("#555555"))

        cardsContainer.addView(card)
    }

    // ─── CARD 2: Impacto dos Bônus ───
    private fun buildBonusImpact(r: AnalysisResultV2) {
        val card = createCard("\uD83D\uDCB0 Impacto dos B\u00F4nus")
        val MIN_DATA = 3

        addText(card, "Comparativo de corridas com e sem b\u00F4nus", 12f, Color.parseColor("#666666"))

        addDivider(card)

        val p = r.priorityImpact
        addText(card, "\u2B06 Prioridade", 13f, Color.parseColor("#333333"), true)
        if (p.count >= MIN_DATA) {
            val diffP = maxOf(p.avgPricePerKm - p.avgPricePerKmWithout, 0.0)
            val diffPctP = if (p.avgPricePerKmWithout > 0) (diffP / p.avgPricePerKmWithout * 100) else 0.0
            val goodDiffP = maxOf(p.goodRatePercent - r.goodPercent, 0.0)
            addText(card, buildString {
                append("Presente em ${"%.0f".format(p.percentage)}% das corridas")
            }, 12f, Color.parseColor("#555555"))
            addText(card, buildString {
                append("R\$/km m\u00E9dio: R\$ ${AnalysisHelperV2.formatBr(p.avgPricePerKm)}")
                append("  (sem: R\$ ${AnalysisHelperV2.formatBr(p.avgPricePerKmWithout)})")
            }, 12f, Color.parseColor("#555555"))
            addText(card, buildString {
                append("\u2B06 ${"%.0f".format(diffPctP)}% melhor R\$/km  |  ${"%.0f".format(goodDiffP)}% mais corridas \"boas\"")
            }, 12f, GREEN, true)
        } else {
            addText(card, "Dados insuficientes (m\u00EDn. $MIN_DATA corridas com prioridade)", 12f, Color.parseColor("#999999"))
        }

        addDivider(card)

        val d = r.dynamicImpact
        addText(card, "\u26A1 Din\u00E2mica", 13f, Color.parseColor("#333333"), true)
        if (d.count >= MIN_DATA) {
            val diffD = maxOf(d.avgPricePerKm - d.avgPricePerKmWithout, 0.0)
            val diffPctD = if (d.avgPricePerKmWithout > 0) (diffD / d.avgPricePerKmWithout * 100) else 0.0
            val goodDiffD = maxOf(d.goodRatePercent - r.goodPercent, 0.0)
            addText(card, buildString {
                append("Presente em ${"%.0f".format(d.percentage)}% das corridas")
            }, 12f, Color.parseColor("#555555"))
            addText(card, buildString {
                append("R\$/km m\u00E9dio: R\$ ${AnalysisHelperV2.formatBr(d.avgPricePerKm)}")
                append("  (sem: R\$ ${AnalysisHelperV2.formatBr(d.avgPricePerKmWithout)})")
            }, 12f, Color.parseColor("#555555"))
            addText(card, buildString {
                append("\u2B06 ${"%.0f".format(diffPctD)}% melhor R\$/km  |  ${"%.0f".format(goodDiffD)}% mais corridas \"boas\"")
            }, 12f, GREEN, true)
        } else {
            addText(card, "Dados insuficientes (m\u00EDn. $MIN_DATA corridas com din\u00E2mica)", 12f, Color.parseColor("#999999"))
        }

        addDivider(card)
        addText(card, "\uD83D\uDCA1 O que significa? B\u00F4nus de prioridade s\u00E3o oferecidos pela Uber para incentivar aceita\u00E7\u00E3o em \u00E1reas de alta demanda. O indicador mostra o impacto real no ganho por km \u2014 quanto maior a diferen\u00E7a, mais vale a pena priorizar essas corridas.",
            11f, Color.parseColor("#888888"))

        cardsContainer.addView(card)
    }

    // ─── CARD 3: Melhor Horário ───
    private fun buildMelhorHorario(r: AnalysisResultV2) {
        val card = createCard("\u23F0 Melhor hor\u00E1rio para aceitar")
        val bestData = r.hourlyData.find { it.hour == r.bestHourToAccept }

        if (bestData != null && bestData.rideCount > 0) {
            addText(card, buildString {
                append("\u2B50 Melhor hor\u00E1rio: ${bestData.hour}h \u00E0s ${(bestData.hour + 1) % 24}h")
                append("  |  R\$ ${AnalysisHelperV2.formatBr(bestData.avgPricePerKm)}/km")
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
                addBar(hContainer, "${h.hour}h", "R\$ ${AnalysisHelperV2.formatBr(h.avgPricePerKm)}",
                    (h.avgPricePerKm / maxVal).toFloat())
            }
            card.addView(hContainer)
        } else {
            addText(card, "Nenhuma corrida aceita no per\u00EDodo", 12f, Color.parseColor("#999999"))
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 4: Dinâmica por Horário ───
    private fun buildDinamicaHorario(r: AnalysisResultV2) {
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
            addText(card, "Nenhuma corrida com din\u00E2mica no per\u00EDodo", 12f, Color.parseColor("#999999"))
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 5: Melhores Cidades ───
    private fun buildCidades(r: AnalysisResultV2) {
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
                setTextColor(Color.parseColor("#333333"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            header.addView(TextView(this).apply {
                text = "${c.rideCount} corridas"
                textSize = 11f
                setTextColor(Color.parseColor("#888888"))
            })
            row.addView(header)

            addText(row, buildString {
                append("R\$/km: R\$ ${AnalysisHelperV2.formatBr(c.avgPricePerKm)}")
                append("  |  Din\u00E2mica: ${"%.0f".format(c.dynamicPercentage)}%")
                append("  |  Melhor hor\u00E1rio: ${c.bestHour}h")
            }, 11f, Color.parseColor("#666666"))

            if (c != r.topCities.take(6).last()) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
                        .apply { setMargins(0, 4, 0, 4) }
                    setBackgroundColor(Color.parseColor("#F0F0F0"))
                })
            }

            card.addView(row)
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 6: Melhores Bairros ───
    private fun buildBairros(r: AnalysisResultV2) {
        if (r.topNeighborhoods.isEmpty()) return
        val card = createCard("\uD83C\uDFE0 Melhores bairros")

        card.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 8)
            }
            text = "\uD83D\uDCCA R\$/km = m\u00E9dia nas corridas aceitas | \uD83D\uDD25 % = corridas com din\u00E2mica no total ofertado"
            textSize = 10f
            setTextColor(Color.parseColor("#8E9AAF"))
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
                setTextColor(Color.parseColor("#333333"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                text = "R\$ ${AnalysisHelperV2.formatBr(n.avgPricePerKm)} | ${"%.0f".format(n.dynamicPercentage)}%"
                textSize = 11f
                setTextColor(Color.parseColor("#666666"))
            })

            card.addView(row)
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 7: Previsão de Ganhos por Horário ───
    private fun buildHourlyForecast(r: AnalysisResultV2) {
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
                setTextColor(Color.parseColor("#1A2C3E"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })

            addDetailRow(hourContainer, "\uD83D\uDCB0", "Ganho m\u00E9dio",
                "R\$ ${AnalysisHelperV2.formatBr(hour.avgEarningsPerHour)}/hora", Color.parseColor("#1A2C3E"))
            addDetailRow(hourContainer, "\uD83D\uDE97", "Corridas", "${hour.rideCount}", Color.parseColor("#1A2C3E"))

            val dinamicaColor = if (hour.dynamicPercent >= 30) Color.parseColor("#00A86B") else Color.parseColor("#F97316")
            addDetailRow(hourContainer, "\u26A1", "Din\u00E2mica",
                "${String.format("%.0f", hour.dynamicPercent)}%", dinamicaColor)

            val fraction = (hour.avgEarningsPerHour / maxGanho).toFloat()
            val progressContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 8)
                    .apply { topMargin = 8 }
                val bg = GradientDrawable()
                bg.setColor(Color.parseColor("#E5E7EB"))
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
                        fillBg.setColor(Color.parseColor("#00A86B"))
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
                    setBackgroundColor(Color.parseColor("#EEF2F6"))
                })
            }

            card.addView(hourContainer)
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 8: Análise de Aceitação por Valor ───
    private fun buildAcceptanceByValue(r: AnalysisResultV2) {
        if (r.acceptanceByValue.isEmpty()) return
        val card = createCard("\u2705 An\u00E1lise de aceita\u00E7\u00E3o por valor")

        val maxFrac = r.acceptanceByValue.maxOfOrNull { it.barFraction }?.coerceAtLeast(0.01f) ?: 1f
        for (v in r.acceptanceByValue) {
            val line = "${v.rangeLabel}  ${v.offered} ofertadas \u2192 ${v.accepted} aceitas (${"%.0f".format(v.acceptanceRate)}%)"
            addBarSimple(card, v.rangeLabel,
                "${v.offered} ofertadas \u2192 ${v.accepted} aceitas (${"%.0f".format(v.acceptanceRate)}%)",
                v.barFraction / maxFrac, BLUE)
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 9: Corridas Perdidas ───
    private fun buildLostRides(r: AnalysisResultV2) {
        val lost = r.lostRides
        if (lost.lostCount == 0) return
        val card = createCard("\u274C Corridas perdidas (n\u00E3o aceitas)")

        addText(card, "\uD83D\uDCB0 Valor m\u00E9dio perdido: R\$ ${AnalysisHelperV2.formatBr(lost.avgLostValue)}")
        addText(card, "\uD83D\uDCCA R\$/km m\u00E9dio perdido: R\$ ${AnalysisHelperV2.formatBr(lost.avgLostPricePerKm)}")
        addText(card, "\u23F0 Hor\u00E1rio com mais perdas: ${lost.peakLossHour} (${"%.0f".format(lost.peakLossPercent)}%)")
        addText(card, "\uD83D\uDE97 Categoria: ${lost.topLostCategory} (${"%.0f".format(lost.topLostCategoryPercent)}% das perdas)")
        addText(card, "\uD83D\uDCCD Cidade: ${lost.topLostCity} (${"%.0f".format(lost.topLostCityPercent)}% das perdas)")

        cardsContainer.addView(card)
    }

    // ─── CARD 10: Tendência de Dinâmica ───
    private fun buildDynamicTrend(r: AnalysisResultV2) {
        if (r.dynamicTrend.size < 2) return
        val card = createCard("\uD83D\uDCC8 Tend\u00EAncia de din\u00E2mica (7 dias)")

        val maxFrac = r.dynamicTrend.maxOfOrNull { it.barFraction }?.coerceAtLeast(0.01f) ?: 1f
        for (d in r.dynamicTrend) {
            val arrow = if (d.isUp) "\uD83D\uDD3C" else "\uD83D\uDD3B"
            addBarSimple(card, d.dayLabel,
                "${"%.0f".format(d.dynamicPercent)}% $arrow",
                d.barFraction / maxFrac, if (d.isUp) GREEN else RED)
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 11: Projeção do Dia ───
    private fun buildDailyProjection(r: AnalysisResultV2) {
        val p = r.dailyProjection
        if (p.completedRides == 0) return
        val card = createCard("\uD83C\uDFAF Proje\u00E7\u00E3o do dia")

        addText(card, "\uD83D\uDCB0 Ganho atual: R\$ ${AnalysisHelperV2.formatBr(p.currentEarnings)} (${p.completedRides} corridas)")
        addText(card, "\u23F1\uFE0F Horas trabalhadas: ${"%.1f".format(p.hoursWorked)}h")
        addText(card, "\uD83D\uDCC8 Ganho m\u00E9dio/h: R\$ ${AnalysisHelperV2.formatBr(p.avgPerHour)}")

        addDivider(card)

        addText(card, "\uD83C\uDFAF Meta do dia: R\$ ${AnalysisHelperV2.formatBr(p.targetDay)}")
        addText(card, "\uD83D\uDCCA Proje\u00E7\u00E3o (${"%.0f".format(p.targetHours)}h): R\$ ${AnalysisHelperV2.formatBr(p.projectedWithTargetHours)}")

        val remainingColor = if (p.remaining <= 0) GREEN else ORANGE
        val remainingText = if (p.remaining <= 0) {
            "\u2705 Meta atingida! Excedente: R\$ ${AnalysisHelperV2.formatBr(-p.remaining)}"
        } else {
            "\u26A0\uFE0F Faltam R\$ ${AnalysisHelperV2.formatBr(p.remaining)} para bater a meta"
        }
        addText(card, remainingText, 12f, remainingColor, true)

        cardsContainer.addView(card)
    }

    // ─── CARD 12: Dias Mais Lucrativos ───
    private fun buildWeekdayRanking(r: AnalysisResultV2) {
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
                setTextColor(Color.parseColor("#1A2C3E"))
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })

            addDetailRow(dayContainer, "\uD83D\uDCB0", "Lucro m\u00E9dio",
                "R\$ ${AnalysisHelperV2.formatBr(day.avgEarnings)}", day.avgEarnings > 0)
            addDetailRow(dayContainer, "\uD83D\uDE97", "Corridas", "${day.rideCount}", true)
            addDetailRow(dayContainer, "\uD83D\uDCCA", "R\$/km m\u00E9dio",
                "R\$ ${AnalysisHelperV2.formatBr(day.avgPricePerKm)}", true)

            val fraction = (day.avgEarnings / maxProfit).toFloat()
            val progressContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 8)
                    .apply { topMargin = 8 }
                val bg = GradientDrawable()
                bg.setColor(Color.parseColor("#E5E7EB"))
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
                        fillBg.setColor(Color.parseColor("#00A86B"))
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
                    setBackgroundColor(Color.parseColor("#EEF2F6"))
                })
            }

            card.addView(dayContainer)
        }

        cardsContainer.addView(card)
    }

    // ─── CARD: Resumo Financeiro ───
    private fun buildResumoFinanceiro(r: AnalysisResultV2) {
        val card = createCard("💰 RESUMO FINANCEIRO")

        val netProfit = r.totalEarnings - r.totalCost

        addInfoRow(card, "Faturamento bruto", "R$ ${AnalysisHelperV2.formatBr(r.totalEarnings)}", "#1A2C3E")
        addInfoRow(card, "Custo total", "-R$ ${AnalysisHelperV2.formatBr(r.totalCost)}", "#EF4444")
        addDivider(card)
        addInfoRow(card, "Lucro líquido", "R$ ${AnalysisHelperV2.formatBr(netProfit)}",
            if (netProfit >= 0) "#00A86B" else "#EF4444", bold = true)
        addInfoRow(card, "Custo por km", "R$ ${AnalysisHelperV2.formatBr(r.totalCostPerKm)}", "#6B7280")
        addInfoRow(card, "Margem líquida", "${"%.1f".format(r.profitMargin)}%",
            if (r.profitMargin >= 20) "#00A86B" else if (r.profitMargin >= 0) "#F59E0B" else "#EF4444")

        cardsContainer.addView(card)
    }

    private fun addInfoRow(container: LinearLayout, label: String, value: String, colorHex: String, bold: Boolean = false) {
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
            setTextColor(Color.parseColor("#666666"))
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(Color.parseColor(colorHex))
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        })
        container.addView(row)
    }

    // ─── CARD: Distribuição de Scores ───
    private fun buildScoreDistribution(r: AnalysisResultV2) {
        val card = createCard("🎯 DISTRIBUIÇÃO DAS OFERTAS")

        addText(card, "Total de ofertas: ${r.offeredCount}", 13f, Color.parseColor("#333333"))

        addDivider(card)

        val scoreContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = VERTICAL
        }

        addBar(scoreContainer, "✅ Boa (≥80%)", "${"%.0f".format(r.goodPercent)}%",
            (r.goodPercent / 100).toFloat(), Color.parseColor("#00A86B"))
        addBar(scoreContainer, "⚠️ Média (50-79%)", "${"%.0f".format(r.mediumPercent)}%",
            (r.mediumPercent / 100).toFloat(), Color.parseColor("#F59E0B"))
        addBar(scoreContainer, "❌ Ruim (<50%)", "${"%.0f".format(r.badPercent)}%",
            (r.badPercent / 100).toFloat(), Color.parseColor("#EF4444"))

        card.addView(scoreContainer)

        addDivider(card)

        addText(card, "📊 Taxa de aceitação: ${"%.1f".format(r.acceptanceRate)}%", 13f,
            if (r.acceptanceRate >= 70) Color.parseColor("#00A86B") else Color.parseColor("#F59E0B"), true)

        addText(card, buildString {
            append("→ Você aceitou ${r.acceptedCount} de ${r.offeredCount} corridas")
            if (r.offeredCount - r.acceptedCount > 0) {
                append("\n→ ${r.offeredCount - r.acceptedCount} corridas foram recusadas ou expiraram")
            }
        }, 11f, Color.parseColor("#666666"))

        cardsContainer.addView(card)
    }

    // ─── CARD: Comparativo com Período Anterior ───
    private fun buildComparativoPeriodo(r: AnalysisResultV2) {
        if (r.previousPeriodEarnings <= 0 && r.previousPeriodRides <= 0) return

        val card = createCard("📈 COMPARATIVO COM PERÍODO ANTERIOR")

        val earningsDiff = r.totalEarnings - r.previousPeriodEarnings
        val earningsPct = if (r.previousPeriodEarnings > 0)
            (earningsDiff / r.previousPeriodEarnings) * 100 else 0.0

        val ridesDiff = r.acceptedCount - r.previousPeriodRides
        val ridesPct = if (r.previousPeriodRides > 0)
            (ridesDiff.toDouble() / r.previousPeriodRides) * 100 else 0.0

        addComparisonRow(card, "Faturamento",
            "R$ ${AnalysisHelperV2.formatBr(r.totalEarnings)}",
            "R$ ${AnalysisHelperV2.formatBr(r.previousPeriodEarnings)}",
            earningsDiff, earningsPct)

        addComparisonRow(card, "Corridas",
            "${r.acceptedCount}",
            "${r.previousPeriodRides}",
            ridesDiff.toDouble(), ridesPct)

        cardsContainer.addView(card)
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
            setTextColor(Color.parseColor("#666666"))
        })
        headerRow.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 0.6f)
            text = "Atual: $current  |  Anterior: $previous"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
        })
        row.addView(headerRow)

        val arrowIcon = if (diff > 0) "▲" else if (diff < 0) "▼" else "●"
        val arrowColor = if (diff > 0) "#00A86B" else if (diff < 0) "#EF4444" else "#888888"

        addText(row, "$arrowIcon ${"%.1f".format(Math.abs(pct))}% (${if (diff > 0) "+" else ""}${AnalysisHelperV2.formatBr(diff)})",
            11f, Color.parseColor(arrowColor))

        container.addView(row)
    }

    // ─── CARD: Insights e Recomendações ───
    private fun buildInsights(r: AnalysisResultV2) {
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
            insights.add("⏰ Melhor horário para trabalhar: ${bestHour.hour}h (R$ ${AnalysisHelperV2.formatBr(bestHour.avgPricePerKm)}/km)")
        }

        if (r.priorityImpact.count > 0 && r.priorityImpact.avgPricePerKm > r.priorityImpact.avgPricePerKmWithout) {
            val diff = r.priorityImpact.avgPricePerKm - r.priorityImpact.avgPricePerKmWithout
            insights.add("✨ Prioridade aumenta seu R$/km em R$ ${AnalysisHelperV2.formatBr(diff)}. Vale a pena priorizar!")
        }

        if (r.lostRides.lostCount > 0 && r.lostRides.avgLostPricePerKm > 0) {
            insights.add("📉 Você deixou de ganhar R$ ${AnalysisHelperV2.formatBr(r.lostRides.avgLostValue)} em média por corrida não aceita.")
        }

        if (insights.isEmpty()) {
            insights.add("📊 Continue monitorando. Nenhum insight crítico no momento.")
        }

        val card = createCard("💡 INSIGHTS E RECOMENDAÇÕES")

        for (insight in insights.take(5)) {
            addText(card, insight, 12f,
                if (insight.startsWith("✅") || insight.startsWith("✨")) Color.parseColor("#00A86B")
                else if (insight.startsWith("⚠️") || insight.startsWith("📉")) Color.parseColor("#F59E0B")
                else if (insight.startsWith("🔴")) Color.parseColor("#EF4444")
                else Color.parseColor("#333333"))

            if (insight != insights.last()) {
                addDivider(card)
            }
        }

        cardsContainer.addView(card)
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
            setTextColor(Color.parseColor("#6B7280"))
        })

        val textColor = when (color) {
            is Int -> color
            is Boolean -> if (color) Color.parseColor("#1A2C3E") else Color.parseColor("#6B7280")
            else -> Color.parseColor("#1A2C3E")
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
