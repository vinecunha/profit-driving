package com.profitdriving

import android.util.Log

object L {
    private val debug = BuildConfig.DEBUG
    fun d(tag: String, msg: String) { if (debug) Log.d(tag, msg) }
    fun w(tag: String, msg: String) { if (debug) Log.w(tag, msg) }
    fun e(tag: String, msg: String, t: Throwable? = null) { if (debug) Log.e(tag, msg, t) }
}
