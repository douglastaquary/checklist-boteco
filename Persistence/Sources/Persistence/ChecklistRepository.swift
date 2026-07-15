import Foundation
import GRDB
import Models
public final class ChecklistRepository: Sendable {
  public let dbQueue: DatabaseQueue
  private let syncCallbackBox = SyncCallbackBox()

  public init(dbQueue: DatabaseQueue, onSyncRequested: (@Sendable () -> Void)? = nil) {
    self.dbQueue = dbQueue
    syncCallbackBox.handler = onSyncRequested
  }

  public func bindSyncHandler(_ handler: @escaping @Sendable () -> Void) {
    syncCallbackBox.handler = handler
  }

  private func notifySyncRequested() {
    syncCallbackBox.handler?()
  }

  public func seedInitialDataIfNeeded() throws {
    try dbQueue.write { db in
      let count = try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM User") ?? 0
      guard count == 0 else { return }
      try db.execute(
        sql: """
        INSERT INTO User(name, email, password, area, workSector, permissionLevel, allowedAreas, createdAt,
          canRegisterUsers, canCreateActivities, canEditUsers, canCreateInventoryCounts,
          canViewInventoryInsights, canManageAdministrativeStock, mustChangePassword)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 1, 1, 1, 1, 0)
        """,
        arguments: [
          "Admin", "admin@checklistboteco.com", "admin123", Area.atendimento.rawValue,
          WorkSector.gerente.rawValue, PermissionLevel.admin.rawValue,
          Area.allCases.map(\.rawValue).joined(separator: ","), Date.nowMillis,
        ]
      )
      try db.execute(
        sql: """
        INSERT INTO User(name, email, password, area, workSector, permissionLevel, allowedAreas, createdAt,
          canRegisterUsers, canCreateActivities, canEditUsers, canCreateInventoryCounts,
          canViewInventoryInsights, canManageAdministrativeStock, mustChangePassword)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, 0, 0)
        """,
        arguments: [
          "Colaborador", "colaborador@checklistboteco.com", "colab123", Area.atendimento.rawValue,
          WorkSector.atendimento.rawValue, PermissionLevel.user.rawValue,
          Area.atendimento.rawValue, Date.nowMillis,
        ]
      )
      let activities: [(String, Area, Frequency)] = [
        ("Abrir caixa", .atendimento, .diario),
        ("Conferir estoque geladeira", .estoque, .diario),
        ("Limpar balcão", .limpeza, .diario),
        ("Preparar mise en place", .cozinha, .diario),
      ]
      for (name, area, frequency) in activities {
        try db.execute(
          sql: "INSERT INTO Activity(syncId, name, area, frequency, effort) VALUES (?, ?, ?, ?, 1)",
          arguments: ["seed-\(UUID().uuidString)", name, area.rawValue, frequency.rawValue]
        )
      }
    }
  }

  public func purgeLocalSeedArtifactsIfNeeded() throws {
    try dbQueue.write { db in
      let purged = try String.fetchOne(
        db,
        sql: "SELECT value FROM SyncMetadata WHERE key = ?",
        arguments: [MetadataKey.seedPurged]
      )
      guard purged != "1" else { return }
      try db.execute(sql: "DELETE FROM Activity WHERE syncId LIKE 'seed-%'")
      try db.execute(sql: "DELETE FROM ActivityCompletion")
      try db.execute(
        sql: "DELETE FROM User WHERE email IN (?, ?)",
        arguments: ["admin@checklistboteco.com", "colaborador@checklistboteco.com"]
      )
      try db.execute(sql: "DELETE FROM SyncOutbox")
      try upsertMetadata(db, key: MetadataKey.seedPurged, value: "1")
    }
  }

  public func getUserByName(_ name: String) throws -> User? {
    try dbQueue.read { db in
      try UserRecord.fetchOne(db, sql: "SELECT * FROM User WHERE name = ?", arguments: [name])?.toDomain()
    }
  }

  public func getUserByEmail(_ email: String) throws -> User? {
    try dbQueue.read { db in
      try UserRecord.fetchOne(db, sql: "SELECT * FROM User WHERE email = ?", arguments: [email])?.toDomain()
    }
  }

  public func getUserById(_ id: Int64) throws -> User? {
    try dbQueue.read { db in
      try UserRecord.fetchOne(db, sql: "SELECT * FROM User WHERE id = ?", arguments: [id])?.toDomain()
    }
  }

