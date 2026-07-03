package com.profitdriving

import android.content.Context

class AddressReputationManager(private val context: Context) {

    enum class Reputation { GREEN, BLACK, NONE }

    private val db by lazy { DatabaseHelper(context) }

    fun getReputation(rawAddress: String?): Reputation {
        val key = normalize(rawAddress) ?: return Reputation.NONE
        return when (db.getAddressReputation(key)) {
            1    -> Reputation.GREEN
            -1   -> Reputation.BLACK
            else -> Reputation.NONE
        }
    }

    fun setReputation(rawAddress: String?, reputation: Reputation) {
        val key = normalize(rawAddress) ?: return
        val value = when (reputation) {
            Reputation.GREEN -> 1
            Reputation.BLACK -> -1
            Reputation.NONE  -> 0
        }
        db.setAddressReputation(key, value)
    }

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val prefixes = listOf("avenida ", "av. ", "av ", "rua ", "r. ", "r ",
                              "travessa ", "tv. ", "alameda ", "al. ")
        var s = raw.lowercase().trim()
        for (prefix in prefixes) {
            if (s.startsWith(prefix)) { s = s.removePrefix(prefix).trim(); break }
        }
        s = s.replace(Regex("\\s+"), " ")
             .replace(Regex("[,\\-]+\\s*$"), "")
             .trim()
        return if (s.length < 6) null else s.take(30)
    }
}
