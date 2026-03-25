package com.checklistboteco.domain.model

data class Activity(
    val id: Long,
    val name: String,
    val area: Area,
    val frequency: Frequency
)
