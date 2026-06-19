package com.checklistboteco.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.checklistboteco.data.sync.ActivityPayload
import com.checklistboteco.data.sync.CompletionPayload
import com.checklistboteco.data.sync.PendingSyncOperation
import com.checklistboteco.data.sync.RemoteActivityRecord
import com.checklistboteco.data.sync.RemoteCompletionRecord
import com.checklistboteco.data.sync.RemoteTombstone
import com.checklistboteco.data.sync.SyncAckStatus
import com.checklistboteco.data.sync.SyncAcknowledgement
import com.checklistboteco.data.sync.SyncEntityType
import com.checklistboteco.data.sync.SyncOperationType
import com.checklistboteco.data.sync.SyncPullResponse
import com.checklistboteco.data.sync.SyncSession
import com.checklistboteco.database.ChecklistDatabase
import com.checklistboteco.domain.model.Activity
import com.checklistboteco.domain.model.ActivityCompletion
import com.checklistboteco.domain.model.ActivityWithCompletion
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.FeaturePermissions
import com.checklistboteco.domain.model.Frequency
import com.checklistboteco.domain.model.GeoPoint
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.SyncState
import com.checklistboteco.domain.model.User
import com.checklistboteco.domain.model.ValidatedUserRegistration
import com.checklistboteco.domain.model.WorkClockEntry
import com.checklistboteco.domain.model.WorkClockType
import com.checklistboteco.domain.model.WorkSector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

