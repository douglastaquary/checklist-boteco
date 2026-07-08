package com.checklistboteco.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppNetworkFeedback {
    private val lock = Any()
    private var activeRequests = 0

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorDialog = MutableStateFlow<String?>(null)
    val errorDialog: StateFlow<String?> = _errorDialog.asStateFlow()

    fun onRequestStarted() {
        synchronized(lock) {
            activeRequests++
            if (activeRequests == 1) {
                _isLoading.value = true
            }
        }
    }

    fun onRequestFinished() {
        synchronized(lock) {
            activeRequests = (activeRequests - 1).coerceAtLeast(0)
            if (activeRequests == 0) {
                _isLoading.value = false
            }
        }
    }

    fun showError(message: String) {
        if (message.isBlank()) return
        if (SessionExpiredNotifier.isHandling) return
        _errorDialog.value = message
    }

    fun dismissError() {
        _errorDialog.value = null
    }
}
