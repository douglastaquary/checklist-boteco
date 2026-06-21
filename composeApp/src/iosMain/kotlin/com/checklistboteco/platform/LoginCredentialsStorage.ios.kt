package com.checklistboteco.platform

actual object LoginCredentialsStorage {
    private var cached = SavedLoginCredentials()

    actual fun initialize(platformContext: Any?) = Unit

    actual fun load(): SavedLoginCredentials = cached

    actual suspend fun save(username: String, password: String, remember: Boolean): Result<Unit> {
        cached = if (remember) {
            SavedLoginCredentials(
                username = username.trim(),
                password = password,
                remember = true
            )
        } else {
            SavedLoginCredentials()
        }
        return Result.success(Unit)
    }

    actual suspend fun unlockRememberedCredentials(): Result<UnlockedLoginCredentials> {
        return if (cached.remember && cached.username.isNotBlank()) {
            Result.success(
                UnlockedLoginCredentials(
                    username = cached.username,
                    password = cached.password
                )
            )
        } else {
            Result.failure(IllegalStateException("Nenhum login salvo"))
        }
    }

    actual fun clear() {
        cached = SavedLoginCredentials()
    }
}
