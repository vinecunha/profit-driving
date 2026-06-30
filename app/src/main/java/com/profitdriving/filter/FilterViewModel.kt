package com.profitdriving.filter

import android.content.Context
import com.profitdriving.FilterManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FilterState(
    val filterDays: Int = 0,
    val selectedPeriodFilter: String? = null,
    val customStartMs: Long? = null,
    val customEndMs: Long? = null,
    val selectedTypeFilter: String = "all",
    val selectedScoreFilter: String = "all"
)

class FilterViewModel private constructor(context: Context) {

    private val prefs = context.applicationContext

    private val _state = MutableStateFlow(loadFromPrefs())
    val state: StateFlow<FilterState> = _state.asStateFlow()

    private fun loadFromPrefs(): FilterState {
        return FilterState(
            filterDays = FilterManager.loadInt(prefs, "main_filter_days", 0),
            selectedPeriodFilter = FilterManager.loadString(prefs, "main_filter_period", null),
            customStartMs = FilterManager.loadLong(prefs, "main_filter_custom_start", 0L).let { if (it == 0L) null else it },
            customEndMs = FilterManager.loadLong(prefs, "main_filter_custom_end", 0L).let { if (it == 0L) null else it },
            selectedTypeFilter = FilterManager.loadString(prefs, "main_filter_type", "all") ?: "all",
            selectedScoreFilter = FilterManager.loadString(prefs, "main_filter_score", "all") ?: "all"
        )
    }

    fun updateFilterDays(days: Int) {
        _state.value = _state.value.copy(filterDays = days)
        FilterManager.saveInt(prefs, "main_filter_days", days)
    }

    fun updatePeriodFilter(period: String?) {
        _state.value = _state.value.copy(selectedPeriodFilter = period)
        FilterManager.saveString(prefs, "main_filter_period", period ?: "")
    }

    fun updateCustomRange(startMs: Long?, endMs: Long?) {
        _state.value = _state.value.copy(customStartMs = startMs, customEndMs = endMs)
        FilterManager.saveLong(prefs, "main_filter_custom_start", startMs ?: 0L)
        FilterManager.saveLong(prefs, "main_filter_custom_end", endMs ?: 0L)
    }

    fun updateTypeFilter(type: String) {
        _state.value = _state.value.copy(selectedTypeFilter = type)
        FilterManager.saveString(prefs, "main_filter_type", type)
    }

    fun updateScoreFilter(score: String) {
        _state.value = _state.value.copy(selectedScoreFilter = score)
        FilterManager.saveString(prefs, "main_filter_score", score)
    }

    fun clearAll() {
        _state.value = FilterState()
        FilterManager.clearAll(prefs)
    }

    companion object {
        @Volatile
        private var instance: FilterViewModel? = null

        fun getInstance(context: Context): FilterViewModel {
            return instance ?: synchronized(this) {
                instance ?: FilterViewModel(context.applicationContext).also { instance = it }
            }
        }
    }
}
