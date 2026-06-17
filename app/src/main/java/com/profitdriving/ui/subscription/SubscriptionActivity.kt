package com.profitdriving.ui.subscription

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.profitdriving.BuildConfig
import com.profitdriving.MainActivity
import com.profitdriving.R
import com.profitdriving.manager.SubscriptionManager

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var textTitle: TextView
    private lateinit var textSubtitle: TextView
    private lateinit var statusContainer: LinearLayout
    private lateinit var textStatus: TextView
    private lateinit var buttonManage: Button
    private lateinit var cardMonthly: CardView
    private lateinit var cardYearly: CardView
    private lateinit var buttonSubscribe: Button
    private lateinit var buttonRestore: TextView
    private lateinit var debugContainer: LinearLayout
    private lateinit var textDebugStatus: TextView
    private lateinit var buttonFreeTrial: Button
    private lateinit var buttonSkipSubscription: Button
    private lateinit var buttonResetSubscription: Button
    private lateinit var buttonCheckStatus: Button

    private var selectedPlan: String = "monthly"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        subscriptionManager = SubscriptionManager(this)

        initViews()
        setupToolbar()

        if (subscriptionManager.isSubscriptionActive()) {
            showActiveSubscription()
            return
        }

        setupListeners()
        setupDebugMode()
    }

    private fun initViews() {
        textTitle = findViewById(R.id.textTitle)
        textSubtitle = findViewById(R.id.textSubtitle)
        statusContainer = findViewById(R.id.statusContainer)
        textStatus = findViewById(R.id.textStatus)
        buttonManage = findViewById(R.id.buttonManage)
        cardMonthly = findViewById(R.id.cardMonthly)
        cardYearly = findViewById(R.id.cardYearly)
        buttonSubscribe = findViewById(R.id.buttonSubscribe)
        buttonRestore = findViewById(R.id.buttonRestore)
        debugContainer = findViewById(R.id.debugContainer)
        textDebugStatus = findViewById(R.id.textDebugStatus)
        buttonFreeTrial = findViewById(R.id.buttonFreeTrial)
        buttonSkipSubscription = findViewById(R.id.buttonSkipSubscription)
        buttonResetSubscription = findViewById(R.id.buttonResetSubscription)
        buttonCheckStatus = findViewById(R.id.buttonCheckStatus)
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
        cardMonthly.setOnClickListener { selectPlan("monthly") }
        cardYearly.setOnClickListener { selectPlan("yearly") }

        buttonSubscribe.setOnClickListener { handleSubscription() }

        buttonRestore.setOnClickListener {
            Toast.makeText(this, "Verificando compras existentes...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDebugMode() {
        if (!BuildConfig.DEBUG) {
            debugContainer.visibility = android.view.View.GONE
            return
        }

        debugContainer.visibility = android.view.View.VISIBLE
        selectPlan("monthly")
        updateDebugStatus()

        buttonFreeTrial.setOnClickListener {
            subscriptionManager.startFreeTrial(30)
            Toast.makeText(this, "Teste gratis de 30 dias iniciado!", Toast.LENGTH_LONG).show()
            updateDebugStatus()
            goToMain()
        }

        buttonSkipSubscription.setOnClickListener {
            subscriptionManager.activateSubscription(selectedPlan)
            Toast.makeText(this, "Modo DEBUG: Assinatura pulada!", Toast.LENGTH_LONG).show()
            updateDebugStatus()
            goToMain()
        }

        buttonResetSubscription.setOnClickListener {
            subscriptionManager.resetSubscription()
            Toast.makeText(this, "Assinatura resetada!", Toast.LENGTH_SHORT).show()
            updateDebugStatus()
        }

        buttonCheckStatus.setOnClickListener {
            Toast.makeText(this, "Status: ${subscriptionManager.getStatusText()}", Toast.LENGTH_SHORT).show()
            updateDebugStatus()
        }
    }

    private fun showActiveSubscription() {
        textTitle.text = "✅ Assinatura Ativa"
        textSubtitle.text = "Você tem acesso a todas as funcionalidades"
        textStatus.text = subscriptionManager.getStatusText()

        cardMonthly.visibility = android.view.View.GONE
        cardYearly.visibility = android.view.View.GONE
        buttonSubscribe.visibility = android.view.View.GONE

        statusContainer.visibility = android.view.View.VISIBLE

        buttonManage.setOnClickListener {
            Toast.makeText(this, "Gerenciar assinatura", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectPlan(plan: String) {
        selectedPlan = plan
        cardMonthly.isSelected = plan == "monthly"
        cardYearly.isSelected = plan == "yearly"
    }

    private fun handleSubscription() {
        if (BuildConfig.DEBUG) {
            subscriptionManager.activateSubscription(selectedPlan)
            Toast.makeText(
                this,
                "Modo DEBUG: Assinatura ativada! (plano: $selectedPlan)",
                Toast.LENGTH_LONG
            ).show()
            goToMain()
            return
        }

        subscriptionManager.startFreeTrial(30)
        Toast.makeText(
            this,
            "Teste gratis de 30 dias ativado!",
            Toast.LENGTH_LONG
        ).show()
        goToMain()
    }

    private fun updateDebugStatus() {
        textDebugStatus.text = "Status: ${subscriptionManager.getStatusText()}"
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
