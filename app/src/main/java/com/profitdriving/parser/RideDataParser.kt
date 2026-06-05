package com.profitdriving.parser

import com.profitdriving.RideData
import com.profitdriving.accessibility.extractor.RawCardData

interface RideDataParser {
    fun canParse(raw: RawCardData): Boolean
    fun parse(raw: RawCardData): RideData?
}
