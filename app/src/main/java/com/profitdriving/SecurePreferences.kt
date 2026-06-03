package com.profitdriving

import android.content.Context
import android.content.SharedPreferences

object SecurePreferences {
    fun get(context: Context): SharedPreferences =
        ProfitDrivingApp.getInstance().prefs

    fun getFloat(context: Context, key: String, def: Float): Float =
        get(context).getFloat(key, def)

    fun getInt(context: Context, key: String, def: Int): Int =
        get(context).getInt(key, def)

    fun getBoolean(context: Context, key: String, def: Boolean): Boolean =
        get(context).getBoolean(key, def)

    fun getString(context: Context, key: String, def: String?): String? =
        get(context).getString(key, def)

    fun edit(context: Context): SharedPreferences.Editor =
        get(context).edit()

    fun putInt(context: Context, key: String, value: Int) {
        edit(context).putInt(key, value).apply()
    }

    fun putFloat(context: Context, key: String, value: Float) {
        edit(context).putFloat(key, value).apply()
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        edit(context).putBoolean(key, value).apply()
    }

    fun putString(context: Context, key: String, value: String) {
        edit(context).putString(key, value).apply()
    }
}
