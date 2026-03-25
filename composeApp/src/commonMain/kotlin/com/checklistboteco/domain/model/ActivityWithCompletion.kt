package com.checklistboteco.domain.model

data class ActivityWithCompletion(
    val activity: Activity,
    val isCompleted: Boolean,
    val completion: ActivityCompletion? = null
)