  public func upsertRemoteUser(_ remote: User) throws {
    try dbQueue.write { db in
      guard let remoteId = remote.remoteId, !remoteId.isEmpty else { return }
      if let existing = try UserRecord.fetchOne(
        db,
        sql: "SELECT * FROM User WHERE remoteId = ?",
        arguments: [remoteId]
      ) {
        try db.execute(
          sql: """
          UPDATE User SET name = ?, email = ?, area = ?, workSector = ?, permissionLevel = ?, allowedAreas = ?,
            canRegisterUsers = ?, canCreateActivities = ?, canEditUsers = ?,
            canCreateInventoryCounts = ?, canViewInventoryInsights = ?, canManageAdministrativeStock = ?, mustChangePassword = ?
          WHERE id = ?
          """,
          arguments: [
            remote.name, remote.email, remote.area.rawValue, remote.workSector.rawValue,
            remote.permissionLevel.rawValue, remote.allowedAreas.map(\.rawValue).joined(separator: ","),
            remote.featurePermissions.canRegisterUsers, remote.featurePermissions.canCreateActivities,
            remote.featurePermissions.canEditUsers, remote.featurePermissions.canCreateInventoryCounts,
            remote.featurePermissions.canViewInventoryInsights, remote.featurePermissions.canManageAdministrativeStock,
            remote.mustChangePassword,
            existing.id,
          ]
        )
      } else if let existing = try UserRecord.fetchOne(
        db,
        sql: "SELECT * FROM User WHERE email = ?",
        arguments: [remote.email]
      ) {
        try db.execute(
          sql: """
          UPDATE User SET name = ?, area = ?, workSector = ?, permissionLevel = ?, allowedAreas = ?, remoteId = ?,
            canRegisterUsers = ?, canCreateActivities = ?, canEditUsers = ?,
            canCreateInventoryCounts = ?, canViewInventoryInsights = ?, canManageAdministrativeStock = ?, mustChangePassword = ?
          WHERE id = ?
          """,
          arguments: [
            remote.name, remote.area.rawValue, remote.workSector.rawValue,
            remote.permissionLevel.rawValue, remote.allowedAreas.map(\.rawValue).joined(separator: ","),
            remoteId,
            remote.featurePermissions.canRegisterUsers, remote.featurePermissions.canCreateActivities,
            remote.featurePermissions.canEditUsers, remote.featurePermissions.canCreateInventoryCounts,
            remote.featurePermissions.canViewInventoryInsights, remote.featurePermissions.canManageAdministrativeStock,
            remote.mustChangePassword,
            existing.id,
          ]
        )
      } else {
        try db.execute(
          sql: """
          INSERT INTO User(name, email, password, area, workSector, permissionLevel, allowedAreas, createdAt, remoteId,
            canRegisterUsers, canCreateActivities, canEditUsers, canCreateInventoryCounts,
            canViewInventoryInsights, canManageAdministrativeStock, mustChangePassword)
          VALUES (?, ?, '', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          arguments: [
            remote.name, remote.email, remote.area.rawValue, remote.workSector.rawValue,
            remote.permissionLevel.rawValue, remote.allowedAreas.map(\.rawValue).joined(separator: ","),
            remote.createdAt, remoteId,
            remote.featurePermissions.canRegisterUsers, remote.featurePermissions.canCreateActivities,
            remote.featurePermissions.canEditUsers, remote.featurePermissions.canCreateInventoryCounts,
            remote.featurePermissions.canViewInventoryInsights, remote.featurePermissions.canManageAdministrativeStock,
            remote.mustChangePassword,
          ]
        )
      }
    }
  }

  public func upsertRemoteUsers(_ users: [User]) throws {
    for user in users {
      try upsertRemoteUser(user)
    }
  }

  public func saveWorksite(_ info: WorksiteInfo) throws {
    try dbQueue.write { db in
      try upsertMetadata(db, key: MetadataKey.worksiteName, value: info.name)
      try upsertMetadata(db, key: MetadataKey.worksiteLatitude, value: String(info.latitude))
      try upsertMetadata(db, key: MetadataKey.worksiteLongitude, value: String(info.longitude))
      try upsertMetadata(db, key: MetadataKey.worksiteRadius, value: String(info.radiusMeters))
    }
    WorksiteLocation.applyCached(info)
  }

  public func loadWorksite() -> WorksiteInfo {
    let info = (try? dbQueue.read { db -> WorksiteInfo? in
      guard let name = try selectMetadata(db, key: MetadataKey.worksiteName),
            let latStr = try selectMetadata(db, key: MetadataKey.worksiteLatitude),
            let lngStr = try selectMetadata(db, key: MetadataKey.worksiteLongitude),
            let radiusStr = try selectMetadata(db, key: MetadataKey.worksiteRadius),
            let lat = Double(latStr),
            let lng = Double(lngStr),
            let radius = Double(radiusStr)
      else { return nil }
      return WorksiteInfo(name: name, latitude: lat, longitude: lng, radiusMeters: radius)
    }) ?? nil
    if let info {
      WorksiteLocation.applyCached(info)
      return info
    }
    return WorksiteLocation.defaultInfo
  }

  public func getUserByRemoteId(_ remoteId: String) throws -> User? {
    try dbQueue.read { db in
      try UserRecord.fetchOne(db, sql: "SELECT * FROM User WHERE remoteId = ?", arguments: [remoteId])?.toDomain()
    }
  }

  public func insertUser(_ user: User) throws -> User {
    try dbQueue.write { db in
      try db.execute(
        sql: """
        INSERT INTO User(name, email, password, area, workSector, permissionLevel, allowedAreas, createdAt, remoteId,
          canRegisterUsers, canCreateActivities, canEditUsers, canCreateInventoryCounts,
          canViewInventoryInsights, canManageAdministrativeStock, mustChangePassword)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        arguments: [
          user.name, user.email, user.password, user.area.rawValue, user.workSector.rawValue,
          user.permissionLevel.rawValue, user.allowedAreas.map(\.rawValue).joined(separator: ","),
          user.createdAt, user.remoteId,
          user.featurePermissions.canRegisterUsers, user.featurePermissions.canCreateActivities,
          user.featurePermissions.canEditUsers, user.featurePermissions.canCreateInventoryCounts,
          user.featurePermissions.canViewInventoryInsights, user.featurePermissions.canManageAdministrativeStock,
          user.mustChangePassword,
        ]
      )
      let id = db.lastInsertedRowID
      return User(
        id: id,
        name: user.name,
        email: user.email,
        password: user.password,
        area: user.area,
        workSector: user.workSector,
        permissionLevel: user.permissionLevel,
        allowedAreas: user.allowedAreas,
        createdAt: user.createdAt,
        remoteId: user.remoteId,
        featurePermissions: user.featurePermissions,
        mustChangePassword: user.mustChangePassword
      )
    }
  }

  public func syncLocalUserFromRemote(localUserId: Int64, remoteUser: User) throws -> User {
    try dbQueue.write { db in
      try db.execute(
        sql: """
        UPDATE User SET name = ?, email = ?, area = ?, workSector = ?, permissionLevel = ?, allowedAreas = ?,
          remoteId = ?, canRegisterUsers = ?, canCreateActivities = ?, canEditUsers = ?,
          canCreateInventoryCounts = ?, canViewInventoryInsights = ?, canManageAdministrativeStock = ?, mustChangePassword = ?
        WHERE id = ?
        """,
        arguments: [
          remoteUser.name, remoteUser.email, remoteUser.area.rawValue, remoteUser.workSector.rawValue,
          remoteUser.permissionLevel.rawValue, remoteUser.allowedAreas.map(\.rawValue).joined(separator: ","),
          remoteUser.remoteId,
          remoteUser.featurePermissions.canRegisterUsers, remoteUser.featurePermissions.canCreateActivities,
          remoteUser.featurePermissions.canEditUsers, remoteUser.featurePermissions.canCreateInventoryCounts,
          remoteUser.featurePermissions.canViewInventoryInsights, remoteUser.featurePermissions.canManageAdministrativeStock,
          remoteUser.mustChangePassword,
          localUserId,
        ]
      )
      return try UserRecord.fetchOne(db, sql: "SELECT * FROM User WHERE id = ?", arguments: [localUserId])!.toDomain()
    }
  }

  public func saveSyncSession(localUserId: Int64, session: SyncSession) throws {
    try dbQueue.write { db in
      try db.execute(sql: "UPDATE User SET remoteId = ? WHERE id = ?", arguments: [session.remoteUserId, localUserId])
      try upsertMetadata(db, key: MetadataKey.authToken, value: session.authToken)
      try upsertMetadata(db, key: MetadataKey.remoteUserId, value: session.remoteUserId)
    }
  }

  public func updateUserPasswordState(localUserId: Int64, password: String, mustChangePassword: Bool) throws -> User {
    try dbQueue.write { db in
      try db.execute(
        sql: "UPDATE User SET password = ?, mustChangePassword = ? WHERE id = ?",
        arguments: [password, mustChangePassword, localUserId]
      )
      return try UserRecord.fetchOne(db, sql: "SELECT * FROM User WHERE id = ?", arguments: [localUserId])!.toDomain()
    }
  }

  public func getSyncSession() throws -> SyncSession? {
    try dbQueue.read { db in
      let token = try selectMetadata(db, key: MetadataKey.authToken)
      let remoteUserId = try selectMetadata(db, key: MetadataKey.remoteUserId)
      guard let token, !token.isEmpty, let remoteUserId, !remoteUserId.isEmpty else { return nil }
      return SyncSession(authToken: token, remoteUserId: remoteUserId)
    }
  }

  public func clearSyncSession() throws {
    try dbQueue.write { db in
      try upsertMetadata(db, key: MetadataKey.authToken, value: "")
      try upsertMetadata(db, key: MetadataKey.remoteUserId, value: "")
    }
  }

  public func getSyncCursor() throws -> String? {
    try dbQueue.read { try selectMetadata($0, key: MetadataKey.syncCursor) }
  }

  public func setSyncCursor(_ cursor: String) throws {
    try dbQueue.write { try upsertMetadata($0, key: MetadataKey.syncCursor, value: cursor) }
  }

  public func activitiesByArea(_ area: Area) throws -> [ActivityWithCompletion] {
    try dbQueue.read { db in
      let startOfDay = Date.startOfDayMillis
      let rows = try Row.fetchAll(
        db,
        sql: """
        SELECT a.*, c.id AS completionId, c.syncId AS completionSyncId, c.activityId, c.userId,
          c.completedAt, c.imagePath, c.isLate, c.serverRevision AS completionRevision, c.syncState AS completionSyncState
        FROM Activity a
        LEFT JOIN ActivityCompletion c ON c.activityId = a.id AND c.completedAt >= ?
        WHERE a.area = ? AND a.deletedAt IS NULL
        ORDER BY a.name ASC
        """,
        arguments: [startOfDay, area.rawValue]
      )
      return rows.map { row in
        let activity = ActivityRecord(row: row, prefix: "").toDomain()
        let completion: ActivityCompletion? = row["completionId"] != nil
          ? ActivityCompletion(
            id: row["completionId"],
            syncId: row["completionSyncId"],
            activityId: row["activityId"],
            userId: row["userId"],
            completedAt: row["completedAt"],
            imagePath: row["imagePath"],
            isLate: (row["isLate"] as Int64) != 0,
            serviceDate: row["serviceDate"] ?? "",
            serverRevision: row["completionRevision"] ?? 0,
            syncState: SyncState(rawValue: row["completionSyncState"] ?? "SYNCED") ?? .synced
          )
          : nil
        return ActivityWithCompletion(activity: activity, completion: completion)
      }
    }
  }

  public func completeActivity(
    activityId: Int64,
    userId: Int64,
    imagePath: String?,
    isLate: Bool
  ) throws {
    let now = Date.nowMillis
    let syncId = UUID().uuidString
    try dbQueue.write { db in
      try db.execute(
        sql: """
        INSERT INTO ActivityCompletion(syncId, activityId, userId, completedAt, imagePath, isLate, serviceDate)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        arguments: [syncId, activityId, userId, now, imagePath, isLate ? 1 : 0, Date.serviceDate]
      )
      let payload = """
      {"activitySyncId":"\(try activitySyncId(db, activityId: activityId))","completedAt":\(now),"imagePath":\(imagePath.map { "\"\($0)\"" } ?? "null"),"isLate":\(isLate),"serviceDate":"\(Date.serviceDate)"}
      """
      try enqueueOutbox(
        db,
        entityType: .completion,
        entitySyncId: syncId,
        operationType: .completionCreate,
        payload: payload
      )
    }
    notifySyncRequested()
  }

  public func listPendingSyncOperations(limit: Int = 50, now: Int64 = Date.nowMillis) throws -> [PendingSyncOperation] {
    try dbQueue.read { db in
      try PendingSyncRecord.fetchAll(
        db,
        sql: """
        SELECT * FROM SyncOutbox WHERE status = 'PENDING' AND nextAttemptAt <= ?
        ORDER BY createdAt ASC LIMIT ?
        """,
        arguments: [now, limit]
      ).map { $0.toDomain }
    }
  }

  public func repairPendingSyncQueue() throws {
    try dbQueue.write { db in
      let pendingActivities = try Row.fetchAll(
        db,
        sql: """
        SELECT * FROM Activity
        WHERE deletedAt IS NULL AND syncState = 'PENDING'
          AND syncId IS NOT NULL AND syncId != ''
        """
      )
      for row in pendingActivities {
        let syncId: String = row["syncId"]
        let pendingOutbox = try Int.fetchOne(
          db,
          sql: "SELECT COUNT(*) FROM SyncOutbox WHERE entitySyncId = ? AND status = 'PENDING'",
          arguments: [syncId]
        ) ?? 0
        guard pendingOutbox == 0 else { continue }
        let name: String = row["name"]
        let area: String = row["area"]
        let frequency: String = row["frequency"]
        let effort = row["effort"] as Int64? ?? 1
        let serverRevision = row["serverRevision"] as Int64? ?? 0
        let deletedAt = row["deletedAt"] as Int64?
        if deletedAt != nil {
          let payload = """
          {"syncId":"\(syncId)","name":"\(name)","area":"\(area)","frequency":"\(frequency)","effort":\(effort),"baseRevision":\(serverRevision),"deletedAt":\(deletedAt!)}
          """
          try enqueueOutbox(
            db,
            entityType: .activity,
            entitySyncId: syncId,
            operationType: .activityDelete,
            payload: payload
          )
        } else {
          let payload = """
          {"syncId":"\(syncId)","name":"\(name)","area":"\(area)","frequency":"\(frequency)","effort":\(effort),"baseRevision":\(serverRevision)}
          """
          try enqueueOutbox(
            db,
            entityType: .activity,
            entitySyncId: syncId,
            operationType: .activityUpsert,
            payload: payload
          )
        }
      }
    }
  }

  public func acknowledgeSyncOperation(_ ack: SyncAcknowledgement) throws {
    try dbQueue.write { db in
      let row = try Row.fetchOne(
        db,
        sql: "SELECT entityType, entitySyncId, operationType FROM SyncOutbox WHERE operationId = ?",
        arguments: [ack.operationId]
      )
      switch ack.status {
      case .applied, .alreadyApplied:
        if let row {
          let entitySyncId: String = row["entitySyncId"]
          let opType = row["operationType"] as String
          if opType == SyncOperationType.activityUpsert.rawValue {
            try db.execute(
              sql: "UPDATE Activity SET serverRevision = ?, syncState = 'SYNCED' WHERE syncId = ?",
              arguments: [ack.serverRevision, entitySyncId]
            )
          } else if opType == SyncOperationType.activityDelete.rawValue {
            try db.execute(
              sql: "UPDATE Activity SET serverRevision = ?, syncState = 'SYNCED' WHERE syncId = ?",
              arguments: [ack.serverRevision, entitySyncId]
            )
          } else if opType == SyncOperationType.completionCreate.rawValue {
            try db.execute(
              sql: "UPDATE ActivityCompletion SET serverRevision = ?, syncState = 'SYNCED' WHERE syncId = ?",
              arguments: [ack.serverRevision, entitySyncId]
            )
          }
        }
        try db.execute(sql: "DELETE FROM SyncOutbox WHERE operationId = ?", arguments: [ack.operationId])
      case .conflict, .rejected:
        let now = Date.nowMillis
        try db.execute(
          sql: """
          UPDATE SyncOutbox SET attemptCount = attemptCount + 1, nextAttemptAt = ?, lastError = ?, status = 'PENDING'
          WHERE operationId = ?
          """,
          arguments: [now + 15 * 60 * 1000, ack.message ?? ack.status.rawValue, ack.operationId]
        )
      }
    }
  }

  public func markSyncOperationFailed(
    operationId: String,
    attemptCount: Int64,
    nextAttemptAt: Int64,
    error: String
  ) throws {
    try dbQueue.write { db in
      try db.execute(
        sql: """
        UPDATE SyncOutbox SET attemptCount = ?, nextAttemptAt = ?, lastError = ?, status = 'PENDING'
        WHERE operationId = ?
        """,
        arguments: [attemptCount, nextAttemptAt, error, operationId]
      )
    }
  }

  public func applyRemoteSync(_ response: SyncPullResponse) throws {
    try dbQueue.write { db in
      for activity in response.activities {
        if try Int64.fetchOne(
          db,
          sql: "SELECT id FROM Activity WHERE syncId = ?",
          arguments: [activity.syncId]
        ) != nil {
          try db.execute(
            sql: """
            UPDATE Activity SET name = ?, area = ?, frequency = ?, effort = ?, assigneeIds = ?, estimatedDurationMinutes = ?, executionPhase = ?, activeWeekdays = ?, recurrenceAnchorDate = ?, serverRevision = ?, syncState = 'SYNCED'
            WHERE syncId = ?
            """,
            arguments: [activity.name, activity.area, activity.frequency, activity.effort, activity.assigneeIds.joined(separator: ","), activity.estimatedDurationMinutes, activity.executionPhase, activity.activeWeekdays.joined(separator: ","), activity.recurrenceAnchorDate, activity.serverRevision, activity.syncId]
          )
        } else {
          try db.execute(
            sql: """
            INSERT INTO Activity(syncId, name, area, frequency, effort, assigneeIds, estimatedDurationMinutes, executionPhase, activeWeekdays, recurrenceAnchorDate, serverRevision, syncState)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SYNCED')
            """,
            arguments: [activity.syncId, activity.name, activity.area, activity.frequency, activity.effort, activity.assigneeIds.joined(separator: ","), activity.estimatedDurationMinutes, activity.executionPhase, activity.activeWeekdays.joined(separator: ","), activity.recurrenceAnchorDate, activity.serverRevision]
          )
        }
      }
      for completion in response.completions {
        try upsertRemoteCompletion(db, completion: completion)
      }
      for tombstone in response.tombstones where tombstone.entityType == .activity {
        try db.execute(
          sql: "UPDATE Activity SET deletedAt = ?, syncState = 'SYNCED' WHERE syncId = ?",
          arguments: [tombstone.deletedAt, tombstone.entityId]
        )
      }
      if let schedule = response.checklistSchedule,
         let data = try? JSONEncoder().encode(schedule),
         let value = String(data: data, encoding: .utf8) {
        try upsertMetadata(db, key: MetadataKey.checklistSchedule, value: value)
      }
    }
  }

  public func checklistSchedule() throws -> ChecklistSchedule {
    try dbQueue.read { db in
      guard let value = try selectMetadata(db, key: MetadataKey.checklistSchedule),
            let data = value.data(using: .utf8),
            let schedule = try? JSONDecoder().decode(ChecklistSchedule.self, from: data)
      else { return ChecklistSchedule() }
      return schedule
    }
  }

  private func upsertRemoteCompletion(_ db: Database, completion: RemoteCompletionRecord) throws {
    let existing = try Int64.fetchOne(
      db,
      sql: "SELECT id FROM ActivityCompletion WHERE syncId = ?",
      arguments: [completion.syncId]
    )
    guard let activityId = try Int64.fetchOne(
      db,
      sql: "SELECT id FROM Activity WHERE syncId = ?",
      arguments: [completion.activitySyncId]
    ) else { return }
    let localUserId = try resolveLocalUserId(db, remoteUserId: completion.userId)
    guard let localUserId else { return }
    if existing != nil {
      try db.execute(
        sql: "UPDATE ActivityCompletion SET serverRevision = ?, syncState = 'SYNCED' WHERE syncId = ?",
        arguments: [completion.serverRevision, completion.syncId]
      )
      return
    }
    try db.execute(
      sql: """
      INSERT INTO ActivityCompletion(syncId, activityId, userId, completedAt, imagePath, isLate, serviceDate, serverRevision, syncState)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SYNCED')
      """,
      arguments: [
        completion.syncId, activityId, localUserId, completion.completedAt,
        completion.imagePath, completion.isLate ? 1 : 0, completion.serviceDate, completion.serverRevision,
      ]
    )
  }

  private func resolveLocalUserId(_ db: Database, remoteUserId: String) throws -> Int64? {
    if let id = try Int64.fetchOne(
      db,
      sql: "SELECT id FROM User WHERE remoteId = ?",
      arguments: [remoteUserId]
    ) {
      return id
    }
    let sessionRemoteId = try selectMetadata(db, key: MetadataKey.remoteUserId)
    if remoteUserId == sessionRemoteId,
       let id = try Int64.fetchOne(
         db,
         sql: "SELECT id FROM User WHERE remoteId = ?",
         arguments: [remoteUserId]
       ) {
      return id
    }
    return nil
  }

  public func workClockEntries(userId: Int64, dayStart: Int64, dayEnd: Int64) throws -> [WorkClockEntry] {
    try dbQueue.read { db in
      try WorkClockRecord.fetchAll(
        db,
        sql: """
        SELECT * FROM WorkClockEntry WHERE userId = ? AND registeredAt >= ? AND registeredAt < ?
        ORDER BY registeredAt ASC
        """,
        arguments: [userId, dayStart, dayEnd]
      ).map { $0.toDomain }
    }
  }

  public func insertWorkClockEntry(_ entry: WorkClockEntry) throws -> Int64 {
    try dbQueue.write { db in
      try db.execute(
        sql: """
        INSERT INTO WorkClockEntry(userId, type, registeredAt, latitude, longitude, distanceFromWorkMeters, isLate, syncStatus)
        VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')
        """,
        arguments: [
          entry.userId, entry.type.rawValue, entry.registeredAt,
          entry.location.latitude, entry.location.longitude, entry.distanceFromWorkMeters,
          entry.isLate ? 1 : 0,
        ]
      )
      return db.lastInsertedRowID
    }
  }

  public func pendingWorkClockEntries(userId: Int64) throws -> [WorkClockEntry] {
    try dbQueue.read { db in
      try WorkClockRecord.fetchAll(
        db,
        sql: "SELECT * FROM WorkClockEntry WHERE userId = ? AND syncStatus = 'PENDING' ORDER BY registeredAt ASC",
        arguments: [userId]
      ).map { $0.toDomain }
    }
  }

  public func markWorkClockSynced(id: Int64, remoteId: String) throws {
    try dbQueue.write { db in
      try db.execute(
        sql: "UPDATE WorkClockEntry SET syncStatus = 'SYNCED', remoteId = ? WHERE id = ?",
        arguments: [remoteId, id]
      )
    }
  }

  public func inventoryDrafts(administrative: Bool) throws -> [InventoryCountDraft] {
    try dbQueue.read { db in
      try InventoryDraftRecord.fetchAll(
        db,
        sql: "SELECT * FROM InventoryCountDraft WHERE isAdministrative = ? ORDER BY id ASC",
        arguments: [administrative ? 1 : 0]
      ).map { $0.toDomain }
    }
  }

  public func addInventoryDraft(_ draft: InventoryCountDraft, administrative: Bool) throws {
    try dbQueue.write { db in
      try db.execute(
        sql: """
        INSERT INTO InventoryCountDraft(name, quantity, category, volume, volumeUnit, salePriceInCents,
          costPriceInCents, storageCondition, createdAt, isAdministrative)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        arguments: [
          draft.name, draft.quantity, draft.category.rawValue, draft.volume, draft.volumeUnit,
          draft.salePriceInCents, draft.costPriceInCents, draft.storageCondition.rawValue,
          Date.nowMillis, administrative ? 1 : 0,
        ]
      )
    }
  }

  public func clearInventoryDrafts(administrative: Bool) throws {
    try dbQueue.write { db in
      try db.execute(
        sql: "DELETE FROM InventoryCountDraft WHERE isAdministrative = ?",
        arguments: [administrative ? 1 : 0]
      )
    }
  }

  public func deleteInventoryDraft(id: Int64) throws {
    try dbQueue.write { db in
      try db.execute(sql: "DELETE FROM InventoryCountDraft WHERE id = ?", arguments: [id])
    }
  }

  public func updateInventoryDraft(_ draft: InventoryCountDraft) throws {
    try dbQueue.write { db in
      try db.execute(
        sql: """
        UPDATE InventoryCountDraft
        SET name = ?, quantity = ?, category = ?, volume = ?, volumeUnit = ?,
            salePriceInCents = ?, costPriceInCents = ?, storageCondition = ?
        WHERE id = ?
        """,
        arguments: [
          draft.name, draft.quantity, draft.category.rawValue, draft.volume, draft.volumeUnit,
          draft.salePriceInCents, draft.costPriceInCents, draft.storageCondition.rawValue, draft.id,
        ]
      )
    }
  }

  public func allActivities() throws -> [Activity] {
    try dbQueue.read { db in
      try ActivityRecord.fetchAll(db, sql: "SELECT * FROM Activity WHERE deletedAt IS NULL").map { $0.toDomain() }
    }
  }

  public func insertActivity(_ activity: Activity) throws {
    let syncId = activity.syncId ?? newSyncId(prefix: "activity")
    let payload = """
    {"syncId":"\(syncId)","name":"\(activity.name)","area":"\(activity.area.rawValue)","frequency":"\(activity.frequency.rawValue)","effort":\(activity.effort),"assigneeIds":[\(activity.assigneeIds.map { "\"\($0)\"" }.joined(separator: ","))],"estimatedDurationMinutes":\(activity.estimatedDurationMinutes),"executionPhase":"\(activity.executionPhase.rawValue)","activeWeekdays":[\(activity.activeWeekdays.map { "\"\($0)\"" }.joined(separator: ","))],"recurrenceAnchorDate":\(activity.recurrenceAnchorDate.map { "\"\($0)\"" } ?? "null"),"baseRevision":0}
    """
    try dbQueue.write { db in
      try db.execute(
        sql: "INSERT INTO Activity(syncId, name, area, frequency, effort, assigneeIds, estimatedDurationMinutes, executionPhase, activeWeekdays, recurrenceAnchorDate, syncState) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')",
        arguments: [syncId, activity.name, activity.area.rawValue, activity.frequency.rawValue, activity.effort, activity.assigneeIds.joined(separator: ","), activity.estimatedDurationMinutes, activity.executionPhase.rawValue, activity.activeWeekdays.joined(separator: ","), activity.recurrenceAnchorDate]
      )
      try enqueueOutbox(
        db,
        entityType: .activity,
        entitySyncId: syncId,
        operationType: .activityUpsert,
        payload: payload
      )
    }
    notifySyncRequested()
  }

  public func updateActivity(id: Int64, name: String, area: Area, frequency: Frequency) throws {
    try dbQueue.write { db in
      guard let row = try Row.fetchOne(db, sql: "SELECT * FROM Activity WHERE id = ? AND deletedAt IS NULL", arguments: [id]) else {
        return
      }
      let syncId = (row["syncId"] as String?) ?? newSyncId(prefix: "activity")
      if row["syncId"] == nil {
        try db.execute(sql: "UPDATE Activity SET syncId = ? WHERE id = ?", arguments: [syncId, id])
      }
      let serverRevision = row["serverRevision"] as Int64? ?? 0
      let effort = row["effort"] as Int64? ?? 1
      try db.execute(
        sql: """
        UPDATE Activity SET name = ?, area = ?, frequency = ?, syncState = 'PENDING'
        WHERE id = ? AND deletedAt IS NULL
        """,
        arguments: [name, area.rawValue, frequency.rawValue, id]
      )
      let payload = """
      {"syncId":"\(syncId)","name":"\(name)","area":"\(area.rawValue)","frequency":"\(frequency.rawValue)","effort":\(effort),"baseRevision":\(serverRevision)}
      """
      try replacePendingActivityUpsert(db, syncId: syncId)
      try enqueueOutbox(
        db,
        entityType: .activity,
        entitySyncId: syncId,
        operationType: .activityUpsert,
        payload: payload
      )
    }
    notifySyncRequested()
  }

  public func deleteActivity(id: Int64) throws {
    let now = Date.nowMillis
    try dbQueue.write { db in
      guard let row = try Row.fetchOne(db, sql: "SELECT * FROM Activity WHERE id = ?", arguments: [id]) else {
        return
      }
      let syncId = row["syncId"] as String?
      let serverRevision = row["serverRevision"] as Int64? ?? 0
      guard let syncId, !syncId.isEmpty else {
        try db.execute(sql: "DELETE FROM Activity WHERE id = ?", arguments: [id])
        return
      }
      let pendingCount = try Int.fetchOne(
        db,
        sql: """
        SELECT COUNT(*) FROM SyncOutbox
        WHERE entityType = ? AND entitySyncId = ? AND operationType = ?
        """,
        arguments: [SyncEntityType.activity.rawValue, syncId, SyncOperationType.activityUpsert.rawValue]
      ) ?? 0
      let canDropLocalCreate = serverRevision == 0 && pendingCount > 0
      if canDropLocalCreate {
        try db.execute(sql: "DELETE FROM SyncOutbox WHERE entitySyncId = ?", arguments: [syncId])
        try db.execute(sql: "DELETE FROM ActivityCompletion WHERE activityId = ?", arguments: [id])
        try db.execute(sql: "DELETE FROM Activity WHERE id = ?", arguments: [id])
        return
      }
      try db.execute(
        sql: "UPDATE Activity SET deletedAt = ?, syncState = 'PENDING' WHERE id = ?",
        arguments: [now, id]
      )
      let payload = """
      {"syncId":"\(syncId)","name":"\(row["name"] as String? ?? "")","area":"\(row["area"] as String? ?? "")","frequency":"\(row["frequency"] as String? ?? "")","effort":\(row["effort"] as Int64? ?? 1),"baseRevision":\(serverRevision),"deletedAt":\(now)}
      """
      try replacePendingActivityUpsert(db, syncId: syncId)
      try enqueueOutbox(
        db,
        entityType: .activity,
        entitySyncId: syncId,
        operationType: .activityDelete,
        payload: payload
      )
    }
    notifySyncRequested()
  }

  public func updateUserPermissions(userId: Int64, permissions: FeaturePermissions) throws {
    try dbQueue.write { db in
      try db.execute(
        sql: """
        UPDATE User SET canRegisterUsers = ?, canCreateActivities = ?, canEditUsers = ?,
          canCreateInventoryCounts = ?, canViewInventoryInsights = ?, canManageAdministrativeStock = ?
        WHERE id = ?
        """,
        arguments: [
          permissions.canRegisterUsers, permissions.canCreateActivities, permissions.canEditUsers,
          permissions.canCreateInventoryCounts, permissions.canViewInventoryInsights,
          permissions.canManageAdministrativeStock, userId,
        ]
      )
    }
  }

  public func allUsers() throws -> [User] {
    try dbQueue.read { db in
      try UserRecord.fetchAll(db, sql: "SELECT * FROM User").map { $0.toDomain() }
    }
  }

  private func activitySyncId(_ db: Database, activityId: Int64) throws -> String {
    if let syncId: String = try String.fetchOne(db, sql: "SELECT syncId FROM Activity WHERE id = ?", arguments: [activityId]),
       !syncId.isEmpty {
      return syncId
    }
    let syncId = UUID().uuidString
    try db.execute(sql: "UPDATE Activity SET syncId = ? WHERE id = ?", arguments: [syncId, activityId])
    return syncId
  }

  private func enqueueOutbox(
    _ db: Database,
    entityType: SyncEntityType,
    entitySyncId: String,
    operationType: SyncOperationType,
    payload: String
  ) throws {
    let now = Date.nowMillis
    try db.execute(
      sql: """
      INSERT INTO SyncOutbox(operationId, entityType, entitySyncId, operationType, payload, createdAt, nextAttemptAt)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      """,
      arguments: [UUID().uuidString, entityType.rawValue, entitySyncId, operationType.rawValue, payload, now, now]
    )
  }

  private func upsertMetadata(_ db: Database, key: String, value: String) throws {
    try db.execute(sql: "INSERT OR REPLACE INTO SyncMetadata(key, value) VALUES (?, ?)", arguments: [key, value])
  }

  private func selectMetadata(_ db: Database, key: String) throws -> String? {
    try String.fetchOne(db, sql: "SELECT value FROM SyncMetadata WHERE key = ?", arguments: [key])
  }
}

private final class SyncCallbackBox: @unchecked Sendable {
  var handler: (@Sendable () -> Void)?
}

