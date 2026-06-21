package com.checklistboteco.data.remote

import com.checklistboteco.data.sync.SyncPullResponse
import com.checklistboteco.data.sync.SyncPushRequest
import com.checklistboteco.data.sync.SyncPushResponse
import com.checklistboteco.data.sync.SyncSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType

class SyncApiClient private constructor(
    private val baseUrl: String,
    private val httpClient: HttpClient
) {
    suspend fun push(
        session: SyncSession,
        batchId: String,
        request: SyncPushRequest
    ): SyncPushResponse {
        return httpClient.post("$baseUrl/api/sync/push") {
            bearerAuth(session.authToken)
            header("Idempotency-Key", batchId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun pull(
        session: SyncSession,
        cursor: String?,
        limit: Int
    ): SyncPullResponse {
        return httpClient.get("$baseUrl/api/sync/pull") {
            bearerAuth(session.authToken)
            if (!cursor.isNullOrBlank()) {
                parameter("cursor", cursor)
            }
            parameter("limit", limit)
        }.body()
    }

    companion object {
        fun fromEnvironment(): SyncApiClient? {
            val configuredUrl = BackendEnvironment.baseUrl.trim().trimEnd('/')
            if (configuredUrl.isBlank()) return null
            validateSecureUrl(configuredUrl)
            return SyncApiClient(
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
