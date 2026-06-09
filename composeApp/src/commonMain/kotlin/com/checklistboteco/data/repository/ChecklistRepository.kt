package com.checklistboteco.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.checklistboteco.database.ChecklistDatabase
import com.checklistboteco.domain.model.Activity
import com.checklistboteco.domain.model.ActivityCompletion
import com.checklistboteco.domain.model.ActivityWithCompletion
import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.Frequency
import com.checklistboteco.domain.model.FeaturePermissions
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.GeoPoint
import com.checklistboteco.domain.model.User
import com.checklistboteco.domain.model.ValidatedUserRegistration
import com.checklistboteco.domain.model.WorkClockCalculator
import com.checklistboteco.domain.model.WorkClockEntry
import com.checklistboteco.domain.model.WorkClockType
import com.checklistboteco.domain.model.WorkSector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ChecklistRepository(database: ChecklistDatabase) {

    private val queries = database.checklistDatabaseQueries

    // User operations
    fun insertUser(
        name: String,
        email: String,
        password: String,
        area: Area,
        workSector: WorkSector,
        permissionLevel: PermissionLevel,
        allowedAreas: List<Area>,
        createdAt: Long = Clock.System.now().toEpochMilliseconds(),
        featurePermissions: FeaturePermissions = FeaturePermissions()
    ) {
        val areasStr = allowedAreas.joinToString(",") { it.name }
        queries.insertUser(
            name,
            email,
            password,
            area.name,
            workSector.name,
            permissionLevel.name,
            areasStr,
            createdAt,
            featurePermissions.canRegisterUsers.toLongFlag(),
            featurePermissions.canCreateActivities.toLongFlag(),
            featurePermissions.canEditUsers.toLongFlag()
        )
    }

    fun insertRegisteredUser(user: ValidatedUserRegistration) {
        insertUser(
            name = user.fullName,
            email = user.email,
            password = user.password,
            area = user.workSector.activityArea,
            workSector = user.workSector,
            permissionLevel = user.permissionLevel,
            allowedAreas = user.allowedAreas,
            featurePermissions = user.featurePermissions
        )
    }

    fun getUserByName(name: String): User? {
        return queries.selectUserByName(name).executeAsOneOrNull()?.let(::mapToUser)
    }

    fun getUserByEmail(email: String): User? {
        return queries.selectUserByEmail(email).executeAsOneOrNull()?.let(::mapToUser)
    }

    fun getAllUsers(): Flow<List<User>> {
        return queries.selectAllUsers().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map(::mapToUser)
        }
    }

    fun updateUserFeaturePermissions(userId: Long, permissions: FeaturePermissions) {
        queries.updateUserFeaturePermissions(
            permissions.canRegisterUsers.toLongFlag(),
            permissions.canCreateActivities.toLongFlag(),
            permissions.canEditUsers.toLongFlag(),
            userId
        )
    }

    private fun mapToUser(user: com.checklistboteco.database.User): User {
        val permissionLevel = PermissionLevel.fromString(user.permissionLevel)
        val allowedAreas = user.allowedAreas.split(",").mapNotNull { s ->
            Area.entries.find { a -> a.name == s.trim() }
        }.ifEmpty { listOf(WorkSector.fromString(user.workSector).activityArea) }

        return User(
            id = user.id,
            name = user.name,
            email = user.email,
            password = user.password,
            area = Area.fromString(user.area),
            workSector = WorkSector.fromString(user.workSector),
            permissionLevel = permissionLevel,
            allowedAreas = if (permissionLevel == PermissionLevel.ADMIN) Area.entries.toList() else allowedAreas,
            createdAt = user.createdAt,
            featurePermissions = FeaturePermissions(
                canRegisterUsers = user.canRegisterUsers == 1L,
                canCreateActivities = user.canCreateActivities == 1L,
                canEditUsers = user.canEditUsers == 1L
            )
        )
    }

    // Activity operations
    fun insertActivity(name: String, area: Area, frequency: Frequency, effort: Int) {
        queries.insertActivity(name, area.name, frequency.name, effort.toLong())
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
            frequency = Frequency.fromString(row.frequency),
            effort = row.effort.toInt()
        )
    }

    // Completion operations
    fun insertCompletion(activityId: Long, userId: Long, imagePath: String?, isLate: Boolean) {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        queries.insertCompletion(activityId, userId, timestamp, imagePath, if (isLate) 1L else 0L)
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
                            imagePath = it.imagePath,
                            isLate = it.isLate == 1L
                        )
                    }
                )
            }
        }
    }

    // Work clock operations
    fun insertWorkClockEntry(
        userId: Long,
        type: WorkClockType,
        registeredAt: Long,
        location: GeoPoint,
        distanceFromWorkMeters: Double,
        isLate: Boolean
    ) {
        queries.insertWorkClockEntry(
            userId,
            type.name,
            registeredAt,
            location.latitude,
            location.longitude,
            distanceFromWorkMeters,
            isLate.toLongFlag()
        )
    }

    fun getWorkClockEntriesByUserAndDate(userId: Long, date: LocalDate): Flow<List<WorkClockEntry>> {
        val start = date.startOfDayMillis()
        val end = start + DAY_MILLIS
        return queries.selectWorkClockEntriesByUserAndDate(userId, start, end)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map(::mapToWorkClockEntry) }
    }

    fun getWorkClockEntriesByUserAndCurrentWeek(userId: Long): List<WorkClockEntry> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val start = today.startOfWeekMillis()
        val end = start + 7L * DAY_MILLIS
        return queries.selectWorkClockEntriesByUserAndPeriod(userId, start, end)
            .executeAsList()
            .map(::mapToWorkClockEntry)
    }

    private fun mapToWorkClockEntry(row: com.checklistboteco.database.WorkClockEntry): WorkClockEntry {
        return WorkClockEntry(
            id = row.id,
            userId = row.userId,
            type = WorkClockType.fromString(row.type),
            registeredAt = row.registeredAt,
            location = GeoPoint(row.latitude, row.longitude),
            distanceFromWorkMeters = row.distanceFromWorkMeters,
            isLate = row.isLate == 1L
        )
    }

    // Dashboard Stats
    fun getGlobalStats(periodStart: Long): Flow<GlobalDashboardStats> {
        return queries.getGlobalStats(periodStart).asFlow().mapToOne(Dispatchers.IO).map { row ->
            val totalActivities = row.totalActivities
            val totalCompleted = row.totalCompleted
            val lateCompletions = row.lateCompletions
            
            GlobalDashboardStats(
                completionPercentage = if (totalActivities > 0) (totalCompleted.toFloat() / totalActivities.toFloat() * 100).toInt() else 0,
                alertsCount = (totalActivities - totalCompleted).toInt(),
                scheduledCount = totalActivities.toInt(),
                onTimeCount = (totalCompleted - lateCompletions).toInt(),
                lateCount = lateCompletions.toInt()
            )
        }
    }

    fun getRanking(periodStart: Long): Flow<List<UserRanking>> {
        return queries.getRankingData(periodStart).asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { row ->
                val totalCompletions = row.totalCompletions
                val onTimeCompletions = row.onTimeCompletions ?: 0L
                val totalEffort = row.totalEffort ?: 0L
                
                val punctualPercentage = if (totalCompletions > 0) (onTimeCompletions.toFloat() / totalCompletions.toFloat() * 100) else 0f
                val effortPercentage = if (totalCompletions > 0) (totalEffort.toFloat() / (totalCompletions * 5).toFloat() * 100) else 0f
                
                UserRanking(
                    userName = row.name,
                    score = (punctualPercentage * 0.6f + effortPercentage * 0.4f),
                    punctualPercentage = punctualPercentage,
                    effortPercentage = effortPercentage,
                    qualityPercentage = 100f // Placeholder
                )
            }
        }
    }

    // Legacy Stats
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
                "admin@checklistboteco.com",
                "admin123",
                Area.ATENDIMENTO.name,
                WorkSector.GERENTE.name,
                PermissionLevel.ADMIN.name,
                Area.entries.joinToString(",") { it.name },
                Clock.System.now().toEpochMilliseconds(),
                1L,
                1L,
                1L
            )
        }
        if (queries.selectAllActivities().executeAsList().isEmpty()) {
            listOf(
                Triple("Limpar mesas", Area.ATENDIMENTO, Frequency.DIARIO),
                Triple("Verificar estoque de bebidas", Area.ESTOQUE, Frequency.DIARIO),
                Triple("Limpar chão da cozinha", Area.LIMPEZA, Frequency.DIARIO),
                Triple("Verificar validade dos produtos", Area.ESTOQUE, Frequency.QUINZENAL)
            ).forEach { (name, area, freq) ->
                queries.insertActivity(name, area.name, freq.name, 2L)
            }
        }
    }
}

private fun Boolean.toLongFlag(): Long = if (this) 1L else 0L
private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

private fun LocalDate.startOfDayMillis(): Long {
    return Instant.parse("${this}T00:00:00Z").toEpochMilliseconds()
}

private fun LocalDate.startOfWeekMillis(): Long {
    val mondayOffset = dayOfWeek.ordinal
    return startOfDayMillis() - mondayOffset * DAY_MILLIS
}

data class GlobalDashboardStats(
    val completionPercentage: Int,
    val alertsCount: Int,
    val scheduledCount: Int,
    val onTimeCount: Int,
    val lateCount: Int
)

data class UserRanking(
    val userName: String,
    val score: Float,
    val punctualPercentage: Float,
    val effortPercentage: Float,
    val qualityPercentage: Float
)