  private func replacePendingActivityUpsert(_ db: Database, syncId: String) throws {
    try db.execute(
      sql: """
      DELETE FROM SyncOutbox
      WHERE entityType = ? AND entitySyncId = ? AND operationType = ?
      """,
      arguments: [SyncEntityType.activity.rawValue, syncId, SyncOperationType.activityUpsert.rawValue]
    )
  }

  private func newSyncId(prefix: String) -> String {
    "\(prefix)-\(Date.nowMillis)-\(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(16))"
  }

  private enum MetadataKey {
    static let authToken = "auth_token"
    static let remoteUserId = "remote_user_id"
    static let syncCursor = "sync_cursor"
    static let seedPurged = "seed_purged"
    static let worksiteName = "worksite_name"
    static let worksiteLatitude = "worksite_latitude"
    static let worksiteLongitude = "worksite_longitude"
    static let worksiteRadius = "worksite_radius"
    static let checklistSchedule = "checklist_schedule"
  }

extension Date {
  public static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
  public static var startOfDayMillis: Int64 {
    let calendar = Calendar.current
    let start = calendar.startOfDay(for: Date())
    return Int64(start.timeIntervalSince1970 * 1000)
  }
  public static var serviceDate: String {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(identifier: "America/Fortaleza")
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: Date())
  }
}
