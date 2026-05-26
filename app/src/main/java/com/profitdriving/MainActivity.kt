package com.profitdriving

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var statusText: TextView
    private lateinit var tvA11yBadge: TextView
    private lateinit var btnPermissions: TextView
    private var adapter: HistoryAdapter? = null
    private var filterDays = 0

    private val filterViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DatabaseHelper(this)
        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)
        statusText = findViewById(R.id.statusText)
        btnPermissions = findViewById(R.id.btnPermissions)
        val btnConfigure = findViewById<TextView>(R.id.btnConfigure)
        val btnPreview = findViewById<TextView>(R.id.btnPreview)

        filterViews.addAll(listOf(
            findViewById(R.id.btnFilterToday),
            findViewById(R.id.btnFilter7d),
            findViewById(R.id.btnFilter30d),
            findViewById(R.id.btnFilterAll)
        ))

        tvA11yBadge = findViewById(R.id.tvAccessibilityBadge)

        findViewById<TextView>(R.id.btnFilterToday).setOnClickListener { setFilter(0) }
        findViewById<TextView>(R.id.btnFilter7d).setOnClickListener { setFilter(7) }
        findViewById<TextView>(R.id.btnFilter30d).setOnClickListener { setFilter(30) }
        findViewById<TextView>(R.id.btnFilterAll).setOnClickListener { setFilter(-1) }

        btnPermissions.setOnClickListener { openPermissionSettings() }
        btnConfigure.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnPreview.setOnClickListener { showDemoCard() }
        findViewById<TextView>(R.id.btnAnalysis).setOnClickListener {
            startActivity(Intent(this, AnalysisActivity::class.java))
        }
        findViewById<TextView>(R.id.btnClearHistory).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Limpar histórico")
                .setMessage("Tem certeza?")
                .setPositiveButton("Sim") { _, _ ->
                    db.deleteAll()
                    loadFilteredHistory()
                }
                .setNegativeButton("Não", null)
                .show()
        }

        setFilter(0)
    }

    override fun onResume() {
        super.onResume()
        loadFilteredHistory()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun setFilter(days: Int) {
        filterDays = days
        filterViews.forEachIndexed { i, v ->
            val selected = (i == 0 && days == 0) ||
                    (i == 1 && days == 7) ||
                    (i == 2 && days == 30) ||
                    (i == 3 && days == -1)
            v.setBackgroundResource(
                if (selected) R.drawable.pill_selected else R.drawable.pill_unselected
            )
            v.setTextColor(
                if (selected) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt()
            )
        }
        loadFilteredHistory()
    }

    private fun loadFilteredHistory() {
        val sinceMs = if (filterDays >= 0) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -filterDays) }
            cal.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else null
        val records = if (sinceMs != null) db.getFiltered(sinceMs) else db.getAll()
        if (records.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
            adapter = HistoryAdapter(records)
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter
        }
    }

    private fun updateStatus() {
        val a11yOk = isAccessibilityServiceEnabled()
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true

        statusText.text = buildString {
            append("Acessibilidade: ")
            append(if (a11yOk) "✓" else "✗ PENDENTE")
            append("  |  Sobreposição: ")
            append(if (overlayOk) "✓" else "✗ PENDENTE")
        }

        btnPermissions.text = if (a11yOk && overlayOk) "✔ OK" else "Permissões"
        btnPermissions.setBackgroundResource(
            if (a11yOk && overlayOk) R.drawable.pill_selected else R.drawable.pill_unselected
        )
        btnPermissions.setTextColor(
            if (a11yOk && overlayOk) 0xFFFFFFFF.toInt() else 0xFF6B7280.toInt()
        )

        tvA11yBadge.text = if (a11yOk) "Acessível ✓" else "Acessibilidade ✗"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val cn = ComponentName(this, RideAccessibilityService::class.java)
            return enabled.contains(cn.flattenToString())
        } catch (_: Exception) {
            return false
        }
    }

    private fun openPermissionSettings() {
        val a11yOk = isAccessibilityServiceEnabled()
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true

        if (!overlayOk) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        if (!a11yOk) {
            AlertDialog.Builder(this)
                .setTitle("Ativar Serviço de Acessibilidade")
                .setMessage(
                    "1. Toque em \"Abrir Configurações\"\n" +
                    "2. Toque em \"CorridaCerta\"\n" +
                    "3. Ative a chave \"Permitir\"\n" +
                    "4. Confirme em \"OK\""
                )
                .setPositiveButton("Abrir Configurações") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun showDemoCard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            AlertDialog.Builder(this)
                .setTitle("Permissão necessária")
                .setMessage("É preciso conceder permissão de sobreposição de tela para exibir o card de teste.")
                .setPositiveButton("Conceder") { _, _ ->
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ))
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }

        FloatingCardService.start(this, Intent().apply {
            putExtra("value", 22.50)
            putExtra("distanceKm", 5.2)
            putExtra("timeMin", 18)
            putExtra("rating", 4.85)
            putExtra("appName", "Uber")
            putExtra("isDemo", true)
        })
    }
}

