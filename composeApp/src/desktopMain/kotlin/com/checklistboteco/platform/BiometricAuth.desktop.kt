package com.checklistboteco.platform

actual object BiometricAuth {
    actual fun initialize(platformContext: Any?) = Unit

    actual fun isAvailable(): Boolean = false

    actual suspend fun authenticate(
        title: String,
        subtitle: String,
        crypto: Any?
    ): Result<Unit> = Result.success(Unit)
}
