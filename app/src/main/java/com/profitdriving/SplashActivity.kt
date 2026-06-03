package com.profitdriving

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    private var initializationFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setTheme(R.style.Theme_CorridaCerta_Splash)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = DatabaseHelper(applicationContext)
            try {
                CostSummaryCache.invalidate()
                db.getRefuels()
                db.getAllExpenses()
                db.getMonthlyKm()
                db.getDistinctServiceTypes()
                CostSummaryCache.getCurrentSummary(applicationContext)
            } catch (e: Exception) {
                L.e("SplashActivity", "Erro ao pr\u00E9-carregar dados", e)
                initializationFailed = true
            }

            withContext(Dispatchers.Main) {
                if (initializationFailed) {
                    Toast.makeText(
                        this@SplashActivity,
                        "Erro ao carregar banco de dados. O aplicativo pode apresentar problemas.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
        }
    }
}