class ChecklistRepository(
    database: ChecklistDatabase,
    private val onSyncRequested: (() -> Unit)? = null
) {

    private val queries = database.checklistDatabaseQueries
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        ensureSyncIdentifiers()
    }

    fun saveSyncSession(localUserId: Long, session: SyncSession) {
        queries.transaction {
            queries.setUserRemoteId(session.remoteUserId, localUserId)
            queries.upsertSyncMetadata(METADATA_AUTH_TOKEN, session.authToken)
            queries.upsertSyncMetadata(METADATA_REMOTE_USER_ID, session.remoteUserId)
        }
    }

    fun getSyncSession(): SyncSession? {
        val token = queries.selectSyncMetadata(METADATA_AUTH_TOKEN).executeAsOneOrNull()
        val remoteUserId = queries.selectSyncMetadata(METADATA_REMOTE_USER_ID).executeAsOneOrNull()
        if (token.isNullOrBlank() || remoteUserId.isNullOrBlank()) return null
        return SyncSession(authToken = token, remoteUserId = remoteUserId)
    }

    fun clearSyncSession() {
        queries.upsertSyncMetadata(METADATA_AUTH_TOKEN, "")
        queries.upsertSyncMetadata(METADATA_REMOTE_USER_ID, "")
    }

    fun getSyncCursor(): String? {
        return queries.selectSyncMetadata(METADATA_SYNC_CURSOR).executeAsOneOrNull()?.ifBlank { null }
    }

    fun setSyncCursor(cursor: String) {
        queries.upsertSyncMetadata(METADATA_SYNC_CURSOR, cursor)
    }

    fun listPendingSyncOperations(
        limit: Int = DEFAULT_SYNC_BATCH_SIZE,
        now: Long = Clock.System.now().toEpochMilliseconds()
    ): List<PendingSyncOperation> {
        return queries.selectPendingSyncOutbox(now, limit.toLong()).executeAsList().map { row ->
            PendingSyncOperation(
                operationId = row.operationId,
                entityType = SyncEntityType.valueOf(row.entityType),
                entitySyncId = row.entitySyncId,
                operationType = SyncOperationType.valueOf(row.operationType),
                payload = row.payload,
                createdAt = row.createdAt,
                attemptCount = row.attemptCount,
                nextAttemptAt = row.nextAttemptAt,
                lastError = row.lastError,
                status = row.status
            )
        }
    }

    fun markSyncOperationFailed(
        operationId: String,
        attemptCount: Long,
        nextAttemptAt: Long,
        error: String
    ) {
        queries.updateSyncOutboxAttempt(attemptCount, nextAttemptAt, error, OUTBOX_PENDING, operationId)
    }

    fun acknowledgeSyncOperation(ack: SyncAcknowledgement) {
        queries.transaction {
            when (ack.status) {
                SyncAckStatus.APPLIED,
                SyncAckStatus.ALREADY_APPLIED -> {
                    queries.deleteSyncOutboxByOperationId(ack.operationId)
                }
                SyncAckStatus.CONFLICT,
                SyncAckStatus.REJECTED -> {
                    queries.updateSyncOutboxAttempt(
                        0L,
                        Clock.System.now().toEpochMilliseconds() + CONFLICT_RETRY_DELAY_MILLIS,
                        ack.message ?: ack.status.name,
                        OUTBOX_PENDING,
                        ack.operationId
                    )
                }
            }
        }
    }

    fun applyRemoteSync(response: SyncPullResponse) {
        queries.transaction {
            response.activities.forEach(::upsertRemoteActivity)
            response.completions.forEach(::upsertRemoteCompletion)
            response.tombstones.forEach(::applyRemoteTombstone)
            queries.upsertSyncMetadata(METADATA_SYNC_CURSOR, response.nextCursor)
        }
    }

    fun insertUser(
        name: String,
        email: String,
        password: String,
        area: Area,
        workSector: WorkSector,
        permissionLevel: PermissionLevel,
        allowedAreas: List<Area>,
        createdAt: Long = Clock.System.now().toEpochMilliseconds(),
        remoteId: String? = null,
        featurePermissions: FeaturePermissions = FeaturePermissions()
    ) {
        val areasStr = allowedAreas.joinToString(",") { it.name }
        queries.insertUser(
            name,
            email,
            password,
            area.name,
            workSector.name,
            permissionLevel.name,
            areasStr,
            createdAt,
            remoteId,
            featurePermissions.canRegisterUsers.toLongFlag(),
            featurePermissions.canCreateActivities.toLongFlag(),
            featurePermissions.canEditUsers.toLongFlag()
        )
    }

    fun insertRegisteredUser(user: ValidatedUserRegistration) {
        insertUser(
            name = user.fullName,
            email = user.email,
            password = user.password,
            area = user.workSector.activityArea,
            workSector = user.workSector,
            permissionLevel = user.permissionLevel,
            allowedAreas = user.allowedAreas,
            featurePermissions = user.featurePermissions
        )
    }

    fun getUserByName(name: String): User? {
        return queries.selectUserByName(name).executeAsOneOrNull()?.let(::mapToUser)
    }

    fun getUserByEmail(email: String): User? {
        return queries.selectUserByEmail(email).executeAsOneOrNull()?.let(::mapToUser)
    }

    fun getUserByRemoteId(remoteId: String): User? {
        return queries.selectUserByRemoteId(remoteId).executeAsOneOrNull()?.let(::mapToUser)
    }

    fun getAllUsers(): Flow<List<User>> {
        return queries.selectAllUsers().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map(::mapToUser)
        }
    }

    fun updateUserFeaturePermissions(userId: Long, permissions: FeaturePermissions) {
        queries.updateUserFeaturePermissions(
            permissions.canRegisterUsers.toLongFlag(),
            permissions.canCreateActivities.toLongFlag(),
            permissions.canEditUsers.toLongFlag(),
            userId
        )
    }

    fun insertActivity(name: String, area: Area, frequency: Frequency, effort: Int) {
        val now = Clock.System.now().toEpochMilliseconds()
        val syncId = newSyncId("activity")
        val payload = ActivityPayload(
            syncId = syncId,
            name = name,
            area = area.name,
            frequency = frequency.name,
            effort = effort,
            baseRevision = 0L
        )

        queries.transaction {
            queries.insertActivity(
                syncId,
                name,
                area.name,
                frequency.name,
                effort.toLong(),
                0L,
                SyncState.PENDING.name,
                null
            )
            enqueueOperation(
                entityType = SyncEntityType.ACTIVITY,
                entitySyncId = syncId,
                operationType = SyncOperationType.ACTIVITY_UPSERT,
                payload = json.encodeToString(payload),
                createdAt = now
            )
        }
        requestSync()
    }

    fun getAllActivities(): Flow<List<Activity>> {
        return queries.selectAllActivities().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map(::mapToActivity)
        }
    }

    fun getActivitiesByArea(area: Area): Flow<List<Activity>> {
        return queries.selectActivitiesByArea(area.name).asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map(::mapToActivity)
        }
    }

    fun deleteActivity(id: Long) {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.transaction {
            val activity = queries.selectActivityById(id).executeAsOneOrNull() ?: return@transaction
            val syncId = activity.syncId ?: return@transaction
            val pending = queries.selectSyncOutboxByEntity(SyncEntityType.ACTIVITY.name, syncId).executeAsList()
            val canDropRemotelessCreate = activity.serverRevision == 0L &&
                pending.any { it.operationType == SyncOperationType.ACTIVITY_UPSERT.name }

            if (canDropRemotelessCreate) {
                queries.deleteSyncOutboxByEntity(SyncEntityType.ACTIVITY.name, syncId)
                queries.deleteCompletionsByActivity(id)
                queries.hardDeleteActivity(id)
            } else {
                queries.deleteActivity(now, SyncState.PENDING.name, activity.serverRevision, id)
                val payload = ActivityPayload(
                    syncId = syncId,
                    name = activity.name,
                    area = activity.area,
                    frequency = activity.frequency,
                    effort = activity.effort.toInt(),
                    baseRevision = activity.serverRevision,
                    deletedAt = now
                )
                enqueueOperation(
                    entityType = SyncEntityType.ACTIVITY,
                    entitySyncId = syncId,
                    operationType = SyncOperationType.ACTIVITY_DELETE,
                    payload = json.encodeToString(payload),
                    createdAt = now
                )
            }
        }
        requestSync()
    }

    fun insertCompletion(activityId: Long, userId: Long, imagePath: String?, isLate: Boolean) {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val syncId = newSyncId("completion")

        queries.transaction {
            val activity = queries.selectActivityById(activityId).executeAsOneOrNull() ?: return@transaction
            val activitySyncId = activity.syncId ?: return@transaction
            queries.insertCompletion(
                syncId,
                activityId,
                userId,
                timestamp,
                imagePath,
                if (isLate) 1L else 0L,
                0L,
                SyncState.PENDING.name
            )
            val payload = CompletionPayload(
                syncId = syncId,
                activitySyncId = activitySyncId,
                baseRevision = 0L,
                completedAt = timestamp,
                imagePath = imagePath,
                isLate = isLate
            )
            enqueueOperation(
                entityType = SyncEntityType.COMPLETION,
                entitySyncId = syncId,
                operationType = SyncOperationType.COMPLETION_CREATE,
                payload = json.encodeToString(payload),
                createdAt = timestamp
            )
        }
        requestSync()
    }

    fun getActivitiesWithCompletion(area: Area): Flow<List<ActivityWithCompletion>> {
        return queries.selectActivitiesByArea(area.name).asFlow().mapToList(Dispatchers.IO).map { list ->
            val activities = list.map(::mapToActivity)
            activities.map { activity ->
                val periodStart = getPeriodStart(activity.frequency)
                val completions = queries.selectCompletionsByActivityAndDate(activity.id, periodStart).executeAsList()
                val completion = completions.lastOrNull()
                ActivityWithCompletion(
                    activity = activity,
                    isCompleted = completion != null,
                    completion = completion?.let(::mapToCompletion)
                )
            }
        }
    }

    fun insertWorkClockEntry(
        userId: Long,
        type: WorkClockType,
        registeredAt: Long,
        location: GeoPoint,
        distanceFromWorkMeters: Double,
        isLate: Boolean
    ) {
        queries.insertWorkClockEntry(
            userId,
            type.name,
            registeredAt,
            location.latitude,
            location.longitude,
            distanceFromWorkMeters,
            isLate.toLongFlag()
        )
    }

    fun getWorkClockEntriesByUserAndDate(userId: Long, date: LocalDate): Flow<List<WorkClockEntry>> {
        val start = date.startOfDayMillis()
        val end = start + DAY_MILLIS
        return queries.selectWorkClockEntriesByUserAndDate(userId, start, end)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map(::mapToWorkClockEntry) }
    }

    fun getWorkClockEntriesByUserAndCurrentWeek(userId: Long): List<WorkClockEntry> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val start = today.startOfWeekMillis()
        val end = start + 7L * DAY_MILLIS
        return queries.selectWorkClockEntriesByUserAndPeriod(userId, start, end)
            .executeAsList()
            .map(::mapToWorkClockEntry)
    }

    fun getGlobalStats(periodStart: Long): Flow<GlobalDashboardStats> {
        return queries.getGlobalStats(periodStart).asFlow().mapToOne(Dispatchers.IO).map { row ->
            val totalActivities = row.totalActivities
            val totalCompleted = row.totalCompleted
            val lateCompletions = row.lateCompletions

            GlobalDashboardStats(
                completionPercentage = if (totalActivities > 0) {
                    (totalCompleted.toFloat() / totalActivities.toFloat() * 100).toInt()
                } else {
                    0
                },
                alertsCount = (totalActivities - totalCompleted).toInt(),
                scheduledCount = totalActivities.toInt(),
                onTimeCount = (totalCompleted - lateCompletions).toInt(),
                lateCount = lateCompletions.toInt()
            )
        }
    }

    fun getRanking(periodStart: Long): Flow<List<UserRanking>> {
        return queries.getRankingData(periodStart).asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { row ->
                val totalCompletions = row.totalCompletions
                val onTimeCompletions = row.onTimeCompletions ?: 0L
                val totalEffort = row.totalEffort ?: 0L

                val punctualPercentage = if (totalCompletions > 0) {
                    (onTimeCompletions.toFloat() / totalCompletions.toFloat() * 100)
                } else {
                    0f
                }
                val effortPercentage = if (totalCompletions > 0) {
                    (totalEffort.toFloat() / (totalCompletions * 5).toFloat() * 100)
                } else {
                    0f
                }

                UserRanking(
                    userName = row.name,
                    score = (punctualPercentage * 0.6f + effortPercentage * 0.4f),
                    punctualPercentage = punctualPercentage,
                    effortPercentage = effortPercentage,
                    qualityPercentage = 100f
                )
            }
        }
    }

    fun getActivityCountByArea(): Flow<Map<Area, Int>> {
        return queries.countActivitiesByArea().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.associate {
                Area.fromString(it.area) to it.total.toInt()
            }
        }
    }

    fun getCompletionStatsByArea(periodStart: Long): Flow<Map<Area, Pair<Int, Int>>> {
        return queries.selectAllActivities().asFlow().mapToList(Dispatchers.IO).map { activities ->
            val byArea = activities.groupBy { Area.fromString(it.area) }
            byArea.mapValues { (_, areaActivities) ->
                val total = areaActivities.size
                val completed = areaActivities.count { act ->
                    queries.selectCompletionsByActivityAndDate(act.id, periodStart).executeAsList().isNotEmpty()
                }
                total to completed
            }
        }
    }

    fun seedInitialData() {
        if (queries.selectUserByName("admin").executeAsOneOrNull() == null) {
            queries.insertUser(
                "admin",
                "admin@checklistboteco.com",
                "admin123",
                Area.ATENDIMENTO.name,
                WorkSector.GERENTE.name,
                PermissionLevel.ADMIN.name,
                Area.entries.joinToString(",") { it.name },
                Clock.System.now().toEpochMilliseconds(),
                null,
                1L,
                1L,
                1L
            )
        }
        if (queries.selectAllActivities().executeAsList().isEmpty()) {
            listOf(
                Triple("Limpar mesas", Area.ATENDIMENTO, Frequency.DIARIO),
                Triple("Verificar estoque de bebidas", Area.ESTOQUE, Frequency.DIARIO),
                Triple("Limpar chão da cozinha", Area.LIMPEZA, Frequency.DIARIO),
                Triple("Verificar validade dos produtos", Area.ESTOQUE, Frequency.QUINZENAL)
            ).forEach { (name, area, freq) ->
                queries.insertActivity(
                    newSyncId("seed"),
                    name,
                    area.name,
                    freq.name,
                    2L,
                    0L,
                    SyncState.SYNCED.name,
                    null
                )
            }
        }
    }

    private fun ensureSyncIdentifiers() {
        queries.transaction {
            queries.selectActivitiesMissingSyncId().executeAsList().forEach { row ->
                queries.setActivitySyncId(newSyncId("activity"), row.id)
            }
            queries.selectCompletionsMissingSyncId().executeAsList().forEach { row ->
                queries.setCompletionSyncId(newSyncId("completion"), row.id)
            }
        }
    }

    private fun enqueueOperation(
        entityType: SyncEntityType,
        entitySyncId: String,
        operationType: SyncOperationType,
        payload: String,
        createdAt: Long
    ) {
        if (operationType == SyncOperationType.ACTIVITY_UPSERT) {
            queries.selectSyncOutboxByEntity(entityType.name, entitySyncId).executeAsList()
                .filter { it.operationType == SyncOperationType.ACTIVITY_UPSERT.name }
                .forEach { queries.deleteSyncOutboxByOperationId(it.operationId) }
        }
        if (operationType == SyncOperationType.ACTIVITY_DELETE) {
            queries.selectSyncOutboxByEntity(entityType.name, entitySyncId).executeAsList()
                .filter { it.operationType == SyncOperationType.ACTIVITY_UPSERT.name }
                .forEach { queries.deleteSyncOutboxByOperationId(it.operationId) }
        }
        queries.insertSyncOutbox(
            newSyncId("op"),
            entityType.name,
            entitySyncId,
            operationType.name,
            payload,
            createdAt,
            0L,
            createdAt,
            null,
            OUTBOX_PENDING
        )
    }

    private fun upsertRemoteActivity(remote: RemoteActivityRecord) {
        val existing = queries.selectActivityBySyncId(remote.syncId).executeAsOneOrNull()
        if (existing == null) {
            queries.insertActivity(
                remote.syncId,
                remote.name,
                remote.area,
                remote.frequency,
                remote.effort.toLong(),
                remote.serverRevision,
                SyncState.SYNCED.name,
                null
            )
            return
        }
        queries.updateActivity(
            remote.name,
            remote.area,
            remote.frequency,
            remote.effort.toLong(),
            remote.serverRevision,
            SyncState.SYNCED.name,
            null,
            existing.id
        )
    }

    private fun upsertRemoteCompletion(remote: RemoteCompletionRecord) {
        val existing = queries.selectCompletionBySyncId(remote.syncId).executeAsOneOrNull()
        val user = getUserByRemoteId(remote.userId) ?: getUserByRemoteId(
            queries.selectSyncMetadata(METADATA_REMOTE_USER_ID).executeAsOneOrNull().orEmpty()
        )
        val activity = queries.selectActivityBySyncId(remote.activitySyncId).executeAsOneOrNull() ?: return
        val localUserId = user?.id ?: return
        if (existing == null) {
            queries.insertCompletion(
                remote.syncId,
                activity.id,
                localUserId,
                remote.completedAt,
                remote.imagePath,
                remote.isLate.toLongFlag(),
                remote.serverRevision,
                SyncState.SYNCED.name
            )
            return
        }
        queries.markCompletionSync(remote.serverRevision, SyncState.SYNCED.name, remote.syncId)
    }

    private fun applyRemoteTombstone(tombstone: RemoteTombstone) {
        when (tombstone.entityType) {
            SyncEntityType.ACTIVITY -> {
                val existing = queries.selectActivityBySyncId(tombstone.entityId).executeAsOneOrNull() ?: return
                queries.markActivitySync(
                    tombstone.revision,
                    SyncState.DELETED.name,
                    tombstone.deletedAt,
                    tombstone.entityId
                )
                queries.deleteCompletionsByActivity(existing.id)
            }
            SyncEntityType.COMPLETION -> Unit
        }
    }

    private fun getPeriodStart(frequency: Frequency): Long {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return when (frequency) {
            Frequency.DIARIO -> {
                val startOfToday = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                Instant.parse("${startOfToday.date}T00:00:00Z").toEpochMilliseconds()
            }
            Frequency.QUINZENAL -> {
                val dayOfMonth = now.date.dayOfMonth
                val startDay = if (dayOfMonth <= 15) 1 else 16
                val month = now.date.monthNumber.toString().padStart(2, '0')
                Instant.parse("${now.date.year}-$month-${startDay.toString().padStart(2, '0')}T00:00:00Z").toEpochMilliseconds()
            }
            Frequency.MENSAL -> {
                val month = now.date.monthNumber.toString().padStart(2, '0')
                Instant.parse("${now.date.year}-$month-01T00:00:00Z").toEpochMilliseconds()
            }
        }
    }

    private fun mapToUser(user: com.checklistboteco.database.User): User {
        val permissionLevel = PermissionLevel.fromString(user.permissionLevel)
        val allowedAreas = user.allowedAreas.split(",").mapNotNull { s ->
            Area.entries.find { a -> a.name == s.trim() }
        }.ifEmpty {
            listOf(WorkSector.fromString(user.workSector).activityArea)
        }

        return User(
            id = user.id,
            name = user.name,
            email = user.email,
            password = user.password,
            area = Area.fromString(user.area),
            workSector = WorkSector.fromString(user.workSector),
            permissionLevel = permissionLevel,
            allowedAreas = if (permissionLevel == PermissionLevel.ADMIN) Area.entries.toList() else allowedAreas,
            createdAt = user.createdAt,
            remoteId = user.remoteId,
            featurePermissions = FeaturePermissions(
                canRegisterUsers = user.canRegisterUsers == 1L,
                canCreateActivities = user.canCreateActivities == 1L,
                canEditUsers = user.canEditUsers == 1L
            )
        )
    }

    private fun mapToActivity(row: com.checklistboteco.database.Activity): Activity {
        return Activity(
            id = row.id,
            syncId = row.syncId.orEmpty(),
            name = row.name,
            area = Area.fromString(row.area),
            frequency = Frequency.fromString(row.frequency),
            effort = row.effort.toInt(),
            serverRevision = row.serverRevision,
            syncState = SyncState.fromString(row.syncState),
            deletedAt = row.deletedAt
        )
    }

    private fun mapToCompletion(row: com.checklistboteco.database.ActivityCompletion): ActivityCompletion {
        return ActivityCompletion(
            id = row.id,
            syncId = row.syncId.orEmpty(),
            activityId = row.activityId,
            userId = row.userId,
            completedAt = row.completedAt,
            imagePath = row.imagePath,
            isLate = row.isLate == 1L,
            serverRevision = row.serverRevision,
            syncState = SyncState.fromString(row.syncState)
        )
    }

    private fun mapToWorkClockEntry(row: com.checklistboteco.database.WorkClockEntry): WorkClockEntry {
        return WorkClockEntry(
            id = row.id,
            userId = row.userId,
            type = WorkClockType.fromString(row.type),
            registeredAt = row.registeredAt,
            location = GeoPoint(row.latitude, row.longitude),
            distanceFromWorkMeters = row.distanceFromWorkMeters,
            isLate = row.isLate == 1L
        )
    }

    private fun requestSync() {
        onSyncRequested?.invoke()
    }
}

