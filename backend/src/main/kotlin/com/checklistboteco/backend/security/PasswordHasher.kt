package com.checklistboteco.backend.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordHasher(
    private val iterations: Int = 120_000,
    private val keyLength: Int = 256
) {
    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val hash = pbkdf2(password, salt)
        return listOf(
            "pbkdf2_sha256",
            iterations.toString(),
            salt.base64(),
            hash.base64()
        ).joinToString("$")
    }

    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split("$")
        if (parts.size != 4 || parts[0] != "pbkdf2_sha256") return false
        val salt = Base64.getDecoder().decode(parts[2])
        val expected = Base64.getDecoder().decode(parts[3])
        val actual = pbkdf2(password, salt, parts[1].toInt())
        return expected.contentEquals(actual)
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterationCount: Int = iterations): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun ByteArray.base64(): String = Base64.getEncoder().encodeToString(this)
}
