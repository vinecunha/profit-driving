package com.profitdriving

object Constants {
    const val PAGE_SIZE = 100
    const val PAGE_SIZE_DEFAULT = 100
    const val MAX_RETRIES = 3
    const val TIMEOUT_MS = 5000L
    const val DEFAULT_MONTHLY_KM = 3000
    const val DATE_PICKER_DELAY = 500L
    const val SPLASH_DELAY_MS = 1500L
    const val DEBOUNCE_DELAY_MS = 300L
    const val HOURS_AGO_DEFAULT = 24
    const val RAW_LOG_LIMIT = 50
    const val FAILED_LOG_LIMIT = 100
    const val REFUEL_DISPLAY_COUNT = 4
    const val EXPENSE_DISPLAY_COUNT = 5
    const val DAY_MS = 86_400_000L
    const val CACHE_EXPIRY_HOURS = 1
    const val SNACKBAR_DURATION_LONG = 3500
}

object AnimationConstants {
    const val ANIMATION_NONE = "none"
    const val ANIMATION_FADE = "fade"
    const val ANIMATION_SLIDE_RIGHT = "slide_right"
    const val ANIMATION_SLIDE_LEFT = "slide_left"
    const val ANIMATION_FADE_SLIDE = "fade_slide"

    fun getAnimationNames(): List<String> = listOf(
        ANIMATION_NONE,
        ANIMATION_FADE,
        ANIMATION_SLIDE_RIGHT,
        ANIMATION_SLIDE_LEFT,
        ANIMATION_FADE_SLIDE
    )

    fun getAnimationLabels(): Map<String, String> = mapOf(
        ANIMATION_NONE to "Nenhuma",
        ANIMATION_FADE to "Fade In",
        ANIMATION_SLIDE_RIGHT to "Deslizar da Direita",
        ANIMATION_SLIDE_LEFT to "Deslizar da Esquerda",
        ANIMATION_FADE_SLIDE to "Fade + Slide (Recomendado)"
    )
}
