package com.profitdriving

import android.database.Cursor

inline fun <reified T> Cursor.getSafe(columnName: String, default: T): T {
    val idx = getColumnIndex(columnName)
    return if (idx == -1 || isNull(idx)) default else when (default) {
        is Long -> getLong(idx) as T
        is Int -> getInt(idx) as T
        is Double -> getDouble(idx) as T
        is String -> getString(idx) as T
        is Boolean -> (getInt(idx) == 1) as T
        else -> default
    }
}

fun Cursor.getSafeDouble(columnName: String): Double? {
    val idx = getColumnIndex(columnName)
    return if (idx == -1 || isNull(idx)) null else getDouble(idx)
}

fun Cursor.getSafeInt(columnName: String): Int? {
    val idx = getColumnIndex(columnName)
    return if (idx == -1 || isNull(idx)) null else getInt(idx)
}

fun Cursor.getSafeLong(columnName: String): Long? {
    val idx = getColumnIndex(columnName)
    return if (idx == -1 || isNull(idx)) null else getLong(idx)
}

fun Cursor.getSafeString(columnName: String): String? {
    val idx = getColumnIndex(columnName)
    return if (idx == -1 || isNull(idx)) null else getString(idx)
}
