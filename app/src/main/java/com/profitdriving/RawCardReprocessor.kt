package com.profitdriving

import android.content.Context
import com.profitdriving.accessibility.extractor.CardType
import com.profitdriving.accessibility.extractor.RawCardData
import com.profitdriving.parser.RideDataParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RawCardReprocessor(
    private val context: Context,
    private val parsers: List<RideDataParser>
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var periodicJob: Job? = null

    fun startPeriodicReprocessing(intervalMs: Long = DAILY_INTERVAL_MS) {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (true) {
                reprocessFailedCards()
                reprocessPendingCards()
                delay(intervalMs)
            }
        }
    }

    fun stopPeriodicReprocessing() {
        periodicJob?.cancel()
        periodicJob = null
    }

    suspend fun reprocessFailedCards(limit: Int = 50): Int {
        val db = DatabaseHelper(context)
        val failedCards = db.getFailedRawCards(limit)
        var reprocessedCount = 0
        for (card in failedCards) {
            val success = tryReprocess(db, card)
            if (success) reprocessedCount++
        }
        return reprocessedCount
    }

    suspend fun reprocessSuccessfulCards(limit: Int = 50): Int {
        val db = DatabaseHelper(context)
        val successfulCards = db.getSuccessfulRawCards(limit)
        var reprocessedCount = 0
        for (card in successfulCards) {
            val rideId = card.rideId ?: continue
            val rawData = RawCardData(
                cardType = try {
                    CardType.valueOf(card.cardType ?: "UNKNOWN")
                } catch (_: IllegalArgumentException) {
                    CardType.UNKNOWN
                },
                valueNode = null,
                pickupNode = null,
                tripNode = null,
                ratingNode = null,
                serviceNode = null,
                bonusNodes = emptyList(),
                acceptNode = null,
                rawTexts = card.rawTextsList
            )
            for (parser in parsers) {
                if (parser.canParse(rawData)) {
                    val rideData = parser.parse(rawData)
                    if (rideData != null && rideData.value != null && rideData.value > 0) {
                        db.updateRawLogRideData(card.id, rideData)
                        db.updateRideFromRawData(rideId, rideData)
                        reprocessedCount++
                    }
                    break
                }
            }
        }
        return reprocessedCount
    }

    suspend fun reprocessPendingCards(limit: Int = 50): Int {
        val db = DatabaseHelper(context)
        val pendingCards = db.getPendingRawCards(limit)
        var reprocessedCount = 0
        for (card in pendingCards) {
            val success = tryReprocess(db, card)
            if (success) reprocessedCount++
        }
        return reprocessedCount
    }

    private fun tryReprocess(db: DatabaseHelper, card: RawCardLog): Boolean {
        val rawData = RawCardData(
            cardType = try {
                CardType.valueOf(card.cardType ?: "UNKNOWN")
            } catch (_: IllegalArgumentException) {
                CardType.UNKNOWN
            },
            valueNode = null,
            pickupNode = null,
            tripNode = null,
            ratingNode = null,
            serviceNode = null,
            bonusNodes = emptyList(),
            acceptNode = null,
            rawTexts = card.rawTextsList
        )

        for (parser in parsers) {
            if (parser.canParse(rawData)) {
                val rideData = parser.parse(rawData)
                if (rideData != null && rideData.value != null && rideData.value > 0) {
                    val json = db.buildRawDataStringExternal(rideData)
                    db.markRawCardAsProcessed(
                        logId = card.id,
                        status = "success",
                        rideDataJson = json
                    )
                    return true
                }
            }
        }

        db.markRawCardAsProcessed(
            logId = card.id,
            status = "failed",
            rideDataJson = null
        )
        return false
    }

    fun reprocessInBackground(
        limit: Int = 50,
        includeSuccessful: Boolean = false,
        onComplete: ((Int) -> Unit)? = null
    ) {
        scope.launch {
            val failedCount = reprocessFailedCards(limit)
            val pendingCount = reprocessPendingCards(limit)
            val successCount = if (includeSuccessful) reprocessSuccessfulCards(limit) else 0
            onComplete?.invoke(failedCount + pendingCount + successCount)
        }
    }

    companion object {
        private const val DAILY_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
