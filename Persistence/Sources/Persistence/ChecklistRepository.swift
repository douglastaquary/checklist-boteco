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
          canViewInventoryInsights, canManageAdministrativeStock)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 1, 1, 1, 1)
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
          canViewInventoryInsights, canManageAdministrativeStock)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, 0)
        """,
        arguments: [
          "Colaborador", "colaborador@checklistboteco.com", "colab123", Area.atendimento.rawValue,
          WorkSector.atendimento.rawValue, PermissionLevel.user.rawValue,
          Area.atendimento.rawValue, Date.nowMillis,
        ]
      )
      let activities: [(String, Area, Frequency)] = [
        ("Abrir caixa", .atendimento, .daily),
        ("Conferir estoque geladeira", .estoque, .daily),
        ("Limpar balcão", .limpeza, .daily),
        ("Preparar mise en place", .cozinha, .daily),
      ]
      for (name, area, frequency) in activities {
        try db.execute(
          sql: "INSERT INTO Activity(syncId, name, area, frequency, effort) VALUES (?, ?, ?, ?, 1)",
          arguments: [UUID().uuidString, name, area.rawValue, frequency.rawValue]
        )
      }
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
          canViewInventoryInsights, canManageAdministrativeStock)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        arguments: [
          user.name, user.email, user.password, user.area.rawValue, user.workSector.rawValue,
          user.permissionLevel.rawValue, user.allowedAreas.map(\.rawValue).joined(separator: ","),
          user.createdAt, user.remoteId,
          user.featurePermissions.canRegisterUsers, user.featurePermissions.canCreateActivities,
          user.featurePermissions.canEditUsers, user.featurePermissions.canCreateInventoryCounts,
          user.featurePermissions.canViewInventoryInsights, user.featurePermissions.canManageAdministrativeStock,
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
        featurePermissions: user.featurePermissions
      )
    }
  }

  public func syncLocalUserFromRemote(localUserId: Int64, remoteUser: User) throws -> User {
    try dbQueue.write { db in
      try db.execute(
        sql: """
        UPDATE User SET name = ?, email = ?, area = ?, workSector = ?, permissionLevel = ?, allowedAreas = ?,
          remoteId = ?, canRegisterUsers = ?, canCreateActivities = ?, canEditUsers = ?,
          canCreateInventoryCounts = ?, canViewInventoryInsights = ?, canManageAdministrativeStock = ?
        WHERE id = ?
        """,
        arguments: [
          remoteUser.name, remoteUser.email, remoteUser.area.rawValue, remoteUser.workSector.rawValue,
          remoteUser.permissionLevel.rawValue, remoteUser.allowedAreas.map(\.rawValue).joined(separator: ","),
          remoteUser.remoteId,
          remoteUser.featurePermissions.canRegisterUsers, remoteUser.featurePermissions.canCreateActivities,
          remoteUser.featurePermissions.canEditUsers, remoteUser.featurePermissions.canCreateInventoryCounts,
          remoteUser.featurePermissions.canViewInventoryInsights, remoteUser.featurePermissions.canManageAdministrativeStock,
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
        INSERT INTO ActivityCompletion(syncId, activityId, userId, completedAt, imagePath, isLate)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        arguments: [syncId, activityId, userId, now, imagePath, isLate ? 1 : 0]
      )
      let payload = """
      {"activitySyncId":"\(try activitySyncId(db, activityId: activityId))","completedAt":\(now),"imagePath":\(imagePath.map { "\"\($0)\"" } ?? "null"),"isLate":\(isLate)}
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

  public func acknowledgeSyncOperation(_ ack: SyncAcknowledgement) throws {
    try dbQueue.write { db in
      try db.execute(sql: "DELETE FROM SyncOutbox WHERE operationId = ?", arguments: [ack.operationId])
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
            UPDATE Activity SET name = ?, area = ?, frequency = ?, effort = ?, serverRevision = ?, syncState = 'SYNCED'
            WHERE syncId = ?
            """,
            arguments: [activity.name, activity.area, activity.frequency, activity.effort, activity.serverRevision, activity.syncId]
          )
        } else {
          try db.execute(
            sql: """
            INSERT INTO Activity(syncId, name, area, frequency, effort, serverRevision, syncState)
            VALUES (?, ?, ?, ?, ?, ?, 'SYNCED')
            """,
            arguments: [activity.syncId, activity.name, activity.area, activity.frequency, activity.effort, activity.serverRevision]
          )
        }
      }
      for tombstone in response.tombstones where tombstone.entityType == .activity {
        try db.execute(
          sql: "UPDATE Activity SET deletedAt = ?, syncState = 'SYNCED' WHERE syncId = ?",
          arguments: [tombstone.deletedAt, tombstone.entityId]
        )
      }
    }
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

  public func allActivities() throws -> [Activity] {
    try dbQueue.read { db in
      try ActivityRecord.fetchAll(db, sql: "SELECT * FROM Activity WHERE deletedAt IS NULL").map { $0.toDomain() }
    }
  }

  public func insertActivity(_ activity: Activity) throws {
    let syncId = activity.syncId ?? UUID().uuidString
    try dbQueue.write { db in
      try db.execute(
        sql: "INSERT INTO Activity(syncId, name, area, frequency, effort) VALUES (?, ?, ?, ?, ?)",
        arguments: [syncId, activity.name, activity.area.rawValue, activity.frequency.rawValue, activity.effort]
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

private enum MetadataKey {
  static let authToken = "auth_token"
  static let remoteUserId = "remote_user_id"
  static let syncCursor = "sync_cursor"
}

extension Date {
  public static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
  public static var startOfDayMillis: Int64 {
    let calendar = Calendar.current
    let start = calendar.startOfDay(for: Date())
    return Int64(start.timeIntervalSince1970 * 1000)
  }
}
