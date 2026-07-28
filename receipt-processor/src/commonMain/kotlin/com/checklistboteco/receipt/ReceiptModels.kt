package com.checklistboteco.receipt

import kotlinx.serialization.Serializable

@Serializable
data class ReceiptLineItem(
    val description: String,
    val quantity: Double,
    val unitPriceInCents: Long,
    val totalInCents: Long,
    val category: String,
    val lowConfidence: Boolean = false
)

@Serializable
data class ReceiptScan(
    val purchaseDate: String? = null,
    val supplier: String? = null,
    val paymentMethod: String? = null,
    val totalItems: Int? = null,
    val totalInCents: Long? = null,
    val items: List<ReceiptLineItem> = emptyList(),
    val rawText: String = ""
)

@Serializable
data class CategoryGroup(
    val category: String,
    val items: List<ReceiptLineItem>,
    val subtotalInCents: Long,
    val isTopSpend: Boolean = false
)

@Serializable
data class ReceiptSession(
    val scans: List<ReceiptScan> = emptyList(),
    val location: String = DEFAULT_LOCATION
) {
    val allItems: List<ReceiptLineItem>
        get() = scans.flatMap { it.items }

    val totalInCents: Long
        get() = allItems.sumOf { it.totalInCents }

    val itemCount: Int
        get() = allItems.size

    val supplier: String?
        get() = scans.mapNotNull { it.supplier }.lastOrNull()

    val purchaseDate: String?
        get() = scans.mapNotNull { it.purchaseDate }.lastOrNull()

    val paymentMethod: String?
        get() = scans.mapNotNull { it.paymentMethod }.lastOrNull()

    fun isEmpty(): Boolean = allItems.isEmpty()

    companion object {
        const val DEFAULT_LOCATION = "Beco da Praia"
    }
}

object ReceiptProcessor {
    fun parseReceipt(ocrText: String): ReceiptScan {
        return ReceiptTextParser.parse(ocrText)
    }

    fun buildGroups(session: ReceiptSession, topSpendCount: Int = 2): List<CategoryGroup> {
        return ReceiptSessionAggregator.groupByCategory(session, topSpendCount)
    }

    fun mergeScan(session: ReceiptSession, scan: ReceiptScan): ReceiptSession {
        return ReceiptSessionAggregator.merge(session, scan)
    }

    fun toCsv(session: ReceiptSession): String {
        return ReceiptCsvBuilder.build(session)
    }
}
