package com.checklistboteco.domain.model

data class Activity(
    val id: Long,
    val syncId: String,
    val name: String,
    val area: Area,
    val frequency: Frequency,
    val effort: Int = 1,
    val serverRevision: Long = 0L,
    val syncState: SyncState = SyncState.SYNCED,
    val deletedAt: Long? = null
)
