package com.checklistboteco.presentation.viewmodel

import com.checklistboteco.receipt.ReceiptProcessor
import com.checklistboteco.receipt.ReceiptSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PurchasesViewModelLogicTest {
    @Test
    fun ingestAndGroupFromOcrText() {
        val fixture = """
            DESCRICAO QTD VL. UNIT TOTAL
            CERVEJA HEINEKEN 2 4,50 9,00
            DETERGENTE 1 3,00 3,00
            VALOR TOTAL (R$) 12,00
            TER CARTAO DEBITO
        """.trimIndent()
        val scan = ReceiptProcessor.parseReceipt(fixture)
        val session = ReceiptProcessor.mergeScan(ReceiptSession(), scan)
        val groups = ReceiptProcessor.buildGroups(session)
        assertFalse(session.isEmpty())
        assertTrue(groups.isNotEmpty())
        assertEquals(1200L, session.totalInCents)
        assertEquals("Cartão Débito", session.paymentMethod)
    }
}
