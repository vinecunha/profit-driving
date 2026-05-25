package com.profitdriving

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_PICKUP_DISTANCE REAL")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_PICKUP_TIME INTEGER")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_TRIP_DISTANCE REAL")
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_TRIP_TIME INTEGER")
        }
    }

    fun insert(record: RideRecord): Long {
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
        }
        return writableDatabase.insert(TABLE_NAME, null, cv)
    }

    fun getAll(): List<RideRecord> {
        val list = mutableListOf<RideRecord>()
        val cursor = readableDatabase.query(
            TABLE_NAME, null, null, null, null, null,
            "$COL_TIMESTAMP DESC", "100"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(RideRecord(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                    value = it.getDouble(it.getColumnIndexOrThrow(COL_VALUE)).let { v ->
                        if (it.isNull(it.getColumnIndexOrThrow(COL_VALUE))) null else v
                    },
                    distanceKm = it.getDouble(it.getColumnIndexOrThrow(COL_DISTANCE_KM)).let { v ->
                        if (it.isNull(it.getColumnIndexOrThrow(COL_DISTANCE_KM))) null else v
                    },
                    timeMin = it.getInt(it.getColumnIndexOrThrow(COL_TIME_MIN)).let { v ->
                        if (it.isNull(it.getColumnIndexOrThrow(COL_TIME_MIN))) null else v
                    },
                    rating = it.getDouble(it.getColumnIndexOrThrow(COL_RATING)).let { v ->
                        if (it.isNull(it.getColumnIndexOrThrow(COL_RATING))) null else v
                    },
                    pricePerKm = it.getDouble(it.getColumnIndexOrThrow(COL_PRICE_PER_KM)).let { v ->
                        if (it.isNull(it.getColumnIndexOrThrow(COL_PRICE_PER_KM))) null else v
                    },
                    pricePerHour = it.getDouble(it.getColumnIndexOrThrow(COL_PRICE_PER_HOUR)).let { v ->
                        if (it.isNull(it.getColumnIndexOrThrow(COL_PRICE_PER_HOUR))) null else v
                    },
                    appName = it.getString(it.getColumnIndexOrThrow(COL_APP_NAME)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    pickupDistanceKm = it.getDouble(it.getColumnIndexOrThrow(COL_PICKUP_DISTANCE)).let { v ->
                        if (it.isNull(it.getColumnIndexOrThrow(COL_PICKUP_DISTANCE))) null else v
                    },
                    pickupTimeMin = it.getInt(it.getColumnIndexOrThrow(COL_PICKUP_TIME)).let { v ->
                        if (it.isNull(it.getColumnIndexOrThrow(COL_PICKUP_TIME))) null else v
                    },
                    tripDistanceKm = it.getDouble(it.getColumnIndexOrThrow(COL_TRIP_DISTANCE)).let { v ->
                        if (it.isNull(it.getColumnIndexOrThrow(COL_TRIP_DISTANCE))) null else v
                    },
                    tripTimeMin = it.getInt(it.getColumnIndexOrThrow(COL_TRIP_TIME)).let { v ->
                        if (it.isNull(it.getColumnIndexOrThrow(COL_TRIP_TIME))) null else v
                    }
                ))
            }
        }
        return list
    }

    fun deleteAll() {
        writableDatabase.delete(TABLE_NAME, null, null)
    }

    companion object {
        private const val DATABASE_NAME = "profit_driving.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_NAME = "ride_history"

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
                $COL_TIMESTAMP INTEGER NOT NULL
            )
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
    val tripTimeMin: Int? = null
)
