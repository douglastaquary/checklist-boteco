package com.checklistboteco.receipt

object ReceiptCsvBuilder {
    private const val HEADER =
        "Data;Mercadoria;Categoria;Local;Fornecedor;Quantidade;Unidade;Valor Unitário;Valor Total;Forma Pagamento"

    fun build(session: ReceiptSession): String {
        if (session.isEmpty()) return HEADER
        val date = formatDate(session.purchaseDate)
        val supplier = session.supplier.orEmpty()
        val payment = session.paymentMethod.orEmpty()
        val location = session.location
        val rows = session.allItems.map { item ->
            listOf(
                date,
                escape(item.description),
                escape(item.category),
                escape(location),
                escape(supplier),
                formatQuantity(item.quantity),
                "UN",
                formatMoney(item.unitPriceInCents),
                formatMoney(item.totalInCents),
                escape(payment)
            ).joinToString(";")
        }
        return (listOf(HEADER) + rows).joinToString("\n")
    }

    private fun formatDate(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val parts = iso.split('-')
        if (parts.size != 3) return iso
        return "${parts[2]}/${parts[1]}/${parts[0]}"
    }

    private fun formatQuantity(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ',')
    }

    private fun formatMoney(cents: Long): String {
        val reais = cents / 100
        val frac = kotlin.math.abs(cents % 100).toString().padStart(2, '0')
        return "$reais,$frac"
    }

    private fun escape(value: String): String = value.replace(';', ',')
}
