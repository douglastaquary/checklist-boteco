import Foundation

enum ReceiptTextParser {
  private static let moneyPattern = #"R?\$?\s*(\d{1,3}(?:\.\d{3})*(?:,\d{2})|\d+[.,]\d{2}|\d+)"#
  private static let itemPattern =
    #"^(.+?)\s+(\d+(?:[.,]\d+)?)\s+(\d{1,3}(?:\.\d{3})*(?:,\d{2})|\d+[.,]\d{2})\s+(\d{1,3}(?:\.\d{3})*(?:,\d{2})|\d+[.,]\d{2})\s*$"#

  static func parse(_ ocrText: String) -> ReceiptScan {
    let lines = normalizeLines(ocrText)
    let supplier = lines.first(where: looksLikeSupplier)
    let purchaseDate = lines.compactMap { date(in: $0) }.first
    let paymentMethod = lines.first(where: looksLikePayment).map(normalizePayment)

    let headerIndex = lines.firstIndex(where: isItemHeader) ?? -1
    let footerIndex = lines.firstIndex(where: isFooter) ?? lines.count
    let start = headerIndex >= 0 ? headerIndex + 1 : 0
    let end = max(start, footerIndex)
    let items = Array(lines[start..<end]).compactMap(parseItemLine)

    let totalItems = lines.compactMap(extractTotalItems).first ?? Int(items.reduce(0) { $0 + $1.quantity })
    let totalInCents = lines.compactMap(extractTotalAmount).first ?? items.reduce(0) { $0 + $1.totalInCents }

    return ReceiptScan(
      purchaseDate: purchaseDate,
      supplier: supplier,
      paymentMethod: paymentMethod,
      totalItems: totalItems,
      totalInCents: totalInCents,
      items: items,
      rawText: ocrText
    )
  }

