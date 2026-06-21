package com.checklistboteco.data.remote

import com.checklistboteco.domain.model.Area
import com.checklistboteco.domain.model.FeaturePermissions
import com.checklistboteco.domain.model.PermissionLevel
import com.checklistboteco.domain.model.User
import com.checklistboteco.domain.model.WorkClockType
import com.checklistboteco.domain.model.WorkSector
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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
                "A API do Checklist Boteco deve usar HTTPS fora de hosts locais de desenvolvimento."
            }
        }
    }
}

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
    val permissions: FeaturePermissionsDto = FeaturePermissionsDto()
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
                allowedAreas.map { Area.fromString(it) }
            },
            createdAt = createdAt,
            featurePermissions = permissions.toDomain()
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
    val canManageAdministrativeStock: Boolean = false
) {
    fun toDomain(): FeaturePermissions {
        return FeaturePermissions(
            canRegisterUsers = canRegisterUsers,
            canCreateActivities = canCreateActivities,
            canEditUsers = canEditUsers,
            canCreateInventoryCounts = canCreateInventoryCounts,
            canViewInventoryInsights = canViewInventoryInsights,
            canManageAdministrativeStock = canManageAdministrativeStock
        )
    }
}

@Serializable private data class InventoryCountRequestDto(val countDate:String,val countedAt:String,val location:String="Beco da Praia",val items:List<InventoryCountItemDto>)
@Serializable private data class InventoryCountItemDto(val name:String,val quantity:Double,val category:String,val volume:Double,val volumeUnit:String,val salePriceInCents:Long,val costPriceInCents:Long?,val condition:String)
@Serializable private data class InventoryAuditRequestDto(val date:String,val location:String="Beco da Praia")
@Serializable data class RemoteInventoryAudit(val date:String,val location:String,val items:List<RemoteInventoryAuditItem> = emptyList(),val totalOpening:Double=0.0,val totalSold:Double=0.0,val totalRemaining:Double=0.0)
@Serializable data class RemoteInventoryAuditItem(val product:String,val status:String,val notes:String,val openingQuantity:Double=0.0,val soldQuantity:Double=0.0,val theoreticalRemaining:Double=0.0)
@Serializable data class RemoteApplyDailyAudit(val audit:RemoteInventoryAudit?=null,val balances:List<RemoteAdminStockBalance> = emptyList(),val alreadyApplied:Boolean=false)
@Serializable data class RemoteAdminStockBalance(val productKey:String,val productName:String,val location:String,val quantity:Double=0.0)

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
