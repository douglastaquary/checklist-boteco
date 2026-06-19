package com.checklistboteco.database.composeApp

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.checklistboteco.database.ChecklistDatabase
import com.checklistboteco.database.ChecklistDatabaseQueries
import kotlin.Long
import kotlin.Unit
import kotlin.reflect.KClass

internal val KClass<ChecklistDatabase>.schema: SqlSchema<QueryResult.Value<Unit>>
  get() = ChecklistDatabaseImpl.Schema

internal fun KClass<ChecklistDatabase>.newInstance(driver: SqlDriver): ChecklistDatabase =
    ChecklistDatabaseImpl(driver)

private class ChecklistDatabaseImpl(
  driver: SqlDriver,
) : TransacterImpl(driver), ChecklistDatabase {
  override val checklistDatabaseQueries: ChecklistDatabaseQueries = ChecklistDatabaseQueries(driver)

  public object Schema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
      get() = 4

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
      driver.execute(null, """
          |CREATE TABLE User (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    name TEXT NOT NULL,
          |    email TEXT NOT NULL DEFAULT '',
          |    password TEXT NOT NULL,
          |    area TEXT NOT NULL,
          |    workSector TEXT NOT NULL DEFAULT 'ATENDIMENTO',
          |    permissionLevel TEXT NOT NULL,
          |    allowedAreas TEXT NOT NULL,
          |    createdAt INTEGER NOT NULL DEFAULT 0,
          |    remoteId TEXT,
          |    canRegisterUsers INTEGER NOT NULL DEFAULT 0,
          |    canCreateActivities INTEGER NOT NULL DEFAULT 0,
          |    canEditUsers INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE Activity (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    syncId TEXT,
          |    name TEXT NOT NULL,
          |    area TEXT NOT NULL,
          |    frequency TEXT NOT NULL,
          |    effort INTEGER NOT NULL DEFAULT 1,
          |    serverRevision INTEGER NOT NULL DEFAULT 0,
          |    syncState TEXT NOT NULL DEFAULT 'SYNCED',
          |    deletedAt INTEGER
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE ActivityCompletion (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    syncId TEXT,
          |    activityId INTEGER NOT NULL,
          |    userId INTEGER NOT NULL,
          |    completedAt INTEGER NOT NULL,
          |    imagePath TEXT,
          |    isLate INTEGER NOT NULL DEFAULT 0,
          |    serverRevision INTEGER NOT NULL DEFAULT 0,
          |    syncState TEXT NOT NULL DEFAULT 'SYNCED',
          |    FOREIGN KEY (activityId) REFERENCES Activity(id),
          |    FOREIGN KEY (userId) REFERENCES User(id)
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE SyncOutbox (
          |    operationId TEXT PRIMARY KEY,
          |    entityType TEXT NOT NULL,
          |    entitySyncId TEXT NOT NULL,
          |    operationType TEXT NOT NULL,
          |    payload TEXT NOT NULL,
          |    createdAt INTEGER NOT NULL,
          |    attemptCount INTEGER NOT NULL DEFAULT 0,
          |    nextAttemptAt INTEGER NOT NULL,
          |    lastError TEXT,
          |    status TEXT NOT NULL DEFAULT 'PENDING'
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE SyncMetadata (
          |    key TEXT PRIMARY KEY,
          |    value TEXT NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE WorkClockEntry (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    userId INTEGER NOT NULL,
          |    type TEXT NOT NULL,
          |    registeredAt INTEGER NOT NULL,
          |    latitude REAL NOT NULL,
          |    longitude REAL NOT NULL,
          |    distanceFromWorkMeters REAL NOT NULL,
          |    isLate INTEGER NOT NULL DEFAULT 0,
          |    FOREIGN KEY (userId) REFERENCES User(id)
          |)
          """.trimMargin(), 0)
      return QueryResult.Unit
    }

    private fun migrateInternal(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
    ): QueryResult.Value<Unit> {
      if (oldVersion <= 1 && newVersion > 1) {
        driver.execute(null, "ALTER TABLE User ADD COLUMN email TEXT NOT NULL DEFAULT ''", 0)
        driver.execute(null,
            "ALTER TABLE User ADD COLUMN workSector TEXT NOT NULL DEFAULT 'ATENDIMENTO'", 0)
        driver.execute(null, "ALTER TABLE User ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0", 0)
        driver.execute(null,
            "ALTER TABLE User ADD COLUMN canRegisterUsers INTEGER NOT NULL DEFAULT 0", 0)
        driver.execute(null,
            "ALTER TABLE User ADD COLUMN canCreateActivities INTEGER NOT NULL DEFAULT 0", 0)
        driver.execute(null, "ALTER TABLE User ADD COLUMN canEditUsers INTEGER NOT NULL DEFAULT 0",
            0)
      }
      if (oldVersion <= 2 && newVersion > 2) {
        driver.execute(null, """
            |CREATE TABLE WorkClockEntry (
            |    id INTEGER PRIMARY KEY AUTOINCREMENT,
            |    userId INTEGER NOT NULL,
            |    type TEXT NOT NULL,
            |    registeredAt INTEGER NOT NULL,
            |    latitude REAL NOT NULL,
            |    longitude REAL NOT NULL,
            |    distanceFromWorkMeters REAL NOT NULL,
            |    isLate INTEGER NOT NULL DEFAULT 0,
            |    FOREIGN KEY (userId) REFERENCES User(id)
            |)
            """.trimMargin(), 0)
      }
      if (oldVersion <= 3 && newVersion > 3) {
        driver.execute(null, "ALTER TABLE User ADD COLUMN remoteId TEXT", 0)
        driver.execute(null, "ALTER TABLE Activity ADD COLUMN syncId TEXT", 0)
        driver.execute(null,
            "ALTER TABLE Activity ADD COLUMN serverRevision INTEGER NOT NULL DEFAULT 0", 0)
        driver.execute(null,
            "ALTER TABLE Activity ADD COLUMN syncState TEXT NOT NULL DEFAULT 'SYNCED'", 0)
        driver.execute(null, "ALTER TABLE Activity ADD COLUMN deletedAt INTEGER", 0)
        driver.execute(null, "ALTER TABLE ActivityCompletion ADD COLUMN syncId TEXT", 0)
        driver.execute(null,
            "ALTER TABLE ActivityCompletion ADD COLUMN serverRevision INTEGER NOT NULL DEFAULT 0",
            0)
        driver.execute(null,
            "ALTER TABLE ActivityCompletion ADD COLUMN syncState TEXT NOT NULL DEFAULT 'SYNCED'", 0)
        driver.execute(null, """
            |CREATE TABLE SyncOutbox (
            |    operationId TEXT PRIMARY KEY,
            |    entityType TEXT NOT NULL,
            |    entitySyncId TEXT NOT NULL,
            |    operationType TEXT NOT NULL,
            |    payload TEXT NOT NULL,
            |    createdAt INTEGER NOT NULL,
            |    attemptCount INTEGER NOT NULL DEFAULT 0,
            |    nextAttemptAt INTEGER NOT NULL,
            |    lastError TEXT,
            |    status TEXT NOT NULL DEFAULT 'PENDING'
            |)
            """.trimMargin(), 0)
        driver.execute(null, """
            |CREATE TABLE SyncMetadata (
            |    key TEXT PRIMARY KEY,
            |    value TEXT NOT NULL
            |)
            """.trimMargin(), 0)
      }
      return QueryResult.Unit
    }

    override fun migrate(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
      vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> {
      var lastVersion = oldVersion

      callbacks.filter { it.afterVersion in oldVersion until newVersion }
      .sortedBy { it.afterVersion }
      .forEach { callback ->
        migrateInternal(driver, oldVersion = lastVersion, newVersion = callback.afterVersion + 1)
        callback.block(driver)
        lastVersion = callback.afterVersion + 1
      }

      if (lastVersion < newVersion) {
        migrateInternal(driver, lastVersion, newVersion)
      }
      return QueryResult.Unit
    }
  }
}
