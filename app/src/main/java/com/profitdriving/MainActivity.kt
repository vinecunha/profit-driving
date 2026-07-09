package com.profitdriving

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.profitdriving.SecurePreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.widget.Switch
import android.widget.Toast
import android.view.MenuItem
import com.google.android.material.navigation.NavigationView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import com.profitdriving.ui.subscription.SubscriptionActivity
import com.profitdriving.ui.about.AboutActivity

class MainActivity : BaseActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var progressBar: View
    private lateinit var tvA11yBadge: TextView
    private lateinit var btnRadar: View
    private var adapter: HistoryAdapter? = null
    private var filterDays = 0
    private var customStartMs: Long? = null
    private var customEndMs: Long? = null
    private var selectedPeriodFilter: String? = null
    private var selectedTypeFilter = "all"
    private var selectedScoreFilter = "all"
    private var currentPage = 0
    private var pageSize = 100
    private var hasMore = false
    private var needsRefresh = true
    private val handler = Handler(Looper.getMainLooper())

    private fun computeFilterTimeRange(): Pair<Long?, Long?> {
        return if (filterDays >= 0) {
            val sinceMs = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -filterDays) }.apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            sinceMs to null
        } else if (customStartMs != null) {
            customStartMs to customEndMs
        } else {
            null to null
        }
    }

    private fun applyExtraFilters(records: List<RideRecord>): List<RideRecord> {
        return records
            .let { r -> if (selectedTypeFilter != "all") r.filter { it.serviceType == selectedTypeFilter } else r }
            .let { r ->
                when (selectedScoreFilter) {
                    "good" -> r.filter { it.scorePercent != null && it.scorePercent >= 80.0 }
                    "medium" -> r.filter { it.scorePercent != null && it.scorePercent >= 50.0 && it.scorePercent < 80.0 }
                    "bad" -> r.filter { it.scorePercent != null && it.scorePercent < 50.0 }
                    else -> r
                }
            }
    }

    private val dataUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "NEW_RIDE_SAVED") {
                loadFilteredHistory()
            }
        }
    }

    private lateinit var filterManager: FilterManager
    private var filtersExpanded = false

    // Navigation Drawer
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var statusSwitch: Switch
    private lateinit var tvStatusText: TextView
    private lateinit var ivStatusIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupBottomNav(Screen.HOME)
        setupToolbar(
            showLogo = true,
            actionText = "LIMPAR HISTÓRICO",
            actionListener = {
                val (sinceMs, untilMs) = computeFilterTimeRange()
                val allRange = db.getFiltered(sinceMs, limit = null, untilMs = untilMs)
                val recordsToDelete = applyExtraFilters(allRange)
                val count = recordsToDelete.size

                AlertDialog.Builder(this)
                    .setTitle(if (filterDays != 0 || customStartMs != null) "Limpar filtrados" else "Limpar hist\u00F3rico")
                    .setMessage("$count corrida(s) ser\u00E3o apagadas permanentemente.\nEsta a\u00E7\u00E3o n\u00E3o pode ser desfeita.")
                    .setPositiveButton("Apagar") { _, _ ->
                        for (r in recordsToDelete) {
                            db.deleteRide(r.id)
                        }
                        loadFilteredHistory()
                        Snackbar.make(findViewById(android.R.id.content),
                            "$count corrida(s) apagada(s)", Snackbar.LENGTH_LONG)
                            .setAction("Desfazer") {
                                for (r in recordsToDelete) {
                                    if (db.getRideById(r.id) == null) {
                                        db.insert(r)
                                    }
                                }
                                loadFilteredHistory()
                            }
                            .setActionTextColor(AppColors.overlayWarning)
                            .show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )

        // Configurar Navigation Drawer
        setupNavigationDrawer()

        filterDays = savedInstanceState?.getInt("filterDays", -1) ?: -1
        if (filterDays == -1) {
            filterDays = FilterManager.loadInt(this, "main_filter_days", 0)
        }

        db = DatabaseHelper(this)
        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)

        if (savedInstanceState != null) {
            selectedTypeFilter = savedInstanceState.getString("typeFilter", "all") ?: "all"
            selectedScoreFilter = savedInstanceState.getString("scoreFilter", "all") ?: "all"
            filtersExpanded = savedInstanceState.getBoolean("filtersExpanded", false)
        } else {
            selectedTypeFilter = FilterManager.loadString(this, "main_filter_type", "all") ?: "all"
            selectedScoreFilter = FilterManager.loadString(this, "main_filter_score", "all") ?: "all"
            filtersExpanded = FilterManager.loadBoolean(this, "main_filter_expanded", false)
        }

        selectedPeriodFilter = FilterManager.loadString(this, "main_filter_period", null)
        customStartMs = FilterManager.loadLong(this, "main_filter_custom_start", 0L).let { if (it == 0L) null else it }
        customEndMs = FilterManager.loadLong(this, "main_filter_custom_end", 0L).let { if (it == 0L) null else it }

        tvA11yBadge = findViewById(R.id.tvAccessibilityBadge)
        btnRadar = findViewById(R.id.btnRadar)

        val btnLoadMore = findViewById<TextView>(R.id.btnLoadMore)
        btnLoadMore.setOnClickListener { loadNextPage() }

        btnRadar.setOnClickListener {
            openPermissionSettings()
        }

        filterManager = FilterManager(this)
        setupPeriodFilter()
        setupTypeFilter()
        setupScoreFilter()

        val filterToggle = findViewById<LinearLayout>(R.id.btnFilterToggle)
        val filterContainer = findViewById<LinearLayout>(R.id.filterContainer)
        val tvFilterToggleIcon = findViewById<TextView>(R.id.tvFilterToggleIcon)

        filterContainer.visibility = if (filtersExpanded) View.VISIBLE else View.GONE
        tvFilterToggleIcon.text = if (filtersExpanded) "\u25B2" else "\u25BC"

        filterToggle.setOnClickListener {
            filtersExpanded = !filtersExpanded
            filterContainer.visibility = if (filtersExpanded) View.VISIBLE else View.GONE
            tvFilterToggleIcon.text = if (filtersExpanded) "\u25B2" else "\u25BC"
            FilterManager.saveBoolean(this, "main_filter_expanded", filtersExpanded)
        }

        findViewById<TextView>(R.id.btnClearFilters).setOnClickListener {
            filterDays = 0
            selectedPeriodFilter = null
            selectedTypeFilter = "all"
            selectedScoreFilter = "all"
            FilterManager.clearAll(this)
            setupPeriodFilter()
            setupTypeFilter()
            setupScoreFilter()
            loadFilteredHistory()
        }
    }

    override fun onResume() {
        super.onResume()

        val filter = IntentFilter("NEW_RIDE_SAVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(dataUpdateReceiver, filter)
        }

        pageSize = SecurePreferences.get(this)
            .getInt(SettingsActivity.KEY_PAGE_SIZE, 100)
        setupTypeFilter()
        if (needsRefresh) {
            loadFilteredHistory()
            needsRefresh = false
        }
        updateStatus()

        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true

        if (isAccessibilityServiceEnabled() && overlayOk) {
            FloatingBubbleService.start(this)
        }
    }

    override fun onPause() {
        super.onPause()
        needsRefresh = true
        try { unregisterReceiver(dataUpdateReceiver) } catch (_: Exception) {}
    }

    private fun loadFilteredHistory() {
        currentPage = 0
        loadPage(reset = true)
    }

    private fun loadPage(reset: Boolean) {
        val btnLoadMore = findViewById<TextView>(R.id.btnLoadMore)
        if (reset) {
            progressBar.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.GONE
            btnLoadMore.visibility = View.GONE
        }
        lifecycleScope.launch {
            try {
            val (records, totalCount) = withContext(Dispatchers.IO) {
                val (sinceMs, untilMs) = computeFilterTimeRange()
                val pageRecords = db.getFiltered(sinceMs, pageSize, currentPage * pageSize, untilMs = untilMs)
                val filtered = applyExtraFilters(pageRecords)

                val records = filtered
                    .let { CardHashGenerator.recoverRidesFromRawLogs(it, db) }
                    .filter { CardHashGenerator.isValidRide(it) }
                    .filter { it.value != null && it.value > 0 && (it.distanceKm != null && it.distanceKm > 0 || it.timeMin != null && it.timeMin > 0) }

                val totalCount = db.getCount(sinceMs, untilMs)
                Pair(records, totalCount)
            }

            if (reset) progressBar.visibility = View.GONE
            if (records.isEmpty() && reset) {
                recyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
                btnLoadMore.visibility = View.GONE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
                if (reset || adapter == null) {
                    val costSummary = withContext(Dispatchers.IO) {
                        CostCalculator.calculateCostSummary(
                            db.getRefuels(), db.getAllExpenses(), db.getMonthlyKm()
                        )
                    }
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
                    val confirmedIds = withContext(Dispatchers.IO) {
                        db.getDailyRidesByDate(today).map { it.rideId }.toSet()
                    }
                    adapter = HistoryAdapter(
                        records,
                        costPerKm = costSummary.totalCostPerKm,
                        confirmedRideIds = confirmedIds,
                        onDeleteRide = { ride ->
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Excluir corrida")
                                .setMessage("Tem certeza que deseja excluir esta corrida?")
                                .setPositiveButton("Excluir") { _, _ ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        db.deleteRide(ride.id)
                                        withContext(Dispatchers.Main) {
                                            loadFilteredHistory()
                                        }
                                    }
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                        },
                        onAddToMyDay = { ride ->
                            val dailyRide = DailyRide(
                                rideId = ride.id,
                                date = today,
                                originalValue = ride.value ?: 0.0,
                                isCompleted = true
                            )
                            lifecycleScope.launch(Dispatchers.IO) {
                                db.insertDailyRide(dailyRide)
                                withContext(Dispatchers.Main) {
                                    loadFilteredHistory()
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Corrida adicionada ao Meu Dia!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onRemoveFromMyDay = { ride ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                db.deleteDailyRideByRideId(ride.id)
                                withContext(Dispatchers.Main) {
                                    loadFilteredHistory()
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Corrida removida do Meu Dia!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                    recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                    recyclerView.adapter = adapter
                } else {
                    adapter!!.appendData(records)
                }
                val loadedSoFar = (currentPage + 1) * pageSize
                hasMore = loadedSoFar < totalCount
                btnLoadMore.visibility = if (hasMore) View.VISIBLE else View.GONE
                    btnLoadMore.text = if (hasMore)
                    "Ver mais (${totalCount - loadedSoFar} restantes)" else ""
            }
            } catch (e: Exception) {
                if (reset) progressBar.visibility = View.GONE
                L.e("MainActivity", "Erro ao carregar página", e)
                Snackbar.make(findViewById(android.R.id.content),
                    "Erro ao carregar dados", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadNextPage() {
        currentPage++
        loadPage(reset = false)
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
        // Atualizar status card
        if (::ivStatusIcon.isInitialized) {
            ivStatusIcon.setImageResource(
                if (allOk) R.drawable.ic_check_circle else R.drawable.ic_error
            )
            tvStatusText.text = if (allOk) "Serviço Ativo" else "Serviço Inativo"
            statusSwitch.isChecked = allOk
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val enabled = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val cnV2 = ComponentName(this, com.profitdriving.accessibility.RideAccessibilityServiceV2::class.java)
            val cnV1 = ComponentName(this, RideAccessibilityService::class.java)
            return enabled.contains(cnV2.flattenToString()) || enabled.contains(cnV1.flattenToString())
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
                    "2. Toque em \"Corrida Certa\"\n" +
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

    private fun setupPeriodFilter() {
        val container = findViewById<ViewGroup>(R.id.periodFilterContainer)
        filterManager.clearContainer(container)
        val options = listOf(
            FilterManager.FilterOption("0", "Hoje"),
            FilterManager.FilterOption("7", "7 dias"),
            FilterManager.FilterOption("30", "30 dias"),
            FilterManager.FilterOption("custom", "Período", "\uD83D\uDCC5")
        )
        val selected = selectedPeriodFilter?.let { setOf(it) } ?: emptySet()
        val view = filterManager.createFilterSection(
            parent = container,
            title = "",
            options = options,
            selectedIds = selected,
            singleSelection = true,
            callback = object : FilterManager.FilterCallback {
                override fun onFilterChanged(filterId: String, isSelected: Boolean) {
                    if (filterId == "custom" && isSelected) {
                        showDateRangePicker()
                        return
                    }
                    selectedPeriodFilter = if (isSelected) filterId else null
                    filterDays = selectedPeriodFilter?.toIntOrNull() ?: 0
                    customStartMs = null
                    customEndMs = null
                    FilterManager.saveString(this@MainActivity, "main_filter_period", selectedPeriodFilter ?: "")
                    FilterManager.saveInt(this@MainActivity, "main_filter_days", filterDays)
                    FilterManager.saveLong(this@MainActivity, "main_filter_custom_start", 0L)
                    FilterManager.saveLong(this@MainActivity, "main_filter_custom_end", 0L)
                    loadFilteredHistory()
                }
                override fun onClearAll() {}
            }
        )

        val marginTopDp = 16
        val marginTopPx = (marginTopDp * resources.displayMetrics.density).toInt()
        
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams
        params?.topMargin = marginTopPx
        view.layoutParams = params
        
        container.addView(view)
    }

    private fun showDateRangePicker() {
        val cal = Calendar.getInstance()
        val startYear = cal.get(Calendar.YEAR)
        val startMonth = cal.get(Calendar.MONTH)
        val startDay = cal.get(Calendar.DAY_OF_MONTH)

        fun buildPicker(title: String, onDateSet: (Int, Int, Int) -> Unit) {
            val datePicker = DatePicker(this).apply {
                init(startYear, startMonth, startDay, null)
                maxDate = System.currentTimeMillis()
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(datePicker)
                .setPositiveButton("OK") { _, _ ->
                    onDateSet(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        buildPicker("Data inicial") { year, month, day ->
            cal.set(year, month, day, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startTime = cal.timeInMillis

            buildPicker("Data final") { y, m, d ->
                cal.set(y, m, d, 23, 59, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val endTime = cal.timeInMillis

                customStartMs = startTime
                customEndMs = endTime
                filterDays = -1
                selectedPeriodFilter = "custom"
                setupPeriodFilter()
                loadFilteredHistory()
            }
        }
    }
 
    private fun setupTypeFilter() {
        val container = findViewById<ViewGroup>(R.id.typeFilterContainer)
        filterManager.clearContainer(container)
        val types = db.getDistinctServiceTypes()
        val options = listOf(FilterManager.FilterOption("all", "Todas")) +
            types.map { FilterManager.FilterOption(it, it) }
        val selected = if (selectedTypeFilter == "all") emptySet() else setOf(selectedTypeFilter)
        val view = filterManager.createFilterSection(
            parent = container,
            title = "",
            options = options,
            selectedIds = selected,
            singleSelection = true,
            callback = object : FilterManager.FilterCallback {
                override fun onFilterChanged(filterId: String, isSelected: Boolean) {
                    selectedTypeFilter = if (isSelected) filterId else "all"
                    FilterManager.saveString(this@MainActivity, "main_filter_type", selectedTypeFilter)
                    loadFilteredHistory()
                }
                override fun onClearAll() {}
            }
        )
        container.addView(view)
    }

    private fun setupScoreFilter() {
        val container = findViewById<ViewGroup>(R.id.scoreFilterContainer)
        filterManager.clearContainer(container)
        val options = listOf(
            FilterManager.FilterOption("all", "Todas", "⭐"),
            FilterManager.FilterOption("good", "Boas (80%+)", "✅"),
            FilterManager.FilterOption("medium", "Médias (50-80%)", "⚠️"),
            FilterManager.FilterOption("bad", "Ruins (<50%)", "❌")
        )
        val selected = if (selectedScoreFilter == "all") emptySet() else setOf(selectedScoreFilter)
        val view = filterManager.createFilterSection(
            parent = container,
            title = "",
            options = options,
            selectedIds = selected,
            singleSelection = true,
            callback = object : FilterManager.FilterCallback {
                override fun onFilterChanged(filterId: String, isSelected: Boolean) {
                    selectedScoreFilter = if (isSelected) filterId else "all"
                    FilterManager.saveString(this@MainActivity, "main_filter_score", selectedScoreFilter)
                    loadFilteredHistory()
                }
                override fun onClearAll() {}
            }
        )
        container.addView(view)
    }

    // ============================================================
    // MENU LATERAL - APENAS ITENS QUE NÃO ESTÃO NO BOTTOM NAV
    // ============================================================

    private fun setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)

        // Adicionar hamburger visível no header (tema NoActionBar)
        val btnAction = findViewById<View>(R.id.btnAction)
        val headerRow = btnAction.parent as? LinearLayout
        if (headerRow != null) {
            val hamburger = ImageView(this).apply {
                setImageResource(R.drawable.ic_menu)
                setPadding(12, 0, 8, 0)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = LinearLayout.LayoutParams(
                    48, LinearLayout.LayoutParams.MATCH_PARENT
                )
                setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
            }
            headerRow.addView(hamburger, 0)
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_subscription -> {
                    startActivity(Intent(this, SubscriptionActivity::class.java))
                    drawerLayout.closeDrawers()
                }
                R.id.nav_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                    drawerLayout.closeDrawers()
                }
                R.id.nav_help -> {
                    showHelpDialog()
                    drawerLayout.closeDrawers()
                }
                R.id.nav_privacy -> {
                    openPrivacyPolicy()
                    drawerLayout.closeDrawers()
                }
                R.id.nav_captures -> {
                    startActivity(Intent(this, CaptureHistoryActivity::class.java))
                    drawerLayout.closeDrawers()
                }
            }
            true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("💡 Ajuda")
            .setMessage(
                "📖 Como usar o Profit Driving:\n\n" +
                "1. Ative a acessibilidade nas configurações\n" +
                "2. Abra o Uber normalmente\n" +
                "3. O app captura automaticamente as corridas\n" +
                "4. Acompanhe suas estatísticas\n\n" +
                "🔒 Seus dados são 100% privados\n" +
                "📱 Dados ficam apenas no seu dispositivo"
            )
            .setPositiveButton("Entendi", null)
            .show()
    }

    private fun openPrivacyPolicy() {
        val url = "https://seu-site.com/privacidade"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        db.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("filterDays", filterDays)
        outState.putString("typeFilter", selectedTypeFilter)
        outState.putString("scoreFilter", selectedScoreFilter)
        outState.putBoolean("filtersExpanded", filtersExpanded)
    }
}

// ============================================================
// HISTORY ADAPTER CORRIGIDO - Com funções seguras de formatação
// ============================================================

class HistoryAdapter(
    records: List<RideRecord>,
    private val costPerKm: Double = 0.0,
    private val onDeleteRide: ((RideRecord) -> Unit)? = null,
    private val onAddToMyDay: ((RideRecord) -> Unit)? = null,
    private val onRemoveFromMyDay: ((RideRecord) -> Unit)? = null,
    private val confirmedRideIds: Set<Long> = emptySet()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private data class DisplayRide(
        val ride: RideRecord,
        val maskedPickup: String,
        val maskedDropoff: String,
        val profitRange: ProfitRange,
        val profitValue: String
    )

    private var displayRecords: List<DisplayRide> = records.map { it.toDisplay() }
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    private val expandedItems = mutableSetOf<Int>()
    private var reputationManager: AddressReputationManager? = null

    private fun RideRecord.toDisplay(): DisplayRide {
        return DisplayRide(
            ride = this,
            maskedPickup = CardHashGenerator.maskAddress(pickupAddress),
            maskedDropoff = CardHashGenerator.maskAddress(dropoffAddress),
            profitRange = getProfitRangeForRide(this),
            profitValue = formatProfitValue(this)
        )
    }

    companion object {
        private const val TYPE_NORMAL = 0
    }

    // ============================================================
    // FORMATAÇÃO DELEGADA AO FormatUtils
    // ============================================================

    override fun getItemViewType(position: Int): Int {
        return TYPE_NORMAL
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: androidx.cardview.widget.CardView = view.findViewById(R.id.cardRoot)
        val layoutServiceBadge: View = view.findViewById(R.id.layoutServiceBadge)
        val ivServiceTypeIcon: ImageView = view.findViewById(R.id.tvServiceTypeIcon)
        val tvServiceType: TextView = view.findViewById(R.id.tvServiceType)
        val tvAppBadge: TextView = view.findViewById(R.id.tvAppBadge)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val tvRatingText: TextView = view.findViewById(R.id.tvRatingText)
        val tvPricePerKm: TextView = view.findViewById(R.id.tvPricePerKm)
        val tvPricePerHour: TextView = view.findViewById(R.id.tvPricePerHour)
        val tvPricePerMin: TextView = view.findViewById(R.id.tvPricePerMin)
        val tvKmBadge: TextView = view.findViewById(R.id.tvKmBadge)
        val tvHourBadge: TextView = view.findViewById(R.id.tvHourBadge)
        val tvMinBadge: TextView = view.findViewById(R.id.tvMinBadge)
        val tvTotalInfo: TextView = view.findViewById(R.id.tvTotalInfo)
        val tvPriorityBonus: TextView = view.findViewById(R.id.tvPriorityBonus)
        val tvDynamicBonus: TextView = view.findViewById(R.id.tvDynamicBonus)
        val tvScore: TextView = view.findViewById(R.id.tvScore)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val btnExpandProfit: TextView = view.findViewById(R.id.btnExpandProfit)
        val expandableProfitDetails: View = view.findViewById(R.id.expandableProfitDetails)
        val tvProfitDetail: TextView = view.findViewById(R.id.tvProfitDetail)
        val layoutTripInfo: View = view.findViewById(R.id.layoutTripInfo)
        val tvPickupDistance: TextView = view.findViewById(R.id.tvPickupDistance)
        val tvPickupTime: TextView = view.findViewById(R.id.tvPickupTime)
        val tvPickupAddress: TextView = view.findViewById(R.id.tvPickupAddress)
        val dotPickupReputation: View = view.findViewById(R.id.dotPickupReputation)
        val tvDropoffDistance: TextView = view.findViewById(R.id.tvDropoffDistance)
        val tvDropoffTime: TextView = view.findViewById(R.id.tvDropoffTime)
        val tvDropoffAddress: TextView = view.findViewById(R.id.tvDropoffAddress)
        val dotDropoffReputation: View = view.findViewById(R.id.dotDropoffReputation)
        val tvDecisionBadge: TextView = view.findViewById(R.id.tvDecisionBadge)
        val ivStops: ImageView = view.findViewById(R.id.ivStops)
        val tvStops: TextView = view.findViewById(R.id.tvStops)
        val tvProfitIcon: TextView = view.findViewById(R.id.tvProfitIcon)
        val tvProfitLabel: TextView = view.findViewById(R.id.tvProfitLabel)
        val tvProfitValue: TextView = view.findViewById(R.id.tvProfitValue)
        val ivCardMenu: ImageView = view.findViewById(R.id.ivCardMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ride_card, parent, false)
        return ViewHolder(view)
    }

    private fun getBadgeTextAndColor(state: Int): Pair<String, Int> {
        return when (state) {
            0 -> Pair("✅ Bom", AppColors.metricGood)
            1 -> Pair("⚠️ Médio", AppColors.metricMedium)
            2 -> Pair("❌ Ruim", AppColors.metricBad)
            else -> Pair("—", AppColors.textSecondary)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val display = displayRecords[position]
        val r = display.ride
        val vh = holder as ViewHolder

        val serviceType = r.serviceType ?: r.appName
        vh.tvServiceType.text = serviceType

        // Badge do app (Uber / 99)
        val appName = r.appName ?: "Uber"
        when {
            appName.equals("99", ignoreCase = true) -> {
                vh.tvAppBadge.text = "99"
                vh.tvAppBadge.setBackgroundResource(R.drawable.bg_99_circle)
                vh.tvAppBadge.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        vh.itemView.context, R.color.app_99_text
                    )
                )
                vh.tvAppBadge.visibility = View.VISIBLE
            }
            appName.equals("Uber", ignoreCase = true) -> {
                vh.tvAppBadge.text = "Uber"
                vh.tvAppBadge.setBackgroundResource(R.drawable.bg_uber_circle)
                vh.tvAppBadge.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        vh.itemView.context, R.color.app_uber_text
                    )
                )
                vh.tvAppBadge.visibility = View.VISIBLE
            }
            else -> vh.tvAppBadge.visibility = View.GONE
        }

        val iconRes = when {
            r.serviceType == null -> R.drawable.ic_ride_generic
            r.serviceType!!.contains("Moto", ignoreCase = true) -> R.drawable.ic_moto
            r.serviceType!!.contains("Black", ignoreCase = true) ||
            r.serviceType!!.contains("Bag", ignoreCase = true) ||
            r.serviceType == "99Top" || r.serviceType == "Top" ||
            r.serviceType == "99Black" || r.serviceType == "99VIP" -> R.drawable.ic_car_luxury
            r.serviceType!!.contains("Entrega", ignoreCase = true) ||
            r.serviceType!!.contains("Flash", ignoreCase = true) ||
            r.serviceType!!.contains("Envios", ignoreCase = true) -> R.drawable.ic_delivery
            r.serviceType!!.contains("UberX", ignoreCase = true) ||
            r.serviceType!!.contains("99Pop", ignoreCase = true) ||
            r.serviceType!!.contains("Pop", ignoreCase = true) ||
            r.serviceType!!.contains("Comfort", ignoreCase = true) ||
            r.serviceType!!.contains("Juntos", ignoreCase = true) ||
            r.serviceType!!.startsWith("Manual - ") -> R.drawable.ic_car
            else -> R.drawable.ic_ride_generic
        }
        vh.ivServiceTypeIcon.setImageResource(iconRes)

        val (pillColor, textColor, iconColor) = when {
            serviceType.contains("Black", ignoreCase = true) ->
                Triple(AppColors.serviceUberx, Color.WHITE, Color.WHITE)
            serviceType.contains("Comfort", ignoreCase = true) || serviceType.contains("Confort", ignoreCase = true) ->
                Triple(AppColors.accent, Color.WHITE, Color.WHITE)
            serviceType.contains("Moto", ignoreCase = true) || serviceType.contains("Entrega", ignoreCase = true) ->
                Triple(AppColors.success, Color.WHITE, Color.WHITE)
            else ->
                Triple(AppColors.bgSurface, AppColors.textPrimary, AppColors.textSecondary)
        }
        val pillRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 28f, vh.layoutServiceBadge.context.resources.displayMetrics
        )
        vh.layoutServiceBadge.background = GradientDrawable().apply {
            cornerRadius = pillRadius
            setColor(pillColor)
        }
        vh.ivServiceTypeIcon.setColorFilter(iconColor)
        vh.tvServiceType.setTextColor(textColor)

        val decisionText = when {
            r.scorePercent != null && r.scorePercent >= 80 -> "✅ BOA (${FormatUtils.percent(r.scorePercent)})"
            r.scorePercent != null && r.scorePercent >= 50 -> "⚠️ MÉDIA (${FormatUtils.percent(r.scorePercent)})"
            else -> "❌ RUIM (${FormatUtils.percent(r.scorePercent)})"
        }
        val decisionBg = when {
            r.scorePercent != null && r.scorePercent >= 80 -> AppColors.successBg
            r.scorePercent != null && r.scorePercent >= 50 -> AppColors.warningBg
            else -> AppColors.errorBg
        }
        val decisionTextColor = when {
            r.scorePercent != null && r.scorePercent >= 80 -> AppColors.successText
            r.scorePercent != null && r.scorePercent >= 50 -> AppColors.warningText
            else -> AppColors.errorText
        }
        vh.tvDecisionBadge.text = decisionText
        vh.tvDecisionBadge.background = GradientDrawable().apply {
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12f, vh.tvDecisionBadge.context.resources.displayMetrics
            )
            setColor(decisionBg)
        }
        vh.tvDecisionBadge.setTextColor(decisionTextColor)
        vh.tvDecisionBadge.visibility = if (r.scorePercent != null) View.VISIBLE else View.GONE

        vh.tvPrice.text = FormatUtils.currency(r.value)
        vh.tvRatingText.text = FormatUtils.decimal(r.rating)
        vh.tvPricePerKm.text = FormatUtils.decimal(r.pricePerKm)
        vh.tvPricePerHour.text = FormatUtils.decimal(r.pricePerHour)

        val pricePerMin = if (r.timeMin != null && r.timeMin > 0 && r.value != null) 
            r.value / r.timeMin 
        else null
        vh.tvPricePerMin.text = FormatUtils.decimal(pricePerMin)

        val kmState = r.kmState ?: 3
        val hourState = r.hourState ?: 3
        val minState = r.minState ?: 3
        val ratingState = r.ratingState ?: 3

        fun setBadge(badge: TextView, state: Int) {
            val radius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12f, badge.context.resources.displayMetrics
            )
            when (state) {
                0 -> {
                    badge.text = "✅ Bom"
                    badge.background = GradientDrawable().apply {
                        cornerRadius = radius
                        setColor(AppColors.successBg)
                    }
                    badge.setTextColor(AppColors.successText)
                    badge.visibility = View.VISIBLE
                }
                1 -> {
                    badge.text = "⚠️ Médio"
                    badge.background = GradientDrawable().apply {
                        cornerRadius = radius
                        setColor(AppColors.warningBg)
                    }
                    badge.setTextColor(AppColors.warningText)
                    badge.visibility = View.VISIBLE
                }
                2 -> {
                    badge.text = "❌ Ruim"
                    badge.background = GradientDrawable().apply {
                        cornerRadius = radius
                        setColor(AppColors.errorBg)
                    }
                    badge.setTextColor(AppColors.errorText)
                    badge.visibility = View.VISIBLE
                }
                else -> badge.visibility = View.GONE
            }
        }
        setBadge(vh.tvKmBadge, kmState)
        setBadge(vh.tvHourBadge, hourState)
        setBadge(vh.tvMinBadge, minState)

        val ratingColor = when (ratingState) {
            0 -> AppColors.successText
            1 -> AppColors.warningText
            2 -> AppColors.errorText
            else -> AppColors.textSecondary
        }
        vh.tvRatingText.setTextColor(ratingColor)

        val pickupDist = r.pickupDistanceKm
        val tripDist = r.tripDistanceKm
        
        val pickupTime = r.pickupTimeMin
        val tripTime = r.tripTimeMin
        
        val totalDist = (pickupDist ?: 0.0) + (tripDist ?: r.distanceKm ?: 0.0)
        val totalTime = (pickupTime ?: 0) + (tripTime ?: r.timeMin ?: 0)

        vh.tvPickupDistance.text = if (pickupDist != null && pickupDist > 0) 
            FormatUtils.distance(pickupDist) else ""
        vh.tvPickupTime.text = if (pickupTime != null && pickupTime > 0) 
            FormatUtils.time(pickupTime) else ""
        vh.tvPickupAddress.text = display.maskedPickup
        val rm = reputationManager ?: AddressReputationManager(vh.itemView.context).also { reputationManager = it }
        val pkKey = rm.normalize(r.pickupAddress)
        if (pkKey != null) {
            when (rm.getReputation(r.pickupAddress)) {
                AddressReputationManager.Reputation.GREEN -> {
                    vh.dotPickupReputation.setBackgroundResource(R.drawable.dot_green)
                    vh.dotPickupReputation.visibility = View.VISIBLE
                }
                AddressReputationManager.Reputation.BLACK -> {
                    vh.dotPickupReputation.setBackgroundResource(R.drawable.dot_red)
                    vh.dotPickupReputation.visibility = View.VISIBLE
                }
                AddressReputationManager.Reputation.NONE -> vh.dotPickupReputation.visibility = View.GONE
            }
        } else {
            vh.dotPickupReputation.visibility = View.GONE
        }

        vh.tvDropoffDistance.text = if (tripDist != null && tripDist > 0) 
            FormatUtils.distance(tripDist) else ""
        vh.tvDropoffTime.text = if (tripTime != null && tripTime > 0) 
            FormatUtils.time(tripTime) else ""
        vh.tvDropoffAddress.text = display.maskedDropoff
        if (rm.normalize(r.dropoffAddress) != null) {
            when (rm.getReputation(r.dropoffAddress)) {
                AddressReputationManager.Reputation.GREEN -> {
                    vh.dotDropoffReputation.setBackgroundResource(R.drawable.dot_green)
                    vh.dotDropoffReputation.visibility = View.VISIBLE
                }
                AddressReputationManager.Reputation.BLACK -> {
                    vh.dotDropoffReputation.setBackgroundResource(R.drawable.dot_red)
                    vh.dotDropoffReputation.visibility = View.VISIBLE
                }
                AddressReputationManager.Reputation.NONE -> vh.dotDropoffReputation.visibility = View.GONE
            }
        } else {
            vh.dotDropoffReputation.visibility = View.GONE
        }

        val totalParts = mutableListOf<String>()
        if (totalDist > 0) {
            totalParts.add(FormatUtils.distance(totalDist))
        }
        if (totalTime > 0) {
            totalParts.add("${totalTime} min")
        }
        vh.tvTotalInfo.text = totalParts.joinToString(" · ")
        vh.tvTotalInfo.visibility = if (totalParts.isNotEmpty()) View.VISIBLE else View.GONE

        vh.tvScore.visibility = View.GONE

        val priorityBonus = r.priorityBonus
        val dynamicBonus = r.dynamicBonus

        if (priorityBonus != null && priorityBonus > 0) {
            vh.tvPriorityBonus.text = "\u26A1 R$${FormatUtils.decimal(priorityBonus)} prioridade"
            vh.tvPriorityBonus.visibility = View.VISIBLE
        } else {
            vh.tvPriorityBonus.visibility = View.GONE
        }

        if (dynamicBonus != null && dynamicBonus > 0) {
            vh.tvDynamicBonus.text = "\uD83D\uDD25 R$${FormatUtils.decimal(dynamicBonus)} din\u00E2mica"
            vh.tvDynamicBonus.visibility = View.VISIBLE
        } else {
            vh.tvDynamicBonus.visibility = View.GONE
        }

        vh.tvProfitIcon.text = display.profitRange.icon
        vh.tvProfitIcon.visibility = View.VISIBLE
        vh.tvProfitLabel.text = display.profitRange.label
        vh.tvProfitLabel.setTextColor(display.profitRange.color)
        vh.tvProfitLabel.visibility = View.VISIBLE
        vh.tvProfitValue.text = display.profitValue
        vh.tvProfitValue.setTextColor(display.profitRange.color)
        vh.tvProfitValue.visibility = View.VISIBLE

        if (r.hasMultipleStops) {
            vh.tvStops.text = "Com paradas"
            vh.tvStops.visibility = View.VISIBLE
            vh.ivStops.visibility = View.VISIBLE
        } else {
            vh.tvStops.visibility = View.GONE
            vh.ivStops.visibility = View.GONE
        }

        val isExpanded = expandedItems.contains(position)
        vh.expandableProfitDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE

        if (isExpanded) {
            val rideValue = r.value ?: 0.0
            val distance = totalDist
            val usedCostPerKm = r.costPerKmAtTime ?: costPerKm
            val totalCost = distance * usedCostPerKm
            val lucro = rideValue - totalCost
            val lucroPerc = if (rideValue > 0) (lucro / rideValue) * 100 else 0.0

            val detailText = buildString {
                appendLine("\uD83D\uDCCA Detalhamento do Lucro:")
                appendLine("─────────────────────────")
                appendLine("Valor da corrida: ${FormatUtils.currency(rideValue)}")
                appendLine("Custo por km: ${FormatUtils.decimal(usedCostPerKm)}")
                appendLine("Distância: ${FormatUtils.distance(distance)}")
                appendLine("Custo total: ${FormatUtils.currency(totalCost)}")
                appendLine("─────────────────────────")
                val lucroStr = FormatUtils.currency(lucro)
                val percStr = FormatUtils.decimal(lucroPerc)
                append("\uD83D\uDCB5 Lucro: $lucroStr ($percStr%)")
            }
            vh.tvProfitDetail.text = detailText
        }

        vh.btnExpandProfit.text = if (isExpanded) "- recolher detalhes" else "+ detalhar corrida"

        vh.btnExpandProfit.setOnClickListener {
            val currentlyExpanded = expandedItems.contains(position)
            if (currentlyExpanded) {
                expandedItems.remove(position)
            } else {
                expandedItems.add(position)
            }
            notifyItemChanged(position)
        }

        vh.tvStatus.text = when (r.status) {
            "ACCEPTED" -> "\u2705 ACEITA"
            "DECLINED" -> "\u274C RECUSADA"
            else -> ""
        }

        when (r.status) {
            "ACCEPTED" -> vh.cardRoot.setBackgroundResource(R.drawable.card_bg_accepted)
            "DECLINED" -> vh.cardRoot.setBackgroundResource(R.drawable.card_bg_declined)
            else -> {
                vh.cardRoot.setBackgroundResource(R.drawable.card_bg_neutral)
                vh.cardRoot.cardElevation = 0f
                }
            }

            vh.tvTimestamp.text = dateFormat.format(java.util.Date(r.timestamp))

            vh.ivCardMenu.setOnClickListener { menuView ->
                val popup = android.widget.PopupMenu(menuView.context, menuView)
                val isConfirmed = r.id in confirmedRideIds
                popup.menu.add(0, 1, 0, "Compartilhar")
                popup.menu.add(0, 2, 0, if (isConfirmed) "Remover do meu dia" else "Confirmar corrida")
                popup.menu.add(0, 3, 0, "Excluir")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> shareCardAsImage(menuView.context, vh.cardRoot)
                        2 -> {
                            if (isConfirmed) {
                                onRemoveFromMyDay?.invoke(r)
                            } else {
                                onAddToMyDay?.invoke(r)
                            }
                        }
                        3 -> onDeleteRide?.invoke(r)
                    }
                    true
                }
                popup.show()
            }
        }

    private fun shareCardAsImage(context: android.content.Context, cardRoot: View) {
        val w = if (cardRoot.measuredWidth > 0) cardRoot.measuredWidth else cardRoot.width
        val h = if (cardRoot.measuredHeight > 0) cardRoot.measuredHeight else cardRoot.height
        if (w <= 0 || h <= 0) return
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        cardRoot.draw(canvas)
        val cacheDir = java.io.File(context.cacheDir, "images")
        cacheDir.mkdirs()
        val file = java.io.File(cacheDir, "card_${System.currentTimeMillis()}.png")
        try {
            val out = java.io.FileOutputStream(file)
            out.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Compartilhar corrida"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getProfitRangeForRide(ride: RideRecord): ProfitRange {
        val rideValue = ride.value ?: 0.0
        val distance = (ride.pickupDistanceKm ?: 0.0) + (ride.tripDistanceKm ?: ride.distanceKm ?: 0.0)
        val usedCostPerKm = ride.costPerKmAtTime ?: costPerKm
        val totalCost = distance * usedCostPerKm
        val profit = rideValue - totalCost
        val profitPercent = if (rideValue > 0) (profit / rideValue) * 100 else 0.0
        return ProfitRange.fromPercent(profitPercent)
    }

    private fun formatProfitValue(ride: RideRecord): String {
        val rideValue = ride.value ?: 0.0
        val distance = (ride.pickupDistanceKm ?: 0.0) + (ride.tripDistanceKm ?: ride.distanceKm ?: 0.0)
        val usedCostPerKm = ride.costPerKmAtTime ?: costPerKm
        val totalCost = distance * usedCostPerKm
        val profit = rideValue - totalCost
        val profitPercent = if (rideValue > 0) (profit / rideValue) * 100 else 0.0
        
        val profitFormatted = FormatUtils.currency(profit)
        val percentFormatted = FormatUtils.decimal(profitPercent)
        
        return if (profit >= 0) {
            "$profitFormatted ($percentFormatted%)"
        } else {
            "-${FormatUtils.decimal(-profit)} ($percentFormatted%)"
        }
    }

    fun appendData(newRecords: List<RideRecord>) {
        val newDisplay = newRecords.map { it.toDisplay() }
        displayRecords = displayRecords + newDisplay
        notifyItemRangeInserted(displayRecords.size - newDisplay.size, newDisplay.size)
    }

    fun updateData(newRecords: List<RideRecord>) {
        val newDisplay = newRecords.map { it.toDisplay() }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = displayRecords.size
            override fun getNewListSize() = newDisplay.size
            override fun areItemsTheSame(o: Int, n: Int) =
                displayRecords[o].ride.id == newDisplay[n].ride.id
            override fun areContentsTheSame(o: Int, n: Int) =
                displayRecords[o].ride == newDisplay[n].ride
        })
        displayRecords = newDisplay
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = displayRecords.size
}