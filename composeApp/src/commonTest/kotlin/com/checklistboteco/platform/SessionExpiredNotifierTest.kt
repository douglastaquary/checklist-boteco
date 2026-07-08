package com.checklistboteco.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionExpiredNotifierTest {
    @Test
    fun returnsTokenWhenApiUnavailable() {
        assertEquals("", requireRemoteToken(api = null, token = null))
    }

    @Test
    fun notifyIsIdempotentUntilReset() {
        SessionExpiredNotifier.reset()
        SessionExpiredNotifier.notify("Sua sessão expirou")
        assertTrue(SessionExpiredNotifier.isHandling)
        SessionExpiredNotifier.notify("Outra mensagem")
        SessionExpiredNotifier.reset()
        assertFalse(SessionExpiredNotifier.isHandling)
    }
}
