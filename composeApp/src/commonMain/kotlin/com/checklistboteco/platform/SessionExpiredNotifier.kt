package com.checklistboteco.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class SessionExpiredEvent(val reason: String)

object SessionExpiredNotifier {
    private val lock = Any()
    private var pending: SessionExpiredEvent? = null

    private val _events = MutableSharedFlow<SessionExpiredEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SessionExpiredEvent> = _events.asSharedFlow()

    val isHandling: Boolean
        get() = synchronized(lock) { pending != null }

    fun notify(reason: String) {
        val message = reason.trim().ifBlank { "Sua sessão expirou. Entre novamente." }
        val shouldEmit = synchronized(lock) {
            if (pending != null) return@synchronized false
            pending = SessionExpiredEvent(message)
            true
        }
        if (shouldEmit) {
            _events.tryEmit(SessionExpiredEvent(message))
        }
    }

    fun reset() {
        synchronized(lock) {
            pending = null
        }
    }
}