private fun Boolean.toLongFlag(): Long = if (this) 1L else 0L
private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
private const val DEFAULT_SYNC_BATCH_SIZE = 100
private const val METADATA_AUTH_TOKEN = "sync.authToken"
private const val METADATA_REMOTE_USER_ID = "sync.remoteUserId"
private const val METADATA_SYNC_CURSOR = "sync.cursor"
private const val OUTBOX_PENDING = "PENDING"
private const val CONFLICT_RETRY_DELAY_MILLIS = 60_000L

private fun LocalDate.startOfDayMillis(): Long {
    return Instant.parse("${this}T00:00:00Z").toEpochMilliseconds()
}

private fun LocalDate.startOfWeekMillis(): Long {
    val mondayOffset = dayOfWeek.ordinal
    return startOfDayMillis() - mondayOffset * DAY_MILLIS
}

private fun newSyncId(prefix: String): String {
    val alphabet = "0123456789abcdef"
    val randomPart = buildString(16) {
        repeat(16) {
            append(alphabet[Random.nextInt(alphabet.length)])
        }
    }
    return "$prefix-${Clock.System.now().toEpochMilliseconds()}-$randomPart"
}

data class GlobalDashboardStats(
    val completionPercentage: Int,
    val alertsCount: Int,
    val scheduledCount: Int,
    val onTimeCount: Int,
    val lateCount: Int
)

data class UserRanking(
    val userName: String,
    val score: Float,
    val punctualPercentage: Float,
    val effortPercentage: Float,
    val qualityPercentage: Float
)
