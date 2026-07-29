package com.checklistboteco.data.remote

import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.FeaturePermissions
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import com.checklistboteco.domain.model.WorkClockType
import com.checklistboteco.domain.model.WorkClockAbsenceDetail
import com.checklistboteco.domain.model.WorkSector
import com.checklistboteco.domain.model.WorksiteInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import com.checklistboteco.domain.model.InventoryCountDraft

class BackendApiClient private constructor(
    private val baseUrl: String,
    private val httpClient: HttpClient
) {
    suspend fun login(
        email: String,
        password: String,
        deviceId: String,
        deviceName: String
    ): RemoteLoginResult {
        return httpClient.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                LoginRequestDto(
                    email = email,
                    password = password,
                    deviceId = deviceId,
                    deviceName = deviceName
                )
            )
        }.body<LoginResponseDto>().toResult()
    }

    suspend fun verifyDevice(
        challengeId: String,
        code: String,
        deviceId: String,
        deviceName: String
    ): RemoteLoginResult {
        return httpClient.post("$baseUrl/api/auth/verify-device") {
            contentType(ContentType.Application.Json)
            setBody(
                VerifyDeviceRequestDto(
                    challengeId = challengeId,
                    code = code,
                    deviceId = deviceId,
                    deviceName = deviceName
                )
            )
        }.body<LoginResponseDto>().toResult()
    }

    suspend fun fetchCurrentUser(token: String): RemoteLoginResult {
        val user = httpClient.get("$baseUrl/api/me") {
            bearerAuth(token)
        }.body<PublicUserDto>()
        return RemoteLoginResult(
            token = token,
            user = user.toDomain(),
            remoteUserId = user.id
        )
    }

    suspend fun changeMyPassword(
        token: String,
        currentPassword: String,
        newPassword: String
    ): User {
        val user = httpClient.post("$baseUrl/api/me/change-password") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequestDto(currentPassword, newPassword))
        }.body<PublicUserDto>()
        return user.toDomain().copy(remoteId = user.id)
    }

    suspend fun listUsers(token: String): List<User> {
        return httpClient.get("$baseUrl/api/users") {
            bearerAuth(token)
        }.body<List<PublicUserDto>>().map { it.toDomain().copy(remoteId = it.id) }
    }

    suspend fun createUser(
        token: String,
        name: String,
        email: String,
        password: String,
        workSector: WorkSector,
        permissionLevel: PermissionLevel = PermissionLevel.USER,
        permissions: FeaturePermissions = FeaturePermissions()
    ): User {
        val created = httpClient.post("$baseUrl/api/users") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateUserRequestDto(
                    name = name,
                    email = email,
                    password = password,
                    workSector = workSector.name,
                    permissionLevel = permissionLevel.name,
                    permissions = FeaturePermissionsDto.fromDomain(permissions)
                )
            )
        }.body<PublicUserDto>()
        return created.toDomain().copy(remoteId = created.id)
    }

    suspend fun updateUserPermissions(
        token: String,
        userId: String,
        permissions: FeaturePermissions
    ): User {
        val updated = httpClient.patch("$baseUrl/api/users/$userId/permissions") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PermissionUpdateRequestDto(FeaturePermissionsDto.fromDomain(permissions)))
        }.body<PublicUserDto>()
        return updated.toDomain().copy(remoteId = updated.id)
    }

    suspend fun fetchWorksite(token: String): WorksiteInfo {
        val dto = httpClient.get("$baseUrl/api/work-clock/worksite") {
            bearerAuth(token)
        }.body<WorksiteInfoDto>()
        return WorksiteInfo(
            name = dto.name,
            latitude = dto.latitude,
            longitude = dto.longitude,
            radiusMeters = dto.radiusMeters
        )
    }

    suspend fun fetchMyWorkClockSummary(token: String, from: String, to: String): RemoteWorkClockSummary {
        val dto = httpClient.get("$baseUrl/api/work-clock/me/summary?from=$from&to=$to") {
            bearerAuth(token)
        }.body<WorkClockSummaryDto>()
        return RemoteWorkClockSummary(
            absenceDays = dto.absenceDays,
            absenceDates = dto.absenceDates,
            absenceDetails = dto.absenceDetails.map { WorkClockAbsenceDetail(it.date, it.reason) }
        )
    }

    suspend fun fetchDashboardStats(token: String): DashboardStatsDto {
        return httpClient.get("$baseUrl/api/admin/dashboard") {
            bearerAuth(token)
        }.body()
    }

    suspend fun listInventoryCounts(token: String): List<InventoryCountSessionDto> {
        return httpClient.get("$baseUrl/api/inventory/counts") {
            bearerAuth(token)
        }.body()
    }

    suspend fun listAdminStockBalances(token: String): List<RemoteAdminStockBalance> {
        return httpClient.get("$baseUrl/api/inventory/admin-stock/balances") {
            bearerAuth(token)
        }.body()
    }

    suspend fun health(): Boolean {
        return runCatching {
            httpClient.get("$baseUrl/api/health").body<Map<String, String>>()["status"] == "ok"
        }.getOrDefault(false)
    }

    suspend fun pushWorkClockEntry(
        token: String,
        deviceId: String,
        remoteUserId: String,
        _localEntryId: Long,
        type: WorkClockType,
        registeredAt: Long,
        latitude: Double,
        longitude: Double,
        distanceFromWorkMeters: Double,
        isLate: Boolean
    ): String {
        val now = registeredAt
        val remoteId = "$remoteUserId-${type.name}-$registeredAt"
        httpClient.post("$baseUrl/api/sync/push") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                SyncPushRequestDto(
                    deviceId = deviceId,
                    workClockEntries = listOf(
                        WorkClockEntryDto(
                            id = remoteId,
                            userId = remoteUserId,
                            type = type.name,
                            registeredAt = registeredAt,
                            latitude = latitude,
                            longitude = longitude,
                            distanceFromWorkMeters = distanceFromWorkMeters,
                            isLate = isLate,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                )
            )
        }
        return remoteId
    }

    suspend fun submitInventoryCount(token: String, date: String, items: List<InventoryCountDraft>) {
        httpClient.post("$baseUrl/api/inventory/counts") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody(InventoryCountRequestDto(date,countedAt=kotlinx.datetime.Clock.System.now().toString(),items=items.map { InventoryCountItemDto(it.name,it.quantity,it.category.name,it.volume,it.volumeUnit,it.salePriceInCents,it.costPriceInCents,it.storageCondition.name) }))
        }
    }

    suspend fun submitAdminStockCount(token: String, date: String, items: List<InventoryCountDraft>) {
        httpClient.post("$baseUrl/api/inventory/admin-stock/counts") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody(InventoryCountRequestDto(date,countedAt=kotlinx.datetime.Clock.System.now().toString(),items=items.map { InventoryCountItemDto(it.name,it.quantity,it.category.name,it.volume,it.volumeUnit,it.salePriceInCents,it.costPriceInCents,it.storageCondition.name) }))
        }
    }

    suspend fun inventoryDailyAudit(token:String,date:String):RemoteInventoryAudit = httpClient.post("$baseUrl/api/inventory/audit/daily") {
        bearerAuth(token); contentType(ContentType.Application.Json); setBody(InventoryAuditRequestDto(date))
    }.body()

    suspend fun applyDailyAudit(token: String, date: String): RemoteApplyDailyAudit = httpClient.post("$baseUrl/api/inventory/audit/daily/apply") {
        bearerAuth(token); contentType(ContentType.Application.Json); setBody(InventoryAuditRequestDto(date))
    }.body()

    suspend fun salesImportPreview(token: String, fileName: String, csv: String): RemoteImportBatch =
        httpClient.post("$baseUrl/api/sales/imports/preview") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(SalesPreviewRequestDto(fileName = fileName, csv = csv))
        }.body()

    suspend fun salesImportCommit(
        token: String,
        batchId: String,
        mapping: Map<String, String>? = null
    ): RemoteImportBatch =
        httpClient.post("$baseUrl/api/sales/imports/$batchId/commit") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                SalesCommitRequestDto(
                    mapping = mapping ?: emptyMap()
                )
            )
        }.body()

    companion object {
        fun fromEnvironment(): BackendApiClient? {
            val configuredUrl = BackendEnvironment.baseUrl.trim().trimEnd('/')
            if (configuredUrl.isBlank()) return null
            validateSecureUrl(configuredUrl)
            return BackendApiClient(
                baseUrl = configuredUrl,
                httpClient = createAppHttpClient()
            )
        }

        private fun validateSecureUrl(url: String) {
            val parsed = Url(url)
            if (parsed.protocol.name == "https") return
            val host = parsed.host.lowercase()
            val isLocalDev = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
            require(isLocalDev && parsed.protocol.name == "http") {
                "A API do Xocoalho deve usar HTTPS fora de hosts locais de desenvolvimento."
            }
        }
    }
}

