package com.checklistboteco.platform

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object AppErrorMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun toUserMessage(error: Throwable): String {
        return when (error) {
            is ApiException -> error.userMessage
            is ClientRequestException -> fromHttp(error.response.status.value, error.message.orEmpty())
            is ServerResponseException -> fromHttp(error.response.status.value, error.message.orEmpty())
            is ResponseException -> fromHttp(error.response.status.value, error.message.orEmpty())
            is ConnectTimeoutException, is SocketTimeoutException ->
                "A conexão demorou demais. Verifique a internet e tente novamente."
            is IOException ->
                "Sem conexão com a internet ou servidor indisponível. Tente novamente em instantes."
            else -> when {
                error.message?.contains("Backend não configurado", ignoreCase = true) == true ->
                    "Este aparelho não está configurado para acessar o servidor. Verifique a URL da API."
                error.message?.contains("login novamente", ignoreCase = true) == true ->
                    "Sua sessão expirou. Saia e entre novamente."
                error.message?.contains("Código de verificação", ignoreCase = true) == true ->
                    error.message!!
                error.message?.contains("Email ou senha", ignoreCase = true) == true ->
                    "Usuário ou senha inválidos."
                error.message?.contains("HTTPS", ignoreCase = true) == true ->
                    "A conexão com o servidor não é segura. Contate o suporte."
                !error.message.isNullOrBlank() -> error.message!!
                else -> "Não foi possível concluir a operação. Tente novamente."
            }
        }
    }

    fun fromHttp(status: Int, rawBody: String): String {
        parseMessage(rawBody)?.let { return it }
        return when (status) {
            400 -> "Os dados enviados são inválidos. Revise as informações e tente novamente."
            401 -> "Sessão expirada ou credenciais inválidas. Faça login novamente."
            403 -> "Você não tem permissão para esta ação."
            404 -> "Recurso não encontrado no servidor."
            408, 504 -> "O servidor demorou para responder. Tente novamente."
            in 500..599 -> "O servidor encontrou um problema. Tente novamente em instantes."
            else -> "Não foi possível concluir a operação (erro $status)."
        }
    }

    private fun parseMessage(rawBody: String): String? {
        if (rawBody.isBlank()) return null
        return runCatching {
            val element = json.parseToJsonElement(rawBody)
            element.jsonObject["message"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}
