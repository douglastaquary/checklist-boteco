package com.checklistboteco.backend.model

import kotlinx.serialization.Serializable

@Serializable
enum class AreaDto {
    ATENDIMENTO,
    COZINHA,
    ESTOQUE,
    LIMPEZA
}

@Serializable
enum class WorkSectorDto(val area: AreaDto) {
    ATENDIMENTO(AreaDto.ATENDIMENTO),
    COZINHA(AreaDto.COZINHA),
    SERVICOS_GERAIS(AreaDto.LIMPEZA),
    GARCON(AreaDto.ATENDIMENTO),
    CUMIM(AreaDto.ATENDIMENTO),
    CHEFE_DE_COZINHA(AreaDto.COZINHA),
    GERENTE(AreaDto.ATENDIMENTO),
    AJUDANTE_DE_COZINHA(AreaDto.COZINHA),
    ATENDENTE(AreaDto.ATENDIMENTO),
    BARMAN(AreaDto.ATENDIMENTO)
}

@Serializable
enum class PermissionLevelDto {
    ADMIN,
    USER
}

@Serializable
enum class FrequencyDto {
    DIARIO,
    QUINZENAL,
    MENSAL
}

@Serializable
enum class SyncStatusDto {
    SYNCED,
    PENDING,
    DELETED
}

@Serializable
data class FeaturePermissionsDto(
    val canRegisterUsers: Boolean = false,
    val canCreateActivities: Boolean = false,
    val canEditUsers: Boolean = false
) {
    companion object {
        val Admin = FeaturePermissionsDto(
            canRegisterUsers = true,
            canCreateActivities = true,
            canEditUsers = true
        )
    }
}

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val passwordHash: String = "",
    val area: AreaDto,
    val workSector: WorkSectorDto,
    val permissionLevel: PermissionLevelDto,
    val allowedAreas: List<AreaDto>,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatusDto = SyncStatusDto.SYNCED,
    val permissions: FeaturePermissionsDto = FeaturePermissionsDto()
)

@Serializable
data class PublicUserDto(
    val id: String,
    val name: String,
    val email: String,
    val area: AreaDto,
    val workSector: WorkSectorDto,
    val permissionLevel: PermissionLevelDto,
    val allowedAreas: List<AreaDto>,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatusDto = SyncStatusDto.SYNCED,
    val permissions: FeaturePermissionsDto = FeaturePermissionsDto()
)

@Serializable
data class CreateUserRequest(
    val name: String,
    val email: String,
    val password: String,
    val workSector: WorkSectorDto,
    val permissionLevel: PermissionLevelDto = PermissionLevelDto.USER,
    val permissions: FeaturePermissionsDto = FeaturePermissionsDto()
)

@Serializable
data class PermissionUpdateRequest(
    val permissions: FeaturePermissionsDto
)

@Serializable
data class ActivityDto(
    val id: String,
    val name: String,
    val area: AreaDto,
    val frequency: FrequencyDto,
    val effort: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatusDto = SyncStatusDto.SYNCED
)

@Serializable
data class CreateActivityRequest(
    val name: String,
    val area: AreaDto,
    val frequency: FrequencyDto,
    val effort: Int = 1
)

@Serializable
data class ActivityCompletionDto(
    val id: String,
    val activityId: String,
    val userId: String,
    val completedAt: Long,
    val imagePath: String? = null,
    val isLate: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatusDto = SyncStatusDto.SYNCED
)

@Serializable
data class WorkClockEntryDto(
    val id: String,
    val userId: String,
    val type: String,
    val registeredAt: Long,
    val latitude: Double,
    val longitude: Double,
    val distanceFromWorkMeters: Double,
    val isLate: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatusDto = SyncStatusDto.SYNCED
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceId: String? = null,
    val deviceName: String? = null
)

@Serializable
data class LoginResponse(
    val token: String? = null,
    val user: PublicUserDto? = null,
    val requiresTwoFactor: Boolean = false,
    val challengeId: String? = null,
    val deliveryHint: String? = null,
    val developmentCode: String? = null
)

@Serializable
data class VerifyDeviceRequest(
    val challengeId: String,
    val code: String,
    val deviceId: String,
    val deviceName: String? = null
)

@Serializable
data class DashboardStatsDto(
    val totalUsers: Int,
    val totalActivities: Int,
    val totalCompletions: Int,
    val pendingSyncItems: Int,
    val activitiesByArea: Map<AreaDto, Int>
)

@Serializable
data class SyncPullResponse(
    val serverTime: Long,
    val users: List<PublicUserDto>,
    val activities: List<ActivityDto>,
    val completions: List<ActivityCompletionDto>,
    val workClockEntries: List<WorkClockEntryDto>
)

@Serializable
data class SyncPushRequest(
    val users: List<CreateUserRequest> = emptyList(),
    val activities: List<ActivityDto> = emptyList(),
    val completions: List<ActivityCompletionDto> = emptyList(),
    val workClockEntries: List<WorkClockEntryDto> = emptyList()
)

@Serializable
data class ApiError(
    val message: String
)

fun UserDto.publicDto(): PublicUserDto {
    return PublicUserDto(
        id = id,
        name = name,
        email = email,
        area = area,
        workSector = workSector,
        permissionLevel = permissionLevel,
        allowedAreas = allowedAreas,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
        permissions = permissions
    )
}
