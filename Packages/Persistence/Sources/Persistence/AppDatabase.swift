import Foundation
import GRDB

enum DatabaseSchema {
  static let createSQL = """
  CREATE TABLE IF NOT EXISTS User (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    email TEXT NOT NULL DEFAULT '',
    password TEXT NOT NULL,
    area TEXT NOT NULL,
    workSector TEXT NOT NULL DEFAULT 'ATENDIMENTO',
    permissionLevel TEXT NOT NULL,
    allowedAreas TEXT NOT NULL,
    createdAt INTEGER NOT NULL DEFAULT 0,
    remoteId TEXT,
    canRegisterUsers INTEGER NOT NULL DEFAULT 0,
    canCreateActivities INTEGER NOT NULL DEFAULT 0,
    canEditUsers INTEGER NOT NULL DEFAULT 0,
    canCreateInventoryCounts INTEGER NOT NULL DEFAULT 0,
    canViewInventoryInsights INTEGER NOT NULL DEFAULT 0,
    canManageAdministrativeStock INTEGER NOT NULL DEFAULT 0,
    canImportPurchases INTEGER NOT NULL DEFAULT 0,
    mustChangePassword INTEGER NOT NULL DEFAULT 0
  );
  CREATE TABLE IF NOT EXISTS Activity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    syncId TEXT,
    name TEXT NOT NULL,
    area TEXT NOT NULL,
    frequency TEXT NOT NULL,
    effort INTEGER NOT NULL DEFAULT 1,
    assigneeIds TEXT NOT NULL DEFAULT '',
    estimatedDurationMinutes INTEGER NOT NULL DEFAULT 15,
    executionPhase TEXT NOT NULL DEFAULT 'BEFORE_LUNCH',
    activeWeekdays TEXT NOT NULL DEFAULT 'TUESDAY,FRIDAY,SATURDAY,SUNDAY',
    recurrenceAnchorDate TEXT,
    serverRevision INTEGER NOT NULL DEFAULT 0,
    syncState TEXT NOT NULL DEFAULT 'SYNCED',
    deletedAt INTEGER
  );
  CREATE TABLE IF NOT EXISTS ActivityCompletion (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    syncId TEXT,
    activityId INTEGER NOT NULL,
    userId INTEGER NOT NULL,
    completedAt INTEGER NOT NULL,
    imagePath TEXT,
    isLate INTEGER NOT NULL DEFAULT 0,
    serviceDate TEXT NOT NULL DEFAULT '',
    serverRevision INTEGER NOT NULL DEFAULT 0,
    syncState TEXT NOT NULL DEFAULT 'SYNCED'
  );
  CREATE TABLE IF NOT EXISTS SyncOutbox (
    operationId TEXT PRIMARY KEY,
    entityType TEXT NOT NULL,
    entitySyncId TEXT NOT NULL,
    operationType TEXT NOT NULL,
    payload TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    attemptCount INTEGER NOT NULL DEFAULT 0,
    nextAttemptAt INTEGER NOT NULL,
    lastError TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING'
  );
  CREATE TABLE IF NOT EXISTS SyncMetadata (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
  );
  CREATE TABLE IF NOT EXISTS WorkClockEntry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    userId INTEGER NOT NULL,
    type TEXT NOT NULL,
    registeredAt INTEGER NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    distanceFromWorkMeters REAL NOT NULL,
    isLate INTEGER NOT NULL DEFAULT 0,
    syncStatus TEXT NOT NULL DEFAULT 'PENDING',
    remoteId TEXT
  );
  CREATE TABLE IF NOT EXISTS InventoryCountDraft (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    quantity REAL NOT NULL,
    category TEXT NOT NULL,
    volume REAL NOT NULL,
    volumeUnit TEXT NOT NULL,
    salePriceInCents INTEGER NOT NULL,
    costPriceInCents INTEGER,
    storageCondition TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    isAdministrative INTEGER NOT NULL DEFAULT 0
  );
  """
}

public enum AppDatabase {
  public static func open(path: String? = nil) throws -> DatabaseQueue {
    let dbPath: String
    if let path {
      dbPath = path
    } else {
      let support = try FileManager.default.url(
        for: .applicationSupportDirectory,
        in: .userDomainMask,
        appropriateFor: nil,
        create: true
      )
      dbPath = support.appendingPathComponent("checklist.sqlite").path
    }
    let queue = try DatabaseQueue(path: dbPath)
    try migrator.migrate(queue)
    return queue
  }

  public static func inMemory() throws -> DatabaseQueue {
    let queue = try DatabaseQueue()
    try migrator.migrate(queue)
    return queue
  }

  private static var migrator: DatabaseMigrator {
    var migrator = DatabaseMigrator()
    migrator.registerMigration("v1") { db in
      try db.execute(sql: DatabaseSchema.createSQL)
    }
    migrator.registerMigration("v2-checklist-timing") { db in
      let activityColumns = try db.columns(in: "Activity").map(\.name)
      if !activityColumns.contains("assigneeIds") { try db.execute(sql: "ALTER TABLE Activity ADD COLUMN assigneeIds TEXT NOT NULL DEFAULT ''") }
      if !activityColumns.contains("estimatedDurationMinutes") { try db.execute(sql: "ALTER TABLE Activity ADD COLUMN estimatedDurationMinutes INTEGER NOT NULL DEFAULT 15") }
      if !activityColumns.contains("executionPhase") { try db.execute(sql: "ALTER TABLE Activity ADD COLUMN executionPhase TEXT NOT NULL DEFAULT 'BEFORE_LUNCH'") }
      if !activityColumns.contains("activeWeekdays") { try db.execute(sql: "ALTER TABLE Activity ADD COLUMN activeWeekdays TEXT NOT NULL DEFAULT 'TUESDAY,FRIDAY,SATURDAY,SUNDAY'") }
      if !activityColumns.contains("recurrenceAnchorDate") { try db.execute(sql: "ALTER TABLE Activity ADD COLUMN recurrenceAnchorDate TEXT") }
      let completionColumns = try db.columns(in: "ActivityCompletion").map(\.name)
      if !completionColumns.contains("serviceDate") { try db.execute(sql: "ALTER TABLE ActivityCompletion ADD COLUMN serviceDate TEXT NOT NULL DEFAULT ''") }
    }
    migrator.registerMigration("v3-user-password-state") { db in
      let userColumns = try db.columns(in: "User").map(\.name)
      if !userColumns.contains("mustChangePassword") {
        try db.execute(sql: "ALTER TABLE User ADD COLUMN mustChangePassword INTEGER NOT NULL DEFAULT 0")
      }
    }
    migrator.registerMigration("v4-can-import-purchases") { db in
      let userColumns = try db.columns(in: "User").map(\.name)
      if !userColumns.contains("canImportPurchases") {
        try db.execute(sql: "ALTER TABLE User ADD COLUMN canImportPurchases INTEGER NOT NULL DEFAULT 0")
      }
    }
    return migrator
  }
}
