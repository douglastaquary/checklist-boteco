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
      get() = 3

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
          |    canRegisterUsers INTEGER NOT NULL DEFAULT 0,
          |    canCreateActivities INTEGER NOT NULL DEFAULT 0,
          |    canEditUsers INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE Activity (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    name TEXT NOT NULL,
          |    area TEXT NOT NULL,
          |    frequency TEXT NOT NULL,
          |    effort INTEGER NOT NULL DEFAULT 1
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE ActivityCompletion (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    activityId INTEGER NOT NULL,
          |    userId INTEGER NOT NULL,
          |    completedAt INTEGER NOT NULL,
          |    imagePath TEXT,
          |    isLate INTEGER NOT NULL DEFAULT 0,
          |    FOREIGN KEY (activityId) REFERENCES Activity(id),
          |    FOREIGN KEY (userId) REFERENCES User(id)
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
