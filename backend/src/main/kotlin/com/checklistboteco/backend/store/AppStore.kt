package com.checklistboteco.backend.store

import com.checklistboteco.backend.model.ActivityCompletionDto
import com.checklistboteco.backend.model.ActivityDto
import com.checklistboteco.backend.model.AreaDto
import com.checklistboteco.backend.model.CreateActivityRequest
import com.checklistboteco.backend.model.CreateUserRequest
import com.checklistboteco.backend.model.DashboardStatsDto
import com.checklistboteco.backend.model.FeaturePermissionsDto
import com.checklistboteco.backend.model.FrequencyDto
import com.checklistboteco.backend.model.PermissionLevelDto
import com.checklistboteco.backend.model.PublicUserDto
import com.checklistboteco.backend.model.SyncStatusDto
import com.checklistboteco.backend.model.UserDto
import com.checklistboteco.backend.model.WorkClockEntryDto
import com.checklistboteco.backend.model.WorkSectorDto
import com.checklistboteco.backend.model.publicDto
import com.checklistboteco.backend.security.PasswordHasher
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID
import kotlin.random.Random

class AppStore(
    private val databasePath: Path,
    private val passwordHasher: PasswordHasher = PasswordHasher(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    init {
        Files.createDirectories(databasePath.parent)
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL")
                statement.execute("PRAGMA foreign_keys=ON")
            }
        }
        migrate()
        seedIfNeeded()
    }

    @Synchronized
    fun authenticate(email: String, password: String): UserDto? {
        val user = userByEmail(email.trim().lowercase()) ?: return null
        return user.takeIf { passwordHasher.verify(password, it.passwordHash) }
    }

    @Synchronized
    fun getUser(userId: String): UserDto? = userById(userId)

    @Synchronized
    fun users(): List<PublicUserDto> {
        return queryUsers("SELECT * FROM users ORDER BY name").map { it.publicDto() }
    }

    @Synchronized
    fun createUser(request: CreateUserRequest): PublicUserDto {
        require(request.name.isNotBlank()) { "Nome é obrigatório" }
        require(request.email.contains("@")) { "Email inválido" }
        require(userByEmail(request.email.trim().lowercase()) == null) { "Usuário já existe" }

        val now = clock()
        val permissionLevel = request.permissionLevel
        val user = UserDto(
            id = UUID.randomUUID().toString(),
            name = request.name.trim(),
            email = request.email.trim().lowercase(),
            passwordHash = passwordHasher.hash(request.password),
            area = request.workSector.area,
            workSector = request.workSector,
            permissionLevel = permissionLevel,
            allowedAreas = if (permissionLevel == PermissionLevelDto.ADMIN) AreaDto.entries else listOf(request.workSector.area),
            createdAt = now,
            updatedAt = now,
            permissions = if (permissionLevel == PermissionLevelDto.ADMIN) FeaturePermissionsDto.Admin else request.permissions
        )
        connection().use { it.insertUser(user) }
        return user.publicDto()
    }

    @Synchronized
    fun updatePermissions(userId: String, permissions: FeaturePermissionsDto): PublicUserDto {
        connection().use { connection ->
            connection.prepareStatement(
                """
                UPDATE users
                SET can_register_users = ?, can_create_activities = ?, can_edit_users = ?, updated_at = ?, sync_status = ?
                WHERE id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setBoolean(1, permissions.canRegisterUsers)
                statement.setBoolean(2, permissions.canCreateActivities)
                statement.setBoolean(3, permissions.canEditUsers)
                statement.setLong(4, clock())
                statement.setString(5, SyncStatusDto.SYNCED.name)
                statement.setString(6, userId)
                require(statement.executeUpdate() == 1) { "Usuário não encontrado" }
            }
        }
        return requireNotNull(userById(userId)) { "Usuário não encontrado" }.publicDto()
    }

    @Synchronized
    fun activities(): List<ActivityDto> {
        return queryActivities("SELECT * FROM activities ORDER BY name")
    }

    @Synchronized
    fun createActivity(request: CreateActivityRequest): ActivityDto {
        require(request.name.isNotBlank()) { "Nome da atividade é obrigatório" }
        val now = clock()
        val activity = ActivityDto(
            id = UUID.randomUUID().toString(),
            name = request.name.trim(),
            area = request.area,
            frequency = request.frequency,
            effort = request.effort.coerceIn(1, 5),
            createdAt = now,
            updatedAt = now
        )
        connection().use { it.persistActivity(activity) }
        return activity
    }

    @Synchronized
    fun upsertActivities(activities: List<ActivityDto>) {
        connection().use { connection -> activities.forEach { connection.persistActivity(it) } }
    }

    @Synchronized
    fun upsertCompletions(completions: List<ActivityCompletionDto>) {
        connection().use { connection -> completions.forEach { connection.persistCompletion(it) } }
    }

    @Synchronized
    fun upsertWorkClockEntries(entries: List<WorkClockEntryDto>) {
        connection().use { connection -> entries.forEach { connection.persistWorkClockEntry(it) } }
    }

    @Synchronized
    fun completions(): List<ActivityCompletionDto> {
        return queryCompletions("SELECT * FROM completions ORDER BY completed_at DESC")
    }

    @Synchronized
    fun workClockEntries(): List<WorkClockEntryDto> {
        return queryWorkClockEntries("SELECT * FROM work_clock_entries ORDER BY registered_at DESC")
    }

    @Synchronized
    fun dashboard(): DashboardStatsDto {
        val users = users()
        val activities = activities()
        val completions = completions()
        val pending = activities.count { it.syncStatus == SyncStatusDto.PENDING } +
            completions.count { it.syncStatus == SyncStatusDto.PENDING } +
            users.count { it.syncStatus == SyncStatusDto.PENDING }
        return DashboardStatsDto(
            totalUsers = users.size,
            totalActivities = activities.size,
            totalCompletions = completions.size,
            pendingSyncItems = pending,
            activitiesByArea = activities.groupingBy { it.area }.eachCount()
        )
    }

    @Synchronized
    fun pullSince(timestamp: Long): PullData {
        return PullData(
            users = queryUsers("SELECT * FROM users WHERE updated_at > ?", timestamp).map { it.publicDto() },
            activities = queryActivities("SELECT * FROM activities WHERE updated_at > ?", timestamp),
            completions = queryCompletions("SELECT * FROM completions WHERE updated_at > ?", timestamp),
            workClockEntries = queryWorkClockEntries("SELECT * FROM work_clock_entries WHERE updated_at > ?", timestamp)
        )
    }

    @Synchronized
    fun isTrustedDevice(userId: String, deviceId: String): Boolean {
        return connection().use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM trusted_devices WHERE user_id = ? AND device_id = ?").use { statement ->
                statement.setString(1, userId)
                statement.setString(2, deviceId)
                statement.executeQuery().use { result -> result.next() && result.getInt(1) > 0 }
            }
        }
    }

    @Synchronized
    fun createDeviceChallenge(userId: String, deviceId: String, deviceName: String?): DeviceChallenge {
        val challenge = DeviceChallenge(
            id = UUID.randomUUID().toString(),
            userId = userId,
            deviceId = deviceId,
            deviceName = deviceName,
            code = Random.nextInt(100_000, 999_999).toString(),
            expiresAt = clock() + 10L * 60L * 1000L
        )
        connection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO device_challenges(id, user_id, device_id, device_name, code, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, challenge.id)
                statement.setString(2, challenge.userId)
                statement.setString(3, challenge.deviceId)
                statement.setString(4, challenge.deviceName)
                statement.setString(5, challenge.code)
                statement.setLong(6, challenge.expiresAt)
                statement.executeUpdate()
            }
        }
        return challenge
    }

    @Synchronized
    fun verifyDeviceChallenge(challengeId: String, code: String, deviceId: String, deviceName: String?): UserDto? {
        val challenge = connection().use { connection ->
            connection.prepareStatement("SELECT * FROM device_challenges WHERE id = ?").use { statement ->
                statement.setString(1, challengeId)
                statement.executeQuery().use { result -> if (result.next()) result.toDeviceChallenge() else null }
            }
        } ?: return null
        if (challenge.code != code.trim() || challenge.deviceId != deviceId || challenge.expiresAt <= clock()) return null
        trustDevice(challenge.userId, deviceId, deviceName ?: challenge.deviceName)
        connection().use { connection ->
            connection.prepareStatement("DELETE FROM device_challenges WHERE id = ?").use { statement ->
                statement.setString(1, challengeId)
                statement.executeUpdate()
            }
        }
        return userById(challenge.userId)
    }

    private fun trustDevice(userId: String, deviceId: String, deviceName: String?) {
        val now = clock()
        connection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO trusted_devices(user_id, device_id, device_name, trusted_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(user_id, device_id) DO UPDATE SET device_name = excluded.device_name, last_seen_at = excluded.last_seen_at
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, userId)
                statement.setString(2, deviceId)
                statement.setString(3, deviceName)
                statement.setLong(4, now)
                statement.setLong(5, now)
                statement.executeUpdate()
            }
        }
    }

    private fun migrate() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS users(
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        email TEXT NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL,
                        area TEXT NOT NULL,
                        work_sector TEXT NOT NULL,
                        permission_level TEXT NOT NULL,
                        allowed_areas TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        sync_status TEXT NOT NULL,
                        can_register_users INTEGER NOT NULL,
                        can_create_activities INTEGER NOT NULL,
                        can_edit_users INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS activities(
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        area TEXT NOT NULL,
                        frequency TEXT NOT NULL,
                        effort INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        sync_status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS completions(
                        id TEXT PRIMARY KEY,
                        activity_id TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        completed_at INTEGER NOT NULL,
                        image_path TEXT,
                        is_late INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        sync_status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS work_clock_entries(
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        type TEXT NOT NULL,
                        registered_at INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        distance_from_work_meters REAL NOT NULL,
                        is_late INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        sync_status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS trusted_devices(
                        user_id TEXT NOT NULL,
                        device_id TEXT NOT NULL,
                        device_name TEXT,
                        trusted_at INTEGER NOT NULL,
                        last_seen_at INTEGER NOT NULL,
                        PRIMARY KEY(user_id, device_id)
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS device_challenges(
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        device_id TEXT NOT NULL,
                        device_name TEXT,
                        code TEXT NOT NULL,
                        expires_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute("CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_activities_updated_at ON activities(updated_at)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_completions_updated_at ON completions(updated_at)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_work_clock_updated_at ON work_clock_entries(updated_at)")
            }
        }
    }

    private fun seedIfNeeded() {
        val hasUsers = connection().use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM users").use { statement ->
                statement.executeQuery().use { result -> result.next() && result.getInt(1) > 0 }
            }
        }
        if (hasUsers) return
        val now = clock()
        val admin = UserDto(
            id = UUID.randomUUID().toString(),
            name = "admin",
            email = "admin@checklistboteco.com",
            passwordHash = passwordHasher.hash("admin123"),
            area = AreaDto.ATENDIMENTO,
            workSector = WorkSectorDto.GERENTE,
            permissionLevel = PermissionLevelDto.ADMIN,
            allowedAreas = AreaDto.entries,
            createdAt = now,
            updatedAt = now,
            permissions = FeaturePermissionsDto.Admin
        )
        val activities = listOf(
            ActivityDto(UUID.randomUUID().toString(), "Limpar mesas", AreaDto.ATENDIMENTO, FrequencyDto.DIARIO, 2, now, now),
            ActivityDto(UUID.randomUUID().toString(), "Verificar estoque de bebidas", AreaDto.ESTOQUE, FrequencyDto.DIARIO, 2, now, now),
            ActivityDto(UUID.randomUUID().toString(), "Limpar chão da cozinha", AreaDto.LIMPEZA, FrequencyDto.DIARIO, 2, now, now)
        )
        connection().use { connection ->
            connection.insertUser(admin)
            activities.forEach { connection.persistActivity(it) }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}")

    private fun userByEmail(email: String): UserDto? = queryUsers("SELECT * FROM users WHERE email = ?", email).firstOrNull()
    private fun userById(userId: String): UserDto? = queryUsers("SELECT * FROM users WHERE id = ?", userId).firstOrNull()

    private fun queryUsers(sql: String, parameter: Any? = null): List<UserDto> {
        return connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameter?.let { statement.setObject(1, it) }
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toUser()) } }
            }
        }
    }

    private fun queryActivities(sql: String, parameter: Any? = null): List<ActivityDto> {
        return connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameter?.let { statement.setObject(1, it) }
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toActivity()) } }
            }
        }
    }

    private fun queryCompletions(sql: String, parameter: Any? = null): List<ActivityCompletionDto> {
        return connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameter?.let { statement.setObject(1, it) }
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toCompletion()) } }
            }
        }
    }

    private fun queryWorkClockEntries(sql: String, parameter: Any? = null): List<WorkClockEntryDto> {
        return connection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                parameter?.let { statement.setObject(1, it) }
                statement.executeQuery().use { result -> buildList { while (result.next()) add(result.toWorkClockEntry()) } }
            }
        }
    }

    private fun Connection.insertUser(user: UserDto) {
        prepareStatement(
            """
            INSERT INTO users(id, name, email, password_hash, area, work_sector, permission_level, allowed_areas, created_at, updated_at, sync_status, can_register_users, can_create_activities, can_edit_users)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                email = excluded.email,
                area = excluded.area,
                work_sector = excluded.work_sector,
                permission_level = excluded.permission_level,
                allowed_areas = excluded.allowed_areas,
                updated_at = excluded.updated_at,
                sync_status = excluded.sync_status,
                can_register_users = excluded.can_register_users,
                can_create_activities = excluded.can_create_activities,
                can_edit_users = excluded.can_edit_users
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, user.id)
            statement.setString(2, user.name)
            statement.setString(3, user.email)
            statement.setString(4, user.passwordHash)
            statement.setString(5, user.area.name)
            statement.setString(6, user.workSector.name)
            statement.setString(7, user.permissionLevel.name)
            statement.setString(8, user.allowedAreas.joinToString(",") { it.name })
            statement.setLong(9, user.createdAt)
            statement.setLong(10, user.updatedAt)
            statement.setString(11, user.syncStatus.name)
            statement.setBoolean(12, user.permissions.canRegisterUsers)
            statement.setBoolean(13, user.permissions.canCreateActivities)
            statement.setBoolean(14, user.permissions.canEditUsers)
            statement.executeUpdate()
        }
    }

    private fun Connection.persistActivity(activity: ActivityDto) {
        prepareStatement(
            """
            INSERT INTO activities(id, name, area, frequency, effort, created_at, updated_at, sync_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET name = excluded.name, area = excluded.area, frequency = excluded.frequency, effort = excluded.effort, updated_at = excluded.updated_at, sync_status = excluded.sync_status
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, activity.id)
            statement.setString(2, activity.name)
            statement.setString(3, activity.area.name)
            statement.setString(4, activity.frequency.name)
            statement.setInt(5, activity.effort)
            statement.setLong(6, activity.createdAt)
            statement.setLong(7, activity.updatedAt)
            statement.setString(8, activity.syncStatus.name)
            statement.executeUpdate()
        }
    }

    private fun Connection.persistCompletion(completion: ActivityCompletionDto) {
        prepareStatement(
            """
            INSERT INTO completions(id, activity_id, user_id, completed_at, image_path, is_late, created_at, updated_at, sync_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET activity_id = excluded.activity_id, user_id = excluded.user_id, completed_at = excluded.completed_at, image_path = excluded.image_path, is_late = excluded.is_late, updated_at = excluded.updated_at, sync_status = excluded.sync_status
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, completion.id)
            statement.setString(2, completion.activityId)
            statement.setString(3, completion.userId)
            statement.setLong(4, completion.completedAt)
            statement.setString(5, completion.imagePath)
            statement.setBoolean(6, completion.isLate)
            statement.setLong(7, completion.createdAt)
            statement.setLong(8, completion.updatedAt)
            statement.setString(9, completion.syncStatus.name)
            statement.executeUpdate()
        }
    }

    private fun Connection.persistWorkClockEntry(entry: WorkClockEntryDto) {
        prepareStatement(
            """
            INSERT INTO work_clock_entries(id, user_id, type, registered_at, latitude, longitude, distance_from_work_meters, is_late, created_at, updated_at, sync_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, type = excluded.type, registered_at = excluded.registered_at, latitude = excluded.latitude, longitude = excluded.longitude, distance_from_work_meters = excluded.distance_from_work_meters, is_late = excluded.is_late, updated_at = excluded.updated_at, sync_status = excluded.sync_status
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, entry.id)
            statement.setString(2, entry.userId)
            statement.setString(3, entry.type)
            statement.setLong(4, entry.registeredAt)
            statement.setDouble(5, entry.latitude)
            statement.setDouble(6, entry.longitude)
            statement.setDouble(7, entry.distanceFromWorkMeters)
            statement.setBoolean(8, entry.isLate)
            statement.setLong(9, entry.createdAt)
            statement.setLong(10, entry.updatedAt)
            statement.setString(11, entry.syncStatus.name)
            statement.executeUpdate()
        }
    }

    private fun ResultSet.toUser(): UserDto {
        return UserDto(
            id = getString("id"),
            name = getString("name"),
            email = getString("email"),
            passwordHash = getString("password_hash"),
            area = AreaDto.valueOf(getString("area")),
            workSector = WorkSectorDto.valueOf(getString("work_sector")),
            permissionLevel = PermissionLevelDto.valueOf(getString("permission_level")),
            allowedAreas = getString("allowed_areas").split(",").filter { it.isNotBlank() }.map(AreaDto::valueOf),
            createdAt = getLong("created_at"),
            updatedAt = getLong("updated_at"),
            syncStatus = SyncStatusDto.valueOf(getString("sync_status")),
            permissions = FeaturePermissionsDto(
                canRegisterUsers = getBoolean("can_register_users"),
                canCreateActivities = getBoolean("can_create_activities"),
                canEditUsers = getBoolean("can_edit_users")
            )
        )
    }

    private fun ResultSet.toActivity(): ActivityDto {
        return ActivityDto(
            id = getString("id"),
            name = getString("name"),
            area = AreaDto.valueOf(getString("area")),
            frequency = FrequencyDto.valueOf(getString("frequency")),
            effort = getInt("effort"),
            createdAt = getLong("created_at"),
            updatedAt = getLong("updated_at"),
            syncStatus = SyncStatusDto.valueOf(getString("sync_status"))
        )
    }

    private fun ResultSet.toCompletion(): ActivityCompletionDto {
        return ActivityCompletionDto(
            id = getString("id"),
            activityId = getString("activity_id"),
            userId = getString("user_id"),
            completedAt = getLong("completed_at"),
            imagePath = getString("image_path"),
            isLate = getBoolean("is_late"),
            createdAt = getLong("created_at"),
            updatedAt = getLong("updated_at"),
            syncStatus = SyncStatusDto.valueOf(getString("sync_status"))
        )
    }

    private fun ResultSet.toWorkClockEntry(): WorkClockEntryDto {
        return WorkClockEntryDto(
            id = getString("id"),
            userId = getString("user_id"),
            type = getString("type"),
            registeredAt = getLong("registered_at"),
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
            distanceFromWorkMeters = getDouble("distance_from_work_meters"),
            isLate = getBoolean("is_late"),
            createdAt = getLong("created_at"),
            updatedAt = getLong("updated_at"),
            syncStatus = SyncStatusDto.valueOf(getString("sync_status"))
        )
    }

    private fun ResultSet.toDeviceChallenge(): DeviceChallenge {
        return DeviceChallenge(
            id = getString("id"),
            userId = getString("user_id"),
            deviceId = getString("device_id"),
            deviceName = getString("device_name"),
            code = getString("code"),
            expiresAt = getLong("expires_at")
        )
    }
}

data class PullData(
    val users: List<PublicUserDto>,
    val activities: List<ActivityDto>,
    val completions: List<ActivityCompletionDto>,
    val workClockEntries: List<WorkClockEntryDto>
)

data class DeviceChallenge(
    val id: String,
    val userId: String,
    val deviceId: String,
    val deviceName: String?,
    val code: String,
    val expiresAt: Long
)
