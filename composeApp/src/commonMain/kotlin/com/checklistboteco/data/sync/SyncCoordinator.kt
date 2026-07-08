package com.checklistboteco.data.sync

import com.checklistboteco.data.remote.SyncApiClient
import com.checklistboteco.data.repository.ChecklistRepository
import com.checklistboteco.platform.ApiException
import com.checklistboteco.platform.DeviceIdentity
import com.checklistboteco.platform.SessionExpiredNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.math.min

class SyncCoordinator(
    private val repository: ChecklistRepository,
    private val syncApiClient: SyncApiClient?,
    private val scheduler: SyncScheduler = NoOpSyncScheduler,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    fun start() {
        scheduler.schedulePeriodic()
    }

    fun requestSync() {
        scheduler.scheduleImmediate()
        scope.launch {
            syncOnce()
        }
    }

    suspend fun syncOnce() {
        val client = syncApiClient ?: return
        val session = repository.getSyncSession() ?: return
        mutex.withLock {
            repository.repairPendingSyncQueue()
            pushPendingOperations(client, session)
            pullRemoteChanges(client, session)
        }
    }

    private suspend fun pushPendingOperations(
        client: SyncApiClient,
        session: SyncSession
    ) {
        while (true) {
            val pending = repository.listPendingSyncOperations(limit = PUSH_BATCH_SIZE)
            if (pending.isEmpty()) return

            val request = SyncPushRequest(
                deviceId = DeviceIdentity.getOrCreateDeviceId(),
                operations = pending.map(::toEnvelope)
            )

            runCatching {
                client.push(session, newBatchId(), request)
            }.onSuccess { response ->
                response.acknowledgements.forEach { ack ->
                    ack.conflict?.let { conflict ->
                        repository.applyRemoteSync(
                            SyncPullResponse(
                                nextCursor = repository.getSyncCursor().orEmpty(),
                                hasMore = false,
                                activities = listOf(conflict)
                            )
                        )
                    }
                    repository.acknowledgeSyncOperation(ack)
                }
            }.onFailure { error ->
                if (isUnauthorized(error)) {
                    repository.clearSyncSession()
                    return
                }
                val now = Clock.System.now().toEpochMilliseconds()
                pending.forEach { operation ->
                    val nextAttemptCount = operation.attemptCount + 1
                    repository.markSyncOperationFailed(
                        operationId = operation.operationId,
                        attemptCount = nextAttemptCount,
                        nextAttemptAt = now + backoffMillis(nextAttemptCount),
                        error = error.message ?: "Falha ao enviar sincronização"
                    )
                }
                return
            }
        }
    }

    private suspend fun pullRemoteChanges(
        client: SyncApiClient,
        session: SyncSession
    ) {
        while (true) {
            val response = runCatching {
                client.pull(
                    session = session,
                    cursor = repository.getSyncCursor(),
                    limit = PULL_PAGE_SIZE
                )
            }.getOrElse { error ->
                if (isUnauthorized(error)) {
                    repository.clearSyncSession()
                }
                return
            }
            repository.applyRemoteSync(response)
            if (!response.hasMore) return
        }
    }

    private fun toEnvelope(operation: PendingSyncOperation): SyncOperationEnvelope {
        return when (operation.operationType) {
            SyncOperationType.ACTIVITY_UPSERT -> {
                val payload = json.decodeFromString<ActivityPayload>(operation.payload)
                SyncOperationEnvelope(
                    operationId = operation.operationId,
                    type = operation.operationType,
                    entityId = operation.entitySyncId,
                    baseRevision = payload.baseRevision,
                    occurredAt = operation.createdAt,
                    payload = json.parseToJsonElement(json.encodeToString(
                        ActivityUpsertPayload(
                            name = payload.name,
                            area = payload.area,
                            frequency = payload.frequency,
                            effort = payload.effort,
                            assigneeIds = payload.assigneeIds,
                            estimatedDurationMinutes = payload.estimatedDurationMinutes,
                            executionPhase = payload.executionPhase,
                            activeWeekdays = payload.activeWeekdays,
                            recurrenceAnchorDate = payload.recurrenceAnchorDate
                        )
                    )) as JsonObject
                )
            }
            SyncOperationType.ACTIVITY_DELETE -> {
                val payload = json.decodeFromString<ActivityPayload>(operation.payload)
                SyncOperationEnvelope(
                    operationId = operation.operationId,
                    type = operation.operationType,
                    entityId = operation.entitySyncId,
                    baseRevision = payload.baseRevision,
                    occurredAt = payload.deletedAt ?: operation.createdAt,
                    payload = json.parseToJsonElement(json.encodeToString(
                        ActivityDeletePayload(
                            deletedAt = payload.deletedAt ?: operation.createdAt
                        )
                    )) as JsonObject
                )
            }
            SyncOperationType.COMPLETION_CREATE -> {
                val payload = json.decodeFromString<CompletionPayload>(operation.payload)
                SyncOperationEnvelope(
                    operationId = operation.operationId,
                    type = operation.operationType,
                    entityId = operation.entitySyncId,
                    baseRevision = payload.baseRevision,
                    occurredAt = payload.completedAt,
                    payload = json.parseToJsonElement(json.encodeToString(
                        CompletionCreatePayload(
                            activitySyncId = payload.activitySyncId,
                            completedAt = payload.completedAt,
                            imagePath = payload.imagePath,
                            isLate = payload.isLate,
                            serviceDate = payload.serviceDate
                        )
                    )) as JsonObject
                )
            }
        }
    }
}

private const val PUSH_BATCH_SIZE = 100
private const val PULL_PAGE_SIZE = 500
private const val BASE_BACKOFF_MILLIS = 30_000L
private const val MAX_BACKOFF_MILLIS = 15 * 60 * 1000L

private fun backoffMillis(attemptCount: Long): Long {
    val multiplier = 1L shl min(attemptCount.toInt().coerceAtMost(10), 10)
    return min(BASE_BACKOFF_MILLIS * multiplier, MAX_BACKOFF_MILLIS)
}

private fun newBatchId(): String {
    return "batch-${Clock.System.now().toEpochMilliseconds()}"
}

private fun isUnauthorized(error: Throwable): Boolean {
    return when (error) {
        is ApiException -> error.httpStatus == 401
        else -> false
    }
}
