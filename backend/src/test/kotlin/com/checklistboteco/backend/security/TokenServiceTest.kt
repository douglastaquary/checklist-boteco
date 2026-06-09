package com.checklistboteco.backend.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenServiceTest {
    @Test
    fun issuesAndVerifiesToken() {
        val service = TokenService(secret = "test", clock = { 1_000L }, ttlMillis = 10_000L)

        val payload = service.verify(service.issue("user-1", isAdmin = true))

        assertNotNull(payload)
        assertEquals("user-1", payload.userId)
        assertTrue(payload.isAdmin)
    }

    @Test
    fun rejectsExpiredToken() {
        var now = 1_000L
        val service = TokenService(secret = "test", clock = { now }, ttlMillis = 1L)
        val token = service.issue("user-1", isAdmin = false)

        now = 2_000L

        assertNull(service.verify(token))
    }
}
