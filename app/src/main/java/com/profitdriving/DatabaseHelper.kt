package com.profitdriving

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
        db.execSQL(CREATE_FUEL_REFUELS)
        db.execSQL(CREATE_FIXED_EXPENSES_V8)
        db.execSQL(CREATE_VARIABLE_COSTS)
        db.execSQL(CREATE_COST_SETTINGS)
        db.execSQL(CREATE_MONTHLY_STATS)
        db.execSQL(CREATE_EXPENSES)
        db.execSQL(CREATE_DAILY_RIDES)
        db.execSQL(CREATE_RAW_LOGS_TABLE)
        db.execSQL(CREATE_RAW_LOGS_INDEXES)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dropoff_address ON $TABLE_NAME(dropoff_address)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_PICKUP_DISTANCE REAL")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_PICKUP_TIME INTEGER")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_TRIP_DISTANCE REAL")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_TRIP_TIME INTEGER")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_SERVICE_TYPE TEXT")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_BONUS_AMOUNT REAL")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_STATUS TEXT DEFAULT 'EXPIRED'")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_STOPS INTEGER")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_SCORE_PERCENT REAL")
        }
        if (oldVersion < 7) {
            db.execSQL(CREATE_FUEL_REFUELS)
            db.execSQL(CREATE_FIXED_EXPENSES_V7)
            db.execSQL(CREATE_COST_SETTINGS)
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE $TABLE_FIXED_EXPENSES ADD COLUMN $COL_E_PERIODICITY TEXT DEFAULT 'monthly'")
            db.execSQL("ALTER TABLE $TABLE_FIXED_EXPENSES ADD COLUMN $COL_E_USEFUL_LIFE INTEGER DEFAULT 1")
            db.execSQL(CREATE_VARIABLE_COSTS)
        }
        if (oldVersion < 9) {
            db.execSQL("ALTER TABLE $TABLE_FUEL_REFUELS ADD COLUMN $COL_R_NOTES TEXT")
            db.execSQL(CREATE_MONTHLY_STATS)
        }
        if (oldVersion < 10) {
            db.execSQL(CREATE_EXPENSES)
            db.execSQL("""
                INSERT INTO $TABLE_EXPENSES ($COL_E_NAME, $COL_E_VALUE, $COL_EX_COST_TYPE,
                    $COL_EX_PERIODICITY, $COL_E_USEFUL_LIFE, $COL_E_CATEGORY, $COL_E_CREATED_AT)
                SELECT $COL_E_NAME, $COL_E_VALUE, 'fixed' AS $COL_EX_COST_TYPE,
                    $COL_E_PERIODICITY, $COL_E_USEFUL_LIFE, $COL_E_CATEGORY, $COL_E_CREATED_AT
                FROM $TABLE_FIXED_EXPENSES
            """.trimIndent())
            db.execSQL("""
                INSERT INTO $TABLE_EXPENSES ($COL_E_NAME, $COL_E_VALUE, $COL_EX_COST_TYPE,
                    $COL_E_CATEGORY, $COL_E_CREATED_AT)
                SELECT $COL_VC_NAME, $COL_VC_COST_PER_KM, 'per_km' AS $COL_EX_COST_TYPE,
                    $COL_VC_CATEGORY, $COL_VC_CREATED_AT
                FROM $TABLE_VARIABLE_COSTS
            """.trimIndent())
        }
        if (oldVersion < 11) {
            db.execSQL(CREATE_DAILY_RIDES)
        }
        if (oldVersion < 12) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_PICKUP_ADDRESS TEXT")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_DROPOFF_ADDRESS TEXT")
        }
        if (oldVersion < 13) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_dropoff_address ON $TABLE_NAME(dropoff_address)")
        }
        if (oldVersion < 14) {
            db.execSQL("ALTER TABLE $TABLE_EXPENSES ADD COLUMN $COL_E_TOTAL_ORIGINAL REAL")
            db.execSQL("ALTER TABLE $TABLE_EXPENSES ADD COLUMN $COL_E_PAYMENT_STATUS TEXT DEFAULT 'PENDING'")
            db.execSQL("ALTER TABLE $TABLE_EXPENSES ADD COLUMN $COL_E_PAID_AMOUNT REAL DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_EXPENSES ADD COLUMN $COL_E_INSTALLMENT_TOTAL INTEGER DEFAULT 1")
            db.execSQL("ALTER TABLE $TABLE_EXPENSES ADD COLUMN $COL_E_INSTALLMENT_CURRENT INTEGER DEFAULT 1")
            db.execSQL("ALTER TABLE $TABLE_EXPENSES ADD COLUMN $COL_E_DUE_DATE INTEGER")
            db.execSQL("ALTER TABLE $TABLE_EXPENSES ADD COLUMN $COL_E_LAST_PAYMENT INTEGER")
            // Fix existing YEARLY records: value was stored as annual/12, restore to full annual
            db.execSQL("UPDATE $TABLE_EXPENSES SET $COL_E_VALUE = $COL_E_VALUE * 12 WHERE $COL_EX_PERIODICITY = 'yearly'")
            db.execSQL("UPDATE $TABLE_EXPENSES SET $COL_E_TOTAL_ORIGINAL = $COL_E_VALUE WHERE $COL_E_TOTAL_ORIGINAL IS NULL")
        }
        if (oldVersion < 15) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_PRIORITY_BONUS REAL")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_DYNAMIC_BONUS REAL")
        }
        if (oldVersion < 16) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_COST_PER_KM_AT_TIME REAL")
        }
        if (oldVersion < 17) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_HAS_MULTIPLE_STOPS INTEGER DEFAULT 0")
            db.execSQL("UPDATE $TABLE_NAME SET $COL_HAS_MULTIPLE_STOPS = 1 WHERE $COL_STOPS IS NOT NULL AND $COL_STOPS > 0")
        }
        if (oldVersion < 18) {
            db.execSQL("ALTER TABLE $TABLE_DAILY_RIDES ADD COLUMN $COL_DR_CANCELLED_WITH_FEE INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_DAILY_RIDES ADD COLUMN $COL_DR_FEE_VALUE REAL")
        }
        if (oldVersion < 19) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_CARD_HASH TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_card_hash ON $TABLE_NAME($COL_CARD_HASH)")
        }
        if (oldVersion < 20) {
            db.execSQL(CREATE_RAW_LOGS_TABLE)
            db.execSQL(CREATE_RAW_LOGS_INDEXES)
        }
    }

    fun updateStatus(id: Long, status: String) = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply { put(COL_STATUS, status) }
            db.update(TABLE_NAME, cv, "$COL_ID = ?", arrayOf(id.toString()))
        } finally {
            db.close()
        }
    }

    fun updateRideValue(rideId: Long, newValue: Double) = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_VALUE, newValue)
            }
            db.update(TABLE_NAME, cv, "$COL_ID = ?", arrayOf(rideId.toString()))
        } finally {
            db.close()
        }
    }

    fun insert(record: RideRecord): Long = synchronized(dbLock) {
        val currentCostPerKm = calculateCurrentCostPerKm()
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_VALUE, record.value)
                put(COL_DISTANCE_KM, record.distanceKm)
                put(COL_TIME_MIN, record.timeMin)
                put(COL_RATING, record.rating)
                put(COL_PRICE_PER_KM, record.pricePerKm)
                put(COL_PRICE_PER_HOUR, record.pricePerHour)
                put(COL_APP_NAME, record.appName)
                put(COL_TIMESTAMP, record.timestamp)
                put(COL_PICKUP_DISTANCE, record.pickupDistanceKm)
                put(COL_PICKUP_TIME, record.pickupTimeMin)
                put(COL_TRIP_DISTANCE, record.tripDistanceKm)
                put(COL_TRIP_TIME, record.tripTimeMin)
                put(COL_SERVICE_TYPE, record.serviceType)
                put(COL_BONUS_AMOUNT, record.bonusAmount)
                put(COL_PRIORITY_BONUS, record.priorityBonus)
                put(COL_DYNAMIC_BONUS, record.dynamicBonus)
                put(COL_STATUS, record.status)
                put(COL_HAS_MULTIPLE_STOPS, record.hasMultipleStops)
                put(COL_SCORE_PERCENT, record.scorePercent)
                put(COL_PICKUP_ADDRESS, record.pickupAddress)
                put(COL_DROPOFF_ADDRESS, record.dropoffAddress)
                if (currentCostPerKm != null) {
                    put(COL_COST_PER_KM_AT_TIME, currentCostPerKm)
                }
                if (record.cardHash != null) {
                    put(COL_CARD_HASH, record.cardHash)
                }
            }
            db.insert(TABLE_NAME, null, cv)
        } finally {
            db.close()
        }
    }

    fun existsByCardHash(hash: String, hoursAgo: Int = 24): Boolean {
        if (hash.isBlank()) return false
        val cutoffTime = System.currentTimeMillis() - (hoursAgo * 60 * 60 * 1000L)
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_NAME WHERE $COL_CARD_HASH = ? AND $COL_TIMESTAMP > ?",
            arrayOf(hash, cutoffTime.toString())
        )
        cursor.use {
            if (it.moveToFirst() && it.getInt(0) > 0) {
                return true
            }
        }
        return false
    }

    fun getRideIdByCardHash(cardHash: String): Long? {
        val cursor = readableDatabase.query(
            TABLE_NAME,
            arrayOf(COL_ID),
            "$COL_CARD_HASH = ?",
            arrayOf(cardHash),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        return null
    }

    fun updateRideValueByHash(cardHash: String, newValue: Double, newTimestamp: Long) = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_VALUE, newValue)
                put(COL_TIMESTAMP, newTimestamp)
                put(COL_PRICE_PER_KM, null as Double?)
                put(COL_PRICE_PER_HOUR, null as Double?)
            }
            db.update(TABLE_NAME, cv, "$COL_CARD_HASH = ?", arrayOf(cardHash))
        } finally {
            db.close()
        }
    }

    fun getRideById(id: Long): RideRecord? {
        val cursor = readableDatabase.query(
            TABLE_NAME, null,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return RideRecord(
                    id = it.getSafeLong(COL_ID) ?: 0L,
                    value = it.getSafeDouble(COL_VALUE),
                    distanceKm = it.getSafeDouble(COL_DISTANCE_KM),
                    timeMin = it.getSafeInt(COL_TIME_MIN),
                    rating = it.getSafeDouble(COL_RATING),
                    pricePerKm = it.getSafeDouble(COL_PRICE_PER_KM),
                    pricePerHour = it.getSafeDouble(COL_PRICE_PER_HOUR),
                    appName = it.getSafeString(COL_APP_NAME) ?: "",
                    timestamp = it.getSafeLong(COL_TIMESTAMP) ?: 0L,
                    pickupDistanceKm = it.getSafeDouble(COL_PICKUP_DISTANCE),
                    pickupTimeMin = it.getSafeInt(COL_PICKUP_TIME),
                    tripDistanceKm = it.getSafeDouble(COL_TRIP_DISTANCE),
                    tripTimeMin = it.getSafeInt(COL_TRIP_TIME),
                    serviceType = it.getSafeString(COL_SERVICE_TYPE),
                    bonusAmount = it.getSafeDouble(COL_BONUS_AMOUNT),
                    status = it.getSafeString(COL_STATUS) ?: "EXPIRED",
                    hasMultipleStops = it.getSafe(COL_HAS_MULTIPLE_STOPS, false),
                    scorePercent = it.getSafeDouble(COL_SCORE_PERCENT),
                    priorityBonus = it.getSafeDouble(COL_PRIORITY_BONUS),
                    dynamicBonus = it.getSafeDouble(COL_DYNAMIC_BONUS),
                    pickupAddress = it.getSafeString(COL_PICKUP_ADDRESS),
                    dropoffAddress = it.getSafeString(COL_DROPOFF_ADDRESS),
                    costPerKmAtTime = it.getSafeDouble(COL_COST_PER_KM_AT_TIME),
                    cardHash = it.getSafeString(COL_CARD_HASH)
                )
            }
        }
        return null
    }

    fun insertRawLog(
        cardHash: String?,
        pkg: String,
        cardType: String?,
        rawTexts: List<String>,
        rideData: RideData? = null
    ): Long = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_RAW_CARD_HASH, cardHash)
                put(COL_RAW_TIMESTAMP, System.currentTimeMillis())
                put(COL_RAW_PACKAGE, pkg)
                put(COL_RAW_CARD_TYPE, cardType)
                put(COL_RAW_TEXTS, rawTexts.joinToString("\n---\n"))
                rideData?.let {
                    put(COL_RAW_RIDE_DATA, buildRawDataString(it))
                }
                put(COL_RAW_STATUS, "pending")
            }
            db.insert(TABLE_RAW_LOGS, null, cv)
        } finally {
            db.close()
        }
    }

    fun updateRawLogStatus(
        logId: Long,
        rideId: Long? = null,
        status: String,
        error: String? = null
    ) = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                rideId?.let { put(COL_RAW_RIDE_ID, it) }
                put(COL_RAW_STATUS, status)
                error?.let { put(COL_RAW_ERROR, it) }
            }
            db.update(TABLE_RAW_LOGS, cv, "$COL_RAW_ID = ?", arrayOf(logId.toString()))
        } finally {
            db.close()
        }
    }

    fun getFailedRawLogs(limit: Int = 100): List<Pair<Long, String>> = synchronized(dbLock) {
        val result = mutableListOf<Pair<Long, String>>()
        val cursor = readableDatabase.query(
            TABLE_RAW_LOGS,
            arrayOf(COL_RAW_ID, COL_RAW_TEXTS),
            "$COL_RAW_STATUS IN ('failed', 'pending')",
            null, null, null,
            "$COL_RAW_TIMESTAMP ASC",
            limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val texts = it.getString(1)
                result.add(Pair(id, texts))
            }
        }
        return result
    }

    private fun buildRawDataString(ride: RideData): String {
        return buildString {
            appendLine("Value: ${ride.value}")
            appendLine("Distance: ${ride.distanceKm}")
            appendLine("Time: ${ride.timeMin}")
            appendLine("Rating: ${ride.rating}")
            appendLine("ServiceType: ${ride.serviceType}")
            appendLine("PickupAddress: ${ride.pickupAddress}")
            appendLine("DropoffAddress: ${ride.dropoffAddress}")
            appendLine("PriorityBonus: ${ride.priorityBonus}")
            appendLine("DynamicBonus: ${ride.dynamicBonus}")
        }
    }

    private fun calculateCurrentCostPerKm(): Double? {
        return try {
            val refuels = getRefuels()
            val expenses = getAllExpenses()
            val monthlyKm = getMonthlyKm()
            CostCalculator.calculateCostSummary(refuels, expenses, monthlyKm).totalCostPerKm
        } catch (e: Exception) { L.e("CorridaCerta", "Erro ao calcular custo/km atual: ${e.message}", e); null }
    }

    fun getAll(): List<RideRecord> {
        return getFiltered(null, null)
    }

    fun getFiltered(sinceMs: Long?): List<RideRecord> {
        return getFiltered(sinceMs, null, 0)
    }

    fun getFiltered(sinceMs: Long?, limit: Int?, offset: Int = 0): List<RideRecord> {
        val list = mutableListOf<RideRecord>()
        val selection = if (sinceMs != null) "$COL_TIMESTAMP >= ?" else null
        val selectionArgs = if (sinceMs != null) arrayOf(sinceMs.toString()) else null
        val limitClause = if (limit != null) "$limit OFFSET $offset" else null
        val cursor = readableDatabase.query(
            TABLE_NAME, null, selection, selectionArgs, null, null,
            "$COL_TIMESTAMP DESC", limitClause
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(RideRecord(
                    id = it.getSafeLong(COL_ID) ?: 0L,
                    value = it.getSafeDouble(COL_VALUE),
                    distanceKm = it.getSafeDouble(COL_DISTANCE_KM),
                    timeMin = it.getSafeInt(COL_TIME_MIN),
                    rating = it.getSafeDouble(COL_RATING),
                    pricePerKm = it.getSafeDouble(COL_PRICE_PER_KM),
                    pricePerHour = it.getSafeDouble(COL_PRICE_PER_HOUR),
                    appName = it.getSafeString(COL_APP_NAME) ?: "",
                    timestamp = it.getSafeLong(COL_TIMESTAMP) ?: 0L,
                    pickupDistanceKm = it.getSafeDouble(COL_PICKUP_DISTANCE),
                    pickupTimeMin = it.getSafeInt(COL_PICKUP_TIME),
                    tripDistanceKm = it.getSafeDouble(COL_TRIP_DISTANCE),
                    tripTimeMin = it.getSafeInt(COL_TRIP_TIME),
                    serviceType = it.getSafeString(COL_SERVICE_TYPE),
                    bonusAmount = it.getSafeDouble(COL_BONUS_AMOUNT),
                    status = it.getSafeString(COL_STATUS) ?: "EXPIRED",
                    hasMultipleStops = it.getSafe(COL_HAS_MULTIPLE_STOPS, false),
                    scorePercent = it.getSafeDouble(COL_SCORE_PERCENT),
                    priorityBonus = it.getSafeDouble(COL_PRIORITY_BONUS),
                    dynamicBonus = it.getSafeDouble(COL_DYNAMIC_BONUS),
                    pickupAddress = it.getSafeString(COL_PICKUP_ADDRESS),
                    dropoffAddress = it.getSafeString(COL_DROPOFF_ADDRESS),
                    costPerKmAtTime = it.getSafeDouble(COL_COST_PER_KM_AT_TIME),
                    cardHash = it.getSafeString(COL_CARD_HASH)
                ))
            }
        }
        return list
    }

    fun getCount(sinceMs: Long?): Int {
        val db = readableDatabase
        val selection = if (sinceMs != null) "$COL_TIMESTAMP >= ?" else null
        val selectionArgs = if (sinceMs != null) arrayOf(sinceMs.toString()) else null
        val sql = "SELECT COUNT(*) FROM $TABLE_NAME" +
                (if (selection != null) " WHERE $selection" else "")
        val cursor = db.rawQuery(sql, selectionArgs)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    fun deleteAll() = synchronized(dbLock) {
        val db = writableDatabase
        try {
            db.delete(TABLE_NAME, null, null)
        } finally {
            db.close()
        }
    }

    // ── Fuel Refuels ──

    fun insertRefuel(r: RefuelRecord): Long = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_R_TIMESTAMP, r.timestamp)
                put(COL_R_ODOMETER, r.odometerKm)
                put(COL_R_LITERS, r.liters)
                put(COL_R_PRICE, r.pricePerLiter)
                put(COL_R_TOTAL, r.totalValue)
                put(COL_R_FULL_TANK, if (r.isFullTank) 1 else 0)
                put(COL_R_FUEL_TYPE, r.fuelType)
                put(COL_R_NOTES, r.notes)
            }
            db.insert(TABLE_FUEL_REFUELS, null, cv)
        } finally {
            db.close()
        }
    }

    fun getRefuels(): List<RefuelRecord> {
        val list = mutableListOf<RefuelRecord>()
        val cursor = readableDatabase.query(
            TABLE_FUEL_REFUELS, null, null, null, null, null,
            "$COL_R_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(RefuelRecord(
                    id = it.getSafeLong(COL_ID) ?: 0L,
                    timestamp = it.getSafeLong(COL_R_TIMESTAMP) ?: 0L,
                    odometerKm = it.getSafeDouble(COL_R_ODOMETER) ?: 0.0,
                    liters = it.getSafeDouble(COL_R_LITERS) ?: 0.0,
                    pricePerLiter = it.getSafeDouble(COL_R_PRICE) ?: 0.0,
                    totalValue = it.getSafeDouble(COL_R_TOTAL) ?: 0.0,
                    isFullTank = it.getSafe(COL_R_FULL_TANK, false),
                    fuelType = it.getSafeString(COL_R_FUEL_TYPE) ?: "",
                    notes = it.getSafeString(COL_R_NOTES)
                ))
            }
        }
        return list
    }

    fun deleteRefuel(id: Long) {
        writableDatabase.delete(TABLE_FUEL_REFUELS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    // ── Fixed Expenses ──

    fun insertExpense(e: FixedExpense): Long = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_E_NAME, e.name)
                put(COL_E_VALUE, e.value)
                put(COL_E_PERIODICITY, e.periodicity)
                put(COL_E_CATEGORY, e.category)
                put(COL_E_CREATED_AT, System.currentTimeMillis())
                put(COL_E_USEFUL_LIFE, e.usefulLifeMonths)
            }
            db.insert(TABLE_FIXED_EXPENSES, null, cv)
        } finally {
            db.close()
        }
    }

    fun updateExpense(e: FixedExpense) = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_E_NAME, e.name)
                put(COL_E_VALUE, e.value)
                put(COL_E_PERIODICITY, e.periodicity)
                put(COL_E_CATEGORY, e.category)
                put(COL_E_USEFUL_LIFE, e.usefulLifeMonths)
            }
            db.update(TABLE_FIXED_EXPENSES, cv, "$COL_ID = ?", arrayOf(e.id.toString()))
        } finally {
            db.close()
        }
    }

    fun deleteExpense(id: Long) = synchronized(dbLock) {
        val db = writableDatabase
        try {
            db.delete(TABLE_FIXED_EXPENSES, "$COL_ID = ?", arrayOf(id.toString()))
        } finally {
            db.close()
        }
    }

    fun getExpenses(): List<FixedExpense> {
        val list = mutableListOf<FixedExpense>()
        val cursor = readableDatabase.query(
            TABLE_FIXED_EXPENSES, null, null, null, null, null,
            "$COL_E_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(FixedExpense(
                    id = it.getSafeLong(COL_ID) ?: 0L,
                    name = it.getSafeString(COL_E_NAME) ?: "",
                    value = it.getSafeDouble(COL_E_VALUE) ?: 0.0,
                    category = it.getSafeString(COL_E_CATEGORY) ?: "",
                    createdAt = it.getSafeLong(COL_E_CREATED_AT) ?: 0L,
                    periodicity = it.getSafeString(COL_E_PERIODICITY) ?: "monthly",
                    usefulLifeMonths = it.getSafeInt(COL_E_USEFUL_LIFE) ?: 1
                ))
            }
        }
        return list
    }

    // ── Variable Costs ──

    fun insertVariableCost(vc: VariableCost): Long = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_VC_NAME, vc.name)
                put(COL_VC_COST_PER_KM, vc.costPerKm)
                put(COL_VC_CATEGORY, vc.category)
                put(COL_VC_CREATED_AT, vc.createdAt)
            }
            db.insert(TABLE_VARIABLE_COSTS, null, cv)
        } finally {
            db.close()
        }
    }

    fun getVariableCosts(): List<VariableCost> {
        val list = mutableListOf<VariableCost>()
        val cursor = readableDatabase.query(
            TABLE_VARIABLE_COSTS, null, null, null, null, null,
            "$COL_VC_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(VariableCost(
                    id = it.getSafeLong(COL_ID) ?: 0L,
                    name = it.getSafeString(COL_VC_NAME) ?: "",
                    costPerKm = it.getSafeDouble(COL_VC_COST_PER_KM) ?: 0.0,
                    category = it.getSafeString(COL_VC_CATEGORY) ?: "",
                    createdAt = it.getSafeLong(COL_VC_CREATED_AT) ?: 0L
                ))
            }
        }
        return list
    }

    fun deleteVariableCost(id: Long) = synchronized(dbLock) {
        writableDatabase.delete(TABLE_VARIABLE_COSTS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    // ── Unified Expenses ──

    fun insertExpenseItem(e: Expense): Long = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_E_NAME, e.name)
                put(COL_E_VALUE, e.value)
                put(COL_EX_COST_TYPE, e.costType.name.lowercase())
                put(COL_EX_PERIODICITY, e.periodicity?.name?.lowercase())
                put(COL_E_USEFUL_LIFE, e.usefulLifeMonths)
                put(COL_EX_PROFIT_PCT, e.percentageOfProfit)
                put(COL_EX_EVENTS_PER_MONTH, e.estimatedEventsPerMonth)
                put(COL_E_CATEGORY, e.category.name)
                put(COL_R_NOTES, e.notes)
                put(COL_E_CREATED_AT, e.createdAt)
                put(COL_E_TOTAL_ORIGINAL, e.totalOriginalValue)
                put(COL_E_PAYMENT_STATUS, e.paymentStatus)
                put(COL_E_PAID_AMOUNT, e.paidAmount)
                put(COL_E_INSTALLMENT_TOTAL, e.installmentTotal)
                put(COL_E_INSTALLMENT_CURRENT, e.installmentCurrent)
                put(COL_E_DUE_DATE, e.dueDate)
                put(COL_E_LAST_PAYMENT, e.lastPaymentDate)
            }
            db.insert(TABLE_EXPENSES, null, cv)
        } finally {
            db.close()
        }
    }

    fun getAllExpenses(): List<Expense> {
        val list = mutableListOf<Expense>()
        val cursor = readableDatabase.query(
            TABLE_EXPENSES, null, null, null, null, null,
            "$COL_E_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(expenseFromCursor(it))
            }
        }
        return list
    }

    fun getExpensesByCostType(costType: CostType): List<Expense> {
        val list = mutableListOf<Expense>()
        val cursor = readableDatabase.query(
            TABLE_EXPENSES, null,
            "$COL_EX_COST_TYPE = ?", arrayOf(costType.name.lowercase()),
            null, null, "$COL_E_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(expenseFromCursor(it))
            }
        }
        return list
    }

    fun updateExpenseItem(e: Expense) = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_E_NAME, e.name)
                put(COL_E_VALUE, e.value)
                put(COL_EX_COST_TYPE, e.costType.name.lowercase())
                put(COL_EX_PERIODICITY, e.periodicity?.name?.lowercase())
                put(COL_E_USEFUL_LIFE, e.usefulLifeMonths)
                put(COL_EX_PROFIT_PCT, e.percentageOfProfit)
                put(COL_EX_EVENTS_PER_MONTH, e.estimatedEventsPerMonth)
                put(COL_E_CATEGORY, e.category.name)
                put(COL_R_NOTES, e.notes)
                put(COL_E_TOTAL_ORIGINAL, e.totalOriginalValue)
                put(COL_E_PAYMENT_STATUS, e.paymentStatus)
                put(COL_E_PAID_AMOUNT, e.paidAmount)
                put(COL_E_INSTALLMENT_TOTAL, e.installmentTotal)
                put(COL_E_INSTALLMENT_CURRENT, e.installmentCurrent)
                put(COL_E_DUE_DATE, e.dueDate)
                put(COL_E_LAST_PAYMENT, e.lastPaymentDate)
            }
            db.update(TABLE_EXPENSES, cv, "$COL_ID = ?", arrayOf(e.id.toString()))
        } finally {
            db.close()
        }
    }

    fun deleteExpenseItem(id: Long) = synchronized(dbLock) {
        writableDatabase.delete(TABLE_EXPENSES, "$COL_ID = ?", arrayOf(id.toString()))
    }

    private fun expenseFromCursor(cursor: android.database.Cursor): Expense {
        return Expense(
            id = cursor.getSafeLong(COL_ID) ?: 0L,
            name = cursor.getSafeString(COL_E_NAME) ?: "",
            value = cursor.getSafeDouble(COL_E_VALUE) ?: 0.0,
            costType = try {
                CostType.valueOf(cursor.getSafeString(COL_EX_COST_TYPE)?.uppercase() ?: "fixed")
            } catch (_: IllegalArgumentException) { CostType.FIXED },
            periodicity = try {
                cursor.getSafeString(COL_EX_PERIODICITY)
                    ?.uppercase()
                    ?.let { Periodicity.valueOf(it) }
            } catch (_: IllegalArgumentException) { null },
            usefulLifeMonths = cursor.getSafeInt(COL_E_USEFUL_LIFE),
            percentageOfProfit = cursor.getSafeInt(COL_EX_PROFIT_PCT),
            estimatedEventsPerMonth = cursor.getSafeInt(COL_EX_EVENTS_PER_MONTH),
            category = try {
                ExpenseCategory.valueOf(cursor.getSafeString(COL_E_CATEGORY)?.uppercase() ?: "other")
            } catch (_: IllegalArgumentException) { ExpenseCategory.OTHER },
            notes = cursor.getSafeString(COL_R_NOTES),
            createdAt = cursor.getSafeLong(COL_E_CREATED_AT) ?: 0L,
            totalOriginalValue = cursor.getSafeDouble(COL_E_TOTAL_ORIGINAL),
            paymentStatus = cursor.getSafeString(COL_E_PAYMENT_STATUS) ?: "PENDING",
            paidAmount = cursor.getSafeDouble(COL_E_PAID_AMOUNT) ?: 0.0,
            installmentTotal = cursor.getSafeInt(COL_E_INSTALLMENT_TOTAL) ?: 1,
            installmentCurrent = cursor.getSafeInt(COL_E_INSTALLMENT_CURRENT) ?: 1,
            dueDate = cursor.getSafeLong(COL_E_DUE_DATE),
            lastPaymentDate = cursor.getSafeLong(COL_E_LAST_PAYMENT)
        )
    }

    // ── Monthly Stats ──

    fun saveMonthlyStat(year: Int, month: Int, totalKm: Double, totalFuelCost: Double, avgConsumption: Double?) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_MS_YEAR, year)
                put(COL_MS_MONTH, month)
                put(COL_MS_TOTAL_KM, totalKm)
                put(COL_MS_TOTAL_FUEL_COST, totalFuelCost)
                put(COL_MS_AVG_CONSUMPTION, avgConsumption)
            }
            db.insertWithOnConflict(TABLE_MONTHLY_STATS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } finally {
            db.close()
        }
    }

    fun getMonthlyStats(): List<MonthlyStat> {
        val list = mutableListOf<MonthlyStat>()
        val cursor = readableDatabase.query(
            TABLE_MONTHLY_STATS, null, null, null, null, null,
            "$COL_MS_YEAR DESC, $COL_MS_MONTH DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(MonthlyStat(
                    id = it.getSafeLong(COL_ID) ?: 0L,
                    year = it.getSafeInt(COL_MS_YEAR) ?: 0,
                    month = it.getSafeInt(COL_MS_MONTH) ?: 0,
                    totalKm = it.getSafeDouble(COL_MS_TOTAL_KM) ?: 0.0,
                    totalFuelCost = it.getSafeDouble(COL_MS_TOTAL_FUEL_COST) ?: 0.0,
                    avgConsumption = it.getSafeDouble(COL_MS_AVG_CONSUMPTION)
                ))
            }
        }
        return list
    }

    // ── Cost Settings ──

    fun getMonthlyKm(): Int {
        val cursor = readableDatabase.query(
            TABLE_COST_SETTINGS, null, "$COL_ID = 1", null, null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) return it.getSafeInt(COL_CS_MONTHLY_KM) ?: 3000
        }
        return 3000
    }

    fun saveMonthlyKm(km: Int) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_ID, 1)
                put(COL_CS_MONTHLY_KM, km)
                put(COL_CS_UPDATED_AT, System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_COST_SETTINGS, null, cv,
                SQLiteDatabase.CONFLICT_REPLACE)
        } finally {
            db.close()
        }
    }

    fun getDistinctServiceTypes(): List<String> {
        val list = mutableListOf<String>()
        val cursor = readableDatabase.query(
            true, TABLE_NAME, arrayOf(COL_SERVICE_TYPE),
            "$COL_SERVICE_TYPE IS NOT NULL AND $COL_SERVICE_TYPE NOT LIKE 'Manual -%'", null, null, null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(0))
            }
        }
        return list
    }

    fun getRidesByDateRange(startMs: Long, endMs: Long): List<RideRecord> {
        val list = mutableListOf<RideRecord>()
        val cursor = readableDatabase.query(
            TABLE_NAME, null,
            "$COL_TIMESTAMP >= ? AND $COL_TIMESTAMP <= ?",
            arrayOf(startMs.toString(), endMs.toString()),
            null, null, "$COL_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(RideRecord(
                    id = it.getSafeLong(COL_ID) ?: 0L,
                    value = it.getSafeDouble(COL_VALUE),
                    distanceKm = it.getSafeDouble(COL_DISTANCE_KM),
                    timeMin = it.getSafeInt(COL_TIME_MIN),
                    rating = it.getSafeDouble(COL_RATING),
                    pricePerKm = it.getSafeDouble(COL_PRICE_PER_KM),
                    pricePerHour = it.getSafeDouble(COL_PRICE_PER_HOUR),
                    appName = it.getSafeString(COL_APP_NAME) ?: "",
                    timestamp = it.getSafeLong(COL_TIMESTAMP) ?: 0L,
                    pickupDistanceKm = it.getSafeDouble(COL_PICKUP_DISTANCE),
                    pickupTimeMin = it.getSafeInt(COL_PICKUP_TIME),
                    tripDistanceKm = it.getSafeDouble(COL_TRIP_DISTANCE),
                    tripTimeMin = it.getSafeInt(COL_TRIP_TIME),
                    serviceType = it.getSafeString(COL_SERVICE_TYPE),
                    bonusAmount = it.getSafeDouble(COL_BONUS_AMOUNT),
                    status = it.getSafeString(COL_STATUS) ?: "EXPIRED",
                    hasMultipleStops = it.getSafe(COL_HAS_MULTIPLE_STOPS, false),
                    scorePercent = it.getSafeDouble(COL_SCORE_PERCENT),
                    priorityBonus = it.getSafeDouble(COL_PRIORITY_BONUS),
                    dynamicBonus = it.getSafeDouble(COL_DYNAMIC_BONUS),
                    costPerKmAtTime = it.getSafeDouble(COL_COST_PER_KM_AT_TIME),
                    cardHash = it.getSafeString(COL_CARD_HASH)
                ))
            }
        }
        return list
    }

    // ── Daily Rides ──

    fun insertDailyRide(dailyRide: DailyRide): Long = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_DR_RIDE_ID, dailyRide.rideId)
                put(COL_DR_DATE, dailyRide.date)
                put(COL_DR_ORIGINAL_VALUE, dailyRide.originalValue)
                put(COL_DR_ADJUSTED_VALUE, dailyRide.adjustedValue)
                put(COL_DR_TIP_AMOUNT, dailyRide.tipAmount)
                put(COL_DR_IS_COMPLETED, if (dailyRide.isCompleted) 1 else 0)
                put(COL_DR_CANCELLED_WITH_FEE, if (dailyRide.cancelledWithFee) 1 else 0)
                put(COL_DR_NOTES, dailyRide.notes)
                put(COL_DR_CREATED_AT, dailyRide.createdAt)
                put(COL_DR_UPDATED_AT, dailyRide.updatedAt)
            }
            db.insert(TABLE_DAILY_RIDES, null, cv)
        } finally {
            db.close()
        }
    }

    fun getDailyRidesByDate(date: String): List<DailyRide> {
        val list = mutableListOf<DailyRide>()
        val cursor = readableDatabase.query(
            TABLE_DAILY_RIDES, null,
            "$COL_DR_DATE = ?", arrayOf(date),
            null, null, "$COL_DR_CREATED_AT ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(dailyRideFromCursor(it))
            }
        }
        return list
    }

    fun getDailyRidesByDateRange(startMs: Long, endMs: Long): List<DailyRide> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDate = dateFormat.format(Date(startMs))
        val endDate = dateFormat.format(Date(endMs))
        val list = mutableListOf<DailyRide>()
        val cursor = readableDatabase.query(
            TABLE_DAILY_RIDES, null,
            "$COL_DR_DATE >= ? AND $COL_DR_DATE <= ?",
            arrayOf(startDate, endDate),
            null, null, "$COL_DR_DATE ASC, $COL_DR_CREATED_AT ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(dailyRideFromCursor(it))
            }
        }
        return list
    }

    fun getAllDailyRides(): List<DailyRide> {
        val list = mutableListOf<DailyRide>()
        val cursor = readableDatabase.query(
            TABLE_DAILY_RIDES, null, null, null, null, null,
            "$COL_DR_DATE DESC, $COL_DR_CREATED_AT ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(dailyRideFromCursor(it))
            }
        }
        return list
    }

    fun updateDailyRide(dailyRide: DailyRide) = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_DR_RIDE_ID, dailyRide.rideId)
                put(COL_DR_DATE, dailyRide.date)
                put(COL_DR_ORIGINAL_VALUE, dailyRide.originalValue)
                put(COL_DR_ADJUSTED_VALUE, dailyRide.adjustedValue)
                put(COL_DR_TIP_AMOUNT, dailyRide.tipAmount)
                put(COL_DR_IS_COMPLETED, if (dailyRide.isCompleted) 1 else 0)
                put(COL_DR_CANCELLED_WITH_FEE, if (dailyRide.cancelledWithFee) 1 else 0)
                put(COL_DR_NOTES, dailyRide.notes)
                put(COL_DR_UPDATED_AT, System.currentTimeMillis())
            }
            db.update(TABLE_DAILY_RIDES, cv, "$COL_ID = ?", arrayOf(dailyRide.id.toString()))
        } finally {
            db.close()
        }
    }

    fun deleteDailyRide(id: Long) = synchronized(dbLock) {
        writableDatabase.delete(TABLE_DAILY_RIDES, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getTodayRideRecords(date: String): List<RideRecord> {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + 86_400_000
        val addedRideIds = getDailyRidesByDate(date).map { it.rideId }.toSet()
        return getFiltered(todayStart, null, 0)
            .filter { it.id !in addedRideIds }
            .filter { it.serviceType == null || !it.serviceType!!.startsWith("Manual -") }
    }

    fun insertManualRide(serviceType: String, value: Double, distanceKm: Double, timeMin: Int): Long = synchronized(dbLock) {
        val db = writableDatabase
        try {
            val cv = ContentValues().apply {
                put(COL_VALUE, value)
                put(COL_DISTANCE_KM, distanceKm)
                put(COL_TIME_MIN, timeMin)
                put(COL_TRIP_DISTANCE, distanceKm)
                put(COL_TRIP_TIME, timeMin)
                put(COL_APP_NAME, "")
                put(COL_TIMESTAMP, System.currentTimeMillis())
                put(COL_SERVICE_TYPE, "Manual - $serviceType")
                put(COL_STATUS, "COMPLETED")
            }
            db.insert(TABLE_NAME, null, cv)
        } finally {
            db.close()
        }
    }

    fun getCompletedVisitsCountForAddress(address: String): Int {
        if (address.isBlank()) return 0

        val sanitized = address
            .replace("%", "\\%")
            .replace("_", "\\_")

        val db = readableDatabase
        val cursor = db.rawQuery(
            """SELECT COUNT(*)
               FROM $TABLE_NAME r
               INNER JOIN $TABLE_DAILY_RIDES dr ON r.$COL_ID = dr.$COL_DR_RIDE_ID
               WHERE r.$COL_DROPOFF_ADDRESS LIKE ?
               AND dr.$COL_DR_IS_COMPLETED = 1""",
            arrayOf("%$sanitized%")
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0)
            }
        }
        return 0
    }

    fun isRideAlreadyAddedToday(rideId: Long, date: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE_DAILY_RIDES, arrayOf(COL_ID),
            "$COL_DR_RIDE_ID = ? AND $COL_DR_DATE = ?",
            arrayOf(rideId.toString(), date),
            null, null, null
        )
        cursor.use {
            return it.count > 0
        }
    }

    private fun dailyRideFromCursor(cursor: android.database.Cursor): DailyRide {
        return DailyRide(
            id = cursor.getSafeLong(COL_ID) ?: 0L,
            rideId = cursor.getSafeLong(COL_DR_RIDE_ID) ?: 0L,
            date = cursor.getSafeString(COL_DR_DATE) ?: "",
            originalValue = cursor.getSafeDouble(COL_DR_ORIGINAL_VALUE) ?: 0.0,
            adjustedValue = cursor.getSafeDouble(COL_DR_ADJUSTED_VALUE),
            tipAmount = cursor.getSafeDouble(COL_DR_TIP_AMOUNT) ?: 0.0,
            isCompleted = cursor.getSafe(COL_DR_IS_COMPLETED, false),
            cancelledWithFee = cursor.getSafe(COL_DR_CANCELLED_WITH_FEE, false),
            notes = cursor.getSafeString(COL_DR_NOTES),
            createdAt = cursor.getSafeLong(COL_DR_CREATED_AT) ?: 0L,
            updatedAt = cursor.getSafeLong(COL_DR_UPDATED_AT) ?: 0L
        )
    }

    companion object {
        private val dbLock = Any()
        private const val DATABASE_NAME = "profit_driving.db"
        private const val DATABASE_VERSION = 20
        private const val TABLE_NAME = "ride_history"
        private const val TABLE_FUEL_REFUELS = "fuel_refuels"
        private const val TABLE_EXPENSES = "expenses"
        private const val TABLE_FIXED_EXPENSES = "fixed_expenses"
        private const val TABLE_VARIABLE_COSTS = "variable_costs"
        private const val TABLE_COST_SETTINGS = "cost_settings"
        private const val TABLE_MONTHLY_STATS = "monthly_stats"
        private const val TABLE_DAILY_RIDES = "daily_rides"

        private const val COL_ID = "id"
        private const val COL_VALUE = "ride_value"
        private const val COL_DISTANCE_KM = "distance_km"
        private const val COL_TIME_MIN = "time_min"
        private const val COL_RATING = "rating"
        private const val COL_PRICE_PER_KM = "price_per_km"
        private const val COL_PRICE_PER_HOUR = "price_per_hour"
        private const val COL_APP_NAME = "app_name"
        private const val COL_TIMESTAMP = "ts"
        private const val COL_PICKUP_DISTANCE = "pickup_distance_km"
        private const val COL_PICKUP_TIME = "pickup_time_min"
        private const val COL_TRIP_DISTANCE = "trip_distance_km"
        private const val COL_TRIP_TIME = "trip_time_min"
        private const val COL_SERVICE_TYPE = "service_type"
        private const val COL_BONUS_AMOUNT = "bonus_amount"
        private const val COL_PRIORITY_BONUS = "priority_bonus"
        private const val COL_DYNAMIC_BONUS = "dynamic_bonus"
        private const val COL_STATUS = "status"
        private const val COL_STOPS = "stops"
        private const val COL_HAS_MULTIPLE_STOPS = "has_multiple_stops"
        private const val COL_SCORE_PERCENT = "score_percent"
        private const val COL_PICKUP_ADDRESS = "pickup_address"
        private const val COL_DROPOFF_ADDRESS = "dropoff_address"
        private const val COL_COST_PER_KM_AT_TIME = "cost_per_km_at_time"
        private const val COL_CARD_HASH = "card_hash"

        // Raw card logs columns
        private const val TABLE_RAW_LOGS = "raw_card_logs"
        private const val COL_RAW_ID = "id"
        private const val COL_RAW_RIDE_ID = "ride_id"
        private const val COL_RAW_CARD_HASH = "card_hash"
        private const val COL_RAW_TIMESTAMP = "timestamp"
        private const val COL_RAW_PACKAGE = "package_name"
        private const val COL_RAW_CARD_TYPE = "card_type"
        private const val COL_RAW_TEXTS = "raw_texts_json"
        private const val COL_RAW_RIDE_DATA = "ride_data_json"
        private const val COL_RAW_STATUS = "status"
        private const val COL_RAW_ERROR = "error"

        // Fuel refuels columns
        private const val COL_R_TIMESTAMP = "timestamp"
        private const val COL_R_ODOMETER = "odometer_km"
        private const val COL_R_LITERS = "liters"
        private const val COL_R_PRICE = "price_per_liter"
        private const val COL_R_TOTAL = "total_value"
        private const val COL_R_FULL_TANK = "is_full_tank"
        private const val COL_R_FUEL_TYPE = "fuel_type"
        private const val COL_R_NOTES = "notes"

        // Fixed expenses / unified expenses columns
        private const val COL_E_NAME = "name"
        private const val COL_E_VALUE = "value"
        private const val COL_E_CATEGORY = "category"
        private const val COL_E_CREATED_AT = "created_at"
        private const val COL_E_PERIODICITY = "periodicity"
        private const val COL_E_USEFUL_LIFE = "useful_life_months"
        private const val COL_EX_COST_TYPE = "cost_type"
        private const val COL_EX_PERIODICITY = "periodicity"
        private const val COL_EX_PROFIT_PCT = "percentage_of_profit"
        private const val COL_EX_EVENTS_PER_MONTH = "estimated_events_per_month"
        private const val COL_E_TOTAL_ORIGINAL = "total_original_value"
        private const val COL_E_PAYMENT_STATUS = "payment_status"
        private const val COL_E_PAID_AMOUNT = "paid_amount"
        private const val COL_E_INSTALLMENT_TOTAL = "installment_total"
        private const val COL_E_INSTALLMENT_CURRENT = "installment_current"
        private const val COL_E_DUE_DATE = "due_date"
        private const val COL_E_LAST_PAYMENT = "last_payment_date"

        // Variable costs columns
        private const val COL_VC_NAME = "name"
        private const val COL_VC_COST_PER_KM = "cost_per_km"
        private const val COL_VC_CATEGORY = "category"
        private const val COL_VC_CREATED_AT = "created_at"

        // Monthly stats columns
        private const val COL_MS_YEAR = "year"
        private const val COL_MS_MONTH = "month"
        private const val COL_MS_TOTAL_KM = "total_km"
        private const val COL_MS_TOTAL_FUEL_COST = "total_fuel_cost"
        private const val COL_MS_AVG_CONSUMPTION = "avg_consumption"

        // Cost settings columns
        private const val COL_CS_MONTHLY_KM = "monthly_km"
        private const val COL_CS_UPDATED_AT = "updated_at"

        // Daily rides columns
        private const val COL_DR_RIDE_ID = "ride_id"
        private const val COL_DR_DATE = "date"
        private const val COL_DR_ORIGINAL_VALUE = "original_value"
        private const val COL_DR_ADJUSTED_VALUE = "adjusted_value"
        private const val COL_DR_TIP_AMOUNT = "tip_amount"
        private const val COL_DR_IS_COMPLETED = "is_completed"
        private const val COL_DR_CANCELLED_WITH_FEE = "cancelled_with_fee"
        private const val COL_DR_FEE_VALUE = "fee_value"
        private const val COL_DR_NOTES = "notes"
        private const val COL_DR_CREATED_AT = "created_at"
        private const val COL_DR_UPDATED_AT = "updated_at"

        private val CREATE_FUEL_REFUELS = """
            CREATE TABLE $TABLE_FUEL_REFUELS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_R_TIMESTAMP INTEGER NOT NULL,
                $COL_R_ODOMETER REAL NOT NULL,
                $COL_R_LITERS REAL NOT NULL,
                $COL_R_PRICE REAL NOT NULL,
                $COL_R_TOTAL REAL NOT NULL,
                $COL_R_FULL_TANK INTEGER DEFAULT 0,
                $COL_R_FUEL_TYPE TEXT DEFAULT 'gasoline',
                $COL_R_NOTES TEXT
            )
        """.trimIndent()

        private val CREATE_FIXED_EXPENSES_V7 = """
            CREATE TABLE IF NOT EXISTS $TABLE_FIXED_EXPENSES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_E_NAME TEXT NOT NULL,
                $COL_E_VALUE REAL NOT NULL,
                $COL_E_CATEGORY TEXT,
                $COL_E_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()

        private val CREATE_FIXED_EXPENSES_V8 = """
            CREATE TABLE IF NOT EXISTS $TABLE_FIXED_EXPENSES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_E_NAME TEXT NOT NULL,
                $COL_E_VALUE REAL NOT NULL,
                $COL_E_CATEGORY TEXT,
                $COL_E_CREATED_AT INTEGER NOT NULL,
                $COL_E_PERIODICITY TEXT DEFAULT 'monthly',
                $COL_E_USEFUL_LIFE INTEGER DEFAULT 1
            )
        """.trimIndent()

        private val CREATE_VARIABLE_COSTS = """
            CREATE TABLE IF NOT EXISTS $TABLE_VARIABLE_COSTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_VC_NAME TEXT NOT NULL,
                $COL_VC_COST_PER_KM REAL NOT NULL,
                $COL_VC_CATEGORY TEXT,
                $COL_VC_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()

        private val CREATE_EXPENSES = """
            CREATE TABLE $TABLE_EXPENSES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                ${COL_E_NAME} TEXT NOT NULL,
                ${COL_E_VALUE} REAL NOT NULL,
                ${COL_EX_COST_TYPE} TEXT NOT NULL,
                ${COL_EX_PERIODICITY} TEXT,
                ${COL_E_USEFUL_LIFE} INTEGER DEFAULT 1,
                ${COL_EX_PROFIT_PCT} INTEGER,
                ${COL_EX_EVENTS_PER_MONTH} INTEGER,
                ${COL_E_CATEGORY} TEXT NOT NULL,
                ${COL_R_NOTES} TEXT,
                ${COL_E_CREATED_AT} INTEGER NOT NULL,
                ${COL_E_TOTAL_ORIGINAL} REAL,
                ${COL_E_PAYMENT_STATUS} TEXT DEFAULT 'PENDING',
                ${COL_E_PAID_AMOUNT} REAL DEFAULT 0,
                ${COL_E_INSTALLMENT_TOTAL} INTEGER DEFAULT 1,
                ${COL_E_INSTALLMENT_CURRENT} INTEGER DEFAULT 1,
                ${COL_E_DUE_DATE} INTEGER,
                ${COL_E_LAST_PAYMENT} INTEGER
            )
        """.trimIndent()

        private val CREATE_MONTHLY_STATS = """
            CREATE TABLE IF NOT EXISTS $TABLE_MONTHLY_STATS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MS_YEAR INTEGER NOT NULL,
                $COL_MS_MONTH INTEGER NOT NULL,
                $COL_MS_TOTAL_KM REAL NOT NULL,
                $COL_MS_TOTAL_FUEL_COST REAL NOT NULL,
                $COL_MS_AVG_CONSUMPTION REAL,
                UNIQUE($COL_MS_YEAR, $COL_MS_MONTH)
            )
        """.trimIndent()

        private val CREATE_DAILY_RIDES = """
            CREATE TABLE IF NOT EXISTS $TABLE_DAILY_RIDES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DR_RIDE_ID INTEGER NOT NULL,
                $COL_DR_DATE TEXT NOT NULL,
                $COL_DR_ORIGINAL_VALUE REAL NOT NULL,
                $COL_DR_ADJUSTED_VALUE REAL,
                $COL_DR_TIP_AMOUNT REAL DEFAULT 0,
                $COL_DR_IS_COMPLETED INTEGER DEFAULT 0,
                $COL_DR_CANCELLED_WITH_FEE INTEGER DEFAULT 0,
                $COL_DR_FEE_VALUE REAL,
                $COL_DR_NOTES TEXT,
                $COL_DR_CREATED_AT INTEGER,
                $COL_DR_UPDATED_AT INTEGER
            )
        """.trimIndent()

        private val CREATE_COST_SETTINGS = """
            CREATE TABLE IF NOT EXISTS $TABLE_COST_SETTINGS (
                $COL_ID INTEGER PRIMARY KEY DEFAULT 1,
                $COL_CS_MONTHLY_KM INTEGER DEFAULT 3000,
                $COL_CS_UPDATED_AT INTEGER NOT NULL
            )
        """.trimIndent()

        private val CREATE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_VALUE REAL,
                $COL_DISTANCE_KM REAL,
                $COL_TIME_MIN INTEGER,
                $COL_RATING REAL,
                $COL_PRICE_PER_KM REAL,
                $COL_PRICE_PER_HOUR REAL,
                $COL_APP_NAME TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_SERVICE_TYPE TEXT,
                $COL_BONUS_AMOUNT REAL,
                $COL_PRIORITY_BONUS REAL,
                $COL_DYNAMIC_BONUS REAL,
                $COL_STATUS TEXT DEFAULT 'EXPIRED',
                $COL_STOPS INTEGER,
                $COL_HAS_MULTIPLE_STOPS INTEGER DEFAULT 0,
                $COL_SCORE_PERCENT REAL,
                $COL_COST_PER_KM_AT_TIME REAL
            )
        """.trimIndent()

        private val CREATE_RAW_LOGS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_RAW_LOGS (
                $COL_RAW_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_RAW_RIDE_ID INTEGER,
                $COL_RAW_CARD_HASH TEXT,
                $COL_RAW_TIMESTAMP INTEGER NOT NULL,
                $COL_RAW_PACKAGE TEXT NOT NULL,
                $COL_RAW_CARD_TYPE TEXT,
                $COL_RAW_TEXTS TEXT NOT NULL,
                $COL_RAW_RIDE_DATA TEXT,
                $COL_RAW_STATUS TEXT DEFAULT 'pending',
                $COL_RAW_ERROR TEXT
            )
        """.trimIndent()

        private val CREATE_RAW_LOGS_INDEXES = """
            CREATE INDEX IF NOT EXISTS idx_raw_logs_ts ON $TABLE_RAW_LOGS($COL_RAW_TIMESTAMP);
            CREATE INDEX IF NOT EXISTS idx_raw_logs_hash ON $TABLE_RAW_LOGS($COL_RAW_CARD_HASH);
            CREATE INDEX IF NOT EXISTS idx_raw_logs_status ON $TABLE_RAW_LOGS($COL_RAW_STATUS);
        """.trimIndent()
    }
}

