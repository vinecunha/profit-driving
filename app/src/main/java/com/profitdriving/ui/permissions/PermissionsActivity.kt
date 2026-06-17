package com.profitdriving.ui.permissions

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.profitdriving.L
import com.profitdriving.PreferenceManager
import com.profitdriving.R
import com.profitdriving.accessibility.RideAccessibilityServiceV2
import com.profitdriving.ui.subscription.SubscriptionActivity

class PermissionsActivity : AppCompatActivity() {

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var textStatus: TextView
    private lateinit var buttonActivate: Button
    private lateinit var buttonNext: Button
    private lateinit var check1: TextView
    private lateinit var check2: TextView
    private lateinit var check3: TextView
    private lateinit var statusCard: CardView

    companion object {
        private const val TAG = "PermissionsActivity"
        private const val REQUEST_CODE_ACCESSIBILITY = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        L.d(TAG, "PermissionsActivity criada")

        preferenceManager = PreferenceManager(this)
        initViews()
        setupToolbar()
        setupListeners()
        checkAccessibilityStatus()
    }

    override fun onResume() {
        super.onResume()
        L.d(TAG, "onResume — verificando status da acessibilidade")
        checkAccessibilityStatus()
    }

    private fun initViews() {
        textStatus = findViewById(R.id.textStatus)
        buttonActivate = findViewById(R.id.buttonActivate)
        buttonNext = findViewById(R.id.buttonNext)
        check1 = findViewById(R.id.check1)
        check2 = findViewById(R.id.check2)
        check3 = findViewById(R.id.check3)
        statusCard = findViewById(R.id.statusCard)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupListeners() {
        buttonActivate.setOnClickListener {
            L.d(TAG, "Botão ATIVAR ACESSIBILIDade clicado")
            openAccessibilitySettings()
        }

        buttonNext.setOnClickListener {
            L.d(TAG, "Botão PRÓXIMO clicado — navegando para SubscriptionActivity")
            preferenceManager.setHasSeenPermissionsScreen(true)
            startActivity(Intent(this, SubscriptionActivity::class.java))
            finish()
        }
    }

    private fun checkAccessibilityStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        L.d(TAG, "Status do serviço de acessibilidade: ${if (isEnabled) "ATIVADO" else "DESATIVADO"}")
        updateUI(isEnabled)

        if (isEnabled) {
            check1.text = "✅"
            check1.setTextColor(getColor(R.color.success))
            check2.text = "✅"
            check2.setTextColor(getColor(R.color.success))
            check3.text = "✅"
            check3.setTextColor(getColor(R.color.success))

            buttonNext.isEnabled = true
            buttonNext.alpha = 1.0f
        } else {
            check1.text = "⬜"
            check1.setTextColor(getColor(R.color.text_secondary))
            check2.text = "⬜"
            check2.setTextColor(getColor(R.color.text_secondary))
            check3.text = "⬜"
            check3.setTextColor(getColor(R.color.text_secondary))

            buttonNext.isEnabled = false
            buttonNext.alpha = 0.5f
        }
    }

    private fun updateUI(isEnabled: Boolean) {
        if (isEnabled) {
            textStatus.text = "✅ Ativado"
            textStatus.setTextColor(getColor(R.color.success))
            buttonActivate.text = "✅ ACESSIBILIDADE ATIVADA"
            buttonActivate.isEnabled = false
            buttonActivate.alpha = 0.7f
        } else {
            textStatus.text = "❌ Desativado"
            textStatus.setTextColor(getColor(R.color.error))
            buttonActivate.text = "ATIVAR ACESSIBILIDADE"
            buttonActivate.isEnabled = true
            buttonActivate.alpha = 1.0f
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = ComponentName(this, RideAccessibilityServiceV2::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service.flattenToString()) == true
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivityForResult(intent, REQUEST_CODE_ACCESSIBILITY)
        } catch (e: Exception) {
            L.e(TAG, "Erro ao abrir configurações de acessibilidade", e)
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivityForResult(intent, REQUEST_CODE_ACCESSIBILITY)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ACCESSIBILITY) {
            L.d(TAG, "Voltou das configurações — verificando status")
            checkAccessibilityStatus()
        }
    }
}
