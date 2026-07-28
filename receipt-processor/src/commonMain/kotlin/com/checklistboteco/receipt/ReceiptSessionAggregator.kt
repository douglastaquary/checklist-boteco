package com.checklistboteco.receipt

object ReceiptSessionAggregator {
    fun merge(session: ReceiptSession, scan: ReceiptScan): ReceiptSession {
        if (scan.items.isEmpty() && scan.supplier == null && scan.purchaseDate == null) {
            return session
        }
        return session.copy(scans = session.scans + scan)
    }

    fun groupByCategory(session: ReceiptSession, topSpendCount: Int = 2): List<CategoryGroup> {
        val grouped = session.allItems
            .groupBy { it.category }
            .map { (category, items) ->
                CategoryGroup(
                    category = category,
                    items = items,
                    subtotalInCents = items.sumOf { it.totalInCents }
                )
            }
            .sortedByDescending { it.subtotalInCents }

        if (grouped.isEmpty()) return emptyList()
        val topKeys = grouped.take(topSpendCount.coerceAtLeast(1)).map { it.category }.toSet()
        return grouped.map { group ->
            group.copy(isTopSpend = group.category in topKeys && grouped.size > 1)
        }
    }

    fun clear(): ReceiptSession = ReceiptSession()
}
