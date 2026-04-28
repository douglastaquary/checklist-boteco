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
      get() = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
      driver.execute(null, """
          |CREATE TABLE User (
          |    id INTEGER PRIMARY KEY AUTOINCREMENT,
          |    name TEXT NOT NULL,
          |    password TEXT NOT NULL,
          |    area TEXT NOT NULL,
          |    permissionLevel TEXT NOT NULL,
          |    allowedAreas TEXT NOT NULL
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
      return QueryResult.Unit
    }

    override fun migrate(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
      vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
  }
}
