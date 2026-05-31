package com.profitdriving

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.VERTICAL
import android.widget.ProgressBar
import android.widget.TextView

import androidx.core.content.ContextCompat
import java.util.Calendar

class AnalysisActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var cardsContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: View
    private var currentPeriod = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis)
        setupBottomNav(Screen.ANALYSIS)
        setupToolbar(title = "An\u00E1lise", showBack = true)

        cardsContainer = findViewById(R.id.cardsContainer)
        progressBar = findViewById(R.id.progressBar)
        scrollView = findViewById(R.id.scrollView)

        findViewById<TextView>(R.id.btnPeriodToday).setOnClickListener { setPeriod(0) }
        findViewById<TextView>(R.id.btnPeriodWeek).setOnClickListener { setPeriod(7) }
        findViewById<TextView>(R.id.btnPeriodMonth).setOnClickListener { setPeriod(30) }

        setPeriod(0)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun setPeriod(days: Int) {
        currentPeriod = days
        updateButtons()

        val sinceMs = when (days) {
            0 -> Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            7 -> Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            30 -> Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            else -> 0L
        }

        progressBar.visibility = View.VISIBLE
        cardsContainer.visibility = View.GONE
        scrollView.visibility = View.GONE
        Thread {
            val records = db.getFiltered(sinceMs)
            val result = AnalysisHelperV2.calculate(records)
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
        val sel = R.drawable.pill_selected
        val unsel = R.drawable.pill_unselected
        today.background = ContextCompat.getDrawable(this, if (currentPeriod == 0) sel else unsel)
        today.setTextColor(if (currentPeriod == 0) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        week.background = ContextCompat.getDrawable(this, if (currentPeriod == 7) sel else unsel)
        week.setTextColor(if (currentPeriod == 7) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
        month.background = ContextCompat.getDrawable(this, if (currentPeriod == 30) sel else unsel)
        month.setTextColor(if (currentPeriod == 30) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt())
    }

    private fun buildCards(r: AnalysisResultV2) {
        cardsContainer.removeAllViews()
        cardIndex = 0
        buildVisaoGeral(r)
        buildBonusImpact(r)
        buildMelhorHorario(r)
        buildDinamicaHorario(r)
        buildCidades(r)
        buildBairros(r)
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
        fraction: Float, barColor: Int = Color.parseColor("#00A86B")
    ) {
        val row = LinearLayout(this).apply {
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

        addText(card, buildString {
            append("${r.totalRides} corridas \u00B7 ")
            append("R\$ ${AnalysisHelperV2.formatBr(r.totalEarnings)} total \u00B7 ")
            append("${AnalysisHelperV2.formatBr1(r.totalKm)} km \u00B7 ")
            append(AnalysisHelperV2.hoursMinutes(r.totalMinutes))
        }, 12f, Color.parseColor("#555555"))
        addText(card, buildString {
            append("R\$/km m\u00E9dio: R\$ ${AnalysisHelperV2.formatBr(r.avgPricePerKm)}")
            append("  |  R\$/h m\u00E9dio: R\$ ${AnalysisHelperV2.formatBr(r.avgPricePerHour)}")
        }, 12f, Color.parseColor("#666666"))

        addDivider(card)
        addText(card, "Status das corridas", 13f, Color.parseColor("#333333"), true)

        val statusBarColor = mapOf(
            "Aceitas" to Color.parseColor("#00A86B"),
            "Recusadas" to Color.parseColor("#EF4444"),
            "Expiradas" to Color.parseColor("#F59E0B")
        )
        val statusContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = VERTICAL
        }
        addBar(statusContainer, "Aceitas", "${"%.0f".format(r.acceptedPercent)}%",
            (r.acceptedPercent / 100).toFloat(), statusBarColor["Aceitas"]!!)
        addBar(statusContainer, "Recusadas", "${"%.0f".format(r.declinedPercent)}%",
            (r.declinedPercent / 100).toFloat(), statusBarColor["Recusadas"]!!)
        addBar(statusContainer, "Expiradas", "${"%.0f".format(r.expiredPercent)}%",
            (r.expiredPercent / 100).toFloat(), statusBarColor["Expiradas"]!!)
        card.addView(statusContainer)

        if (r.goodPercent > 0 || r.mediumPercent > 0 || r.badPercent > 0) {
            addDivider(card)
            addText(card, "Avalia\u00E7\u00E3o das corridas", 13f, Color.parseColor("#333333"), true)
            val scoreColors = mapOf(
                "Boa (\u226580)" to Color.parseColor("#00A86B"),
                "M\u00E9dia (50-79)" to Color.parseColor("#F59E0B"),
                "Ruim (<50)" to Color.parseColor("#EF4444")
            )
            val scoreContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = VERTICAL
            }
            addBar(scoreContainer, "Boa (\u226580)", "${"%.0f".format(r.goodPercent)}%",
                (r.goodPercent / 100).toFloat(), scoreColors["Boa (\u226580)"]!!)
            addBar(scoreContainer, "M\u00E9dia (50-79)", "${"%.0f".format(r.mediumPercent)}%",
                (r.mediumPercent / 100).toFloat(), scoreColors["M\u00E9dia (50-79)"]!!)
            addBar(scoreContainer, "Ruim (<50)", "${"%.0f".format(r.badPercent)}%",
                (r.badPercent / 100).toFloat(), scoreColors["Ruim (<50)"]!!)
            card.addView(scoreContainer)
        }

        cardsContainer.addView(card)
    }

    // ─── CARD 2: Impacto dos Bônus ───
    private fun buildBonusImpact(r: AnalysisResultV2) {
        val card = createCard("\uD83D\uDCB0 Impacto dos B\u00F4nus")
        addText(card, "Comparativo de corridas com e sem b\u00F4nus", 12f, Color.parseColor("#666666"))

        addDivider(card)
        addText(card, "\u2B06 Prioridade (${"%.0f".format(r.priorityImpact.percentage)}% das corridas)", 13f, Color.parseColor("#333333"), true)
        addText(card, "Corridas: ${r.priorityImpact.count}  |  Valor m\u00E9dio: R\$ ${AnalysisHelperV2.formatBr(r.priorityImpact.avgBonusValue)}", 12f, Color.parseColor("#555555"))
        addText(card, "R\$/km m\u00E9dio: R\$ ${AnalysisHelperV2.formatBr(r.priorityImpact.avgPricePerKm)}  |  Sem prioridade: R\$ ${AnalysisHelperV2.formatBr(r.priorityImpact.avgPricePerKmWithout)}", 12f, Color.parseColor("#555555"))
        val diffP = r.priorityImpact.avgPricePerKm - r.priorityImpact.avgPricePerKmWithout
        val signalP = if (diffP >= 0) "+" else ""
        addText(card, "Diferen\u00E7a: ${signalP}R\$ ${AnalysisHelperV2.formatBr(diffP)}/km  (${signalP}${"%.0f".format(r.priorityImpact.goodRatePercent - (r.goodPercent))}% boas)",
            11f, if (diffP >= 0) Color.parseColor("#00A86B") else Color.parseColor("#EF4444"), true)

        addDivider(card)
        addText(card, "\u26A1 Din\u00E2mica (${"%.0f".format(r.dynamicImpact.percentage)}% das corridas)", 13f, Color.parseColor("#333333"), true)
        addText(card, "Corridas: ${r.dynamicImpact.count}  |  Valor m\u00E9dio: R\$ ${AnalysisHelperV2.formatBr(r.dynamicImpact.avgBonusValue)}", 12f, Color.parseColor("#555555"))
        addText(card, "R\$/km m\u00E9dio: R\$ ${AnalysisHelperV2.formatBr(r.dynamicImpact.avgPricePerKm)}  |  Sem din\u00E2mica: R\$ ${AnalysisHelperV2.formatBr(r.dynamicImpact.avgPricePerKmWithout)}", 12f, Color.parseColor("#555555"))
        val diffD = r.dynamicImpact.avgPricePerKm - r.dynamicImpact.avgPricePerKmWithout
        val signalD = if (diffD >= 0) "+" else ""
        addText(card, "Diferen\u00E7a: ${signalD}R\$ ${AnalysisHelperV2.formatBr(diffD)}/km  (${signalD}${"%.0f".format(r.dynamicImpact.goodRatePercent - (r.goodPercent))}% boas)",
            11f, if (diffD >= 0) Color.parseColor("#00A86B") else Color.parseColor("#EF4444"), true)

        cardsContainer.addView(card)
    }

    // ─── CARD 3: Melhor Horário ───
    private fun buildMelhorHorario(r: AnalysisResultV2) {
        val card = createCard("\u23F0 Melhor hor\u00E1rio para aceitar")

        if (r.bestHourToAccept.rideCount > 0) {
            addText(card, buildString {
                append("\u2B50 Melhor hor\u00E1rio: ${r.bestHourToAccept.hour}h \u00E0s ${(r.bestHourToAccept.hour + 1) % 24}h")
                append("  |  R\$ ${AnalysisHelperV2.formatBr(r.bestHourToAccept.avgPricePerKm)}/km")
                append("  |  ${r.bestHourToAccept.rideCount} corridas")
            }, 12f, Color.parseColor("#00A86B"), true)
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

        if (r.peakDynamicHour.rideCount > 0) {
            addText(card, buildString {
                append("\u26A1 Pico de din\u00E2mica: ${r.peakDynamicHour.hour}h")
                append("  |  ${"%.0f".format(r.peakDynamicHour.dynamicPercentage)}% com din\u00E2mica")
            }, 12f, Color.parseColor("#F59E0B"), true)
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
                    (h.dynamicPercentage / maxVal).toFloat(), Color.parseColor("#F59E0B"))
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

        for (n in r.topNeighborhoods.take(6)) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
            }

            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                text = "${n.neighborhood}"
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
}
