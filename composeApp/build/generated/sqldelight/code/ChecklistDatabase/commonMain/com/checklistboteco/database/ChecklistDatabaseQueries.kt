package com.checklistboteco.database

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class ChecklistDatabaseQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAllUsers(mapper: (
    id: Long,
    name: String,
    password: String,
    area: String,
    permissionLevel: String,
    allowedAreas: String,
  ) -> T): Query<T> = Query(-1_301_143_170, arrayOf("User"), driver, "ChecklistDatabase.sq",
      "selectAllUsers",
      "SELECT User.id, User.name, User.password, User.area, User.permissionLevel, User.allowedAreas FROM User") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!
    )
  }

  public fun selectAllUsers(): Query<User> = selectAllUsers { id, name, password, area,
      permissionLevel, allowedAreas ->
    User(
      id,
      name,
      password,
      area,
      permissionLevel,
      allowedAreas
    )
  }

  public fun <T : Any> selectUserByName(name: String, mapper: (
    id: Long,
    name: String,
    password: String,
    area: String,
    permissionLevel: String,
    allowedAreas: String,
  ) -> T): Query<T> = SelectUserByNameQuery(name) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!
    )
  }

  public fun selectUserByName(name: String): Query<User> = selectUserByName(name) { id, name_,
      password, area, permissionLevel, allowedAreas ->
    User(
      id,
      name_,
      password,
      area,
      permissionLevel,
      allowedAreas
    )
  }

  public fun <T : Any> selectAllActivities(mapper: (
    id: Long,
    name: String,
    area: String,
    frequency: String,
  ) -> T): Query<T> = Query(384_786_999, arrayOf("Activity"), driver, "ChecklistDatabase.sq",
      "selectAllActivities",
      "SELECT Activity.id, Activity.name, Activity.area, Activity.frequency FROM Activity") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!
    )
  }

  public fun selectAllActivities(): Query<Activity> = selectAllActivities { id, name, area,
      frequency ->
    Activity(
      id,
      name,
      area,
      frequency
    )
  }

  public fun <T : Any> selectActivitiesByArea(area: String, mapper: (
    id: Long,
    name: String,
    area: String,
    frequency: String,
  ) -> T): Query<T> = SelectActivitiesByAreaQuery(area) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!
    )
  }

  public fun selectActivitiesByArea(area: String): Query<Activity> = selectActivitiesByArea(area) {
      id, name, area_, frequency ->
    Activity(
      id,
      name,
      area_,
      frequency
    )
  }

  public fun <T : Any> selectCompletionsByActivityAndDate(
    activityId: Long,
    completedAt: Long,
    mapper: (
      id: Long,
      activityId: Long,
      userId: Long,
      completedAt: Long,
      imagePath: String?,
    ) -> T,
  ): Query<T> = SelectCompletionsByActivityAndDateQuery(activityId, completedAt) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getString(4)
    )
  }

  public fun selectCompletionsByActivityAndDate(activityId: Long, completedAt: Long):
      Query<ActivityCompletion> = selectCompletionsByActivityAndDate(activityId, completedAt) { id,
      activityId_, userId, completedAt_, imagePath ->
    ActivityCompletion(
      id,
      activityId_,
      userId,
      completedAt_,
      imagePath
    )
  }

  public fun <T : Any> selectCompletionsByAreaAndDate(
    area: String,
    completedAt: Long,
    mapper: (
      id: Long,
      activityId: Long,
      userId: Long,
      completedAt: Long,
      imagePath: String?,
    ) -> T,
  ): Query<T> = SelectCompletionsByAreaAndDateQuery(area, completedAt) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getString(4)
    )
  }

  public fun selectCompletionsByAreaAndDate(area: String, completedAt: Long):
      Query<ActivityCompletion> = selectCompletionsByAreaAndDate(area, completedAt) { id,
      activityId, userId, completedAt_, imagePath ->
    ActivityCompletion(
      id,
      activityId,
      userId,
      completedAt_,
      imagePath
    )
  }

  public fun <T : Any> countActivitiesByArea(mapper: (area: String, total: Long) -> T): Query<T> =
      Query(149_918_501, arrayOf("Activity"), driver, "ChecklistDatabase.sq",
      "countActivitiesByArea", "SELECT area, COUNT(*) AS total FROM Activity GROUP BY area") {
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

  public fun insertUser(
    name: String,
    password: String,
    area: String,
    permissionLevel: String,
    allowedAreas: String,
  ) {
    driver.execute(-240_167_137, """
        |INSERT INTO User(name, password, area, permissionLevel, allowedAreas)
        |VALUES (?, ?, ?, ?, ?)
        """.trimMargin(), 5) {
          bindString(0, name)
          bindString(1, password)
          bindString(2, area)
          bindString(3, permissionLevel)
          bindString(4, allowedAreas)
        }
    notifyQueries(-240_167_137) { emit ->
      emit("User")
    }
  }

  public fun insertActivity(
    name: String,
    area: String,
    frequency: String,
  ) {
    driver.execute(-80_309_149, """
        |INSERT INTO Activity(name, area, frequency)
        |VALUES (?, ?, ?)
        """.trimMargin(), 3) {
          bindString(0, name)
          bindString(1, area)
          bindString(2, frequency)
        }
    notifyQueries(-80_309_149) { emit ->
      emit("Activity")
    }
  }

  public fun insertCompletion(
    activityId: Long,
    userId: Long,
    completedAt: Long,
    imagePath: String?,
  ) {
    driver.execute(1_837_465_648, """
        |INSERT INTO ActivityCompletion(activityId, userId, completedAt, imagePath)
        |VALUES (?, ?, ?, ?)
        """.trimMargin(), 4) {
          bindLong(0, activityId)
          bindLong(1, userId)
          bindLong(2, completedAt)
          bindString(3, imagePath)
        }
    notifyQueries(1_837_465_648) { emit ->
      emit("ActivityCompletion")
    }
  }

  public fun updateActivity(
    name: String,
    area: String,
    frequency: String,
    id: Long,
  ) {
    driver.execute(-533_267_853, """
        |UPDATE Activity SET name = ?, area = ?, frequency = ?
        |WHERE id = ?
        """.trimMargin(), 4) {
          bindString(0, name)
          bindString(1, area)
          bindString(2, frequency)
          bindLong(3, id)
        }
    notifyQueries(-533_267_853) { emit ->
      emit("Activity")
    }
  }

  public fun deleteActivity(id: Long) {
    driver.execute(838_550_869, """DELETE FROM Activity WHERE id = ?""", 1) {
          bindLong(0, id)
        }
    notifyQueries(838_550_869) { emit ->
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
        """SELECT User.id, User.name, User.password, User.area, User.permissionLevel, User.allowedAreas FROM User WHERE name = ?""",
        mapper, 1) {
      bindString(0, name)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectUserByName"
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
        """SELECT Activity.id, Activity.name, Activity.area, Activity.frequency FROM Activity WHERE area = ?""",
        mapper, 1) {
      bindString(0, area)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectActivitiesByArea"
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
    |SELECT ActivityCompletion.id, ActivityCompletion.activityId, ActivityCompletion.userId, ActivityCompletion.completedAt, ActivityCompletion.imagePath FROM ActivityCompletion 
    |WHERE activityId = ? AND completedAt >= ?
    """.trimMargin(), mapper, 2) {
      bindLong(0, activityId)
      bindLong(1, completedAt)
    }

    override fun toString(): String = "ChecklistDatabase.sq:selectCompletionsByActivityAndDate"
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
    |SELECT ac.id, ac.activityId, ac.userId, ac.completedAt, ac.imagePath FROM ActivityCompletion ac
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
}
