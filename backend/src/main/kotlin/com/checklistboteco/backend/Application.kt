package com.checklistboteco.backend

import com.checklistboteco.backend.model.ApiError
import com.checklistboteco.backend.routes.configureRoutes
import com.checklistboteco.backend.security.TokenService
import com.checklistboteco.backend.store.AppStore
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import java.nio.file.Paths
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        checklistBotecoBackend()
    }.start(wait = true)
}

fun Application.checklistBotecoBackend(
    store: AppStore = AppStore(Paths.get(System.getenv("CHECKLIST_BOTECO_DB") ?: "backend-data/checklist-boteco.db")),
    tokenService: TokenService = TokenService(System.getenv("JWT_SECRET") ?: "change-me-in-production")
) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Options)
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiError(cause.message ?: "Requisição inválida"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled backend error", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Erro interno"))
        }
    }
    configureRoutes(store, tokenService)
}
