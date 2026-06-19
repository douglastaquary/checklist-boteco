package com.checklistboteco.database

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Double
import kotlin.Long
import kotlin.String

public class ChecklistDatabaseQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAllUsers(mapper: (
    id: Long,
    name: String,
    email: String,
    password: String,
    area: String,
    workSector: String,
    permissionLevel: String,
    allowedAreas: String,
    createdAt: Long,
    remoteId: String?,
    canRegisterUsers: Long,
    canCreateActivities: Long,
    canEditUsers: Long,
  ) -> T): Query<T> = Query(-1_301_143_170, arrayOf("User"), driver, "ChecklistDatabase.sq",
      "selectAllUsers",
      "SELECT User.id, User.name, User.email, User.password, User.area, User.workSector, User.permissionLevel, User.allowedAreas, User.createdAt, User.remoteId, User.canRegisterUsers, User.canCreateActivities, User.canEditUsers FROM User") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getString(9),
      cursor.getLong(10)!!,
      cursor.getLong(11)!!,
      cursor.getLong(12)!!
    )
  }

  public fun selectAllUsers(): Query<User> = selectAllUsers { id, name, email, password, area,
      workSector, permissionLevel, allowedAreas, createdAt, remoteId, canRegisterUsers,
      canCreateActivities, canEditUsers ->
    User(
      id,
      name,
      email,
      password,
      area,
      workSector,
      permissionLevel,
      allowedAreas,
      createdAt,
      remoteId,
      canRegisterUsers,
      canCreateActivities,
      canEditUsers
    )
  }

  public fun <T : Any> selectUserByName(name: String, mapper: (
    id: Long,
    name: String,
    email: String,
    password: String,
    area: String,
    workSector: String,
    permissionLevel: String,
    allowedAreas: String,
    createdAt: Long,
    remoteId: String?,
    canRegisterUsers: Long,
    canCreateActivities: Long,
    canEditUsers: Long,
  ) -> T): Query<T> = SelectUserByNameQuery(name) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getString(9),
      cursor.getLong(10)!!,
      cursor.getLong(11)!!,
      cursor.getLong(12)!!
    )
  }

  public fun selectUserByName(name: String): Query<User> = selectUserByName(name) { id, name_,
      email, password, area, workSector, permissionLevel, allowedAreas, createdAt, remoteId,
      canRegisterUsers, canCreateActivities, canEditUsers ->
    User(
      id,
      name_,
      email,
      password,
      area,
      workSector,
      permissionLevel,
      allowedAreas,
      createdAt,
      remoteId,
      canRegisterUsers,
      canCreateActivities,
      canEditUsers
    )
  }

  public fun <T : Any> selectUserByEmail(email: String, mapper: (
    id: Long,
    name: String,
    email: String,
    password: String,
    area: String,
    workSector: String,
    permissionLevel: String,
    allowedAreas: String,
    createdAt: Long,
    remoteId: String?,
    canRegisterUsers: Long,
    canCreateActivities: Long,
    canEditUsers: Long,
  ) -> T): Query<T> = SelectUserByEmailQuery(email) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getString(9),
      cursor.getLong(10)!!,
      cursor.getLong(11)!!,
      cursor.getLong(12)!!
    )
  }

  public fun selectUserByEmail(email: String): Query<User> = selectUserByEmail(email) { id, name,
      email_, password, area, workSector, permissionLevel, allowedAreas, createdAt, remoteId,
      canRegisterUsers, canCreateActivities, canEditUsers ->
    User(
      id,
      name,
      email_,
      password,
      area,
      workSector,
      permissionLevel,
      allowedAreas,
      createdAt,
      remoteId,
      canRegisterUsers,
      canCreateActivities,
      canEditUsers
    )
  }

  public fun <T : Any> selectUserByRemoteId(remoteId: String?, mapper: (
    id: Long,
    name: String,
    email: String,
    password: String,
    area: String,
    workSector: String,
    permissionLevel: String,
    allowedAreas: String,
    createdAt: Long,
    remoteId: String?,
    canRegisterUsers: Long,
    canCreateActivities: Long,
    canEditUsers: Long,
  ) -> T): Query<T> = SelectUserByRemoteIdQuery(remoteId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getString(9),
      cursor.getLong(10)!!,
      cursor.getLong(11)!!,
      cursor.getLong(12)!!
    )
  }

  public fun selectUserByRemoteId(remoteId: String?): Query<User> = selectUserByRemoteId(remoteId) {
      id, name, email, password, area, workSector, permissionLevel, allowedAreas, createdAt,
      remoteId_, canRegisterUsers, canCreateActivities, canEditUsers ->
    User(
      id,
      name,
      email,
      password,
      area,
      workSector,
      permissionLevel,
      allowedAreas,
      createdAt,
      remoteId_,
      canRegisterUsers,
      canCreateActivities,
      canEditUsers
    )
  }

  public fun <T : Any> selectAllActivities(mapper: (
    id: Long,
    syncId: String?,
    name: String,
    area: String,
    frequency: String,
    effort: Long,
    serverRevision: Long,
    syncState: String,
    deletedAt: Long?,
  ) -> T): Query<T> = Query(384_786_999, arrayOf("Activity"), driver, "ChecklistDatabase.sq",
      "selectAllActivities",
      "SELECT Activity.id, Activity.syncId, Activity.name, Activity.area, Activity.frequency, Activity.effort, Activity.serverRevision, Activity.syncState, Activity.deletedAt FROM Activity WHERE deletedAt IS NULL") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1),
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)
    )
  }

  public fun selectAllActivities(): Query<Activity> = selectAllActivities { id, syncId, name, area,
      frequency, effort, serverRevision, syncState, deletedAt ->
    Activity(
      id,
      syncId,
      name,
      area,
      frequency,
      effort,
      serverRevision,
      syncState,
      deletedAt
    )
  }

  public fun <T : Any> selectActivitiesByArea(area: String, mapper: (
    id: Long,
    syncId: String?,
    name: String,
    area: String,
    frequency: String,
    effort: Long,
    serverRevision: Long,
    syncState: String,
    deletedAt: Long?,
  ) -> T): Query<T> = SelectActivitiesByAreaQuery(area) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1),
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)
    )
  }

  public fun selectActivitiesByArea(area: String): Query<Activity> = selectActivitiesByArea(area) {
      id, syncId, name, area_, frequency, effort, serverRevision, syncState, deletedAt ->
    Activity(
      id,
      syncId,
      name,
      area_,
      frequency,
      effort,
      serverRevision,
      syncState,
      deletedAt
    )
  }

  public fun <T : Any> selectActivityById(id: Long, mapper: (
    id: Long,
    syncId: String?,
    name: String,
    area: String,
    frequency: String,
    effort: Long,
    serverRevision: Long,
    syncState: String,
    deletedAt: Long?,
  ) -> T): Query<T> = SelectActivityByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1),
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)
    )
  }

  public fun selectActivityById(id: Long): Query<Activity> = selectActivityById(id) { id_, syncId,
      name, area, frequency, effort, serverRevision, syncState, deletedAt ->
    Activity(
      id_,
      syncId,
      name,
      area,
      frequency,
      effort,
      serverRevision,
      syncState,
      deletedAt
    )
  }

  public fun <T : Any> selectActivityBySyncId(syncId: String?, mapper: (
    id: Long,
    syncId: String?,
    name: String,
    area: String,
    frequency: String,
    effort: Long,
    serverRevision: Long,
    syncState: String,
    deletedAt: Long?,
  ) -> T): Query<T> = SelectActivityBySyncIdQuery(syncId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1),
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)
    )
  }

  public fun selectActivityBySyncId(syncId: String?): Query<Activity> =
      selectActivityBySyncId(syncId) { id, syncId_, name, area, frequency, effort, serverRevision,
      syncState, deletedAt ->
    Activity(
      id,
      syncId_,
      name,
      area,
      frequency,
      effort,
      serverRevision,
      syncState,
      deletedAt
    )
  }

  public fun <T : Any> selectCompletionBySyncId(syncId: String?, mapper: (
    id: Long,
    syncId: String?,
    activityId: Long,
    userId: Long,
    completedAt: Long,
    imagePath: String?,
    isLate: Long,
    serverRevision: Long,
    syncState: String,
  ) -> T): Query<T> = SelectCompletionBySyncIdQuery(syncId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1),
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5),
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getString(8)!!
    )
  }

  public fun selectCompletionBySyncId(syncId: String?): Query<ActivityCompletion> =
      selectCompletionBySyncId(syncId) { id, syncId_, activityId, userId, completedAt, imagePath,
      isLate, serverRevision, syncState ->
    ActivityCompletion(
      id,
      syncId_,
      activityId,
      userId,
      completedAt,
      imagePath,
      isLate,
      serverRevision,
      syncState
    )
  }

  public fun <T : Any> selectCompletionsByActivityAndDate(
    activityId: Long,
    completedAt: Long,
    mapper: (
      id: Long,
      syncId: String?,
      activityId: Long,
      userId: Long,
      completedAt: Long,
      imagePath: String?,
      isLate: Long,
      serverRevision: Long,
      syncState: String,
    ) -> T,
  ): Query<T> = SelectCompletionsByActivityAndDateQuery(activityId, completedAt) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1),
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5),
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getString(8)!!
    )
  }

  public fun selectCompletionsByActivityAndDate(activityId: Long, completedAt: Long):
      Query<ActivityCompletion> = selectCompletionsByActivityAndDate(activityId, completedAt) { id,
      syncId, activityId_, userId, completedAt_, imagePath, isLate, serverRevision, syncState ->
    ActivityCompletion(
      id,
      syncId,
      activityId_,
      userId,
      completedAt_,
      imagePath,
      isLate,
      serverRevision,
      syncState
    )
  }

  public fun <T : Any> selectWorkClockEntriesByUserAndDate(
    userId: Long,
    registeredAt: Long,
    registeredAt_: Long,
    mapper: (
      id: Long,
      userId: Long,
      type: String,
      registeredAt: Long,
      latitude: Double,
      longitude: Double,
      distanceFromWorkMeters: Double,
      isLate: Long,
    ) -> T,
  ): Query<T> = SelectWorkClockEntriesByUserAndDateQuery(userId, registeredAt, registeredAt_) {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!,
      cursor.getDouble(4)!!,
      cursor.getDouble(5)!!,
      cursor.getDouble(6)!!,
      cursor.getLong(7)!!
    )
  }

  public fun selectWorkClockEntriesByUserAndDate(
    userId: Long,
    registeredAt: Long,
    registeredAt_: Long,
  ): Query<WorkClockEntry> = selectWorkClockEntriesByUserAndDate(userId, registeredAt,
      registeredAt_) { id, userId_, type, registeredAt__, latitude, longitude,
      distanceFromWorkMeters, isLate ->
    WorkClockEntry(
      id,
      userId_,
      type,
      registeredAt__,
      latitude,
      longitude,
      distanceFromWorkMeters,
      isLate
    )
  }

  public fun <T : Any> selectWorkClockEntriesByUserAndPeriod(
    userId: Long,
    registeredAt: Long,
    registeredAt_: Long,
    mapper: (
      id: Long,
      userId: Long,
      type: String,
      registeredAt: Long,
      latitude: Double,
      longitude: Double,
      distanceFromWorkMeters: Double,
      isLate: Long,
    ) -> T,
  ): Query<T> = SelectWorkClockEntriesByUserAndPeriodQuery(userId, registeredAt, registeredAt_) {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!,
      cursor.getDouble(4)!!,
      cursor.getDouble(5)!!,
      cursor.getDouble(6)!!,
      cursor.getLong(7)!!
    )
  }

  public fun selectWorkClockEntriesByUserAndPeriod(
    userId: Long,
    registeredAt: Long,
    registeredAt_: Long,
  ): Query<WorkClockEntry> = selectWorkClockEntriesByUserAndPeriod(userId, registeredAt,
      registeredAt_) { id, userId_, type, registeredAt__, latitude, longitude,
      distanceFromWorkMeters, isLate ->
    WorkClockEntry(
      id,
      userId_,
      type,
      registeredAt__,
      latitude,
      longitude,
      distanceFromWorkMeters,
      isLate
    )
  }

  public fun <T : Any> selectCompletionsByAreaAndDate(
    area: String,
    completedAt: Long,
    mapper: (
      id: Long,
      syncId: String?,
      activityId: Long,
      userId: Long,
      completedAt: Long,
      imagePath: String?,
      isLate: Long,
      serverRevision: Long,
      syncState: String,
    ) -> T,
  ): Query<T> = SelectCompletionsByAreaAndDateQuery(area, completedAt) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1),
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5),
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getString(8)!!
    )
  }

  public fun selectCompletionsByAreaAndDate(area: String, completedAt: Long):
      Query<ActivityCompletion> = selectCompletionsByAreaAndDate(area, completedAt) { id, syncId,
      activityId, userId, completedAt_, imagePath, isLate, serverRevision, syncState ->
    ActivityCompletion(
      id,
      syncId,
      activityId,
      userId,
      completedAt_,
      imagePath,
      isLate,
      serverRevision,
      syncState
    )
  }

  public fun <T : Any> countActivitiesByArea(mapper: (area: String, total: Long) -> T): Query<T> =
      Query(149_918_501, arrayOf("Activity"), driver, "ChecklistDatabase.sq",
      "countActivitiesByArea",
      "SELECT area, COUNT(*) AS total FROM Activity WHERE deletedAt IS NULL GROUP BY area") {
      cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getLong(1)!!
    )
  }

  public fun countActivitiesByArea(): Query<CountActivitiesByArea> = countActivitiesByArea { area,
      total ->
    CountActivitiesByArea(
      area,
      total
    )
  }

  public fun <T : Any> countCompletionsByAreaAndDate(completedAt: Long, mapper: (area: String,
      completed: Long) -> T): Query<T> = CountCompletionsByAreaAndDateQuery(completedAt) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getLong(1)!!
    )
  }

  public fun countCompletionsByAreaAndDate(completedAt: Long): Query<CountCompletionsByAreaAndDate>
      = countCompletionsByAreaAndDate(completedAt) { area, completed ->
    CountCompletionsByAreaAndDate(
      area,
      completed
    )
  }

  public fun <T : Any> selectSyncOutboxByEntity(
    entityType: String,
    entitySyncId: String,
    mapper: (
      operationId: String,
      entityType: String,
      entitySyncId: String,
      operationType: String,
      payload: String,
      createdAt: Long,
      attemptCount: Long,
      nextAttemptAt: Long,
      lastError: String?,
      status: String,
    ) -> T,
  ): Query<T> = SelectSyncOutboxByEntityQuery(entityType, entitySyncId) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getString(8),
      cursor.getString(9)!!
    )
  }

  public fun selectSyncOutboxByEntity(entityType: String, entitySyncId: String): Query<SyncOutbox> =
      selectSyncOutboxByEntity(entityType, entitySyncId) { operationId, entityType_, entitySyncId_,
      operationType, payload, createdAt, attemptCount, nextAttemptAt, lastError, status ->
    SyncOutbox(
      operationId,
      entityType_,
      entitySyncId_,
      operationType,
      payload,
      createdAt,
      attemptCount,
      nextAttemptAt,
      lastError,
      status
    )
  }

  public fun <T : Any> selectPendingSyncOutbox(
    nextAttemptAt: Long,
    `value`: Long,
    mapper: (
      operationId: String,
      entityType: String,
      entitySyncId: String,
      operationType: String,
      payload: String,
      createdAt: Long,
      attemptCount: Long,
      nextAttemptAt: Long,
      lastError: String?,
      status: String,
    ) -> T,
  ): Query<T> = SelectPendingSyncOutboxQuery(nextAttemptAt, value) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getString(8),
      cursor.getString(9)!!
    )
  }

  public fun selectPendingSyncOutbox(nextAttemptAt: Long, value_: Long): Query<SyncOutbox> =
      selectPendingSyncOutbox(nextAttemptAt, value_) { operationId, entityType, entitySyncId,
      operationType, payload, createdAt, attemptCount, nextAttemptAt_, lastError, status ->
    SyncOutbox(
      operationId,
      entityType,
      entitySyncId,
      operationType,
      payload,
      createdAt,
      attemptCount,
      nextAttemptAt_,
      lastError,
      status
    )
  }

  public fun selectSyncMetadata(key: String): Query<String> = SelectSyncMetadataQuery(key) {
      cursor ->
    cursor.getString(0)!!
  }

  public fun <T : Any> selectActivitiesMissingSyncId(mapper: (
    id: Long,
    syncId: String?,
    name: String,
    area: String,
    frequency: String,
    effort: Long,
    serverRevision: Long,
    syncState: String,
    deletedAt: Long?,
  ) -> T): Query<T> = Query(2_105_109_944, arrayOf("Activity"), driver, "ChecklistDatabase.sq",
      "selectActivitiesMissingSyncId",
      "SELECT Activity.id, Activity.syncId, Activity.name, Activity.area, Activity.frequency, Activity.effort, Activity.serverRevision, Activity.syncState, Activity.deletedAt FROM Activity WHERE syncId IS NULL OR syncId = ''") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1),
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)
    )
  }

  public fun selectActivitiesMissingSyncId(): Query<Activity> = selectActivitiesMissingSyncId { id,
      syncId, name, area, frequency, effort, serverRevision, syncState, deletedAt ->
    Activity(
      id,
      syncId,
      name,
      area,
      frequency,
      effort,
      serverRevision,
      syncState,
      deletedAt
    )
  }

  public fun <T : Any> selectCompletionsMissingSyncId(mapper: (
    id: Long,
    syncId: String?,
    activityId: Long,
    userId: Long,
    completedAt: Long,
    imagePath: String?,
    isLate: Long,
    serverRevision: Long,
    syncState: String,
  ) -> T): Query<T> = Query(-1_675_953_700, arrayOf("ActivityCompletion"), driver,
      "ChecklistDatabase.sq", "selectCompletionsMissingSyncId",
      "SELECT ActivityCompletion.id, ActivityCompletion.syncId, ActivityCompletion.activityId, ActivityCompletion.userId, ActivityCompletion.completedAt, ActivityCompletion.imagePath, ActivityCompletion.isLate, ActivityCompletion.serverRevision, ActivityCompletion.syncState FROM ActivityCompletion WHERE syncId IS NULL OR syncId = ''") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1),
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5),
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getString(8)!!
    )
  }

  public fun selectCompletionsMissingSyncId(): Query<ActivityCompletion> =
      selectCompletionsMissingSyncId { id, syncId, activityId, userId, completedAt, imagePath,
      isLate, serverRevision, syncState ->
    ActivityCompletion(
      id,
      syncId,
      activityId,
      userId,
      completedAt,
      imagePath,
      isLate,
      serverRevision,
      syncState
    )
  }

  public fun <T : Any> getGlobalStats(periodStart: Long, mapper: (
    totalActivities: Long,
    totalCompleted: Long,
    lateCompletions: Long,
  ) -> T): Query<T> = GetGlobalStatsQuery(periodStart) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!
    )
  }

  public fun getGlobalStats(periodStart: Long): Query<GetGlobalStats> =
      getGlobalStats(periodStart) { totalActivities, totalCompleted, lateCompletions ->
    GetGlobalStats(
      totalActivities,
      totalCompleted,
      lateCompletions
    )
  }

  public fun <T : Any> getRankingData(periodStart: Long, mapper: (
    name: String,
    totalCompletions: Long,
    onTimeCompletions: Long?,
    totalEffort: Double?,
  ) -> T): Query<T> = GetRankingDataQuery(periodStart) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2),
      cursor.getDouble(3)
    )
  }

  public fun getRankingData(periodStart: Long): Query<GetRankingData> =
      getRankingData(periodStart) { name, totalCompletions, onTimeCompletions, totalEffort ->
    GetRankingData(
      name,
      totalCompletions,
      onTimeCompletions,
      totalEffort
    )
  }

  public fun insertUser(
    name: String,
    email: String,
    password: String,
    area: String,
    workSector: String,
    permissionLevel: String,
    allowedAreas: String,
    createdAt: Long,
    remoteId: String?,
    canRegisterUsers: Long,
    canCreateActivities: Long,
    canEditUsers: Long,
  ) {
    driver.execute(-240_167_137, """
        |INSERT INTO User(name, email, password, area, workSector, permissionLevel, allowedAreas, createdAt, remoteId, canRegisterUsers, canCreateActivities, canEditUsers)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 12) {
          bindString(0, name)
          bindString(1, email)
          bindString(2, password)
          bindString(3, area)
          bindString(4, workSector)
          bindString(5, permissionLevel)
          bindString(6, allowedAreas)
          bindLong(7, createdAt)
          bindString(8, remoteId)
          bindLong(9, canRegisterUsers)
          bindLong(10, canCreateActivities)
          bindLong(11, canEditUsers)
        }
    notifyQueries(-240_167_137) { emit ->
      emit("User")
    }
  }

  public fun insertActivity(
    syncId: String?,
    name: String,
    area: String,
    frequency: String,
    effort: Long,
    serverRevision: Long,
    syncState: String,
    deletedAt: Long?,
  ) {
    driver.execute(-80_309_149, """
        |INSERT INTO Activity(syncId, name, area, frequency, effort, serverRevision, syncState, deletedAt)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 8) {
          bindString(0, syncId)
          bindString(1, name)
          bindString(2, area)
          bindString(3, frequency)
          bindLong(4, effort)
          bindLong(5, serverRevision)
          bindString(6, syncState)
          bindLong(7, deletedAt)
        }
    notifyQueries(-80_309_149) { emit ->
      emit("Activity")
    }
  }

  public fun insertCompletion(
    syncId: String?,
    activityId: Long,
    userId: Long,
    completedAt: Long,
    imagePath: String?,
    isLate: Long,
    serverRevision: Long,
    syncState: String,
  ) {
    driver.execute(1_837_465_648, """
        |INSERT INTO ActivityCompletion(syncId, activityId, userId, completedAt, imagePath, isLate, serverRevision, syncState)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 8) {
          bindString(0, syncId)
          bindLong(1, activityId)
          bindLong(2, userId)
          bindLong(3, completedAt)
          bindString(4, imagePath)
          bindLong(5, isLate)
          bindLong(6, serverRevision)
          bindString(7, syncState)
        }
    notifyQueries(1_837_465_648) { emit ->
      emit("ActivityCompletion")
    }
  }

  public fun insertWorkClockEntry(
    userId: Long,
    type: String,
    registeredAt: Long,
    latitude: Double,
    longitude: Double,
    distanceFromWorkMeters: Double,
    isLate: Long,
  ) {
    driver.execute(-1_255_298_743, """
        |INSERT INTO WorkClockEntry(userId, type, registeredAt, latitude, longitude, distanceFromWorkMeters, isLate)
        |VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 7) {
          bindLong(0, userId)
          bindString(1, type)
          bindLong(2, registeredAt)
          bindDouble(3, latitude)
          bindDouble(4, longitude)
          bindDouble(5, distanceFromWorkMeters)
          bindLong(6, isLate)
        }
    notifyQueries(-1_255_298_743) { emit ->
      emit("WorkClockEntry")
    }
  }

  public fun updateUserFeaturePermissions(
    canRegisterUsers: Long,
    canCreateActivities: Long,
    canEditUsers: Long,
    id: Long,
  ) {
    driver.execute(1_698_201_405, """
        |UPDATE User
        |SET canRegisterUsers = ?, canCreateActivities = ?, canEditUsers = ?
        |WHERE id = ?
        """.trimMargin(), 4) {
          bindLong(0, canRegisterUsers)
          bindLong(1, canCreateActivities)
          bindLong(2, canEditUsers)
          bindLong(3, id)
        }
    notifyQueries(1_698_201_405) { emit ->
      emit("User")
    }
  }

  public fun updateActivity(
    name: String,
    area: String,
    frequency: String,
    effort: Long,
    serverRevision: Long,
    syncState: String,
    deletedAt: Long?,
    id: Long,
  ) {
    driver.execute(-533_267_853, """
        |UPDATE Activity SET name = ?, area = ?, frequency = ?, effort = ?, serverRevision = ?, syncState = ?, deletedAt = ?
        |WHERE id = ?
        """.trimMargin(), 8) {
          bindString(0, name)
          bindString(1, area)
          bindString(2, frequency)
          bindLong(3, effort)
          bindLong(4, serverRevision)
          bindString(5, syncState)
          bindLong(6, deletedAt)
          bindLong(7, id)
        }
    notifyQueries(-533_267_853) { emit ->
      emit("Activity")
    }
  }

  public fun deleteActivity(
    deletedAt: Long?,
    syncState: String,
    serverRevision: Long,
    id: Long,
  ) {
    driver.execute(838_550_869, """
        |UPDATE Activity SET deletedAt = ?, syncState = ?, serverRevision = ?
        |WHERE id = ?
        """.trimMargin(), 4) {
          bindLong(0, deletedAt)
          bindString(1, syncState)
          bindLong(2, serverRevision)
          bindLong(3, id)
        }
    notifyQueries(838_550_869) { emit ->
      emit("Activity")
    }
  }

  public fun hardDeleteActivity(id: Long) {
    driver.execute(1_459_168_672, """DELETE FROM Activity WHERE id = ?""", 1) {
          bindLong(0, id)
        }
    notifyQueries(1_459_168_672) { emit ->
      emit("Activity")
    }
  }

  public fun deleteCompletionsByActivity(activityId: Long) {
    driver.execute(444_968_823, """DELETE FROM ActivityCompletion WHERE activityId = ?""", 1) {
          bindLong(0, activityId)
        }
    notifyQueries(444_968_823) { emit ->
      emit("ActivityCompletion")
    }
  }

  public fun markActivitySync(
    serverRevision: Long,
    syncState: String,
    deletedAt: Long?,
    syncId: String?,
  ) {
    driver.execute(null, """
        |UPDATE Activity
        |SET serverRevision = ?, syncState = ?, deletedAt = ?
        |WHERE syncId ${ if (syncId == null) "IS" else "=" } ?
        """.trimMargin(), 4) {
          bindLong(0, serverRevision)
          bindString(1, syncState)
          bindLong(2, deletedAt)
          bindString(3, syncId)
        }
    notifyQueries(1_215_382_130) { emit ->
      emit("Activity")
    }
  }

  public fun markCompletionSync(
    serverRevision: Long,
    syncState: String,
    syncId: String?,
  ) {
    driver.execute(null, """
        |UPDATE ActivityCompletion
        |SET serverRevision = ?, syncState = ?
        |WHERE syncId ${ if (syncId == null) "IS" else "=" } ?
        """.trimMargin(), 3) {
          bindLong(0, serverRevision)
          bindString(1, syncState)
          bindString(2, syncId)
        }
    notifyQueries(-1_002_661_185) { emit ->
      emit("ActivityCompletion")
    }
  }

  public fun insertSyncOutbox(
    operationId: String,
    entityType: String,
    entitySyncId: String,
    operationType: String,
    payload: String,
    createdAt: Long,
    attemptCount: Long,
    nextAttemptAt: Long,
    lastError: String?,
    status: String,
  ) {
    driver.execute(73_262_732, """
        |INSERT INTO SyncOutbox(operationId, entityType, entitySyncId, operationType, payload, createdAt, attemptCount, nextAttemptAt, lastError, status)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 10) {
          bindString(0, operationId)
          bindString(1, entityType)
          bindString(2, entitySyncId)
          bindString(3, operationType)
          bindString(4, payload)
          bindLong(5, createdAt)
          bindLong(6, attemptCount)
          bindLong(7, nextAttemptAt)
          bindString(8, lastError)
          bindString(9, status)
        }
    notifyQueries(73_262_732) { emit ->
      emit("SyncOutbox")
    }
  }

  public fun deleteSyncOutboxByOperationId(operationId: String) {
    driver.execute(2_084_489_645, """DELETE FROM SyncOutbox WHERE operationId = ?""", 1) {
          bindString(0, operationId)
        }
    notifyQueries(2_084_489_645) { emit ->
      emit("SyncOutbox")
    }
  }

  public fun deleteSyncOutboxByEntity(entityType: String, entitySyncId: String) {
    driver.execute(-1_170_167_752,
        """DELETE FROM SyncOutbox WHERE entityType = ? AND entitySyncId = ?""", 2) {
          bindString(0, entityType)
          bindString(1, entitySyncId)
        }
    notifyQueries(-1_170_167_752) { emit ->
      emit("SyncOutbox")
    }
  }

  public fun updateSyncOutboxAttempt(
    attemptCount: Long,
    nextAttemptAt: Long,
    lastError: String?,
    status: String,
    operationId: String,
  ) {
    driver.execute(1_679_473_297, """
        |UPDATE SyncOutbox
        |SET attemptCount = ?, nextAttemptAt = ?, lastError = ?, status = ?
        |WHERE operationId = ?
        """.trimMargin(), 5) {
          bindLong(0, attemptCount)
          bindLong(1, nextAttemptAt)
          bindString(2, lastError)
          bindString(3, status)
          bindString(4, operationId)
        }
    notifyQueries(1_679_473_297) { emit ->
      emit("SyncOutbox")
    }
  }

  public fun upsertSyncMetadata(key: String, value_: String) {
    driver.execute(510_614_452, """
        |INSERT OR REPLACE INTO SyncMetadata(key, value)
        |VALUES (?, ?)
        """.trimMargin(), 2) {
          bindString(0, key)
          bindString(1, value_)
        }
    notifyQueries(510_614_452) { emit ->
      emit("SyncMetadata")
    }
  }

  public fun setActivitySyncId(syncId: String?, id: Long) {
    driver.execute(-1_068_237_652, """
        |UPDATE Activity
        |SET syncId = ?
        |WHERE id = ?
        """.trimMargin(), 2) {
          bindString(0, syncId)
          bindLong(1, id)
        }
    notifyQueries(-1_068_237_652) { emit ->
      emit("Activity")
    }
  }

  public fun setCompletionSyncId(syncId: String?, id: Long) {
    driver.execute(1_381_962_233, """
        |UPDATE ActivityCompletion
        |SET syncId = ?
        |WHERE id = ?
        """.trimMargin(), 2) {
          bindString(0, syncId)
          bindLong(1, id)
        }
    notifyQueries(1_381_962_233) { emit ->
      emit("ActivityCompletion")
    }
  }

  public fun setUserRemoteId(remoteId: String?, id: Long) {
    driver.execute(494_868_051, """
        |UPDATE User
        |SET remoteId = ?
        |WHERE id = ?
        """.trimMargin(), 2) {
          bindString(0, remoteId)
          bindLong(1, id)
        }
    notifyQueries(494_868_051) { emit ->
      emit("User")
    }
  }

  private inner class SelectUserByNameQuery<out T : Any>(
    public val name: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("User", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("User", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-698_097_148,
        """SELECT User.id, User.name, User.email, User.password, User.area, User.workSector, User.permissionLevel, User.allowedAreas, User.createdAt, User.remoteId, User.canRegisterUsers, User.canCreateActivities, User.canEditUsers FROM User WHERE name = ?""",
        mapper, 1) {
      bindString(0, name)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectUserByName"
  }

  private inner class SelectUserByEmailQuery<out T : Any>(
    public val email: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("User", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("User", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-174_140_605,
        """SELECT User.id, User.name, User.email, User.password, User.area, User.workSector, User.permissionLevel, User.allowedAreas, User.createdAt, User.remoteId, User.canRegisterUsers, User.canCreateActivities, User.canEditUsers FROM User WHERE email = ?""",
        mapper, 1) {
      bindString(0, email)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectUserByEmail"
  }

  private inner class SelectUserByRemoteIdQuery<out T : Any>(
    public val remoteId: String?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("User", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("User", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(null,
        """SELECT User.id, User.name, User.email, User.password, User.area, User.workSector, User.permissionLevel, User.allowedAreas, User.createdAt, User.remoteId, User.canRegisterUsers, User.canCreateActivities, User.canEditUsers FROM User WHERE remoteId ${ if (remoteId == null) "IS" else "=" } ?""",
        mapper, 1) {
      bindString(0, remoteId)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectUserByRemoteId"
  }

  private inner class SelectActivitiesByAreaQuery<out T : Any>(
    public val area: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("Activity", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("Activity", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(874_359_144,
        """SELECT Activity.id, Activity.syncId, Activity.name, Activity.area, Activity.frequency, Activity.effort, Activity.serverRevision, Activity.syncState, Activity.deletedAt FROM Activity WHERE area = ? AND deletedAt IS NULL""",
        mapper, 1) {
      bindString(0, area)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectActivitiesByArea"
  }

  private inner class SelectActivityByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("Activity", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("Activity", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(203_268_248,
        """SELECT Activity.id, Activity.syncId, Activity.name, Activity.area, Activity.frequency, Activity.effort, Activity.serverRevision, Activity.syncState, Activity.deletedAt FROM Activity WHERE id = ?""",
        mapper, 1) {
      bindLong(0, id)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectActivityById"
  }

  private inner class SelectActivityBySyncIdQuery<out T : Any>(
    public val syncId: String?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("Activity", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("Activity", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(null,
        """SELECT Activity.id, Activity.syncId, Activity.name, Activity.area, Activity.frequency, Activity.effort, Activity.serverRevision, Activity.syncState, Activity.deletedAt FROM Activity WHERE syncId ${ if (syncId == null) "IS" else "=" } ?""",
        mapper, 1) {
      bindString(0, syncId)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectActivityBySyncId"
  }

  private inner class SelectCompletionBySyncIdQuery<out T : Any>(
    public val syncId: String?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("ActivityCompletion", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("ActivityCompletion", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(null,
        """SELECT ActivityCompletion.id, ActivityCompletion.syncId, ActivityCompletion.activityId, ActivityCompletion.userId, ActivityCompletion.completedAt, ActivityCompletion.imagePath, ActivityCompletion.isLate, ActivityCompletion.serverRevision, ActivityCompletion.syncState FROM ActivityCompletion WHERE syncId ${ if (syncId == null) "IS" else "=" } ?""",
        mapper, 1) {
      bindString(0, syncId)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectCompletionBySyncId"
  }

  private inner class SelectCompletionsByActivityAndDateQuery<out T : Any>(
    public val activityId: Long,
    public val completedAt: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("ActivityCompletion", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("ActivityCompletion", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_376_942_719, """
    |SELECT ActivityCompletion.id, ActivityCompletion.syncId, ActivityCompletion.activityId, ActivityCompletion.userId, ActivityCompletion.completedAt, ActivityCompletion.imagePath, ActivityCompletion.isLate, ActivityCompletion.serverRevision, ActivityCompletion.syncState FROM ActivityCompletion 
    |WHERE activityId = ? AND completedAt >= ?
    """.trimMargin(), mapper, 2) {
      bindLong(0, activityId)
      bindLong(1, completedAt)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectCompletionsByActivityAndDate"
  }

  private inner class SelectWorkClockEntriesByUserAndDateQuery<out T : Any>(
    public val userId: Long,
    public val registeredAt: Long,
    public val registeredAt_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("WorkClockEntry", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("WorkClockEntry", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_931_129_031, """
    |SELECT WorkClockEntry.id, WorkClockEntry.userId, WorkClockEntry.type, WorkClockEntry.registeredAt, WorkClockEntry.latitude, WorkClockEntry.longitude, WorkClockEntry.distanceFromWorkMeters, WorkClockEntry.isLate FROM WorkClockEntry
    |WHERE userId = ? AND registeredAt >= ? AND registeredAt < ?
    |ORDER BY registeredAt ASC
    """.trimMargin(), mapper, 3) {
      bindLong(0, userId)
      bindLong(1, registeredAt)
      bindLong(2, registeredAt_)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectWorkClockEntriesByUserAndDate"
  }

  private inner class SelectWorkClockEntriesByUserAndPeriodQuery<out T : Any>(
    public val userId: Long,
    public val registeredAt: Long,
    public val registeredAt_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("WorkClockEntry", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("WorkClockEntry", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-41_935_220, """
    |SELECT WorkClockEntry.id, WorkClockEntry.userId, WorkClockEntry.type, WorkClockEntry.registeredAt, WorkClockEntry.latitude, WorkClockEntry.longitude, WorkClockEntry.distanceFromWorkMeters, WorkClockEntry.isLate FROM WorkClockEntry
    |WHERE userId = ? AND registeredAt >= ? AND registeredAt < ?
    |ORDER BY registeredAt ASC
    """.trimMargin(), mapper, 3) {
      bindLong(0, userId)
      bindLong(1, registeredAt)
      bindLong(2, registeredAt_)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectWorkClockEntriesByUserAndPeriod"
  }

  private inner class SelectCompletionsByAreaAndDateQuery<out T : Any>(
    public val area: String,
    public val completedAt: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("ActivityCompletion", "Activity", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("ActivityCompletion", "Activity", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_983_270_975, """
    |SELECT ac.id, ac.syncId, ac.activityId, ac.userId, ac.completedAt, ac.imagePath, ac.isLate, ac.serverRevision, ac.syncState FROM ActivityCompletion ac
    |JOIN Activity a ON ac.activityId = a.id
    |WHERE a.area = ? AND ac.completedAt >= ?
    """.trimMargin(), mapper, 2) {
      bindString(0, area)
      bindLong(1, completedAt)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectCompletionsByAreaAndDate"
  }

  private inner class CountCompletionsByAreaAndDateQuery<out T : Any>(
    public val completedAt: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("Activity", "ActivityCompletion", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("Activity", "ActivityCompletion", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_121_416_066, """
    |SELECT a.area, COUNT(ac.id) AS completed FROM Activity a
    |LEFT JOIN ActivityCompletion ac ON a.id = ac.activityId AND ac.completedAt >= ?
    |GROUP BY a.area
    """.trimMargin(), mapper, 1) {
      bindLong(0, completedAt)
    }

    override fun toString(): String = "ChecklistDatabase.sq:countCompletionsByAreaAndDate"
  }

  private inner class SelectSyncOutboxByEntityQuery<out T : Any>(
    public val entityType: String,
    public val entitySyncId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("SyncOutbox", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("SyncOutbox", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(697_599_305, """
    |SELECT SyncOutbox.operationId, SyncOutbox.entityType, SyncOutbox.entitySyncId, SyncOutbox.operationType, SyncOutbox.payload, SyncOutbox.createdAt, SyncOutbox.attemptCount, SyncOutbox.nextAttemptAt, SyncOutbox.lastError, SyncOutbox.status FROM SyncOutbox
    |WHERE entityType = ? AND entitySyncId = ?
    |ORDER BY createdAt ASC
    """.trimMargin(), mapper, 2) {
      bindString(0, entityType)
      bindString(1, entitySyncId)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectSyncOutboxByEntity"
  }

  private inner class SelectPendingSyncOutboxQuery<out T : Any>(
    public val nextAttemptAt: Long,
    public val `value`: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("SyncOutbox", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("SyncOutbox", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-554_928_008, """
    |SELECT SyncOutbox.operationId, SyncOutbox.entityType, SyncOutbox.entitySyncId, SyncOutbox.operationType, SyncOutbox.payload, SyncOutbox.createdAt, SyncOutbox.attemptCount, SyncOutbox.nextAttemptAt, SyncOutbox.lastError, SyncOutbox.status FROM SyncOutbox
    |WHERE status = 'PENDING' AND nextAttemptAt <= ?
    |ORDER BY createdAt ASC
    |LIMIT ?
    """.trimMargin(), mapper, 2) {
      bindLong(0, nextAttemptAt)
      bindLong(1, value)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectPendingSyncOutbox"
  }

  private inner class SelectSyncMetadataQuery<out T : Any>(
    public val key: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("SyncMetadata", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("SyncMetadata", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_224_528_895, """SELECT value FROM SyncMetadata WHERE key = ?""",
        mapper, 1) {
      bindString(0, key)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectSyncMetadata"
  }

  private inner class GetGlobalStatsQuery<out T : Any>(
    public val periodStart: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("Activity", "ActivityCompletion", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("Activity", "ActivityCompletion", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_240_323_839, """
    |SELECT
    |    (SELECT COUNT(*) FROM Activity WHERE deletedAt IS NULL) AS totalActivities,
    |    (SELECT COUNT(*) FROM ActivityCompletion WHERE completedAt >= ?) AS totalCompleted,
    |    (SELECT COUNT(*) FROM ActivityCompletion WHERE completedAt >= ? AND isLate = 1) AS lateCompletions
    """.trimMargin(), mapper, 2) {
      bindLong(0, periodStart)
      bindLong(1, periodStart)
    }

    override fun toString(): String = "ChecklistDatabase.sq:getGlobalStats"
  }

  private inner class GetRankingDataQuery<out T : Any>(
    public val periodStart: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("User", "ActivityCompletion", "Activity", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("User", "ActivityCompletion", "Activity", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_526_968_133, """
    |SELECT
    |    u.name,
    |    COUNT(ac.id) AS totalCompletions,
    |    SUM(CASE WHEN ac.isLate = 0 THEN 1 ELSE 0 END) AS onTimeCompletions,
    |    SUM(a.effort) AS totalEffort
    |FROM User AS u
    |LEFT JOIN ActivityCompletion AS ac ON u.id = ac.userId AND ac.completedAt >= ?
    |LEFT JOIN Activity AS a ON ac.activityId = a.id AND a.deletedAt IS NULL
    |GROUP BY u.id, u.name
    |ORDER BY totalEffort DESC
    """.trimMargin(), mapper, 1) {
      bindLong(0, periodStart)
    }

    override fun toString(): String = "ChecklistDatabase.sq:getRankingData"
  }
}