data class RideRecord(
    val id: Long = 0,
    val value: Double?,
    val distanceKm: Double?,
    val timeMin: Int?,
    val rating: Double?,
    val pricePerKm: Double?,
    val pricePerHour: Double?,
    val appName: String,
    val timestamp: Long,
    val pickupDistanceKm: Double? = null,
    val pickupTimeMin: Int? = null,
    val tripDistanceKm: Double? = null,
    val tripTimeMin: Int? = null,
    val serviceType: String? = null,
    val bonusAmount: Double? = null,
    val priorityBonus: Double? = null,
    val dynamicBonus: Double? = null,
    val status: String = "EXPIRED",
    val hasMultipleStops: Boolean = false,
    val scorePercent: Double? = null,
    val pickupAddress: String? = null,
    val dropoffAddress: String? = null,
    val costPerKmAtTime: Double? = null,
    val cardHash: String? = null
)

data class RefuelRecord(
    val id: Long = 0,
    val timestamp: Long,
    val odometerKm: Double,
    val liters: Double,
    val pricePerLiter: Double,
    val totalValue: Double,
    val isFullTank: Boolean,
    val fuelType: String,
    val notes: String? = null
)

data class FixedExpense(
    val id: Long = 0,
    val name: String,
    val value: Double,
    val category: String,
    val createdAt: Long,
    val periodicity: String = "monthly",
    val usefulLifeMonths: Int = 1
)

