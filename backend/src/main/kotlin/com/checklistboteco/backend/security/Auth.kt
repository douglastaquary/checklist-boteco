package com.checklistboteco.backend.security

import com.checklistboteco.backend.model.ApiError
import com.checklistboteco.backend.model.PermissionLevelDto
import com.checklistboteco.backend.store.AppStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond

suspend fun ApplicationCall.requireToken(tokenService: TokenService): TokenPayload? {
    val token = request.header("Authorization")
        ?.removePrefix("Bearer ")
        ?.trim()
    val payload = token?.let(tokenService::verify)
    if (payload == null) {
        respond(HttpStatusCode.Unauthorized, ApiError("Token inválido ou ausente"))
    }
    return payload
}

suspend fun ApplicationCall.requireAdmin(tokenService: TokenService, store: AppStore): TokenPayload? {
    val payload = requireToken(tokenService) ?: return null
    val user = store.getUser(payload.userId)
    if (user?.permissionLevel != PermissionLevelDto.ADMIN) {
        respond(HttpStatusCode.Forbidden, ApiError("Permissão administrativa necessária"))
        return null
    }
    return payload
}
