package com.profitdriving.accessibility.extractor

data class RawCardData(
    val cardType: CardType,
    val valueNode: String?,
    val pickupNode: String?,
    val tripNode: String?,
    val ratingNode: String?,
    val serviceNode: String?,
    val bonusNodes: List<String>,
    val acceptNode: String?,
    val rawTexts: List<String>,
    val validCropCount: Int = 0
) {
    val fullText: String
        get() = rawTexts.joinToString("\n")
}