data class RemoteWorkClockSummary(
    val absenceDays: Int,
    val absenceDates: List<String>,
    val absenceDetails: List<WorkClockAbsenceDetail>
)

data class RemoteLoginResult(
    val token: String? = null,
    val user: User? = null,
    val remoteUserId: String? = null,
    val requiresTwoFactor: Boolean = false,
    val challengeId: String? = null,
    val deliveryHint: String? = null,
    val developmentCode: String? = null
)

@Serializable
private data class LoginRequestDto(
    val email: String,
    val password: String,
    val deviceId: String,
    val deviceName: String
)

@Serializable
private data class VerifyDeviceRequestDto(
    val challengeId: String,
    val code: String,
    val deviceId: String,
    val deviceName: String
)

@Serializable
private data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
private data class LoginResponseDto(
    val token: String? = null,
    val user: PublicUserDto? = null,
    val requiresTwoFactor: Boolean = false,
    val challengeId: String? = null,
    val deliveryHint: String? = null,
    val developmentCode: String? = null
) {
    fun toResult(): RemoteLoginResult {
        return RemoteLoginResult(
            token = token,
            user = user?.toDomain(),
            remoteUserId = user?.id,
            requiresTwoFactor = requiresTwoFactor,
            challengeId = challengeId,
            deliveryHint = deliveryHint,
            developmentCode = developmentCode
        )
    }
}