class HistoryAdapter(private val records: List<RideRecord>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val accentStrip: View = view.findViewById(R.id.accentStrip)
        val tvServiceType: TextView = view.findViewById(R.id.tvServiceType)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvRatingText: TextView = view.findViewById(R.id.tvRatingText)
        val tvPricePerKm: TextView = view.findViewById(R.id.tvPricePerKm)
        val tvPricePerHour: TextView = view.findViewById(R.id.tvPricePerHour)
        val tvPickupDist: TextView = view.findViewById(R.id.tvPickupDist)
        val tvPickupTime: TextView = view.findViewById(R.id.tvPickupTime)
        val tvTripDist: TextView = view.findViewById(R.id.tvTripDist)
        val tvTripTime: TextView = view.findViewById(R.id.tvTripTime)
        val tvTotalDist: TextView = view.findViewById(R.id.tvTotalDist)
        val tvTotalTime: TextView = view.findViewById(R.id.tvTotalTime)
        val tvBonus: TextView = view.findViewById(R.id.tvBonus)
        val tvEfficiency: TextView = view.findViewById(R.id.tvEfficiency)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ride_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val r = records[position]
        val ctx = holder.itemView.context

        holder.tvServiceType.text = r.serviceType ?: r.appName

        holder.tvPrice.text = r.value?.let {
            "R$ %.2f".format(it).replace(".", ",")
        } ?: "---"

        holder.tvRatingText.text = r.rating?.let {
            "%.2f".format(it).replace(".", ",")
        } ?: "---"

        holder.tvPricePerKm.text = r.pricePerKm?.let {
            "R$ %.2f".format(it).replace(".", ",")
        } ?: "---"

        holder.tvPricePerHour.text = r.pricePerHour?.let {
            "R$ %.2f".format(it).replace(".", ",")
        } ?: "---"

        holder.tvPickupDist.text = r.pickupDistanceKm?.let {
            "%.1f km".format(it).replace(".", ",")
        } ?: ""
        holder.tvPickupTime.text = r.pickupTimeMin?.let {
            "${it} min"
        } ?: ""

        holder.tvTripDist.text = r.tripDistanceKm?.let {
            "%.1f km".format(it).replace(".", ",")
        } ?: ""
        holder.tvTripTime.text = r.tripTimeMin?.let {
            "${it} min"
        } ?: ""

        holder.tvTotalDist.text = r.distanceKm?.let {
            "%.1f km".format(it).replace(".", ",")
        } ?: ""
        holder.tvTotalTime.text = r.timeMin?.let {
            "${it} min"
        } ?: ""

        if (r.bonusAmount != null) {
            holder.tvBonus.text = "+ R$ %.2f".format(r.bonusAmount).replace(".", ",")
            holder.tvBonus.visibility = View.VISIBLE
        } else {
            holder.tvBonus.visibility = View.GONE
        }

        val prefs = ctx.getSharedPreferences(SettingsActivity.PREF_NAME, 0)
        val minKm = prefs.getFloat(SettingsActivity.KEY_MIN_KM, 0f)
        val minHour = prefs.getFloat(SettingsActivity.KEY_MIN_HOUR, 0f)
        val minRating = prefs.getFloat(SettingsActivity.KEY_MIN_RATING, 0f)

        val kmOk = r.pricePerKm == null || r.pricePerKm >= minKm.toDouble()
        val hourOk = r.pricePerHour == null || r.pricePerHour >= minHour.toDouble()
        val ratingOk = r.rating == null || r.rating >= minRating.toDouble()

        val cardColor: String
        val efficiencyText: String
        val efficiencyBg: String
        val efficiencyFg: String

        when {
            kmOk && hourOk && ratingOk -> {
                cardColor = "#1B7B4A"
                efficiencyText = "Rendimento Bom"
                efficiencyBg = "#E8F5E9"
                efficiencyFg = "#1A1A2E"
            }
            kmOk && hourOk -> {
                cardColor = "#E65100"
                efficiencyText = "Avaliação abaixo da média"
                efficiencyBg = "#FFF3E0"
                efficiencyFg = "#E65100"
            }
            else -> {
                cardColor = "#B71C1C"
                efficiencyText = "Rendimento Baixo"
                efficiencyBg = "#FFEBEE"
                efficiencyFg = "#C62828"
            }
        }

        val isLowKm = r.pricePerKm != null && r.pricePerKm < 1.0
        holder.tvEfficiency.text = if (isLowKm) "R$/km muito baixo" else efficiencyText
        holder.tvEfficiency.setBackgroundColor(android.graphics.Color.parseColor(
            if (isLowKm) "#FFEBEE" else efficiencyBg
        ))
        holder.tvEfficiency.setTextColor(android.graphics.Color.parseColor(
            if (isLowKm) "#C62828" else efficiencyFg
        ))

        holder.accentStrip.visibility = View.VISIBLE
        val stripBg = holder.accentStrip.background?.mutate() as? android.graphics.drawable.GradientDrawable
        stripBg?.setColor(android.graphics.Color.parseColor(cardColor))

        holder.tvStatus.text = when (r.status) {
            "ACCEPTED" -> "\u2705"
            "DECLINED" -> "\u274C"
            else -> ""
        }

        val now = java.util.Calendar.getInstance()
        val rideTime = java.util.Calendar.getInstance().apply { timeInMillis = r.timestamp }
        holder.tvTimestamp.text = if (now.get(java.util.Calendar.DAY_OF_YEAR) == rideTime.get(java.util.Calendar.DAY_OF_YEAR) &&
            now.get(java.util.Calendar.YEAR) == rideTime.get(java.util.Calendar.YEAR)) {
            java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(r.timestamp))
        } else {
            dateFormat.format(java.util.Date(r.timestamp))
        }
    }

    override fun getItemCount() = records.size
}