data class VariableCost(
    val id: Long = 0,
    val name: String,
    val costPerKm: Double,
    val category: String,
    val createdAt: Long
)

data class MonthlyStat(
    val id: Long = 0,
    val year: Int,
    val month: Int,
    val totalKm: Double,
    val totalFuelCost: Double,
    val avgConsumption: Double?
)

data class NormalizedExpense(
    val name: String,
    val monthlyCost: Double,
    val costPerKm: Double,
    val category: String,
    val periodicity: String?
)

data class CostSummary(
    val avgConsumption: Double,
    val fuelCostPerKm: Double,
    val normalizedExpenses: List<NormalizedExpense>,
    val totalFixedMonthlyCost: Double,
    val fixedCostPerKm: Double,
    val variableCostPerKm: Double,
    val totalCostPerKm: Double,
    val costPerHour: Double,
    val costPerMinute: Double
)

enum class CostType { FIXED, PER_KM, EVENT }

enum class Periodicity { MONTHLY, YEARLY }

enum class ExpenseCategory(val display: String, val icon: String) {
    FUEL("Combustível", "\u26FD"),
    MAINTENANCE("Manutenção", "\uD83D\uDD27"),
    TIRES("Pneus", "\uD83D\uDD04"),
    INSURANCE("Seguro", "\uD83D\uDEE1\uFE0F"),
    TAX("IPVA", "\uD83D\uDCB0"),
    WASH("Lavagem", "\uD83E\uDDFC"),
    PARKING("Estacionamento", "\uD83D\uDD7F\uFE0F"),
    TOLL("Pedágio", "\uD83D\uDEE3\uFE0F"),
    FINANCING("Financiamento", "\uD83C\uDFE6"),
    OTHER("Outros", "\uD83D\uDCCB")
}

enum class PaymentStatus { PENDING, PARTIAL, PAID }

data class Expense(
    val id: Long = 0,
    val name: String,
    val value: Double,
    val costType: CostType,
    val periodicity: Periodicity? = null,
    val usefulLifeMonths: Int? = null,
    val percentageOfProfit: Int? = null,
    val estimatedEventsPerMonth: Int? = null,
    val category: ExpenseCategory,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val totalOriginalValue: Double? = null,
    val paymentStatus: String = "PENDING",
    val paidAmount: Double = 0.0,
    val installmentTotal: Int = 1,
    val installmentCurrent: Int = 1,
    val dueDate: Long? = null,
    val lastPaymentDate: Long? = null
)
