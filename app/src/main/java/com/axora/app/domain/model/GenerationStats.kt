package com.axora.app.domain.model

data class GenerationStats(
    val imagesToday: Int = 0,
    val imagesWeek: Int = 0,
    val imagesTotal: Int = 0,
    val textsToday: Int = 0,
    val textsWeek: Int = 0,
    val textsTotal: Int = 0
)