  private static func normalizeLines(_ text: String) -> [String] {
    text
      .replacingOccurrences(of: "\u{00a0}", with: " ")
      .components(separatedBy: .newlines)
      .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression) }
      .filter { !$0.isEmpty }
  }

  private static func isItemHeader(_ line: String) -> Bool {
    let n = line.uppercased()
    return n.contains("DESCR") && (n.contains("QTD") || n.contains("QUANT")) && (n.contains("UNIT") || n.contains("VL")) && n.contains("TOTAL")
  }

  private static func isFooter(_ line: String) -> Bool {
    let n = line.uppercased()
    return (n.contains("QTD") && n.contains("TOTAL") && n.contains("ITENS"))
      || n.contains("VALOR TOTAL")
      || n.contains("TOTAL DA COMPRA")
      || n.contains("FORMA DE PAGAMENTO")
      || (n.contains("CARTAO") || n.contains("CARTÃO")) && (n.contains("DEBITO") || n.contains("DÉBITO") || n.contains("CREDITO") || n.contains("CRÉDITO"))
  }

  private static func looksLikeSupplier(_ line: String) -> Bool {
    let n = line.uppercased()
    return (n.contains("LTDA") || n.contains("EIRELI") || n.contains(" ME") || n.contains("SA"))
      && !isItemHeader(line) && !isFooter(line) && line.count > 8
  }

  private static func looksLikePayment(_ line: String) -> Bool {
    let n = fold(line.uppercased())
    if n == "FORMA DE PAGAMENTO" { return false }
    return (n.contains("CARTAO") && (n.contains("DEBITO") || n.contains("CREDITO")))
      || n.contains("DINHEIRO")
      || n.contains("PIX")
  }

  private static func normalizePayment(_ line: String) -> String {
    let n = fold(line.uppercased())
    if n.contains("DEBITO") { return "Cartão Débito" }
    if n.contains("CREDITO") { return "Cartão Crédito" }
    if n.contains("PIX") { return "Pix" }
    if n.contains("DINHEIRO") { return "Dinheiro" }
    return line.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  private static func parseItemLine(_ line: String) -> ReceiptLineItem? {
    if isItemHeader(line) || isFooter(line) || looksLikeSupplier(line) { return nil }
    guard let regex = try? NSRegularExpression(pattern: itemPattern),
          let match = regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)),
          match.numberOfRanges == 5,
          let dRange = Range(match.range(at: 1), in: line),
          let qRange = Range(match.range(at: 2), in: line),
          let uRange = Range(match.range(at: 3), in: line),
          let tRange = Range(match.range(at: 4), in: line),
          let quantity = parseNumber(String(line[qRange])),
          let unit = parseMoneyToCents(String(line[uRange])),
          let total = parseMoneyToCents(String(line[tRange]))
    else { return nil }
    let description = String(line[dRange]).trimmingCharacters(in: .whitespacesAndNewlines)
    guard description.count >= 2, quantity > 0, total > 0 else { return nil }
    let expected = Int64((quantity * Double(unit)).rounded())
    return ReceiptLineItem(
      description: description,
      quantity: quantity,
      unitPriceInCents: unit,
      totalInCents: total,
      category: CategoryClassifier.classify(description),
      lowConfidence: abs(expected - total) > 2
    )
  }

  private static func extractTotalItems(_ line: String) -> Int? {
    let n = line.uppercased()
    guard (n.contains("QTD") && n.contains("ITENS")) || n.contains("TOTAL DE ITENS") else { return nil }
    return numbers(in: line).compactMap { Int($0.rounded()) }.last
  }

  private static func extractTotalAmount(_ line: String) -> Int64? {
    let n = line.uppercased()
    guard n.contains("VALOR TOTAL") || n.contains("TOTAL DA COMPRA") || n.contains("TOTAL R$") else { return nil }
    return moneyValues(in: line).last
  }

  private static func date(in line: String) -> String? {
    guard let regex = try? NSRegularExpression(pattern: #"(\d{2}[/-]\d{2}[/-]\d{2,4})"#),
          let match = regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)),
          let range = Range(match.range(at: 1), in: line)
    else { return nil }
    let raw = String(line[range])
    let parts = raw.split { $0 == "/" || $0 == "-" }.map(String.init)
    guard parts.count == 3 else { return raw }
    let year = parts[2].count == 2 ? "20\(parts[2])" : parts[2]
    return "\(year)-\(parts[1].padLeft(to: 2, with: "0"))-\(parts[0].padLeft(to: 2, with: "0"))"
  }

  static func parseNumber(_ raw: String) -> Double? {
    Double(raw.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ".", with: "").replacingOccurrences(of: ",", with: "."))
  }

  static func parseMoneyToCents(_ raw: String) -> Int64? {
    var cleaned = raw
      .replacingOccurrences(of: "R$", with: "", options: .caseInsensitive)
      .replacingOccurrences(of: "$", with: "")
      .trimmingCharacters(in: .whitespacesAndNewlines)
    if cleaned.contains(",") && cleaned.contains(".") {
      cleaned = cleaned.replacingOccurrences(of: ".", with: "").replacingOccurrences(of: ",", with: ".")
    } else if cleaned.contains(",") {
      cleaned = cleaned.replacingOccurrences(of: ",", with: ".")
    }
    guard let value = Double(cleaned) else { return nil }
    return Int64((value * 100).rounded())
  }

  private static func numbers(in line: String) -> [Double] {
    guard let regex = try? NSRegularExpression(pattern: #"(\d+(?:[.,]\d+)?)"#) else { return [] }
    return regex.matches(in: line, range: NSRange(line.startIndex..., in: line)).compactMap {
      guard let range = Range($0.range(at: 1), in: line) else { return nil }
      return parseNumber(String(line[range]))
    }
  }

  private static func moneyValues(in line: String) -> [Int64] {
    guard let regex = try? NSRegularExpression(pattern: moneyPattern) else { return [] }
    return regex.matches(in: line, range: NSRange(line.startIndex..., in: line)).compactMap {
      guard let range = Range($0.range(at: 1), in: line) else { return nil }
      return parseMoneyToCents(String(line[range]))
    }
  }

  private static func fold(_ value: String) -> String {
    value
      .folding(options: .diacriticInsensitive, locale: Locale(identifier: "pt_BR"))
      .uppercased()
  }
}

enum ReceiptCsvBuilder {
  private static let header =
    "Data;Mercadoria;Categoria;Local;Fornecedor;Quantidade;Unidade;Valor Unitário;Valor Total;Forma Pagamento"

  static func build(_ session: ReceiptSession) -> String {
    guard !session.isEmpty else { return header }
    let date = formatDate(session.purchaseDate)
    let rows = session.allItems.map { item in
      [
        date,
        escape(item.description),
        escape(item.category),
        escape(session.location),
        escape(session.supplier ?? ""),
        formatQuantity(item.quantity),
        "UN",
        formatMoney(item.unitPriceInCents),
        formatMoney(item.totalInCents),
        escape(session.paymentMethod ?? "")
      ].joined(separator: ";")
    }
    return ([header] + rows).joined(separator: "\n")
  }

  private static func formatDate(_ iso: String?) -> String {
    guard let iso, let parts = Optional(iso.split(separator: "-")), parts.count == 3 else { return iso ?? "" }
    return "\(parts[2])/\(parts[1])/\(parts[0])"
  }

  private static func formatQuantity(_ value: Double) -> String {
    value.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(value)) : String(value).replacingOccurrences(of: ".", with: ",")
  }

  private static func formatMoney(_ cents: Int64) -> String {
    "\(cents / 100),\(String(format: "%02d", abs(cents % 100)))"
  }

  private static func escape(_ value: String) -> String {
    value.replacingOccurrences(of: ";", with: ",")
  }
}

private extension String {
  func padLeft(to length: Int, with pad: Character) -> String {
    String(repeating: String(pad), count: max(0, length - count)) + self
  }
}
