package com.profitdriving

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView


class FilterManager(private val context: Context) {

    data class FilterOption(
        val id: String,
        val label: String,
        val icon: String? = null
    )

    interface FilterCallback {
        fun onFilterChanged(filterId: String, isSelected: Boolean)
        fun onClearAll()
        fun onSearchChanged(query: String) {}
    }

    fun createFilterSection(
        parent: ViewGroup,
        title: String,
        options: List<FilterOption>,
        selectedIds: Set<String>,
        singleSelection: Boolean = false,
        showSearch: Boolean = false,
        searchHint: String = "Buscar...",
        callback: FilterCallback
    ): View {
        val view = LayoutInflater.from(context).inflate(R.layout.filter_section, parent, false)
        val tvTitle = view.findViewById<TextView>(R.id.tvFilterTitle)
        val container = view.findViewById<WrapContentFlowLayout>(R.id.filterPillContainer)

        tvTitle.text = title

        if (showSearch) {
            val searchContainer = view.findViewById<View>(R.id.searchContainer)
            val etSearch = view.findViewById<EditText>(R.id.etSearch)
            val btnClearSearch = view.findViewById<TextView>(R.id.btnClearSearch)

            searchContainer.visibility = View.VISIBLE
            etSearch.hint = searchHint

            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString() ?: ""
                    btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                    callback.onSearchChanged(query)
                }
            }
            etSearch.addTextChangedListener(textWatcher)

            btnClearSearch.setOnClickListener {
                etSearch.setText("")
            }
        }

        for (option in options) {
            val pill = createPill(option, selectedIds.contains(option.id))
            pill.setOnClickListener {
                if (singleSelection) {
                    for (i in 0 until container.childCount) {
                        val child = container.getChildAt(i) as? TextView ?: continue
                        val childId = child.tag as? String ?: continue
                        if (childId != option.id && child.isSelected) {
                            child.isSelected = false
                            updatePillStyle(child, false)
                            callback.onFilterChanged(childId, false)
                        }
                    }
                    val newState = !pill.isSelected
                    pill.isSelected = newState
                    updatePillStyle(pill, newState)
                    callback.onFilterChanged(option.id, newState)
                } else {
                    val newState = !pill.isSelected
                    pill.isSelected = newState
                    updatePillStyle(pill, newState)
                    callback.onFilterChanged(option.id, newState)
                }
            }
            pill.tag = option.id
            container.addView(pill)
        }

        return view
    }

    fun clearContainer(container: ViewGroup) {
        container.removeAllViews()
    }

    internal fun createPill(option: FilterOption, isSelected: Boolean): TextView {
        val pill = TextView(context).apply {
            text = buildString {
                option.icon?.let { append("$it ") }
                append(option.label)
            }
            textSize = 11f
            setPadding(16, 8, 16, 8)
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 8, 8)
            }
            this.isSelected = isSelected
            updatePillStyle(this, isSelected)
        }
        return pill
    }

    internal fun updatePillStyle(pill: TextView, isSelected: Boolean) {
        if (isSelected) {
            pill.setBackgroundResource(R.drawable.pill_selected)
            pill.setTextColor(AppColors.pillActiveText)
        } else {
            pill.setBackgroundResource(R.drawable.pill_unselected)
            pill.setTextColor(AppColors.pillInactiveText)
        }
    }
}
