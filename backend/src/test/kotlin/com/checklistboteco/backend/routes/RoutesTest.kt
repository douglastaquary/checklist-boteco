package com.checklistboteco.backend.routes

import com.checklistboteco.backend.checklistBotecoBackend
import com.checklistboteco.backend.model.ActivityDto
import com.checklistboteco.backend.model.AreaDto
import com.checklistboteco.backend.model.CreateActivityRequest
import com.checklistboteco.backend.model.FrequencyDto
import com.checklistboteco.backend.model.LoginRequest
import com.checklistboteco.backend.model.LoginResponse
import com.checklistboteco.backend.model.VerifyDeviceRequest
import com.checklistboteco.backend.security.PasswordHasher
import com.checklistboteco.backend.security.TokenService
import com.checklistboteco.backend.store.AppStore
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class RoutesTest {
    @Test
    fun adminCanLoginAndCreateActivity() = testApplication {
        val store = testStore()
        application {
            checklistBotecoBackend(store = store, tokenService = TokenService("test"))
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val firstLogin = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("admin@checklistboteco.com", "admin123", deviceId = "test-device"))
        }.body<LoginResponse>()
        val login = client.post("/api/auth/verify-device") {
            contentType(ContentType.Application.Json)
            setBody(
                VerifyDeviceRequest(
                    challengeId = firstLogin.challengeId!!,
                    code = firstLogin.developmentCode!!,
                    deviceId = "test-device",
                    deviceName = "JUnit"
                )
            )
        }.body<LoginResponse>()

        val response = client.post("/api/activities") {
            bearerAuth(login.token!!)
            contentType(ContentType.Application.Json)
            setBody(CreateActivityRequest("Organizar salão", AreaDto.ATENDIMENTO, FrequencyDto.DIARIO, 2))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("Organizar salão", response.body<ActivityDto>().name)
    }

    @Test
    fun usersEndpointRequiresToken() = testApplication {
        application {
            checklistBotecoBackend(store = testStore(), tokenService = TokenService("test"))
        }

        val response = client.get("/api/users")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun adminWebDoesNotExposeWorkClockSection() = testApplication {
        application {
            checklistBotecoBackend(store = testStore(), tokenService = TokenService("test"))
        }

        val html = client.get("/").body<String>()

        assertTrue("Ponto" !in html)
        assertTrue("Usuários e permissões" in html)
    }

    private fun testStore(): AppStore {
        val path = Files.createTempDirectory("checklist-boteco-test").resolve("checklist-boteco.db")
        return AppStore(path, passwordHasher = PasswordHasher(iterations = 1_000))
    }
}
