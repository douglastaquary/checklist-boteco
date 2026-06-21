package com.checklistboteco.data.remote

import com.checklistboteco.platform.AppErrorMapper
import com.checklistboteco.platform.AppNetworkFeedback
import com.checklistboteco.platform.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private fun HttpRequestBuilder.shouldTrackLoading(): Boolean {
    return !url.encodedPath.endsWith("/api/health")
}

fun createAppHttpClient(
    json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.value < 400) return@validateResponse
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                throw ApiException(AppErrorMapper.fromHttp(response.status.value, body))
            }
        }
    }.also { client ->
        client.plugin(HttpSend).intercept { request ->
            val trackLoading = request.shouldTrackLoading()
            if (trackLoading) AppNetworkFeedback.onRequestStarted()
            try {
                execute(request)
            } finally {
                if (trackLoading) AppNetworkFeedback.onRequestFinished()
            }
        }
    }
}
