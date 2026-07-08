package com.checklistboteco.platform

import com.checklistboteco.data.remote.BackendApiClient
import kotlin.coroutines.cancellation.CancellationException

class RemoteSessionRequiredException(message: String) : CancellationException(message)

fun requireRemoteToken(api: BackendApiClient?, token: String?): String {
    if (api == null) return token.orEmpty()
    if (!token.isNullOrBlank()) return token
    val message = "Faça login novamente"
    SessionExpiredNotifier.notify(message)
    throw RemoteSessionRequiredException(message)
}
