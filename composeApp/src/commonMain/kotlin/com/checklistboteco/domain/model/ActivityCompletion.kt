package com.checklistboteco.domain.model

data class ActivityCompletion(
    val id: Long,
    val syncId: String,
    val activityId: Long,
    val userId: Long,
    val completedAt: Long,
    val imagePath: String?,
    val isLate: Boolean = false,
    val serverRevision: Long = 0L,
    val syncState: SyncState = SyncState.SYNCED
)
