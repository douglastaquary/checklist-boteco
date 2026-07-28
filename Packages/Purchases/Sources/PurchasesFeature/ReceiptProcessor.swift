import Foundation

public struct ReceiptLineItem: Identifiable, Equatable, Sendable {
  public var id: String { "\(description)-\(totalInCents)-\(quantity)" }
  public let description: String
  public let quantity: Double
  public let unitPriceInCents: Int64
  public let totalInCents: Int64
  public let category: String
  public let lowConfidence: Bool

  public init(
    description: String,
    quantity: Double,
    unitPriceInCents: Int64,
    totalInCents: Int64,
    category: String,
    lowConfidence: Bool = false
  ) {
    self.description = description
    self.quantity = quantity
    self.unitPriceInCents = unitPriceInCents
    self.totalInCents = totalInCents
    self.category = category
    self.lowConfidence = lowConfidence
  }
}

public struct ReceiptScan: Equatable, Sendable {
  public var purchaseDate: String?
  public var supplier: String?
  public var paymentMethod: String?
  public var totalItems: Int?
  public var totalInCents: Int64?
  public var items: [ReceiptLineItem]
  public var rawText: String

  public init(
    purchaseDate: String? = nil,
    supplier: String? = nil,
    paymentMethod: String? = nil,
    totalItems: Int? = nil,
    totalInCents: Int64? = nil,
    items: [ReceiptLineItem] = [],
    rawText: String = ""
  ) {
    self.purchaseDate = purchaseDate
    self.supplier = supplier
    self.paymentMethod = paymentMethod
    self.totalItems = totalItems
    self.totalInCents = totalInCents
    self.items = items
    self.rawText = rawText
  }
}

public struct CategoryGroup: Identifiable, Equatable, Sendable {
  public var id: String { category }
  public let category: String
  public let items: [ReceiptLineItem]
  public let subtotalInCents: Int64
  public let isTopSpend: Bool
}

public struct ReceiptSession: Equatable, Sendable {
  public static let defaultLocation = "Beco da Praia"
  public var scans: [ReceiptScan]
  public var location: String

  public init(scans: [ReceiptScan] = [], location: String = defaultLocation) {
    self.scans = scans
    self.location = location
  }

  public var allItems: [ReceiptLineItem] { scans.flatMap(\.items) }
  public var totalInCents: Int64 { allItems.reduce(0) { $0 + $1.totalInCents } }
  public var isEmpty: Bool { allItems.isEmpty }
  public var supplier: String? { scans.compactMap(\.supplier).last }
  public var purchaseDate: String? { scans.compactMap(\.purchaseDate).last }
  public var paymentMethod: String? { scans.compactMap(\.paymentMethod).last }
}

/// Espelho Swift do algoritmo KMP `:receipt-processor` (testado em commonTest).
public enum ReceiptProcessor {
  public static func parseReceipt(_ ocrText: String) -> ReceiptScan {
    ReceiptTextParser.parse(ocrText)
  }

  public static func merge(session: ReceiptSession, scan: ReceiptScan) -> ReceiptSession {
    var copy = session
    copy.scans.append(scan)
    return copy
  }

  public static func buildGroups(session: ReceiptSession, topSpendCount: Int = 2) -> [CategoryGroup] {
    let grouped = Dictionary(grouping: session.allItems, by: \.category)
      .map { category, items in
        CategoryGroup(
          category: category,
          items: items,
          subtotalInCents: items.reduce(0) { $0 + $1.totalInCents },
          isTopSpend: false
        )
      }
      .sorted { $0.subtotalInCents > $1.subtotalInCents }
    guard !grouped.isEmpty else { return [] }
    let top = Set(grouped.prefix(max(topSpendCount, 1)).map(\.category))
    return grouped.map {
      CategoryGroup(
        category: $0.category,
        items: $0.items,
        subtotalInCents: $0.subtotalInCents,
        isTopSpend: grouped.count > 1 && top.contains($0.category)
      )
    }
  }

  public static func toCsv(session: ReceiptSession) -> String {
    ReceiptCsvBuilder.build(session)
  }

  public static func formatBrl(_ cents: Int64) -> String {
    let reais = cents / 100
    let frac = String(format: "%02d", abs(cents % 100))
    return "R$ \(reais),\(frac)"
  }
}

enum CategoryClassifier {
  private static let rules: [(String, [String])] = [
    ("Bebidas", ["cerveja", "heineken", "brahma", "skol", "refrigerante", "coca", "agua", "suco", "vinho", "energetico"]),
    ("Alimentos", ["carne", "frango", "queijo", "pao", "arroz", "feijao", "oleo", "leite", "ovos", "batata", "tomate", "molho"]),
    ("Limpeza", ["detergente", "sabao", "desinfetante", "alcool", "papel higienico", "esponja", "cloro"]),
    ("Descartáveis", ["copo", "prato", "talher", "canudo", "embalagem", "filme"]),
    ("Utilidades", ["pilha", "bateria", "lampada", "isqueiro"])
  ]

  static func classify(_ description: String) -> String {
    let normalized = normalize(description)
    for (category, keywords) in rules {
      if keywords.contains(where: { normalized.contains(normalize($0)) }) {
        return category
      }
    }
    return "Outros"
  }

  private static func normalize(_ value: String) -> String {
    value
      .folding(options: .diacriticInsensitive, locale: Locale(identifier: "pt_BR"))
      .lowercased()
      .replacingOccurrences(of: "[^a-z0-9\\s]", with: " ", options: .regularExpression)
      .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
      .trimmingCharacters(in: .whitespacesAndNewlines)
  }
}
