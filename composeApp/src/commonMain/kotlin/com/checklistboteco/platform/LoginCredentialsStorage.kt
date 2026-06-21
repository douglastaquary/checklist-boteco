package com.checklistboteco.platform

data class SavedLoginCredentials(
    val username: String = "",
    val password: String = "",
    val remember: Boolean = false,
    val requiresBiometricUnlock: Boolean = false
)

data class UnlockedLoginCredentials(
    val username: String,
    val password: String
)

expect object LoginCredentialsStorage {
    fun initialize(platformContext: Any? = null)
    fun load(): SavedLoginCredentials
    suspend fun save(username: String, password: String, remember: Boolean): Result<Unit>
    suspend fun unlockRememberedCredentials(): Result<UnlockedLoginCredentials>
    fun clear()
}
