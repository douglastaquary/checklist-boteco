package com.checklistboteco.database

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.checklistboteco.database.composeApp.newInstance
import com.checklistboteco.database.composeApp.schema
import kotlin.Unit

public interface ChecklistDatabase : Transacter {
  public val checklistDatabaseQueries: ChecklistDatabaseQueries

  public companion object {
    public val Schema: SqlSchema<QueryResult.Value<Unit>>
      get() = ChecklistDatabase::class.schema

    public operator fun invoke(driver: SqlDriver): ChecklistDatabase =
        ChecklistDatabase::class.newInstance(driver)
  }
}
