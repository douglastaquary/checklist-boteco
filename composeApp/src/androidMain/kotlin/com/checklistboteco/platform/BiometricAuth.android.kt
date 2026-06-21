package com.checklistboteco.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

actual object BiometricAuth {
    private var activity: FragmentActivity? = null

    actual fun initialize(platformContext: Any?) {
        activity = platformContext as? FragmentActivity
    }

    actual fun isAvailable(): Boolean {
        val host = activity ?: return false
        return BiometricManager.from(host).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    actual suspend fun authenticate(
        title: String,
        subtitle: String,
        crypto: Any?
    ): Result<Unit> {
        val host = activity ?: return Result.failure(IllegalStateException("Tela indisponível"))
        if (!isAvailable()) {
            return Result.failure(IllegalStateException("Biometria indisponível neste aparelho"))
        }

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(host)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(IllegalStateException(errString.toString()))
                        )
                    }
                }

                override fun onAuthenticationFailed() = Unit
            }

            val prompt = BiometricPrompt(host, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Cancelar")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            val cipher = crypto as? Cipher
            if (cipher != null) {
                prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
            } else {
                prompt.authenticate(promptInfo)
            }
        }
    }
}
