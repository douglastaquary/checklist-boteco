package com.checklistboteco.domain.model

data class ActivityCompletion(
    val id: Long,
    val activityId: Long,
    val userId: Long,
    val completedAt: Long,
    val imagePath: String?
)
