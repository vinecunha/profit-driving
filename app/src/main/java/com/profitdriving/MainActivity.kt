package com.profitdriving

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import java.util.Calendar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tvA11yBadge: TextView
    private lateinit var btnRadar: View
    private var adapter: HistoryAdapter? = null
    private var filterDays = 0

    private val filterViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        filterDays = savedInstanceState?.getInt("filterDays", 0) ?: 0

        db = DatabaseHelper(this)
        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)

        filterViews.addAll(listOf(
            findViewById(R.id.btnFilterToday),
            findViewById(R.id.btnFilter7d),
            findViewById(R.id.btnFilter30d),
            findViewById(R.id.btnFilterAll)
        ))

        tvA11yBadge = findViewById(R.id.tvAccessibilityBadge)
        btnRadar = findViewById(R.id.btnRadar)

        val btnClear = findViewById<TextView>(R.id.btnClearHistory)
        val btnConfigure = findViewById<TextView>(R.id.btnConfigure)
        val btnPreview = findViewById<TextView>(R.id.btnPreview)

        findViewById<TextView>(R.id.btnFilterToday).setOnClickListener { setFilter(0) }
        findViewById<TextView>(R.id.btnFilter7d).setOnClickListener { setFilter(7) }
        findViewById<TextView>(R.id.btnFilter30d).setOnClickListener { setFilter(30) }
        findViewById<TextView>(R.id.btnFilterAll).setOnClickListener { setFilter(-1) }

        btnClear.setOnClickListener {
            val count = db.getAll().size
            AlertDialog.Builder(this)
                .setTitle("Limpar histórico")
                .setMessage("$count corridas serão apagadas permanentemente.\nEsta ação não pode ser desfeita.")
                .setPositiveButton("Apagar tudo") { _, _ ->
                    db.deleteAll()
                    loadFilteredHistory()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        btnConfigure.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnPreview.setOnClickListener { showDemoCard() }

        btnRadar.setOnClickListener {
            openPermissionSettings()
        }

        setFilter(filterDays)
    }

    override fun onResume() {
        super.onResume()
        loadFilteredHistory()
        updateStatus()
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
                if (selected) 0xFFFFFFFF.toInt() else 0xFF8E9AAF.toInt()
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
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            val prefs = getSharedPreferences(SettingsActivity.PREF_NAME, 0)
            if (adapter == null) {
                adapter = HistoryAdapter(
                    records,
                    prefs.getFloat(SettingsActivity.KEY_MIN_KM, 0f),
                    prefs.getFloat(SettingsActivity.KEY_MIN_HOUR, 0f),
                    prefs.getFloat(SettingsActivity.KEY_MIN_RATING, 0f)
                )
                recyclerView.layoutManager = LinearLayoutManager(this)
                recyclerView.adapter = adapter
            } else {
                adapter!!.updateData(records)
            }
        }
    }

    private fun updateStatus() {
        val a11yOk = isAccessibilityServiceEnabled()
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true
        val allOk = a11yOk && overlayOk
        tvA11yBadge.text = if (allOk)
            "✅ Acessibilidade • Sobreposição ✓"
        else
            "⚠️ Acessibilidade • Sobreposição ✗"
        btnRadar.visibility = if (allOk) View.GONE else View.VISIBLE
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

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("filterDays", filterDays)
    }
}

