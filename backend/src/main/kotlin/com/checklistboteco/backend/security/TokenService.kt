package com.checklistboteco.backend.security

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class TokenService(
    private val secret: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = 24L * 60L * 60L * 1000L
) {
    private val json = Json { encodeDefaults = true }

    fun issue(userId: String, isAdmin: Boolean): String {
        val payload = TokenPayload(
            userId = userId,
            isAdmin = isAdmin,
            expiresAt = clock() + ttlMillis
        )
        val payloadPart = json.encodeToString(TokenPayload.serializer(), payload).base64Url()
        val signature = sign(payloadPart)
        return "$payloadPart.$signature"
    }

    fun verify(token: String): TokenPayload? {
        val parts = token.split(".")
        if (parts.size != 2) return null
        if (sign(parts[0]) != parts[1]) return null
        val payload = runCatching {
            json.decodeFromString(TokenPayload.serializer(), String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8))
        }.getOrNull() ?: return null
        return payload.takeIf { it.expiresAt > clock() }
    }

    private fun sign(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun String.base64Url(): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(StandardCharsets.UTF_8))
    }
}

@Serializable
data class TokenPayload(
    val userId: String,
    val isAdmin: Boolean,
    val expiresAt: Long
)
