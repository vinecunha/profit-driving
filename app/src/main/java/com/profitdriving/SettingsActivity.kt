package com.profitdriving

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var etMinKm: EditText
    private lateinit var etMinHour: EditText
    private lateinit var etMinRating: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        etMinKm = findViewById(R.id.etMinKm)
        etMinHour = findViewById(R.id.etMinHour)
        etMinRating = findViewById(R.id.etMinRating)
        btnSave = findViewById(R.id.btnSave)

        loadValues()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        btnSave.setOnClickListener {
            saveValues()
        }
    }

    private fun loadValues() {
        etMinKm.setText(prefs.getFloat(KEY_MIN_KM, 0f).let {
            if (it == 0f) "" else "%.2f".format(it).replace(".", ",")
        })
        etMinHour.setText(prefs.getFloat(KEY_MIN_HOUR, 0f).let {
            if (it == 0f) "" else "%.2f".format(it).replace(".", ",")
        })
        etMinRating.setText(prefs.getFloat(KEY_MIN_RATING, 0f).let {
            if (it == 0f) "" else "%.2f".format(it).replace(".", ",")
        })
    }

    private fun saveValues() {
        val minKm = parseBr(etMinKm.text.toString())
        val minHour = parseBr(etMinHour.text.toString())
        val minRating = parseBr(etMinRating.text.toString())

        prefs.edit().apply {
            putFloat(KEY_MIN_KM, minKm ?: 0f)
            putFloat(KEY_MIN_HOUR, minHour ?: 0f)
            putFloat(KEY_MIN_RATING, minRating ?: 0f)
            apply()
        }

        Toast.makeText(this, "Parâmetros salvos!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun parseBr(value: String): Float? {
        val cleaned = value.trim().replace(",", ".")
        return cleaned.toFloatOrNull()
    }

    companion object {
        const val PREF_NAME = "profit_driving_prefs"
        const val KEY_MIN_KM = "min_km"
        const val KEY_MIN_HOUR = "min_hour"
        const val KEY_MIN_RATING = "min_rating"
        const val KEY_LAST_VALUE = "last_value"
        const val KEY_LAST_DISTANCE = "last_distance"
        const val KEY_LAST_TIME = "last_time"
        const val KEY_LAST_RATING = "last_rating"
        const val KEY_LAST_APP = "last_app"
        const val KEY_LAST_TIMESTAMP = "last_timestamp"
    }
}
