package com.checklistboteco.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.checklistboteco.database.ChecklistDatabase
import com.checklistboteco.domain.model.Activity
import com.checklistboteco.domain.model.ActivityCompletion
import com.checklistboteco.domain.model.ActivityWithCompletion
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.Frequency
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ChecklistRepository(database: ChecklistDatabase) {

    private val queries = database.checklistDatabaseQueries

    // User operations
    fun insertUser(name: String, password: String, area: Area, permissionLevel: PermissionLevel, allowedAreas: List<Area>) {
        val areasStr = allowedAreas.joinToString(",") { it.name }
        queries.insertUser(name, password, area.name, permissionLevel.name, areasStr)
    }

    fun getUserByName(name: String): User? {
        return queries.selectUserByName(name).executeAsOneOrNull()?.let { user ->
            User(
                id = user.id,
                name = user.name,
                password = user.password,
                area = Area.fromString(user.area),
                permissionLevel = PermissionLevel.fromString(user.permissionLevel),
                allowedAreas = user.allowedAreas.split(",").mapNotNull { s ->
                    Area.entries.find { a -> a.name == s.trim() }
                }.ifEmpty { Area.entries.toList() }
            )
        }
    }

    fun getAllUsers(): Flow<List<User>> {
        return queries.selectAllUsers().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { user ->
                User(
                    id = user.id,
                    name = user.name,
                    password = user.password,
                    area = Area.fromString(user.area),
                    permissionLevel = PermissionLevel.fromString(user.permissionLevel),
                    allowedAreas = user.allowedAreas.split(",").mapNotNull { s ->
                        Area.entries.find { a -> a.name == s.trim() }
                    }.ifEmpty { Area.entries.toList() }
                )
            }
        }
    }

    // Activity operations
    fun insertActivity(name: String, area: Area, frequency: Frequency) {
        queries.insertActivity(name, area.name, frequency.name)
    }

    fun getAllActivities(): Flow<List<Activity>> {
        return queries.selectAllActivities().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { mapToActivity(it) }
        }
    }

    fun getActivitiesByArea(area: Area): Flow<List<Activity>> {
        return queries.selectActivitiesByArea(area.name).asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { mapToActivity(it) }
        }
    }

    fun deleteActivity(id: Long) {
        queries.deleteActivity(id)
    }

    private fun mapToActivity(row: com.checklistboteco.database.Activity): Activity {
        return Activity(
            id = row.id,
            name = row.name,
            area = Area.fromString(row.area),
            frequency = Frequency.fromString(row.frequency)
        )
    }

    // Completion operations
    fun insertCompletion(activityId: Long, userId: Long, imagePath: String?) {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        queries.insertCompletion(activityId, userId, timestamp, imagePath)
    }

    private fun getPeriodStart(frequency: Frequency): Long {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return when (frequency) {
            Frequency.DIARIO -> {
                val startOfToday = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                Instant.parse("${startOfToday.date}T00:00:00Z").toEpochMilliseconds()
            }
            Frequency.QUINZENAL -> {
                val dayOfMonth = now.date.dayOfMonth
                val startDay = if (dayOfMonth <= 15) 1 else 16
                val month = now.date.monthNumber.toString().padStart(2, '0')
                Instant.parse("${now.date.year}-$month-${startDay.toString().padStart(2, '0')}T00:00:00Z").toEpochMilliseconds()
            }
            Frequency.MENSAL -> {
                val month = now.date.monthNumber.toString().padStart(2, '0')
                Instant.parse("${now.date.year}-$month-01T00:00:00Z").toEpochMilliseconds()
            }
        }
    }

    fun getActivitiesWithCompletion(area: Area): Flow<List<ActivityWithCompletion>> {
        return queries.selectActivitiesByArea(area.name).asFlow().mapToList(Dispatchers.IO).map { list ->
            val activities = list.map { mapToActivity(it) }
            activities.map { activity ->
                val periodStart = getPeriodStart(activity.frequency)
                val completions = queries.selectCompletionsByActivityAndDate(activity.id, periodStart).executeAsList()
                val completion = completions.lastOrNull()
                ActivityWithCompletion(
                    activity = activity,
                    isCompleted = completion != null,
                    completion = completion?.let {
                        ActivityCompletion(
                            id = it.id,
                            activityId = it.activityId,
                            userId = it.userId,
                            completedAt = it.completedAt,
                            imagePath = it.imagePath
                        )
                    }
                )
            }
        }
    }

    // Stats for admin
    fun getActivityCountByArea(): Flow<Map<Area, Int>> {
        return queries.countActivitiesByArea().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.associate { 
                Area.fromString(it.area) to it.total.toInt()
            }
        }
    }

    fun getCompletionStatsByArea(periodStart: Long): Flow<Map<Area, Pair<Int, Int>>> {
        return queries.selectAllActivities().asFlow().mapToList(Dispatchers.IO).map { activities ->
            val byArea = activities.groupBy { Area.fromString(it.area) }
            byArea.mapValues { (area, areaActivities) ->
                val total = areaActivities.size
                val completed = areaActivities.count { act ->
                    queries.selectCompletionsByActivityAndDate(act.id, periodStart).executeAsList().isNotEmpty()
                }
                total to completed
            }
        }
    }

    fun seedInitialData() {
        if (queries.selectUserByName("admin").executeAsOneOrNull() == null) {
            queries.insertUser(
                "admin",
                "admin123",
                Area.ATENDIMENTO.name,
                PermissionLevel.ADMIN.name,
                Area.entries.joinToString(",") { it.name }
            )
        }
        if (queries.selectAllActivities().executeAsList().isEmpty()) {
            // Atividades de exemplo
            listOf(
                Triple("Limpar mesas", Area.ATENDIMENTO, Frequency.DIARIO),
                Triple("Verificar estoque de bebidas", Area.ESTOQUE, Frequency.DIARIO),
                Triple("Limpar chão da cozinha", Area.LIMPEZA, Frequency.DIARIO),
                Triple("Verificar validade dos produtos", Area.ESTOQUE, Frequency.QUINZENAL)
            ).forEach { (name, area, freq) ->
                queries.insertActivity(name, area.name, freq.name)
            }
        }
    }
}
