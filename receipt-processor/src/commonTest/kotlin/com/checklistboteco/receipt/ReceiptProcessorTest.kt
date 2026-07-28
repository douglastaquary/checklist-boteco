package com.checklistboteco.receipt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReceiptProcessorTest {
    private val fixture = """
        OMERC COMPRE MELHOR DE GENEROS ALIMENTIC. LTDA
        CNPJ 12.345.678/0001-90
        Data: 15/06/2026

        DESCRICAO QTD VL. UNIT TOTAL
        CERVEJA HEINEKEN LN 330ML 24 4,50 108,00
        AGUA COM GAS 510ML 12 1,80 21,60
        DETERGENTE NEUTRO 500ML 6 3,49 20,94
        CARNE BOVINA PATINHO KG 5 42,90 214,50
        COPO DESCARTAVEL 200ML 10 8,90 89,00
        PAPEL HIGIENICO FOLHA DUPLA 4 12,50 50,00
        REFRIGERANTE COCA COLA 2L 8 7,99 63,92

        QTD. TOTAL DE ITENS 69
        VALOR TOTAL (R$) 567,96
        FORMA DE PAGAMENTO
        TER CARTAO DEBITO
    """.trimIndent()

    @Test
    fun parseOmercFixtureExtractsItemsTotalsAndPayment() {
        val scan = ReceiptProcessor.parseReceipt(fixture)
        assertTrue(scan.items.size >= 6, "Esperava ao menos 6 itens, veio ${scan.items.size}")
        assertEquals("Cartão Débito", scan.paymentMethod)
        assertEquals(56796L, scan.totalInCents)
        assertNotNull(scan.supplier)
        assertTrue(scan.supplier!!.contains("OMERC", ignoreCase = true))
        assertEquals("2026-06-15", scan.purchaseDate)
    }

    @Test
    fun classifyKnownProducts() {
        assertEquals("Bebidas", CategoryClassifier.classify("CERVEJA HEINEKEN LN 330ML"))
        assertEquals("Limpeza", CategoryClassifier.classify("DETERGENTE NEUTRO 500ML"))
        assertEquals("Alimentos", CategoryClassifier.classify("CARNE BOVINA PATINHO KG"))
        assertEquals("Outros", CategoryClassifier.classify("ITEM DESCONHECIDO XYZ"))
    }

    @Test
    fun sessionGroupsByCategorySortedBySpend() {
        val scan = ReceiptProcessor.parseReceipt(fixture)
        val session = ReceiptProcessor.mergeScan(ReceiptSession(), scan)
        val groups = ReceiptProcessor.buildGroups(session)
        assertFalse(session.isEmpty())
        assertTrue(groups.isNotEmpty())
        assertEquals(groups, groups.sortedByDescending { it.subtotalInCents })
        assertTrue(groups.any { it.isTopSpend })
        assertTrue(groups.first().subtotalInCents >= groups.last().subtotalInCents)
    }

    @Test
    fun csvBuilderProducesCompatibleHeader() {
        val scan = ReceiptProcessor.parseReceipt(fixture)
        val session = ReceiptProcessor.mergeScan(ReceiptSession(), scan)
        val csv = ReceiptProcessor.toCsv(session)
        val lines = csv.lines()
        assertTrue(lines.first().startsWith("Data;Mercadoria;Categoria;Local"))
        assertTrue(lines.size > 1)
        assertTrue(csv.contains("Beco da Praia"))
        assertTrue(csv.contains("Bebidas") || csv.contains("Alimentos"))
    }

    @Test
    fun malformedLineIsIgnoredOrLowConfidence() {
        val text = """
            DESCRICAO QTD VL. UNIT TOTAL
            PRODUTO QUEBRADO abc x y
            AGUA MINERAL 2 1,50 3,00
            VALOR TOTAL (R$) 3,00
        """.trimIndent()
        val scan = ReceiptProcessor.parseReceipt(text)
        assertEquals(1, scan.items.size)
        assertEquals("AGUA MINERAL", scan.items.first().description)
    }

    @Test
    fun emptySessionStaysEmpty() {
        val session = ReceiptSession()
        assertTrue(session.isEmpty())
        assertEquals(0, ReceiptProcessor.buildGroups(session).size)
        assertEquals(
            "Data;Mercadoria;Categoria;Local;Fornecedor;Quantidade;Unidade;Valor Unitário;Valor Total;Forma Pagamento",
            ReceiptProcessor.toCsv(session)
        )
    }

    @Test
    fun moneyParsingHandlesBrazilianFormat() {
        assertEquals(10800L, ReceiptTextParser.parseMoneyToCents("108,00"))
        assertEquals(21450L, ReceiptTextParser.parseMoneyToCents("214,50"))
        assertEquals(1500L, ReceiptTextParser.parseMoneyToCents("R$ 15,00"))
    }
}
