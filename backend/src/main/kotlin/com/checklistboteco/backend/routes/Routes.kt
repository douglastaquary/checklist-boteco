package com.checklistboteco.backend.routes

import com.checklistboteco.backend.model.ApiError
import com.checklistboteco.backend.model.CreateActivityRequest
import com.checklistboteco.backend.model.CreateUserRequest
import com.checklistboteco.backend.model.LoginRequest
import com.checklistboteco.backend.model.LoginResponse
import com.checklistboteco.backend.model.PermissionUpdateRequest
import com.checklistboteco.backend.model.SyncPullResponse
import com.checklistboteco.backend.model.SyncPushRequest
import com.checklistboteco.backend.model.VerifyDeviceRequest
import com.checklistboteco.backend.model.publicDto
import com.checklistboteco.backend.model.PermissionLevelDto
import com.checklistboteco.backend.security.TokenService
import com.checklistboteco.backend.security.requireAdmin
import com.checklistboteco.backend.security.requireToken
import com.checklistboteco.backend.store.AppStore
import com.checklistboteco.backend.web.adminWebPage
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRoutes(store: AppStore, tokenService: TokenService) {
    routing {
        get("/") {
            call.respondText(adminWebPage(), ContentType.Text.Html)
        }

        route("/api") {
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }

            post("/auth/login") {
                val request = call.receive<LoginRequest>()
                val user = store.authenticate(request.email, request.password)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, ApiError("Email ou senha inválidos"))
                    return@post
                }
                val deviceId = request.deviceId?.trim()
                if (deviceId.isNullOrBlank() || !store.isTrustedDevice(user.id, deviceId)) {
                    val challenge = store.createDeviceChallenge(user.id, deviceId ?: "unknown-device", request.deviceName)
                    call.respond(
                        LoginResponse(
                            requiresTwoFactor = true,
                            challengeId = challenge.id,
                            deliveryHint = "Código de verificação gerado para confirmação do dispositivo",
                            developmentCode = challenge.code
                        )
                    )
                    return@post
                }
                call.respond(
                    LoginResponse(
                        token = tokenService.issue(user.id, user.permissionLevel.name == "ADMIN"),
                        user = user.publicDto()
                    )
                )
            }

            post("/auth/verify-device") {
                val request = call.receive<VerifyDeviceRequest>()
                val user = store.verifyDeviceChallenge(
                    challengeId = request.challengeId,
                    code = request.code,
                    deviceId = request.deviceId,
                    deviceName = request.deviceName
                )
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, ApiError("Código de verificação inválido ou expirado"))
                    return@post
                }
                call.respond(
                    LoginResponse(
                        token = tokenService.issue(user.id, user.permissionLevel == PermissionLevelDto.ADMIN),
                        user = user.publicDto()
                    )
                )
            }

            get("/users") {
                if (call.requireAdmin(tokenService, store) == null) return@get
                call.respond(store.users())
            }

            post("/users") {
                if (call.requireAdmin(tokenService, store) == null) return@post
                call.respond(HttpStatusCode.Created, store.createUser(call.receive<CreateUserRequest>()))
            }

            patch("/users/{id}/permissions") {
                if (call.requireAdmin(tokenService, store) == null) return@patch
                val id = call.parameters["id"]
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("ID obrigatório"))
                    return@patch
                }
                call.respond(store.updatePermissions(id, call.receive<PermissionUpdateRequest>().permissions))
            }

            get("/activities") {
                if (call.requireToken(tokenService) == null) return@get
                call.respond(store.activities())
            }

            post("/activities") {
                if (call.requireAdmin(tokenService, store) == null) return@post
                call.respond(HttpStatusCode.Created, store.createActivity(call.receive<CreateActivityRequest>()))
            }

            get("/completions") {
                if (call.requireToken(tokenService) == null) return@get
                call.respond(store.completions())
            }

            get("/admin/dashboard") {
                if (call.requireAdmin(tokenService, store) == null) return@get
                call.respond(store.dashboard())
            }

            get("/sync/pull") {
                if (call.requireToken(tokenService) == null) return@get
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val data = store.pullSince(since)
                call.respond(
                    SyncPullResponse(
                        serverTime = System.currentTimeMillis(),
                        users = data.users,
                        activities = data.activities,
                        completions = data.completions,
                        workClockEntries = data.workClockEntries
                    )
                )
            }

            post("/sync/push") {
                val payload = call.requireToken(tokenService) ?: return@post
                val request = call.receive<SyncPushRequest>()
                store.upsertActivities(request.activities)
                store.upsertCompletions(request.completions)
                store.upsertWorkClockEntries(request.workClockEntries.filter { it.userId == payload.userId })
                val data = store.pullSince(0)
                call.respond(
                    SyncPullResponse(
                        serverTime = System.currentTimeMillis(),
                        users = data.users,
                        activities = data.activities,
                        completions = data.completions,
                        workClockEntries = data.workClockEntries
                    )
                )
            }
        }
    }
}
