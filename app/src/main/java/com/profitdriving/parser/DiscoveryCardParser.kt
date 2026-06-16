package com.profitdriving.parser

import com.profitdriving.L
import com.profitdriving.RideData
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.accessibility.extractor.UberCardExtractor
import java.util.Locale

class DiscoveryCardParser : RideDataParser {

    override fun canParse(raw: RawCardData): Boolean {
        return false
    }

    override fun parse(raw: RawCardData): RideData? {
        L.d(TAG, "DiscoveryCardParser.parse() iniciado — desativado, retornando null")
        return null
    }

    companion object {
        private const val TAG = "DiscoveryCardParser"
    }
}
