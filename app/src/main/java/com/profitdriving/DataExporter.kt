package com.profitdriving

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class DataExporter(private val context: Context) {

    companion object {
        private const val FORMAT_VERSION = 1
        private const val TAG = "DataExporter"

        private val FIELD_MAP = mapOf(
            "v" to "value", "d" to "distanceKm", "t" to "timeMin", "r" to "rating",
            "pk" to "pricePerKm", "ph" to "pricePerHour", "a" to "appName",
            "ts" to "timestamp", "st" to "serviceType", "pb" to "priorityBonus",
            "db" to "dynamicBonus", "s" to "status", "ms" to "hasMultipleStops",
            "pd" to "pickupDistanceKm", "pt" to "pickupTimeMin",
            "td" to "tripDistanceKm", "tt" to "tripTimeMin",
            "sp" to "scorePercent", "pa" to "pickupAddress", "da" to "dropoffAddress",
            "cp" to "costPerKmAtTime", "ch" to "cardHash",
            "ks" to "kmState", "hs" to "hourState", "mn" to "minState", "rt" to "ratingState"
        )
        private val REVERSE_FIELD_MAP = FIELD_MAP.entries.associate { (k, v) -> v to k }

        private const val TABLE_RIDES = "ride_history"
        private const val TABLE_REFUELS = "fuel_refuels"
        private const val TABLE_EXPENSES = "expenses"
        private const val TABLE_DAILY_RIDES = "daily_rides"
        private const val TABLE_MONTHLY_STATS = "monthly_stats"
        private const val TABLE_FIXED_EXPENSES = "fixed_expenses"
        private const val TABLE_VARIABLE_COSTS = "variable_costs"
        private const val TABLE_COST_SETTINGS = "cost_settings"
        private const val TABLE_ADDRESS_REP = "address_reputation"
        private const val TABLE_RAW_LOGS = "raw_card_logs"
    }

    data class ExportResult(val success: Boolean, val message: String, val rideCount: Int = 0)
    data class ImportResult(val success: Boolean, val message: String, val rideCount: Int = 0)

    fun export(uri: Uri): ExportResult {
        return try {
            val db = DatabaseHelper(context)
            val root = JSONObject()

            root.put("v", FORMAT_VERSION)
            root.put("rides", ridesToJson(db.getAll()))
            root.put("refuels", refuelsToJson(db.getRefuels()))
            root.put("expenses", expensesToJson(db.getAllExpenses()))
            root.put("dailyRides", dailyRidesToJson(db.getAllDailyRides()))
            root.put("monthlyStats", monthlyStatsToJson(db.getMonthlyStats()))
            root.put("fixedExpenses", fixedExpensesToJson(db.getExpenses()))
            root.put("variableCosts", variableCostsToJson(db.getVariableCosts()))
            root.put("costSettings", costSettingsToJson(db.getMonthlyKm()))
            root.put("addressReputations", addressRepToJson(db.getAllAddressReputations()))

            val rawLogs = mutableListOf<RawCardLog>()
            try { rawLogs.addAll(db.getFailedRawCards(Int.MAX_VALUE)) } catch (_: Exception) {}
            try { rawLogs.addAll(db.getPendingRawCards(Int.MAX_VALUE)) } catch (_: Exception) {}
            root.put("rawCardLogs", rawCardLogsToJson(rawLogs.distinctBy { it.id }))

            val rideCount = db.getAll().size
            val json = root.toString()

            context.contentResolver.openOutputStream(uri)?.use { out ->
                val envelope = JSONObject()
                envelope.put("encrypted", true)
                envelope.put("data", FileEncryption.encrypt(json.toByteArray(Charsets.UTF_8), EncryptionConfig.EXPORT_PASSWORD))
                out.write(envelope.toString().toByteArray(Charsets.UTF_8))
            } ?: return ExportResult(false, "Não foi possível abrir o arquivo para escrita")

            ExportResult(true, "Exportado: $rideCount corridas", rideCount)
        } catch (e: Exception) {
            L.e(TAG, "Export falhou", e)
            ExportResult(false, "Erro na exportação: ${e.message}")
        }
    }

    fun import(uri: Uri, clearExisting: Boolean = false): ImportResult {
        return try {
            val rawStr = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: return ImportResult(false, "Não foi possível ler o arquivo")

            val envelope = JSONObject(rawStr)
            val jsonStr = if (envelope.optBoolean("encrypted", false)) {
                val data = envelope.optString("data", "")
                if (data.isEmpty()) return ImportResult(false, "Arquivo criptografado corrompido")
                try {
                    String(FileEncryption.decrypt(data, EncryptionConfig.EXPORT_PASSWORD), Charsets.UTF_8)
                } catch (e: Exception) {
                    return ImportResult(false, "Senha incorreta ou arquivo corrompido")
                }
            } else {
                rawStr
            }

            val root = JSONObject(jsonStr)
            val version = root.optInt("v", 0)
            if (version < 1 || version > FORMAT_VERSION) {
                return ImportResult(false, "Versão do arquivo não suportada: $version")
            }

            val db = DatabaseHelper(context)
            val dbW = db.writableDatabase

            dbW.rawQuery("PRAGMA busy_timeout = 5000", null).close()
            dbW.rawQuery("PRAGMA journal_mode = WAL", null).close()
            dbW.rawQuery("PRAGMA foreign_keys = OFF", null).close()

            dbW.beginTransaction()
            try {
                if (clearExisting) {
                    dbW.execSQL("DELETE FROM $TABLE_RIDES")
                    dbW.execSQL("DELETE FROM $TABLE_REFUELS")
                    dbW.execSQL("DELETE FROM $TABLE_EXPENSES")
                    dbW.execSQL("DELETE FROM $TABLE_DAILY_RIDES")
                    dbW.execSQL("DELETE FROM $TABLE_MONTHLY_STATS")
                    dbW.execSQL("DELETE FROM $TABLE_FIXED_EXPENSES")
                    dbW.execSQL("DELETE FROM $TABLE_VARIABLE_COSTS")
                    dbW.execSQL("DELETE FROM $TABLE_COST_SETTINGS")
                    dbW.execSQL("DELETE FROM $TABLE_ADDRESS_REP")
                    dbW.execSQL("DELETE FROM $TABLE_RAW_LOGS")
                }

                val rideCount = importRides(dbW, root.optJSONArray("rides"))
                importRefuels(dbW, root.optJSONArray("refuels"))
                importExpenses(dbW, root.optJSONArray("expenses"))
                importDailyRides(dbW, root.optJSONArray("dailyRides"))
                importMonthlyStats(dbW, root.optJSONArray("monthlyStats"))
                importFixedExpenses(dbW, root.optJSONArray("fixedExpenses"))
                importVariableCosts(dbW, root.optJSONArray("variableCosts"))
                importCostSettings(dbW, root.optJSONObject("costSettings"))
                importAddressReps(dbW, root.optJSONArray("addressReputations"))
                importRawCardLogs(dbW, root.optJSONArray("rawCardLogs"))

                dbW.setTransactionSuccessful()
                dbW.endTransaction()
                dbW.execSQL("PRAGMA foreign_keys = ON")

                ImportResult(true, "Importado: $rideCount corridas", rideCount)
            } catch (e: Exception) {
                dbW.endTransaction()
                try { dbW.execSQL("PRAGMA foreign_keys = ON") } catch (_: Exception) {}
                throw e
            }
        } catch (e: Exception) {
            L.e(TAG, "Import falhou", e)
            ImportResult(false, "Erro na importação: ${e.message}")
        }
    }

    // --- Serialization ---

    private fun ridesToJson(rides: List<RideRecord>): JSONArray {
        val arr = JSONArray()
        for (r in rides) {
            val obj = JSONObject()
            putOpt(obj, "v", r.value)
            putOpt(obj, "d", r.distanceKm)
            putOpt(obj, "t", r.timeMin)
            putOpt(obj, "r", r.rating)
            putOpt(obj, "pk", r.pricePerKm)
            putOpt(obj, "ph", r.pricePerHour)
            obj.put("a", r.appName)
            obj.put("ts", r.timestamp)
            putOpt(obj, "st", r.serviceType)
            putOpt(obj, "pb", r.priorityBonus)
            putOpt(obj, "db", r.dynamicBonus)
            obj.put("s", r.status)
            if (r.hasMultipleStops) obj.put("ms", 1)
            putOpt(obj, "pd", r.pickupDistanceKm)
            putOpt(obj, "pt", r.pickupTimeMin)
            putOpt(obj, "td", r.tripDistanceKm)
            putOpt(obj, "tt", r.tripTimeMin)
            putOpt(obj, "sp", r.scorePercent)
            putOpt(obj, "pa", r.pickupAddress)
            putOpt(obj, "da", r.dropoffAddress)
            putOpt(obj, "cp", r.costPerKmAtTime)
            putOpt(obj, "ch", r.cardHash)
            putOpt(obj, "ks", r.kmState)
            putOpt(obj, "hs", r.hourState)
            putOpt(obj, "mn", r.minState)
            putOpt(obj, "rt", r.ratingState)
            arr.put(obj)
        }
        return arr
    }

    private fun refuelsToJson(refuels: List<RefuelRecord>): JSONArray {
        val arr = JSONArray()
        for (r in refuels) {
            val obj = JSONObject()
            obj.put("ts", r.timestamp)
            obj.put("od", r.odometerKm)
            obj.put("am", r.amount)
            obj.put("pu", r.pricePerUnit)
            obj.put("ut", r.unitType)
            obj.put("tv", r.totalValue)
            obj.put("ft", r.fuelType)
            if (r.isFullTank) obj.put("fk", 1)
            putOpt(obj, "ct", r.chargerType)
            putOpt(obj, "ps", r.percentageStart)
            putOpt(obj, "pe", r.percentageEnd)
            putOpt(obj, "n", r.notes)
            putOpt(obj, "fl", r.fillLevel?.toDouble())
            if (r.wasFull) obj.put("wf", 1)
            putOpt(obj, "tc", r.totalCapacity)
            arr.put(obj)
        }
        return arr
    }

    private fun expensesToJson(expenses: List<Expense>): JSONArray {
        val arr = JSONArray()
        for (e in expenses) {
            val obj = JSONObject()
            obj.put("n", e.name)
            obj.put("v", e.value)
            obj.put("ct", e.costType.name)
            obj.put("cat", e.category.name)
            obj.put("ca", e.createdAt)
            putOpt(obj, "p", e.periodicity?.name)
            putOpt(obj, "ul", if (e.usefulLifeMonths != null && e.usefulLifeMonths != 1) e.usefulLifeMonths else null)
            putOpt(obj, "pp", e.percentageOfProfit)
            putOpt(obj, "epm", e.estimatedEventsPerMonth)
            putOpt(obj, "notes", e.notes)
            putOpt(obj, "tov", e.totalOriginalValue)
            obj.put("ps", e.paymentStatus)
            if (e.paidAmount != 0.0) obj.put("pa", e.paidAmount)
            if (e.installmentTotal != 1) obj.put("it", e.installmentTotal)
            if (e.installmentCurrent != 1) obj.put("ic", e.installmentCurrent)
            putOpt(obj, "dd", e.dueDate)
            putOpt(obj, "lpd", e.lastPaymentDate)
            arr.put(obj)
        }
        return arr
    }

    private fun dailyRidesToJson(rides: List<DailyRide>): JSONArray {
        val arr = JSONArray()
        for (r in rides) {
            val obj = JSONObject()
            obj.put("ri", r.rideId)
            obj.put("d", r.date)
            obj.put("ov", r.originalValue)
            putOpt(obj, "av", r.adjustedValue)
            if (r.tipAmount != 0.0) obj.put("ta", r.tipAmount)
            if (r.isCompleted) obj.put("ic", 1)
            if (r.cancelledWithFee) obj.put("cf", 1)
            putOpt(obj, "n", r.notes)
            obj.put("ca", r.createdAt)
            obj.put("ua", r.updatedAt)
            arr.put(obj)
        }
        return arr
    }

    private fun monthlyStatsToJson(stats: List<MonthlyStat>): JSONArray {
        val arr = JSONArray()
        for (s in stats) {
            val obj = JSONObject()
            obj.put("y", s.year)
            obj.put("m", s.month)
            obj.put("tk", s.totalKm)
            obj.put("tf", s.totalFuelCost)
            putOpt(obj, "ac", s.avgConsumption)
            arr.put(obj)
        }
        return arr
    }

    private fun fixedExpensesToJson(expenses: List<FixedExpense>): JSONArray {
        val arr = JSONArray()
        for (e in expenses) {
            val obj = JSONObject()
            obj.put("n", e.name)
            obj.put("v", e.value)
            obj.put("cat", e.category)
            obj.put("ca", e.createdAt)
            obj.put("p", e.periodicity)
            if (e.usefulLifeMonths != 1) obj.put("ul", e.usefulLifeMonths)
            arr.put(obj)
        }
        return arr
    }

    private fun variableCostsToJson(costs: List<VariableCost>): JSONArray {
        val arr = JSONArray()
        for (c in costs) {
            val obj = JSONObject()
            obj.put("n", c.name)
            obj.put("cpk", c.costPerKm)
            obj.put("cat", c.category)
            obj.put("ca", c.createdAt)
            arr.put(obj)
        }
        return arr
    }

    private fun costSettingsToJson(monthlyKm: Int): JSONObject {
        val obj = JSONObject()
        obj.put("mk", monthlyKm)
        return obj
    }

    private fun addressRepToJson(reps: List<Pair<String, Int>>): JSONArray {
        val arr = JSONArray()
        for ((addr, rep) in reps) {
            val a = JSONArray()
            a.put(addr)
            a.put(rep)
            arr.put(a)
        }
        return arr
    }

    private fun rawCardLogsToJson(logs: List<RawCardLog>): JSONArray {
        val arr = JSONArray()
        for (log in logs) {
            val obj = JSONObject()
            putOpt(obj, "ri", log.rideId)
            putOpt(obj, "ch", log.cardHash)
            obj.put("ts", log.timestamp)
            obj.put("pkg", log.packageName)
            putOpt(obj, "ct", log.cardType)
            obj.put("rtj", log.rawTextsJson)
            putOpt(obj, "rdj", log.rideDataJson)
            obj.put("s", log.status)
            putOpt(obj, "e", log.error)
            putOpt(obj, "pa", log.processedAt)
            arr.put(obj)
        }
        return arr
    }

    // --- Deserialization with merge logic ---

    private fun importRides(dbW: android.database.sqlite.SQLiteDatabase, arr: JSONArray?): Int {
        if (arr == null) return 0
        val existingHashes = mutableSetOf<String>()
        val existingCombos = mutableSetOf<String>()
        dbW.rawQuery("SELECT card_hash, ts, ride_value, app_name FROM $TABLE_RIDES", null).use { c ->
            while (c.moveToNext()) {
                val hash = c.getStringOrNull(0)
                if (hash != null) existingHashes.add(hash)
                val ts = c.getLong(1)
                val v = c.getDoubleOrNull(2)
                val app = c.getStringOrNull(3)
                if (app != null) existingCombos.add("$ts|${v}|$app")
            }
        }
        var count = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val cardHash = o.optStringOrNull("ch")
            val ts = o.getLong("ts")
            val app = o.getString("a")
            val v = o.optDoubleOrNull("v")
            if (cardHash != null && cardHash in existingHashes) continue
            if ("$ts|${v}|$app" in existingCombos) continue
            val cv = ContentValues()
            cv.put("ride_value", if (v != null) v else java.lang.Double.NaN)
            cv.put("app_name", app)
            cv.put("ts", ts)
            cv.put("status", o.optString("s", "EXPIRED"))
            cv.put("has_multiple_stops", if (o.optInt("ms", 0) == 1) 1 else 0)
            cv.put("distance_km", o.optDouble("d", 0.0))
            cv.put("time_min", o.optInt("t", 0))
            cv.put("rating", o.optDouble("r", 0.0))
            cv.put("price_per_km", o.optDouble("pk", 0.0))
            cv.put("price_per_hour", o.optDouble("ph", 0.0))
            cv.put("service_type", o.optString("st", ""))
            cv.put("priority_bonus", o.optDouble("pb", 0.0))
            cv.put("dynamic_bonus", o.optDouble("db", 0.0))
            cv.put("pickup_distance_km", o.optDouble("pd", 0.0))
            cv.put("pickup_time_min", o.optInt("pt", 0))
            cv.put("trip_distance_km", o.optDouble("td", 0.0))
            cv.put("trip_time_min", o.optInt("tt", 0))
            cv.put("score_percent", o.optDouble("sp", 0.0))
            cv.put("pickup_address", o.optString("pa", ""))
            cv.put("dropoff_address", o.optString("da", ""))
            cv.put("cost_per_km_at_time", o.optDouble("cp", 0.0))
            cv.put("card_hash", cardHash ?: "")
            cv.put("km_state", o.optInt("ks", 0))
            cv.put("hour_state", o.optInt("hs", 0))
            cv.put("min_state", o.optInt("mn", 0))
            cv.put("rating_state", o.optInt("rt", 0))
            dbW.insertOrThrow(TABLE_RIDES, null, cv)
            existingHashes.add(cardHash ?: "")
            existingCombos.add("$ts|${v}|$app")
            count++
        }
        return count
    }

    private fun importRefuels(dbW: android.database.sqlite.SQLiteDatabase, arr: JSONArray?) {
        if (arr == null) return
        val existing = mutableSetOf<String>()
        dbW.rawQuery("SELECT timestamp, odometer_km, amount FROM $TABLE_REFUELS", null).use { c ->
            while (c.moveToNext()) {
                existing.add("${c.getLong(0)}|${c.getDouble(1)}|${c.getDouble(2)}")
            }
        }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ts = o.getLong("ts")
            val od = o.getDouble("od")
            val am = o.getDouble("am")
            val key = "$ts|$od|$am"
            if (key in existing) continue
            val cv = ContentValues()
            cv.put("timestamp", ts)
            cv.put("odometer_km", od)
            cv.put("amount", am)
            cv.put("price_per_unit", o.getDouble("pu"))
            cv.put("unit_type", o.optString("ut", "L"))
            cv.put("total_value", o.getDouble("tv"))
            cv.put("is_full_tank", if (o.optInt("fk", 0) == 1) 1 else 0)
            cv.put("fuel_type", o.getString("ft"))
            cv.put("charger_type", o.optString("ct", ""))
            cv.put("percentage_start", o.optInt("ps", 0))
            cv.put("percentage_end", o.optInt("pe", 0))
            cv.put("notes", o.optString("n", ""))
            cv.put("fill_level", o.optDouble("fl", 0.0).toFloat())
            cv.put("was_full", if (o.optInt("wf", 0) == 1) 1 else 0)
            cv.put("total_capacity", o.optDouble("tc", 0.0))
            dbW.insertOrThrow(TABLE_REFUELS, null, cv)
            existing.add(key)
        }
    }

    private fun importExpenses(dbW: android.database.sqlite.SQLiteDatabase, arr: JSONArray?) {
        if (arr == null) return
        val existing = mutableSetOf<String>()
        dbW.rawQuery("SELECT name, value, created_at, category FROM $TABLE_EXPENSES", null).use { c ->
            while (c.moveToNext()) {
                existing.add("${c.getString(0)}|${c.getDouble(1)}|${c.getLong(2)}|${c.getString(3)}")
            }
        }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.getString("n")
            val value = o.getDouble("v")
            val ca = o.getLong("ca")
            val cat = o.getString("cat")
            val key = "$name|$value|$ca|$cat"
            if (key in existing) continue
            val cv = ContentValues()
            cv.put("name", name)
            cv.put("value", value)
            cv.put("cost_type", o.optString("ct", "FIXED"))
            cv.put("category", try { ExpenseCategory.valueOf(cat).name } catch (_: Exception) { "OTHER" })
            cv.put("created_at", ca)
            cv.put("periodicity", o.optString("p", ""))
            cv.put("useful_life_months", o.optInt("ul", 0))
            cv.put("percentage_of_profit", o.optInt("pp", 0))
            cv.put("estimated_events_per_month", o.optInt("epm", 0))
            cv.put("notes", o.optString("notes", ""))
            cv.put("total_original_value", o.optDouble("tov", 0.0))
            cv.put("payment_status", o.optString("ps", "PENDING"))
            if (o.optDouble("pa", 0.0) != 0.0) cv.put("paid_amount", o.getDouble("pa"))
            cv.put("installment_total", o.optInt("it", 1))
            cv.put("installment_current", o.optInt("ic", 1))
            cv.put("due_date", o.optLong("dd", 0L))
            cv.put("last_payment_date", o.optLong("lpd", 0L))
            dbW.insertOrThrow(TABLE_EXPENSES, null, cv)
            existing.add(key)
        }
    }

    private fun importDailyRides(dbW: android.database.sqlite.SQLiteDatabase, arr: JSONArray?) {
        if (arr == null) return
        val existing = mutableSetOf<String>()
        dbW.rawQuery("SELECT ride_id, date FROM $TABLE_DAILY_RIDES", null).use { c ->
            while (c.moveToNext()) {
                existing.add("${c.getLong(0)}|${c.getString(1)}")
            }
        }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val rideId = o.getLong("ri")
            val date = o.getString("d")
            val key = "$rideId|$date"
            if (key in existing) continue
            val cv = ContentValues()
            cv.put("ride_id", rideId)
            cv.put("date", date)
            cv.put("original_value", o.getDouble("ov"))
            cv.put("adjusted_value", o.optDouble("av", 0.0))
            cv.put("tip_amount", o.optDouble("ta", 0.0))
            cv.put("is_completed", if (o.optInt("ic", 0) == 1) 1 else 0)
            cv.put("cancelled_with_fee", if (o.optInt("cf", 0) == 1) 1 else 0)
            cv.put("notes", o.optString("n", ""))
            cv.put("created_at", o.optLong("ca", System.currentTimeMillis()))
            cv.put("updated_at", o.optLong("ua", System.currentTimeMillis()))
            dbW.insertOrThrow(TABLE_DAILY_RIDES, null, cv)
        }
    }

    private fun importMonthlyStats(dbW: android.database.sqlite.SQLiteDatabase, arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val cv = ContentValues()
            cv.put("year", o.getInt("y"))
            cv.put("month", o.getInt("m"))
            cv.put("total_km", o.getDouble("tk"))
            cv.put("total_fuel_cost", o.getDouble("tf"))
            cv.put("avg_consumption", o.optDouble("ac", 0.0))
            dbW.insertWithOnConflict(TABLE_MONTHLY_STATS, null, cv,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun importFixedExpenses(dbW: android.database.sqlite.SQLiteDatabase, arr: JSONArray?) {
        if (arr == null) return
        val existing = mutableSetOf<String>()
        dbW.rawQuery("SELECT name, value, created_at FROM $TABLE_FIXED_EXPENSES", null).use { c ->
            while (c.moveToNext()) {
                existing.add("${c.getString(0)}|${c.getDouble(1)}|${c.getLong(2)}")
            }
        }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.getString("n")
            val value = o.getDouble("v")
            val ca = o.getLong("ca")
            val key = "$name|$value|$ca"
            if (key in existing) continue
            val cv = ContentValues()
            cv.put("name", name)
            cv.put("value", value)
            cv.put("category", o.getString("cat"))
            cv.put("created_at", ca)
            cv.put("periodicity", o.optString("p", "monthly"))
            cv.put("useful_life_months", o.optInt("ul", 1))
            dbW.insertOrThrow(TABLE_FIXED_EXPENSES, null, cv)
            existing.add(key)
        }
    }

    private fun importVariableCosts(dbW: android.database.sqlite.SQLiteDatabase, arr: JSONArray?) {
        if (arr == null) return
        val existing = mutableSetOf<String>()
        dbW.rawQuery("SELECT name, cost_per_km FROM $TABLE_VARIABLE_COSTS", null).use { c ->
            while (c.moveToNext()) {
                existing.add("${c.getString(0)}|${c.getDouble(1)}")
            }
        }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.getString("n")
            val costPerKm = o.getDouble("cpk")
            val key = "$name|$costPerKm"
            if (key in existing) continue
            val cv = ContentValues()
            cv.put("name", name)
            cv.put("cost_per_km", costPerKm)
            cv.put("category", o.getString("cat"))
            cv.put("created_at", o.getLong("ca"))
            dbW.insertOrThrow(TABLE_VARIABLE_COSTS, null, cv)
            existing.add(key)
        }
    }

    private fun importCostSettings(dbW: android.database.sqlite.SQLiteDatabase, obj: JSONObject?) {
        if (obj == null) return
        dbW.execSQL("INSERT OR REPLACE INTO $TABLE_COST_SETTINGS (id, monthly_km, updated_at) VALUES (1, ?, ?)",
            arrayOf(obj.getInt("mk"), System.currentTimeMillis()))
    }

    private fun importAddressReps(dbW: android.database.sqlite.SQLiteDatabase, arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val a = arr.getJSONArray(i)
            dbW.execSQL("INSERT OR REPLACE INTO $TABLE_ADDRESS_REP (normalized_address, reputation, updated_at) VALUES (?, ?, ?)",
                arrayOf(a.getString(0), a.getInt(1), System.currentTimeMillis()))
        }
    }

    private fun importRawCardLogs(dbW: android.database.sqlite.SQLiteDatabase, arr: JSONArray?) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val cv = ContentValues()
            if (obj.has("ri")) cv.put("ride_id", obj.getLong("ri"))
            if (obj.has("ch")) cv.put("card_hash", obj.getString("ch"))
            cv.put("timestamp", obj.getLong("ts"))
            cv.put("package_name", obj.getString("pkg"))
            if (obj.has("ct")) cv.put("card_type", obj.getString("ct"))
            cv.put("raw_texts_json", obj.getString("rtj"))
            if (obj.has("rdj")) cv.put("ride_data_json", obj.getString("rdj"))
            cv.put("status", obj.optString("s", "pending"))
            if (obj.has("e")) cv.put("error", obj.getString("e"))
            if (obj.has("pa")) cv.put("processed_at", obj.getLong("pa"))
            dbW.insertOrThrow(TABLE_RAW_LOGS, null, cv)
        }
    }

    // --- Helpers ---

    private fun putOpt(obj: JSONObject, key: String, value: Any?) {
        if (value != null) obj.put(key, value)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        return if (has(key) && !isNull(key) && !optString(key).isEmpty()) optDouble(key) else null
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        return if (has(key) && !isNull(key)) optInt(key) else null
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        return if (has(key) && !isNull(key)) optLong(key) else null
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key) else null
    }

    private fun Cursor.getStringOrNull(idx: Int): String? {
        return if (isNull(idx)) null else getString(idx)
    }

    private fun Cursor.getDoubleOrNull(idx: Int): Double? {
        return if (isNull(idx)) null else getDouble(idx)
    }
}
