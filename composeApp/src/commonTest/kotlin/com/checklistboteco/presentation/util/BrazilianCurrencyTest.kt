package com.checklistboteco.presentation.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BrazilianCurrencyTest {
    @Test
    fun formatCentsUsesBrazilianPattern() {
        assertEquals("R$ 18,00", BrazilianCurrency.formatCents(1800))
        assertEquals("R$ 0,01", BrazilianCurrency.formatCents(1))
        assertEquals("R$ 0,00", BrazilianCurrency.formatCents(0))
    }

    @Test
    fun digitsToCentsParsesProgressiveInput() {
        assertEquals(1800L, BrazilianCurrency.digitsToCents("1800"))
        assertEquals(0L, BrazilianCurrency.digitsToCents(""))
        assertEquals(18L, BrazilianCurrency.digitsToCents("18"))
        assertEquals(1800L, BrazilianCurrency.digitsToCents("R$ 18,00"))
    }
}
