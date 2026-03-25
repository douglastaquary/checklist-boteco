package com.checklistboteco.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jvm.sqlite.JdbcSqliteDriver
import com.checklistboteco.database.ChecklistDatabase
import java.io.File

class DesktopDatabaseDriverFactory : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver {
        val dbPath = File(System.getProperty("user.home"), ".checklistboteco/checklist.db")
        dbPath.parentFile?.mkdirs()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbPath.absolutePath}")
        ChecklistDatabase.Schema.create(driver)
        return driver
    }
}