@Serializable
private data class PublicUserDto(
    val id: String,
    val name: String,
    val email: String,
    val area: String,
    val workSector: String,
    val permissionLevel: String,
    val allowedAreas: List<String>,
    val createdAt: Long,
    val permissions: FeaturePermissionsDto = FeaturePermissionsDto(),
    val mustChangePassword: Boolean = false
) {
    fun toDomain(): User {
        val level = PermissionLevel.fromString(permissionLevel)
        return User(
            id = 0L,
            name = name,
            email = email,
            password = "",
            area = Area.fromString(area),
            workSector = WorkSector.fromString(workSector),
            permissionLevel = level,
            allowedAreas = if (level == PermissionLevel.ADMIN) {
                Area.entries.toList()
            } else {
                val parsed = allowedAreas.map { Area.fromString(it) }
                if (parsed.isEmpty()) listOf(WorkSector.fromString(workSector).activityArea) else parsed
            },
            createdAt = createdAt,
            featurePermissions = permissions.toDomain(),
            mustChangePassword = mustChangePassword
        )
    }
}

@Serializable
private data class FeaturePermissionsDto(
    val canRegisterUsers: Boolean = false,
    val canCreateActivities: Boolean = false,
    val canEditUsers: Boolean = false,
    val canCreateInventoryCounts: Boolean = false,
    val canViewInventoryInsights: Boolean = false,
    val canManageAdministrativeStock: Boolean = false,
    val canImportPurchases: Boolean = false
) {
    fun toDomain(): FeaturePermissions {
        return FeaturePermissions(
            canRegisterUsers = canRegisterUsers,
            canCreateActivities = canCreateActivities,
            canEditUsers = canEditUsers,
            canCreateInventoryCounts = canCreateInventoryCounts,
            canViewInventoryInsights = canViewInventoryInsights,
            canManageAdministrativeStock = canManageAdministrativeStock,
            canImportPurchases = canImportPurchases
        )
    }

    companion object {
        fun fromDomain(permissions: FeaturePermissions): FeaturePermissionsDto {
            return FeaturePermissionsDto(
                canRegisterUsers = permissions.canRegisterUsers,
                canCreateActivities = permissions.canCreateActivities,
                canEditUsers = permissions.canEditUsers,
                canCreateInventoryCounts = permissions.canCreateInventoryCounts,
                canViewInventoryInsights = permissions.canViewInventoryInsights,
                canManageAdministrativeStock = permissions.canManageAdministrativeStock,
                canImportPurchases = permissions.canImportPurchases
            )
        }
    }
}

