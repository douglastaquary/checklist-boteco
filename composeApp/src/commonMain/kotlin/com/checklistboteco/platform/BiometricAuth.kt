package com.checklistboteco.platform

expect object BiometricAuth {
    fun initialize(platformContext: Any? = null)
    fun isAvailable(): Boolean
    suspend fun authenticate(
        title: String,
        subtitle: String,
        crypto: Any? = null
    ): Result<Unit>
}