class HistoryAdapter(
    records: List<RideRecord>,
    private val minKm: Float,
    private val minHour: Float,
    private val minRating: Float
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val records = records.toMutableList()
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvServiceType: TextView = view.findViewById(R.id.tvServiceType)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvRatingText: TextView = view.findViewById(R.id.tvRatingText)
        val tvPricePerKm: TextView = view.findViewById(R.id.tvPricePerKm)
        val tvPricePerHour: TextView = view.findViewById(R.id.tvPricePerHour)
        val tvKmBadge: TextView = view.findViewById(R.id.tvKmBadge)
        val tvHourBadge: TextView = view.findViewById(R.id.tvHourBadge)
        val tvPickupInfo: TextView = view.findViewById(R.id.tvPickupInfo)
        val tvPickupStatus: TextView = view.findViewById(R.id.tvPickupStatus)
        val tvTripInfo: TextView = view.findViewById(R.id.tvTripInfo)
        val tvTripStatus: TextView = view.findViewById(R.id.tvTripStatus)
        val tvTotalInfo: TextView = view.findViewById(R.id.tvTotalInfo)
        val tvBonus: TextView = view.findViewById(R.id.tvBonus)
        val tvScore: TextView = view.findViewById(R.id.tvScore)
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

        holder.tvServiceType.text = r.serviceType ?: r.appName

        holder.tvPrice.text = r.value?.let {
            "R$ %.2f".format(it).replace(".", ",")
        } ?: "---"

        holder.tvRatingText.text = r.rating?.let {
            "%.2f".format(it).replace(".", ",")
        } ?: "---"

        holder.tvPricePerKm.text = r.pricePerKm?.let {
            "%.2f".format(it).replace(".", ",")
        } ?: "---"

        holder.tvPricePerHour.text = r.pricePerHour?.let {
            "%.2f".format(it).replace(".", ",")
        } ?: "---"

        // Metric badges
        val kmOk = r.pricePerKm == null || r.pricePerKm >= minKm.toDouble()
        val hourOk = r.pricePerHour == null || r.pricePerHour >= minHour.toDouble()
        val ratingOk = r.rating == null || r.rating >= minRating.toDouble()

        fun badgeState(ok: Boolean, value: Double?, min: Double): Pair<String, Int> {
            if (value == null) return "—" to Color.parseColor("#8E9AAF")
            return if (ok)
                "✅ Bom" to Color.parseColor("#2E7D32")
            else if (value >= min * 0.8)
                "⚠️ Médio" to Color.parseColor("#ED6C02")
            else
                "⬇ Baixo" to Color.parseColor("#D32F2F")
        }

        val (kmText, kmColor) = badgeState(kmOk, r.pricePerKm, minKm.toDouble())
        holder.tvKmBadge.text = kmText
        holder.tvKmBadge.setTextColor(kmColor)

        val (hourText, hourColor) = badgeState(hourOk, r.pricePerHour, minHour.toDouble())
        holder.tvHourBadge.text = hourText
        holder.tvHourBadge.setTextColor(hourColor)

        // Trip details
        val pickupParts = mutableListOf<String>()
        r.pickupDistanceKm?.let { pickupParts.add("%.1f km".format(it).replace(".", ",")) }
        r.pickupTimeMin?.let { pickupParts.add("${it} min") }
        holder.tvPickupInfo.text = pickupParts.joinToString(" · ")

        val tripParts = mutableListOf<String>()
        r.tripDistanceKm?.let { tripParts.add("%.1f km".format(it).replace(".", ",")) }
        r.tripTimeMin?.let { tripParts.add("${it} min") }
        holder.tvTripInfo.text = tripParts.joinToString(" · ")

        val totalParts = mutableListOf<String>()
        r.distanceKm?.let { totalParts.add("%.1f km".format(it).replace(".", ",")) }
        r.timeMin?.let { totalParts.add("${it} min") }
        holder.tvTotalInfo.text = totalParts.joinToString(" · ")

        // Pickup/Trip status badges
        fun pickupTripStatus(): Pair<String, Int> {
            val allOk = kmOk && hourOk
            return when {
                allOk -> "Bom" to Color.parseColor("#2E7D32")
                kmOk || hourOk -> "Médio" to Color.parseColor("#ED6C02")
                else -> "Baixo" to Color.parseColor("#D32F2F")
            }
        }

        val (pickupStatus, pickupStatusColor) = pickupTripStatus()
        holder.tvPickupStatus.text = pickupStatus
        holder.tvPickupStatus.setTextColor(pickupStatusColor)

        val (tripStatus, tripStatusColor) = pickupTripStatus()
        holder.tvTripStatus.text = tripStatus
        holder.tvTripStatus.setTextColor(tripStatusColor)

        // Score with background
        val scoreText = r.scorePercent?.let { "${"%.0f".format(it)}%" } ?: ""
        holder.tvScore.text = scoreText

        val allGood = kmOk && hourOk && ratingOk
        val partial = kmOk || hourOk
        val scoreBgColor = when {
            allGood -> Color.parseColor("#E8F5E9")
            partial -> Color.parseColor("#FFF3E0")
            else -> Color.parseColor("#FFEBEE")
        }
        val scoreFgColor = when {
            allGood -> Color.parseColor("#2E7D32")
            partial -> Color.parseColor("#ED6C02")
            else -> Color.parseColor("#D32F2F")
        }
        holder.tvScore.setBackgroundColor(scoreBgColor)
        holder.tvScore.setTextColor(scoreFgColor)

        // Bonus
        if (r.bonusAmount != null) {
            holder.tvBonus.text = "+ R$ %.2f".format(r.bonusAmount).replace(".", ",")
            holder.tvBonus.visibility = View.VISIBLE
        } else {
            holder.tvBonus.visibility = View.GONE
        }

        // Accepted/Declined status
        holder.tvStatus.text = when (r.status) {
            "ACCEPTED" -> "✅"
            "DECLINED" -> "❌"
            else -> ""
        }

        // Timestamp
        val now = Calendar.getInstance()
        val rideTime = Calendar.getInstance().apply { timeInMillis = r.timestamp }
        holder.tvTimestamp.text = if (now.get(Calendar.DAY_OF_YEAR) == rideTime.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == rideTime.get(Calendar.YEAR)) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(r.timestamp))
        } else {
            dateFormat.format(java.util.Date(r.timestamp))
        }
    }

    fun updateData(newRecords: List<RideRecord>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = records.size
            override fun getNewListSize() = newRecords.size
            override fun areItemsTheSame(o: Int, n: Int) =
                records[o].id == newRecords[n].id
            override fun areContentsTheSame(o: Int, n: Int) =
                records[o] == newRecords[n]
        })
        records.clear()
        records.addAll(newRecords)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = records.size
}
