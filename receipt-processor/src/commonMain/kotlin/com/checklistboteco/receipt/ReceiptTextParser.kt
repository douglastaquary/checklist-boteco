package com.checklistboteco.receipt

object ReceiptTextParser {
    private val moneyRegex = Regex("""R?\$?\s*(\d{1,3}(?:\.\d{3})*(?:,\d{2})|\d+[.,]\d{2}|\d+)""")
    private val qtyRegex = Regex("""(\d+(?:[.,]\d+)?)""")
    private val dateRegex = Regex("""(\d{2}[/-]\d{2}[/-]\d{2,4})""")
    private val itemLineRegex = Regex(
        """^(.+?)\s+(\d+(?:[.,]\d+)?)\s+(\d{1,3}(?:\.\d{3})*(?:,\d{2})|\d+[.,]\d{2})\s+(\d{1,3}(?:\.\d{3})*(?:,\d{2})|\d+[.,]\d{2})\s*$"""
    )
    private val compactItemRegex = Regex(
        """^(.+?)\s+(\d+)\s+x?\s*(\d+[.,]\d{2})\s+(\d+[.,]\d{2})\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(ocrText: String): ReceiptScan {
        val lines = normalizeLines(ocrText)
        val supplier = lines.firstOrNull { looksLikeSupplier(it) }
        val purchaseDate = lines.mapNotNull { dateRegex.find(it)?.groupValues?.get(1) }
            .map { normalizeDate(it) }
            .firstOrNull()
        val paymentMethod = lines.firstOrNull { looksLikePayment(it) }?.let { normalizePayment(it) }

        val headerIndex = lines.indexOfFirst { isItemHeader(it) }
        val footerIndex = lines.indexOfFirst { isFooter(it) }.let { if (it < 0) lines.size else it }
        val itemStart = if (headerIndex >= 0) headerIndex + 1 else 0
        val itemEnd = footerIndex.coerceAtLeast(itemStart)

        val items = lines.subList(itemStart, itemEnd)
            .mapNotNull { parseItemLine(it) }

        val totalItems = lines.firstNotNullOfOrNull { extractTotalItems(it) }
        val totalInCents = lines.firstNotNullOfOrNull { extractTotalAmount(it) }
            ?: items.sumOf { it.totalInCents }

        return ReceiptScan(
            purchaseDate = purchaseDate,
            supplier = supplier,
            paymentMethod = paymentMethod,
            totalItems = totalItems ?: items.sumOf { it.quantity }.toInt(),
            totalInCents = totalInCents,
            items = items,
            rawText = ocrText
        )
    }

    private fun normalizeLines(text: String): List<String> {
        return text
            .replace('\u00a0', ' ')
            .lines()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
    }

    private fun isItemHeader(line: String): Boolean {
        val n = line.uppercase()
        return n.contains("DESCR") && (n.contains("QTD") || n.contains("QUANT")) &&
            (n.contains("UNIT") || n.contains("VL")) && n.contains("TOTAL")
    }

    private fun isFooter(line: String): Boolean {
        val n = line.uppercase()
        return n.contains("QTD") && n.contains("TOTAL") && n.contains("ITENS") ||
            n.contains("VALOR TOTAL") ||
            n.contains("TOTAL DA COMPRA") ||
            n.contains("FORMA DE PAGAMENTO") ||
            n.contains("CARTAO") && n.contains("DEBITO") ||
            n.contains("CARTÃO") && n.contains("DÉBITO")
    }

    private fun looksLikeSupplier(line: String): Boolean {
        val n = line.uppercase()
        return (n.contains("LTDA") || n.contains("EIRELI") || n.contains("ME") || n.contains("SA")) &&
            !isItemHeader(n) && !isFooter(n) && line.length > 8
    }

    private fun looksLikePayment(line: String): Boolean {
        val n = line.uppercase()
            .replace('Á', 'A').replace('É', 'E').replace('Í', 'I').replace('Ó', 'O').replace('Ú', 'U')
            .replace('Ã', 'A').replace('Õ', 'O').replace('Ç', 'C')
        if (n == "FORMA DE PAGAMENTO") return false
        return n.contains("CARTAO") && (n.contains("DEBITO") || n.contains("CREDITO")) ||
            n.contains("DINHEIRO") ||
            n.contains("PIX")
    }

    private fun normalizePayment(line: String): String {
        val n = line.uppercase()
            .replace('Á', 'A').replace('É', 'E').replace('Í', 'I').replace('Ó', 'O').replace('Ú', 'U')
            .replace('Ã', 'A').replace('Õ', 'O').replace('Ç', 'C')
        return when {
            n.contains("DEBITO") -> "Cartão Débito"
            n.contains("CREDITO") -> "Cartão Crédito"
            n.contains("PIX") -> "Pix"
            n.contains("DINHEIRO") -> "Dinheiro"
            else -> line.trim()
        }
    }

    private fun parseItemLine(line: String): ReceiptLineItem? {
        if (isItemHeader(line) || isFooter(line) || looksLikeSupplier(line)) return null
        val match = itemLineRegex.find(line) ?: compactItemRegex.find(line) ?: return parseLooseItem(line)
        val description = match.groupValues[1].trim()
        if (description.length < 2) return null
        val quantity = parseNumber(match.groupValues[2]) ?: return null
        val unitPrice = parseMoneyToCents(match.groupValues[3]) ?: return null
        val total = parseMoneyToCents(match.groupValues[4]) ?: return null
        if (quantity <= 0 || total <= 0) return null
        val expected = (quantity * unitPrice).toLong()
        val lowConfidence = kotlin.math.abs(expected - total) > 2
        return ReceiptLineItem(
            description = description,
            quantity = quantity,
            unitPriceInCents = unitPrice,
            totalInCents = total,
            category = CategoryClassifier.classify(description),
            lowConfidence = lowConfidence
        )
    }

    private fun parseLooseItem(line: String): ReceiptLineItem? {
        val moneys = moneyRegex.findAll(line).map { it.groupValues[1] }.toList()
        if (moneys.size < 2) return null
        val total = parseMoneyToCents(moneys.last()) ?: return null
        val unit = parseMoneyToCents(moneys[moneys.size - 2]) ?: return null
        val withoutMoney = moneyRegex.replace(line, " ").trim()
        val qtyMatch = qtyRegex.findAll(withoutMoney).lastOrNull() ?: return null
        val quantity = parseNumber(qtyMatch.value) ?: return null
        val description = withoutMoney.replace(qtyMatch.value, " ").replace(Regex("\\s+"), " ").trim()
        if (description.length < 2 || quantity <= 0 || total <= 0) return null
        val expected = (quantity * unit).toLong()
        return ReceiptLineItem(
            description = description,
            quantity = quantity,
            unitPriceInCents = unit,
            totalInCents = total,
            category = CategoryClassifier.classify(description),
            lowConfidence = kotlin.math.abs(expected - total) > 2
        )
    }

    private fun extractTotalItems(line: String): Int? {
        val n = line.uppercase()
        if (!(n.contains("QTD") && n.contains("ITENS")) && !n.contains("TOTAL DE ITENS")) return null
        return qtyRegex.findAll(line).mapNotNull { parseNumber(it.value)?.toInt() }.lastOrNull()
    }

    private fun extractTotalAmount(line: String): Long? {
        val n = line.uppercase()
        if (!n.contains("VALOR TOTAL") && !n.contains("TOTAL DA COMPRA") && !n.contains("TOTAL R$")) return null
        return moneyRegex.findAll(line).mapNotNull { parseMoneyToCents(it.groupValues[1]) }.lastOrNull()
    }

    private fun normalizeDate(raw: String): String {
        val parts = raw.split('/', '-')
        if (parts.size != 3) return raw
        val day = parts[0].padStart(2, '0')
        val month = parts[1].padStart(2, '0')
        val year = when (parts[2].length) {
            2 -> "20${parts[2]}"
            else -> parts[2]
        }
        return "$year-$month-$day"
    }

    fun parseNumber(raw: String): Double? {
        val cleaned = raw.trim().replace(".", "").replace(',', '.')
        return cleaned.toDoubleOrNull()
    }

    fun parseMoneyToCents(raw: String): Long? {
        val cleaned = raw.trim()
            .replace("R$", "", ignoreCase = true)
            .replace("$", "")
            .trim()
        if (cleaned.isEmpty()) return null
        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') ->
                cleaned.replace(".", "").replace(',', '.')
            cleaned.contains(',') -> cleaned.replace(',', '.')
            else -> cleaned
        }
        val value = normalized.toDoubleOrNull() ?: return null
        return kotlin.math.round(value * 100.0).toLong()
    }
}
