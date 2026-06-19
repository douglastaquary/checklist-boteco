package com.checklistboteco.domain.model

enum class SyncState {
    SYNCED,
    PENDING,
    DELETED;

    companion object {
        fun fromString(value: String?): SyncState {
            return entries.firstOrNull { it.name == value } ?: SYNCED
        }
    }
}
