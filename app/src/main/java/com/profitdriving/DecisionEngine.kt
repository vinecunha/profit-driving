package com.profitdriving

import android.content.SharedPreferences

object DecisionEngine {

    const val MULTIPLIER_OK       = 1.0
    const val MULTIPLIER_ANALISAR = 0.5
    const val MULTIPLIER_NOK      = 0.0
    const val MULTIPLIER_AUSENTE  = 0.7

    data class ParamScore(
        val name: String,
        val weight: Int,
        val state: ParamState,
        val value: Double?,
        val min: Double,
        val ideal: Double
    ) {
        val points: Double get() = weight * when (state) {
            ParamState.OK       -> MULTIPLIER_OK
            ParamState.ANALISAR -> MULTIPLIER_ANALISAR
            ParamState.NOK      -> MULTIPLIER_NOK
            ParamState.AUSENTE  -> MULTIPLIER_AUSENTE
        }
        val maxPoints: Double get() = weight * MULTIPLIER_OK
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

        val weightKm     = prefs.getInt(SettingsActivity.KEY_WEIGHT_KM, 5)
        val weightHour   = prefs.getInt(SettingsActivity.KEY_WEIGHT_HOUR, 4)
        val weightMin    = prefs.getInt(SettingsActivity.KEY_WEIGHT_MIN, 3)
        val weightRating = prefs.getInt(SettingsActivity.KEY_WEIGHT_RATING, 2)

        val thresholdAceitar  = prefs.getInt(SettingsActivity.KEY_THRESHOLD_ACEITAR, 80)
        val thresholdAnalisar = prefs.getInt(SettingsActivity.KEY_THRESHOLD_ANALISAR, 50)

        val params = listOf(
            ParamScore("R$/km",   weightKm,     evaluateState(kmValue,     minKm,   idealKm),   kmValue,     minKm,   idealKm),
            ParamScore("R$/h",    weightHour,   evaluateState(hourValue,   minHour, idealHour),  hourValue,   minHour, idealHour),
            ParamScore("R$/min",  weightMin,    evaluateState(minValue,    minMin,  idealMin),   minValue,    minMin,  idealMin),
            ParamScore("Nota",    weightRating, evaluateState(ratingValue, minRating, idealRating), ratingValue, minRating, idealRating)
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
        Decision.ACEITAR  -> AppColors.success  // success
        Decision.ANALISAR -> AppColors.warning  // warning
        Decision.RECUSAR  -> AppColors.error  // error
    }

    fun overlayDecisionColor(decision: Decision): Int = when (decision) {
        Decision.ACEITAR  -> AppColors.metricGood  // overlay_success
        Decision.ANALISAR -> AppColors.overlayWarning  // overlay_warning
        Decision.RECUSAR  -> AppColors.metricBad  // overlay_error
    }

    fun decisionText(decision: Decision): String = when (decision) {
        Decision.ACEITAR  -> "✅ BOA"
        Decision.ANALISAR -> "⚠️ MÉDIA"
        Decision.RECUSAR  -> "❌ RUIM"
    }
}
