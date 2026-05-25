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
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var statusText: TextView
    private lateinit var btnPermissions: Button
    private var adapter: HistoryAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = DatabaseHelper(this)
        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)
        statusText = findViewById(R.id.statusText)
        btnPermissions = findViewById(R.id.btnPermissions)
        val btnConfigure = findViewById<Button>(R.id.btnConfigure)
        val btnPreview = findViewById<Button>(R.id.btnPreview)

        btnPermissions.setOnClickListener { openPermissionSettings() }
        btnConfigure.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnPreview.setOnClickListener { showDemoCard() }
        findViewById<View>(R.id.btnClearHistory).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Limpar histórico")
                .setMessage("Tem certeza?")
                .setPositiveButton("Sim") { _, _ ->
                    db.deleteAll()
                    loadHistory()
                }
                .setNegativeButton("Não", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun loadHistory() {
        val records = db.getAll()
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
                    "2. Toque em \"Profit Driving\"\n" +
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
        val tvApp: TextView = view.findViewById(R.id.tvApp)
        val tvValue: TextView = view.findViewById(R.id.tvValue)
        val tvPickup: TextView = view.findViewById(R.id.tvPickup)
        val tvTrip: TextView = view.findViewById(R.id.tvTrip)
        val tvTotal: TextView = view.findViewById(R.id.tvTotal)
        val tvKm: TextView = view.findViewById(R.id.tvKm)
        val tvHour: TextView = view.findViewById(R.id.tvHour)
        val tvRating: TextView = view.findViewById(R.id.tvRating)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val r = records[position]
        val ctx = holder.itemView.context

        holder.tvApp.text = r.appName
        holder.tvValue.text = r.value?.let { "R$ %.2f".format(it).replace(".", ",") } ?: "---"

        holder.tvPickup.text = buildString {
            if (r.pickupDistanceKm != null || r.pickupTimeMin != null) {
                append("Emb: ")
                r.pickupDistanceKm?.let { append("%.1f km".format(it).replace(".", ",")) }
                if (r.pickupTimeMin != null) append(" · ${r.pickupTimeMin} min")
            }
        }
        holder.tvTrip.text = buildString {
            if (r.tripDistanceKm != null || r.tripTimeMin != null) {
                append("Via: ")
                r.tripDistanceKm?.let { append("%.1f km".format(it).replace(".", ",")) }
                if (r.tripTimeMin != null) append(" · ${r.tripTimeMin} min")
            }
        }
        holder.tvTotal.text = buildString {
            if (r.distanceKm != null || r.timeMin != null) {
                append("Total: ")
                r.distanceKm?.let { append("%.1f km".format(it).replace(".", ",")) }
                if (r.timeMin != null) append(" · ${r.timeMin} min")
            }
        }

        holder.tvKm.text = r.pricePerKm?.let {
            "R$/km: %.2f".format(it).replace(".", ",")
        } ?: "R$/km: ---"

        holder.tvHour.text = r.pricePerHour?.let {
            "R$/h: %.2f".format(it).replace(".", ",")
        } ?: "R$/h: ---"

        holder.tvRating.text = r.rating?.let {
            "Nota: %.2f".format(it).replace(".", ",")
        } ?: "Nota: ---"

        holder.tvTime.text = dateFormat.format(Date(r.timestamp))

        val minKm = ctx.getSharedPreferences(SettingsActivity.PREF_NAME, 0)
            .getFloat(SettingsActivity.KEY_MIN_KM, 0f)
        val minHour = ctx.getSharedPreferences(SettingsActivity.PREF_NAME, 0)
            .getFloat(SettingsActivity.KEY_MIN_HOUR, 0f)
        val minRating = ctx.getSharedPreferences(SettingsActivity.PREF_NAME, 0)
            .getFloat(SettingsActivity.KEY_MIN_RATING, 0f)

        val kmOk = r.pricePerKm == null || r.pricePerKm >= minKm.toDouble()
        val hourOk = r.pricePerHour == null || r.pricePerHour >= minHour.toDouble()
        val ratingOk = r.rating == null || r.rating >= minRating.toDouble()

        val color = if (kmOk && hourOk && ratingOk)
            ctx.getColor(R.color.card_green)
        else
            ctx.getColor(R.color.card_red)

        holder.itemView.setBackgroundColor(color)
    }

    override fun getItemCount() = records.size
}
