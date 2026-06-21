package com.checklistboteco.platform

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private object RememberUserKeystore {
    private const val keyAlias = "checklist_boteco_remember_user_bio"
    private const val legacyKeyAlias = "checklist_boteco_remember_user"
    private const val transformation = "AES/GCM/NoPadding"
    private const val gcmTagLength = 128
    private const val ivLength = 12

    fun deleteKeys() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        listOf(keyAlias, legacyKeyAlias).forEach { alias ->
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
    }

    private fun ensureBiometricKey(requireBiometric: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(legacyKeyAlias)) {
            keyStore.deleteEntry(legacyKeyAlias)
        }
        if (!keyStore.containsAlias(keyAlias)) {
            val builder = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            if (requireBiometric) {
                builder.setUserAuthenticationRequired(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationValidityDurationSeconds(-1)
                    builder.setInvalidatedByBiometricEnrollment(true)
                }
            }
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(builder.build()) }
                .generateKey()
        }
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    fun prepareEncryptCipher(requireBiometric: Boolean): Cipher {
        val cipher = Cipher.getInstance(transformation)
        initCipher(cipher, Cipher.ENCRYPT_MODE, ensureBiometricKey(requireBiometric))
        return cipher
    }

    fun prepareDecryptCipher(payload: String, requireBiometric: Boolean): Cipher {
        val decoded = Base64.decode(payload, Base64.NO_WRAP)
        val iv = decoded.copyOfRange(0, ivLength)
        val cipher = Cipher.getInstance(transformation)
        initCipher(
            cipher,
            Cipher.DECRYPT_MODE,
            ensureBiometricKey(requireBiometric),
            GCMParameterSpec(gcmTagLength, iv)
        )
        return cipher
    }

    private fun initCipher(
        cipher: Cipher,
        mode: Int,
        key: SecretKey,
        spec: GCMParameterSpec? = null
    ) {
        try {
            if (spec == null) {
                cipher.init(mode, key)
            } else {
                cipher.init(mode, key, spec)
            }
        } catch (_: UserNotAuthenticatedException) {
            // Esperado para chaves protegidas por biometria.
        } catch (error: KeyPermanentlyInvalidatedException) {
            deleteKeys()
            throw error
        }
    }

    fun encrypt(cipher: Cipher, plainText: String): String {
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(cipher: Cipher, payload: String): String {
        val decoded = Base64.decode(payload, Base64.NO_WRAP)
        val encrypted = decoded.copyOfRange(ivLength, decoded.size)
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}

private object CredentialsCodec {
    private const val separator = '\u0000'

    fun pack(username: String, password: String): String {
        return "${username.trim()}$separator$password"
    }

    fun unpack(payload: String): UnlockedLoginCredentials {
        val separatorIndex = payload.indexOf(separator)
        require(separatorIndex > 0) { "Credenciais salvas inválidas" }
        return UnlockedLoginCredentials(
            username = payload.substring(0, separatorIndex),
            password = payload.substring(separatorIndex + 1)
        )
    }
}

actual object LoginCredentialsStorage {
    private const val prefsName = "checklist_boteco_login"
    private const val rememberKey = "remember"
    private const val credentialsEncKey = "credentials_enc"
    private const val legacyUsernameEncKey = "username_enc"
    private const val legacyUsernameKey = "username"
    private const val legacyPasswordKey = "password"
    private var context: Context? = null

    actual fun initialize(platformContext: Any?) {
        context = (platformContext as? Context)?.applicationContext ?: context
        BiometricAuth.initialize(platformContext)
    }

    actual fun load(): SavedLoginCredentials {
        val prefs = prefsOrNull() ?: return SavedLoginCredentials()
        migrateLegacyPlaintext(prefs)

        val remember = prefs.getBoolean(rememberKey, false)
        if (!remember) return SavedLoginCredentials(remember = false)

        val encryptedCredentials = prefs.getString(credentialsEncKey, null)
            ?: prefs.getString(legacyUsernameEncKey, null)
        if (encryptedCredentials.isNullOrBlank()) {
            return SavedLoginCredentials(remember = true)
        }

        if (BiometricAuth.isAvailable()) {
            return SavedLoginCredentials(
                remember = true,
                requiresBiometricUnlock = true
            )
        }

        val unlocked = decryptWithoutPrompt(encryptedCredentials)
        return SavedLoginCredentials(
            username = unlocked.username,
            password = unlocked.password,
            remember = true
        )
    }

    actual suspend fun save(username: String, password: String, remember: Boolean): Result<Unit> {
        val prefs = prefsOrNull() ?: return Result.failure(IllegalStateException("Armazenamento indisponível"))
        if (!remember) {
            prefs.edit().apply {
                putBoolean(rememberKey, false)
                remove(credentialsEncKey)
                remove(legacyUsernameEncKey)
                remove(legacyUsernameKey)
                remove(legacyPasswordKey)
            }.apply()
            RememberUserKeystore.deleteKeys()
            return Result.success(Unit)
        }

        val trimmedUsername = username.trim()
        if (trimmedUsername.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Usuário e senha são obrigatórios para salvar"))
        }

        val payload = CredentialsCodec.pack(trimmedUsername, password)
        return if (BiometricAuth.isAvailable()) {
            saveWithBiometric(prefs, payload)
        } else {
            saveWithoutBiometric(prefs, payload)
        }
    }

    actual suspend fun unlockRememberedCredentials(): Result<UnlockedLoginCredentials> {
        val prefs = prefsOrNull() ?: return Result.failure(IllegalStateException("Armazenamento indisponível"))
        val encryptedCredentials = prefs.getString(credentialsEncKey, null)
            ?: prefs.getString(legacyUsernameEncKey, null)
            ?: return Result.failure(IllegalStateException("Nenhum login salvo"))

        if (!BiometricAuth.isAvailable()) {
            return Result.success(decryptWithoutPrompt(encryptedCredentials))
        }

        val cipher = RememberUserKeystore.prepareDecryptCipher(encryptedCredentials, requireBiometric = true)
        return BiometricAuth.authenticate(
            title = "Desbloquear login",
            subtitle = "Confirme sua biometria para preencher usuário e senha",
            crypto = cipher
        ).mapCatching {
            val decrypted = RememberUserKeystore.decrypt(cipher, encryptedCredentials)
            decodeStoredCredentials(decrypted)
        }
    }

    actual fun clear() {
        val prefs = prefsOrNull() ?: return
        prefs.edit().clear().apply()
        RememberUserKeystore.deleteKeys()
    }

    private suspend fun saveWithBiometric(
        prefs: SharedPreferences,
        payload: String
    ): Result<Unit> {
        RememberUserKeystore.deleteKeys()
        val cipher = RememberUserKeystore.prepareEncryptCipher(requireBiometric = true)
        return BiometricAuth.authenticate(
            title = "Salvar login",
            subtitle = "Confirme sua biometria para lembrar usuário e senha",
            crypto = cipher
        ).mapCatching {
            val encrypted = RememberUserKeystore.encrypt(cipher, payload)
            prefs.edit().apply {
                putBoolean(rememberKey, true)
                putString(credentialsEncKey, encrypted)
                remove(legacyUsernameEncKey)
                remove(legacyUsernameKey)
                remove(legacyPasswordKey)
            }.apply()
        }
    }

    private fun saveWithoutBiometric(prefs: SharedPreferences, payload: String): Result<Unit> {
        return runCatching {
            RememberUserKeystore.deleteKeys()
            val cipher = RememberUserKeystore.prepareEncryptCipher(requireBiometric = false)
            val encrypted = RememberUserKeystore.encrypt(cipher, payload)
            prefs.edit().apply {
                putBoolean(rememberKey, true)
                putString(credentialsEncKey, encrypted)
                remove(legacyUsernameEncKey)
                remove(legacyUsernameKey)
                remove(legacyPasswordKey)
            }.apply()
        }
    }

    private fun decryptWithoutPrompt(payload: String): UnlockedLoginCredentials {
        val cipher = RememberUserKeystore.prepareDecryptCipher(payload, requireBiometric = false)
        val decrypted = RememberUserKeystore.decrypt(cipher, payload)
        return decodeStoredCredentials(decrypted)
    }

    private fun decodeStoredCredentials(decrypted: String): UnlockedLoginCredentials {
        return runCatching { CredentialsCodec.unpack(decrypted) }
            .getOrElse {
                UnlockedLoginCredentials(username = decrypted, password = "")
            }
    }

    private fun prefsOrNull(): SharedPreferences? {
        val appContext = context ?: return null
        return appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    private fun migrateLegacyPlaintext(prefs: SharedPreferences) {
        val legacyPassword = prefs.getString(legacyPasswordKey, null)
        val legacyUsername = prefs.getString(legacyUsernameKey, null)
        if (legacyPassword == null && legacyUsername == null) return

        prefs.edit().apply {
            remove(legacyPasswordKey)
            remove(legacyUsernameKey)
        }.apply()
    }
}
