package com.profitdriving

import android.content.SharedPreferences

object DecisionEngine {

    data class ParamScore(
        val name: String,
        val rank: Int,
        val state: ParamState,
        val score: Double,
        val value: Double?,
        val min: Double,
        val ideal: Double
    ) {
        private val rankWeight: Double get() = when (rank) {
            1 -> 4.0; 2 -> 3.0; 3 -> 2.0; 4 -> 1.0; else -> 0.0
        }
        val points: Double get() = score * rankWeight
        val maxPoints: Double get() = 10.0 * rankWeight
    }

    enum class ParamState { OK, ANALISAR, NOK, AUSENTE }

    data class DecisionResult(
        val decision: Decision,
        val scorePercent: Double,
        val totalPoints: Double,
        val maxPoints: Double,
        val params: List<ParamScore>
    )

    enum class Decision { ACEITAR, ANALISAR, RECUSAR }

    data class DynamicThresholds(val minKm: Double, val idealKm: Double)

    fun getDynamicThresholds(prefs: SharedPreferences, costPerKm: Double): DynamicThresholds {
        val userMinKm = prefs.getFloat(SettingsActivity.KEY_MIN_KM, 0f).toDouble()
        val userIdealKm = prefs.getFloat(SettingsActivity.KEY_IDEAL_KM, 0f).toDouble()
        val minKm = if (userMinKm > 0) userMinKm else costPerKm * 1.15
        val idealKm = if (userIdealKm > 0) userIdealKm else costPerKm * 1.50
        return DynamicThresholds(minKm, idealKm)
    }

    fun evaluate(
        kmValue: Double?,
        hourValue: Double?,
        minValue: Double?,
        ratingValue: Double?,
        prefs: SharedPreferences
    ): DecisionResult {
        val minKm      = prefs.getFloat(SettingsActivity.KEY_MIN_KM, 2.5f).toDouble()
        val idealKm    = prefs.getFloat(SettingsActivity.KEY_IDEAL_KM, 4.0f).toDouble()
        val minHour    = prefs.getFloat(SettingsActivity.KEY_MIN_HOUR, 30f).toDouble()
        val idealHour  = prefs.getFloat(SettingsActivity.KEY_IDEAL_HOUR, 60f).toDouble()
        val minMin     = prefs.getFloat(SettingsActivity.KEY_MIN_MINUTE, 0.5f).toDouble()
        val idealMin   = prefs.getFloat(SettingsActivity.KEY_IDEAL_MINUTE, 1.0f).toDouble()
        val minRating  = prefs.getFloat(SettingsActivity.KEY_MIN_RATING, 4.85f).toDouble()
        val idealRating= prefs.getFloat(SettingsActivity.KEY_IDEAL_RATING, 4.93f).toDouble()

        val rankKm     = prefs.getInt(SettingsActivity.KEY_RANK_KM, 1)
        val rankHour   = prefs.getInt(SettingsActivity.KEY_RANK_HOUR, 2)
        val rankMin    = prefs.getInt(SettingsActivity.KEY_RANK_MIN, 3)
        val rankRating = prefs.getInt(SettingsActivity.KEY_RANK_RATING, 4)

        val thresholdAceitar  = prefs.getInt(SettingsActivity.KEY_THRESHOLD_ACEITAR, 80)
        val thresholdAnalisar = prefs.getInt(SettingsActivity.KEY_THRESHOLD_ANALISAR, 50)

        val params = listOf(
            ParamScore("R$/km",   rankKm,     evaluateState(kmValue,     minKm,   idealKm),   scoreForValue(kmValue,     minKm,   idealKm),   kmValue,     minKm,   idealKm),
            ParamScore("R$/h",    rankHour,   evaluateState(hourValue,   minHour, idealHour),  scoreForValue(hourValue,   minHour, idealHour),  hourValue,   minHour, idealHour),
            ParamScore("R$/min",  rankMin,    evaluateState(minValue,    minMin,  idealMin),   scoreForValue(minValue,    minMin,  idealMin),   minValue,    minMin,  idealMin),
            ParamScore("Nota",    rankRating, evaluateState(ratingValue, minRating, idealRating), scoreForValue(ratingValue, minRating, idealRating), ratingValue, minRating, idealRating)
        )

        val totalPoints  = params.sumOf { it.points }
        val maxPoints    = params.sumOf { it.maxPoints }
        val scorePercent = if (maxPoints > 0) (totalPoints / maxPoints) * 100.0 else 0.0

        val decision = when {
            scorePercent >= thresholdAceitar  -> Decision.ACEITAR
            scorePercent >= thresholdAnalisar -> Decision.ANALISAR
            else                              -> Decision.RECUSAR
        }

        return DecisionResult(decision, scorePercent, totalPoints, maxPoints, params)
    }

    private fun scoreForValue(value: Double?, min: Double, ideal: Double): Double {
        if (value == null) return 0.0
        if (value >= ideal) return 10.0
        if (value <= min) return 0.0
        val range = ideal - min
        if (range <= 0.0) return if (value >= min) 10.0 else 0.0
        return ((value - min) / range) * 10.0
    }

    private fun evaluateState(value: Double?, min: Double, ideal: Double): ParamState {
        if (value == null) return ParamState.AUSENTE
        return when {
            value >= ideal -> ParamState.OK
            value >= min   -> ParamState.ANALISAR
            else           -> ParamState.NOK
        }
    }

    fun stateColor(state: ParamState): Int = when (state) {
        ParamState.OK       -> AppColors.metricGood
        ParamState.ANALISAR -> AppColors.metricMedium
        ParamState.NOK      -> AppColors.metricBad
        ParamState.AUSENTE  -> AppColors.metricAbsent
    }

    fun decisionColor(decision: Decision): Int = when (decision) {
        Decision.ACEITAR  -> AppColors.success
        Decision.ANALISAR -> AppColors.warning
        Decision.RECUSAR  -> AppColors.error
    }

    fun overlayDecisionColor(decision: Decision): Int = when (decision) {
        Decision.ACEITAR  -> AppColors.metricGood
        Decision.ANALISAR -> AppColors.overlayWarning
        Decision.RECUSAR  -> AppColors.metricBad
    }

    fun decisionText(decision: Decision): String = when (decision) {
        Decision.ACEITAR  -> "✅ BOA"
        Decision.ANALISAR -> "⚠️ MÉDIA"
        Decision.RECUSAR  -> "❌ RUIM"
    }
}
