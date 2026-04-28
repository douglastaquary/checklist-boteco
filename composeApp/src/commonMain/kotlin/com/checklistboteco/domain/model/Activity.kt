package com.checklistboteco.domain.model

data class Activity(
    val id: Long,
    val name: String,
    val area: Area,
    val frequency: Frequency,
    val effort: Int = 1 // 1 to 5
)
