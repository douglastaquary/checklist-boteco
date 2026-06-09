package com.checklistboteco.backend.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordHasherTest {
    @Test
    fun verifiesMatchingPasswordOnly() {
        val hasher = PasswordHasher(iterations = 1_000)
        val encoded = hasher.hash("admin123")

        assertTrue(hasher.verify("admin123", encoded))
        assertFalse(hasher.verify("wrong", encoded))
    }
}
