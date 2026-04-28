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
    effort: Long,
  ) -> T): Query<T> = Query(384_786_999, arrayOf("Activity"), driver, "ChecklistDatabase.sq",
      "selectAllActivities",
      "SELECT Activity.id, Activity.name, Activity.area, Activity.frequency, Activity.effort FROM Activity") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!
    )
  }

  public fun selectAllActivities(): Query<Activity> = selectAllActivities { id, name, area,
      frequency, effort ->
    Activity(
      id,
      name,
      area,
      frequency,
      effort
    )
  }

  public fun <T : Any> selectActivitiesByArea(area: String, mapper: (
    id: Long,
    name: String,
    area: String,
    frequency: String,
    effort: Long,
  ) -> T): Query<T> = SelectActivitiesByAreaQuery(area) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!
    )
  }

  public fun selectActivitiesByArea(area: String): Query<Activity> = selectActivitiesByArea(area) {
      id, name, area_, frequency, effort ->
    Activity(
      id,
      name,
      area_,
      frequency,
      effort
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
      isLate: Long,
    ) -> T,
  ): Query<T> = SelectCompletionsByActivityAndDateQuery(activityId, completedAt) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getString(4),
      cursor.getLong(5)!!
    )
  }

  public fun selectCompletionsByActivityAndDate(activityId: Long, completedAt: Long):
      Query<ActivityCompletion> = selectCompletionsByActivityAndDate(activityId, completedAt) { id,
      activityId_, userId, completedAt_, imagePath, isLate ->
    ActivityCompletion(
      id,
      activityId_,
      userId,
      completedAt_,
      imagePath,
      isLate
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
      isLate: Long,
    ) -> T,
  ): Query<T> = SelectCompletionsByAreaAndDateQuery(area, completedAt) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getString(4),
      cursor.getLong(5)!!
    )
  }

  public fun selectCompletionsByAreaAndDate(area: String, completedAt: Long):
      Query<ActivityCompletion> = selectCompletionsByAreaAndDate(area, completedAt) { id,
      activityId, userId, completedAt_, imagePath, isLate ->
    ActivityCompletion(
      id,
      activityId,
      userId,
      completedAt_,
      imagePath,
      isLate
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
    effort: Long,
  ) {
    driver.execute(-80_309_149, """
        |INSERT INTO Activity(name, area, frequency, effort)
        |VALUES (?, ?, ?, ?)
        """.trimMargin(), 4) {
          bindString(0, name)
          bindString(1, area)
          bindString(2, frequency)
          bindLong(3, effort)
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
    isLate: Long,
  ) {
    driver.execute(1_837_465_648, """
        |INSERT INTO ActivityCompletion(activityId, userId, completedAt, imagePath, isLate)
        |VALUES (?, ?, ?, ?, ?)
        """.trimMargin(), 5) {
          bindLong(0, activityId)
          bindLong(1, userId)
          bindLong(2, completedAt)
          bindString(3, imagePath)
          bindLong(4, isLate)
        }
    notifyQueries(1_837_465_648) { emit ->
      emit("ActivityCompletion")
    }
  }

  public fun updateActivity(
    name: String,
    area: String,
    frequency: String,
    effort: Long,
    id: Long,
  ) {
    driver.execute(-533_267_853, """
        |UPDATE Activity SET name = ?, area = ?, frequency = ?, effort = ?
        |WHERE id = ?
        """.trimMargin(), 5) {
          bindString(0, name)
          bindString(1, area)
          bindString(2, frequency)
          bindLong(3, effort)
          bindLong(4, id)
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
        """SELECT Activity.id, Activity.name, Activity.area, Activity.frequency, Activity.effort FROM Activity WHERE area = ?""",
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
    |SELECT ActivityCompletion.id, ActivityCompletion.activityId, ActivityCompletion.userId, ActivityCompletion.completedAt, ActivityCompletion.imagePath, ActivityCompletion.isLate FROM ActivityCompletion 
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
    |SELECT ac.id, ac.activityId, ac.userId, ac.completedAt, ac.imagePath, ac.isLate FROM ActivityCompletion ac
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
    |    (SELECT COUNT(*) FROM Activity) AS totalActivities,
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
    |LEFT JOIN Activity AS a ON ac.activityId = a.id
    |GROUP BY u.id, u.name
    |ORDER BY totalEffort DESC
    """.trimMargin(), mapper, 1) {
      bindLong(0, periodStart)
    }

    override fun toString(): String = "ChecklistDatabase.sq:getRankingData"
  }
}
