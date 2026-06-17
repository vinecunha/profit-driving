package com.profitdriving.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.profitdriving.BuildConfig
import com.profitdriving.R

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sobre"

        findViewById<TextView>(R.id.textVersion).text = "Versão ${BuildConfig.VERSION_NAME}"
        findViewById<TextView>(R.id.textBuild).text = "Build ${BuildConfig.VERSION_CODE}"

        findViewById<Button>(R.id.buttonPrivacy).setOnClickListener {
            val url = "https://seu-site.com/privacidade"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        findViewById<Button>(R.id.buttonTerms).setOnClickListener {
            val url = "https://seu-site.com/termos"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