@Serializable
private data class CreateUserRequestDto(
    val name: String,
    val email: String,
    val password: String,
    val workSector: String,
    val permissionLevel: String,
    val permissions: FeaturePermissionsDto
)

@Serializable
private data class PermissionUpdateRequestDto(
    val permissions: FeaturePermissionsDto
)

@Serializable
private data class WorksiteInfoDto(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val name: String
)

@Serializable
private data class WorkClockSummaryDto(
    val absenceDays: Int = 0,
    val absenceDates: List<String> = emptyList(),
    val absenceDetails: List<WorkClockAbsenceDetailDto> = emptyList()
)

@Serializable
private data class WorkClockAbsenceDetailDto(
    val date: String,
    val reason: String
)

@Serializable
data class DashboardStatsDto(
    val totalUsers: Int = 0,
    val totalActivities: Int = 0,
    val totalCompletions: Int = 0,
    val pendingSyncItems: Int = 0,
    val activitiesByArea: Map<String, Int> = emptyMap()
)

@Serializable
data class InventoryCountSessionDto(
    val id: String,
    val countDate: String,
    val countedAt: String,
    val location: String
)

@Serializable private data class InventoryCountRequestDto(val countDate:String,val countedAt:String,val location:String="Beco da Praia",val items:List<InventoryCountItemDto>)
@Serializable private data class InventoryCountItemDto(val name:String,val quantity:Double,val category:String,val volume:Double,val volumeUnit:String,val salePriceInCents:Long,val costPriceInCents:Long?,val condition:String)
@Serializable private data class InventoryAuditRequestDto(val date:String,val location:String="Beco da Praia")
@Serializable data class RemoteInventoryAudit(val date:String,val location:String,val items:List<RemoteInventoryAuditItem> = emptyList(),val totalOpening:Double=0.0,val totalSold:Double=0.0,val totalRemaining:Double=0.0)
@Serializable data class RemoteInventoryAuditItem(val product:String,val status:String,val notes:String,val openingQuantity:Double=0.0,val soldQuantity:Double=0.0,val theoreticalRemaining:Double=0.0)
@Serializable data class RemoteApplyDailyAudit(val audit:RemoteInventoryAudit?=null,val balances:List<RemoteAdminStockBalance> = emptyList(),val alreadyApplied:Boolean=false)
@Serializable data class RemoteAdminStockBalance(val productKey:String,val productName:String,val location:String,val quantity:Double=0.0)

@Serializable private data class SalesPreviewRequestDto(val fileName: String, val csv: String)
@Serializable private data class SalesCommitRequestDto(
    val datasetId: String = "sales",
    val mapping: Map<String, String> = emptyMap(),
    val preserveColumns: List<String> = emptyList()
)
@Serializable data class RemoteImportBatch(
    val id: String,
    val fileName: String? = null,
    val status: String = "PREVIEW",
    val headers: List<String> = emptyList(),
    val sampleRows: List<Map<String, String>> = emptyList(),
    val suggestedMapping: Map<String, String> = emptyMap(),
    val mapping: Map<String, String> = emptyMap(),
    val errors: List<RemoteImportError> = emptyList(),
    val totalRows: Int = 0,
    val importedRows: Int = 0
)
@Serializable data class RemoteImportError(
    val row: Int = 0,
    val field: String? = null,
    val message: String = ""
)

@Serializable
private data class SyncPushRequestDto(
    val deviceId: String? = null,
    val workClockEntries: List<WorkClockEntryDto> = emptyList()
)

@Serializable
private data class WorkClockEntryDto(
    val id: String,
    val userId: String,
    val type: String,
    val registeredAt: Long,
    val latitude: Double,
    val longitude: Double,
    val distanceFromWorkMeters: Double,
    val isLate: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "SYNCED"
)
