package com.checklistboteco.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import com.checklistboteco.domain.model.ChecklistSchedule

@Serializable
data class SyncSession(
    val authToken: String,
    val remoteUserId: String
)

@Serializable
enum class SyncEntityType {
    ACTIVITY,
    COMPLETION
}

@Serializable
enum class SyncOperationType {
    ACTIVITY_UPSERT,
    ACTIVITY_DELETE,
    COMPLETION_CREATE
}

@Serializable
data class ActivityPayload(
    val syncId: String,
    val name: String,
    val area: String,
    val frequency: String,
    val effort: Int,
    val assigneeIds: List<String> = emptyList(),
    val estimatedDurationMinutes: Int = 15,
    val executionPhase: String = "BEFORE_LUNCH",
    val activeWeekdays: List<String> = listOf("TUESDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
    val recurrenceAnchorDate: String? = null,
    val baseRevision: Long = 0,
    val deletedAt: Long? = null
)

@Serializable
data class CompletionPayload(
    val syncId: String,
    val activitySyncId: String,
    val baseRevision: Long = 0,
    val completedAt: Long,
    val imagePath: String? = null,
    val isLate: Boolean = false,
    val serviceDate: String = ""
)

@Serializable
data class PendingSyncOperation(
    val operationId: String,
    val entityType: SyncEntityType,
    val entitySyncId: String,
    val operationType: SyncOperationType,
    val payload: String,
    val createdAt: Long,
    val attemptCount: Long,
    val nextAttemptAt: Long,
    val lastError: String? = null,
    val status: String = "PENDING"
)

@Serializable
data class SyncPushRequest(
    val deviceId: String,
    val operations: List<SyncOperationEnvelope>
)

@Serializable
data class SyncOperationEnvelope(
    val operationId: String,
    val type: SyncOperationType,
    val entityId: String,
    val baseRevision: Long,
    val occurredAt: Long,
    val payload: JsonObject
)

@Serializable
data class ActivityUpsertPayload(
    val name: String,
    val area: String,
    val frequency: String,
    val effort: Int,
    val assigneeIds: List<String> = emptyList(),
    val estimatedDurationMinutes: Int = 15,
    val executionPhase: String = "BEFORE_LUNCH",
    val activeWeekdays: List<String> = listOf("TUESDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
    val recurrenceAnchorDate: String? = null
)

@Serializable
data class ActivityDeletePayload(
    val deletedAt: Long
)

@Serializable
data class CompletionCreatePayload(
    val activitySyncId: String,
    val completedAt: Long,
    val imagePath: String? = null,
    val isLate: Boolean = false,
    val serviceDate: String = ""
)

@Serializable
data class SyncPushResponse(
    val serverTime: Long,
    val cursor: String,
    val acknowledgements: List<SyncAcknowledgement>
)

@Serializable
data class SyncAcknowledgement(
    val operationId: String,
    val status: SyncAckStatus,
    val serverRevision: Long = 0,
    val conflict: RemoteActivityRecord? = null,
    val message: String? = null
)

@Serializable
enum class SyncAckStatus {
    APPLIED,
    ALREADY_APPLIED,
    CONFLICT,
    REJECTED
}

@Serializable
data class SyncPullResponse(
    val nextCursor: String,
    val hasMore: Boolean,
    val activities: List<RemoteActivityRecord> = emptyList(),
    val completions: List<RemoteCompletionRecord> = emptyList(),
    val tombstones: List<RemoteTombstone> = emptyList(),
    val checklistSchedule: ChecklistSchedule? = null
)

@Serializable
data class RemoteActivityRecord(
    @JsonNames("id")
    val syncId: String,
    val name: String,
    val area: String,
    val frequency: String,
    val effort: Int,
    val assigneeIds: List<String> = emptyList(),
    val estimatedDurationMinutes: Int = 15,
    val executionPhase: String = "BEFORE_LUNCH",
    val activeWeekdays: List<String> = listOf("TUESDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
    val recurrenceAnchorDate: String? = null,
    val serverRevision: Long,
    val updatedAt: Long
)

@Serializable
data class RemoteCompletionRecord(
    @JsonNames("id")
    val syncId: String,
    @JsonNames("activityId", "activitySyncId")
    val activitySyncId: String,
    val userId: String,
    val completedAt: Long,
    val imagePath: String? = null,
    val isLate: Boolean = false,
    val serviceDate: String = "",
    val serverRevision: Long,
    val updatedAt: Long
)

@Serializable
data class RemoteTombstone(
    val entityType: SyncEntityType,
    val entityId: String,
    val revision: Long,
    val deletedAt: Long
)
