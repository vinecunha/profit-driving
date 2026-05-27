package com.profitdriving

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar

class AnalysisActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var cardsContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: View
    private var currentPeriod = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)

        db = DatabaseHelper(this)
        cardsContainer = findViewById(R.id.cardsContainer)
        progressBar = findViewById(R.id.progressBar)
        scrollView = findViewById(R.id.scrollView)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnPeriodToday).setOnClickListener { setPeriod(0) }
        findViewById<TextView>(R.id.btnPeriodWeek).setOnClickListener { setPeriod(7) }
        findViewById<TextView>(R.id.btnPeriodMonth).setOnClickListener { setPeriod(30) }

        setPeriod(0)
    }

    private fun setPeriod(days: Int) {
        currentPeriod = days
        updateButtons()

        val sinceMs = when (days) {
            0 -> {
                val c = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                c.timeInMillis
            }
            7 -> {
                val c = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                c.timeInMillis
            }
            30 -> {
                val c = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                c.timeInMillis
            }
            else -> 0L
        }

        val handler = Handler(Looper.getMainLooper())
        progressBar.visibility = View.VISIBLE
        cardsContainer.visibility = View.GONE
        scrollView.visibility = View.GONE
        Thread {
            val records = db.getFiltered(sinceMs)
            val result = AnalysisHelper.calculate(records)
            handler.post {
                progressBar.visibility = View.GONE
                scrollView.visibility = View.VISIBLE
                cardsContainer.visibility = View.VISIBLE
                buildCards(result)
            }
        }.start()
    }

    private fun updateButtons() {
        val today = findViewById<TextView>(R.id.btnPeriodToday)
        val week = findViewById<TextView>(R.id.btnPeriodWeek)
        val month = findViewById<TextView>(R.id.btnPeriodMonth)
        today.background = if (currentPeriod == 0) ContextCompat.getDrawable(this, R.drawable.pill_selected) else ContextCompat.getDrawable(this, R.drawable.pill_unselected)
        today.setTextColor(if (currentPeriod == 0) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        week.background = if (currentPeriod == 7) ContextCompat.getDrawable(this, R.drawable.pill_selected) else ContextCompat.getDrawable(this, R.drawable.pill_unselected)
        week.setTextColor(if (currentPeriod == 7) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        month.background = if (currentPeriod == 30) ContextCompat.getDrawable(this, R.drawable.pill_selected) else ContextCompat.getDrawable(this, R.drawable.pill_unselected)
        month.setTextColor(if (currentPeriod == 30) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
    }

    private fun buildCards(result: AnalysisResult) {
        cardsContainer.removeAllViews()

        buildCard1(result)
        buildCard2(result)
        buildCard3(result)
        buildCard4(result)
        buildCard5(result)
        buildCard6(result)
        if (currentPeriod == 0) buildCard7(result)
    }

    private fun createCard(title: String): LinearLayout {
        val card = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 16)

            val bg = GradientDrawable()
            bg.setColor(Color.WHITE)
            bg.cornerRadius = 12 * resources.displayMetrics.density
            background = bg
            elevation = 2 * resources.displayMetrics.density
        }

        val titleView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            text = title
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        card.addView(titleView)
        return card
    }

    private fun addTextToCard(card: LinearLayout, text: String, size: Float = 14f, color: Int = Color.parseColor("#333333"), bold: Boolean = false) {
        val tv = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 2) }
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }
        card.addView(tv)
    }

    private fun addDivider(card: LinearLayout) {
        val div = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 6, 0, 6) }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        card.addView(div)
    }

    private fun addProgressRow(
        container: LinearLayout,
        label: String,
        valueText: String,
        fraction: Float,
        barColor: Int = Color.parseColor("#1B7B4A")
    ) {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
        }

        val labelTv = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.25f
            )
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#555555"))
        }
        row.addView(labelTv)

        val barContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, 16, 0.55f
            )
            val bg = GradientDrawable()
            bg.setColor(Color.parseColor("#E0E0E0"))
            bg.cornerRadius = 8 * resources.displayMetrics.density
            background = bg
        }
        row.addView(barContainer)

        row.post {
            val totalWidth = barContainer.width
            if (totalWidth > 0) {
                val fillWidth = (totalWidth * fraction).toInt()
                val barFill = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        fillWidth,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    val bg = GradientDrawable()
                    bg.setColor(barColor)
                    bg.cornerRadius = 8 * resources.displayMetrics.density
                    background = bg
                }
                barContainer.addView(barFill)
            }
        }

        val valueTv = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f
            )
            text = valueText
            textSize = 12f
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.END
        }
        row.addView(valueTv)

        container.addView(row)
    }

    // ─── CARD 1: Visão Geral ───
    private fun buildCard1(result: AnalysisResult) {
        val card = createCard("Visão Geral")
        addTextToCard(card,
            "${result.totalRides} corridas · R$ ${AnalysisHelper.formatBr(result.totalEarnings)} total" +
                    " · ${AnalysisHelper.formatBr1(result.totalKm)} km" +
                    " · ${AnalysisHelper.hoursMinutes(result.totalMinutes)}"
        )
        addTextToCard(card, buildString {
            append("R$/km médio: ${AnalysisHelper.formatBr(result.avgPricePerKm)}")
            append("   R$/h médio: ${AnalysisHelper.formatBr(result.avgPricePerHour)}")
            result.avgRating?.let { append("   Nota: ${AnalysisHelper.formatBr(it)}") }
        }, 13f, Color.parseColor("#666666"))
        addTextToCard(card, buildString {
            append("Aceitas: ${result.acceptedCount}  Recusadas: ${result.declinedCount}  Expiradas: ${result.expiredCount}")
            result.acceptanceRate?.let { append("  |  Taxa de aceite: ${"%.0f".format(it)}%") }
        }, 13f, Color.parseColor("#666666"))
        result.avgScorePercent?.let {
            addTextToCard(card, "Pontuação média das corridas: ${"%.0f".format(it)}%", 13f, Color.parseColor("#666666"))
        }
        cardsContainer.addView(card)
    }

    // ─── CARD 2: Melhor Horário ───
    private fun buildCard2(result: AnalysisResult) {
        val card = createCard("\u23F0 Melhor horário para rodar")
        addTextToCard(card,
            "Entre ${result.bestHour}h e ${(result.bestHour + 1) % 24}h você ganha em média R$ ${AnalysisHelper.formatBr(result.bestHourAvgKm)}/km"
        )

        val sortedHours = result.hoursMap.entries
            .sortedByDescending { it.value }
            .take(5)

        if (sortedHours.isNotEmpty()) {
            val maxVal = sortedHours.first().value
            val hoursContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 0)
            }

            for ((hour, avg) in sortedHours) {
                addProgressRow(
                    hoursContainer,
                    "${hour}h",
                    "R$ ${AnalysisHelper.formatBr(avg)}",
                    (avg / maxVal).toFloat()
                )
            }
            card.addView(hoursContainer)
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 3: Melhor Dia ───
    private fun buildCard3(result: AnalysisResult) {
        val card = createCard("\uD83D\uDCC5 Melhor dia para rodar")
        addTextToCard(card,
            "${AnalysisHelper.dayName(result.bestDayOfWeek)} é seu dia mais rentável com R$ ${AnalysisHelper.formatBr(result.bestDayAvgKm)}/km"
        )

        val sortedDays = result.daysMap.entries
            .sortedByDescending { it.value }

        if (sortedDays.isNotEmpty()) {
            val maxVal = sortedDays.first().value
            val daysContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 0)
            }

            for (dow in 1..7) {
                val avg = result.daysMap[dow] ?: continue
                addProgressRow(
                    daysContainer,
                    AnalysisHelper.shortDayName(dow),
                    "R$ ${AnalysisHelper.formatBr(avg)}",
                    (avg / maxVal).toFloat()
                )
            }
            card.addView(daysContainer)
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 4: Ranking por Categoria ───
    private fun buildCard4(result: AnalysisResult) {
        if (result.byServiceType.isEmpty()) return
        val card = createCard("\uD83C\uDFC6 Rentabilidade por categoria")

        for ((i, st) in result.byServiceType.withIndex()) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(8, 6, 8, 6)
                if (i == 0) {
                    val border = GradientDrawable()
                    border.setColor(Color.WHITE)
                    border.setStroke(2, Color.parseColor("#FFD700"))
                    border.cornerRadius = 8 * resources.displayMetrics.density
                    background = border
                }
            }

            val header = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = buildString {
                    append(st.serviceType)
                    append(" — ${st.count} corridas")
                    if (i == 0) append("  \u2B50 Mais rentável")
                }
                textSize = 14f
                setTextColor(if (i == 0) Color.parseColor("#FFD700") else Color.BLACK)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            row.addView(header)

            addTextToCard(row,
                "R$/km: ${AnalysisHelper.formatBr(st.avgPricePerKm)}   " +
                        "R$/h: ${AnalysisHelper.formatBr(st.avgPricePerHour)}",
                12f, Color.parseColor("#666666")
            )

            st.avgBonus?.let {
                addTextToCard(row,
                    "Bônus médio +R$ ${AnalysisHelper.formatBr(it)}",
                    12f, Color.parseColor("#FFD700"), true
                )
            }

            if (i < result.byServiceType.size - 1) {
                val div = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { setMargins(0, 4, 0, 4) }
                    setBackgroundColor(Color.parseColor("#F0F0F0"))
                }
                row.addView(div)
            }

            card.addView(row)
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 5: Velocidade de Chamada ───
    private fun buildCard5(result: AnalysisResult) {
        val withSpeed = result.byServiceType.filter { it.avgTimeBetweenRidesMin != null }
        if (withSpeed.isEmpty()) return
        val card = createCard("\u26A1 Qual categoria chama mais rápido")

        val sorted = withSpeed.sortedBy { it.avgTimeBetweenRidesMin }

        for (st in sorted) {
            val isFastest = st.serviceType == result.fastestServiceType
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }

            val nameTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                text = st.serviceType
                textSize = 14f
                setTextColor(Color.parseColor("#333333"))
                if (isFastest) setTypeface(null, android.graphics.Typeface.BOLD)
            }
            row.addView(nameTv)

            val timeTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "${AnalysisHelper.formatBr1(st.avgTimeBetweenRidesMin!!)} min"
                textSize = 14f
                setTextColor(if (isFastest) Color.parseColor("#1B7B4A") else Color.parseColor("#666666"))
                if (isFastest) setTypeface(null, android.graphics.Typeface.BOLD)
            }
            row.addView(timeTv)

            card.addView(row)

            if (isFastest) {
                addTextToCard(card, "Chama mais rápido", 12f, Color.parseColor("#1B7B4A"), true)
            }
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 6: Categorias mais solicitadas ───
    private fun buildCard6(result: AnalysisResult) {
        if (result.byServiceType.isEmpty()) return
        val card = createCard("\uD83D\uDCCA Categorias mais solicitadas")

        val total = result.byServiceType.sumOf { it.count }.toDouble()
        val sorted = result.byServiceType.sortedByDescending { it.count }
        val maxCount = sorted.first().count.toDouble()

        for (st in sorted) {
            val pct = if (total > 0) (st.count / total * 100) else 0.0
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(0, 4, 0, 4)
            }

            addTextToCard(row,
                "${st.serviceType} — ${st.count} corridas (${"%.0f".format(pct)}%)",
                13f, Color.parseColor("#333333"), true
            )

            val barOuter = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 10
                ).apply { setMargins(0, 2, 0, 0) }
                val bg = GradientDrawable()
                bg.setColor(Color.parseColor("#E0E0E0"))
                bg.cornerRadius = 5 * resources.displayMetrics.density
                background = bg
            }
            row.addView(barOuter)

            val fraction = if (maxCount > 0) (st.count / maxCount).toFloat() else 0f
            val barFill = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (barOuter.layoutParams.width * fraction).toInt(),
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                val bg = GradientDrawable()
                bg.setColor(Color.parseColor("#1B7B4A"))
                bg.cornerRadius = 5 * resources.displayMetrics.density
                background = bg
            }
            barOuter.addView(barFill)

            card.addView(row)
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 7: Projeção do dia ───
    private fun buildCard7(result: AnalysisResult) {
        val projection = result.projectedDailyEarnings ?: return
        val card = createCard("\uD83D\uDCC8 Projeção para hoje")

        addTextToCard(card,
            "No ritmo atual você vai ganhar aproximadamente R$ ${AnalysisHelper.formatBr(projection)} hoje",
            15f, Color.parseColor("#1B7B4A"), true
        )

        addTextToCard(card,
            "Baseado em ${result.todayCount} corridas nas últimas ${"%.1f".format(result.todayHoursWorked)}h",
            12f, Color.parseColor("#999999")
        )

        cardsContainer.addView(card)
    }
}
