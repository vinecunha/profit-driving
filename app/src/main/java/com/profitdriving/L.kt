package com.profitdriving

import android.util.Log

object L {
    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }

    private val minLevel = if (BuildConfig.DEBUG) Level.VERBOSE else Level.WARN

    private fun shouldLog(level: Level): Boolean = level.ordinal >= minLevel.ordinal

    fun v(tag: String, msg: String) {
        if (shouldLog(Level.VERBOSE)) Log.v(tag, msg)
    }
    fun d(tag: String, msg: String) {
        if (shouldLog(Level.DEBUG)) Log.d(tag, msg)
    }
    fun i(tag: String, msg: String) {
        if (shouldLog(Level.INFO)) Log.i(tag, msg)
    }
    fun w(tag: String, msg: String) {
        if (shouldLog(Level.WARN)) Log.w(tag, msg)
    }
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (shouldLog(Level.ERROR)) Log.e(tag, msg, t)
    }
}
