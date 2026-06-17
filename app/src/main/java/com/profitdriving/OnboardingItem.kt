package com.profitdriving

data class OnboardingItem(
    val imageRes: Int,
    val title: String,
    val description: String,
    val backgroundColor: Int,
    val showBenefits: Boolean = false,
    val showPrivacyBadge: Boolean = false
)
