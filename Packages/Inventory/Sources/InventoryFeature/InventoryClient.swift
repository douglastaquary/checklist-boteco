import Foundation
import Models
import Network
public struct InventoryDailyAuditItem: Decodable, Sendable, Identifiable, Hashable {
  public var id: String { product }
  public let product: String
  public let status: String
  public let notes: String
  public let openingQuantity: Double
  public let soldQuantity: Double
  public let theoreticalRemaining: Double

  public init(
    product: String,
    status: String,
    notes: String,
    openingQuantity: Double,
    soldQuantity: Double,
    theoreticalRemaining: Double
  ) {
    self.product = product
    self.status = status
    self.notes = notes
    self.openingQuantity = openingQuantity
    self.soldQuantity = soldQuantity
    self.theoreticalRemaining = theoreticalRemaining
  }
}

public struct InventoryDailyAudit: Decodable, Sendable, Hashable {
  public let date: String
  public let location: String
  public let items: [InventoryDailyAuditItem]
  public let totalOpening: Double
  public let totalSold: Double
  public let totalRemaining: Double

  public init(
    date: String,
    location: String,
    items: [InventoryDailyAuditItem],
    totalOpening: Double,
    totalSold: Double,
    totalRemaining: Double
  ) {
    self.date = date
    self.location = location
    self.items = items
    self.totalOpening = totalOpening
    self.totalSold = totalSold
    self.totalRemaining = totalRemaining
  }
}

/// Snapshot navegável (detalhe de auditoria / deep link).
public struct InventoryAuditItemSnapshot: Hashable, Sendable {
  public let product: String
  public let auditDate: String
  public let location: String
  public let status: String
  public let notes: String
  public let openingQuantity: Double
  public let soldQuantity: Double
  public let theoreticalRemaining: Double
  public let totalOpening: Double
  public let totalSold: Double
  public let totalRemaining: Double

  public init(item: InventoryDailyAuditItem, audit: InventoryDailyAudit) {
    product = item.product
    auditDate = audit.date
    location = audit.location
    status = item.status
    notes = item.notes
    openingQuantity = item.openingQuantity
    soldQuantity = item.soldQuantity
    theoreticalRemaining = item.theoreticalRemaining
    totalOpening = audit.totalOpening
    totalSold = audit.totalSold
    totalRemaining = audit.totalRemaining
  }
}

public struct ApplyDailyAuditResponse: Decodable, Sendable {
  public let audit: InventoryDailyAudit?
  public let balances: [InventoryAdminStockBalance]
  public let alreadyApplied: Bool

  public init(audit: InventoryDailyAudit?, balances: [InventoryAdminStockBalance] = [], alreadyApplied: Bool) {
    self.audit = audit
    self.balances = balances
    self.alreadyApplied = alreadyApplied
  }

  public init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    audit = try container.decodeIfPresent(InventoryDailyAudit.self, forKey: .audit)
    balances = try container.decodeIfPresent([InventoryAdminStockBalance].self, forKey: .balances) ?? []
    alreadyApplied = try container.decode(Bool.self, forKey: .alreadyApplied)
  }

  private enum CodingKeys: String, CodingKey {
    case audit, balances, alreadyApplied
  }
}

public struct SalesImportError: Decodable, Sendable {
  public let row: Int
  public let field: String?
  public let message: String
}

public struct SalesImportBatch: Decodable, Sendable {
  public let id: String
  public let fileName: String?
  public let status: String
  public let headers: [String]
  public let sampleRows: [[String: String]]
  public let suggestedMapping: [String: String]
  public let mapping: [String: String]
  public let errors: [SalesImportError]
  public let totalRows: Int
  public let importedRows: Int

  public init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    id = try container.decode(String.self, forKey: .id)
    fileName = try container.decodeIfPresent(String.self, forKey: .fileName)
    status = try container.decodeIfPresent(String.self, forKey: .status) ?? "PREVIEW"
    headers = try container.decodeIfPresent([String].self, forKey: .headers) ?? []
    sampleRows = try container.decodeIfPresent([[String: String]].self, forKey: .sampleRows) ?? []
    suggestedMapping = try container.decodeIfPresent([String: String].self, forKey: .suggestedMapping) ?? [:]
    mapping = try container.decodeIfPresent([String: String].self, forKey: .mapping) ?? [:]
    errors = try container.decodeIfPresent([SalesImportError].self, forKey: .errors) ?? []
    totalRows = try container.decodeIfPresent(Int.self, forKey: .totalRows) ?? 0
    importedRows = try container.decodeIfPresent(Int.self, forKey: .importedRows) ?? 0
  }

  private enum CodingKeys: String, CodingKey {
    case id, fileName, status, headers, sampleRows, suggestedMapping, mapping, errors, totalRows, importedRows
  }
}

public final class InventoryClient: Sendable {
  private let api: APIClient
  private let encoder = JSONEncoder()

  public init(api: APIClient) { self.api = api }

  public func submitCount(token: String, date: String, items: [InventoryCountDraft], administrative: Bool) async throws {
    struct Request: Encodable {
      let countDate: String
      let countedAt: String
      let location: String
      let items: [Item]
      struct Item: Encodable {
        let name: String
        let quantity: Double
        let category: String
        let volume: Double
        let volumeUnit: String
        let salePriceInCents: Int64
        let costPriceInCents: Int64?
        let condition: String
      }
    }
    let path = administrative ? "/api/inventory/admin-stock/counts" : "/api/inventory/counts"
    let body = Request(
      countDate: date,
      countedAt: ISO8601DateFormatter().string(from: Date()),
      location: WorksiteLocation.name,
      items: items.map {
        Request.Item(
          name: $0.name,
          quantity: $0.quantity,
          category: $0.category.rawValue,
          volume: $0.volume,
          volumeUnit: $0.volumeUnit,
          salePriceInCents: $0.salePriceInCents,
          costPriceInCents: $0.costPriceInCents,
          condition: $0.storageCondition.rawValue
        )
      }
    )
    // Backend returns CountSession / AdminStockSession (201) — not a string map.
    struct SubmitCountResponse: Decodable {
      let id: String
    }
    _ = try await api.request(path: path, method: "POST", token: token, body: body) as SubmitCountResponse
  }

  public func dailyAudit(token: String, date: String) async throws -> InventoryDailyAudit {
    struct Request: Encodable {
      let date: String
      let location: String
    }
    return try await api.request(
      path: "/api/inventory/audit/daily",
      method: "POST",
      token: token,
      body: Request(date: date, location: WorksiteLocation.name)
    )
  }

  public func applyDailyAudit(token: String, date: String) async throws -> ApplyDailyAuditResponse {
    struct Request: Encodable {
      let date: String
      let location: String
    }
    return try await api.request(
      path: "/api/inventory/audit/daily/apply",
      method: "POST",
      token: token,
      body: Request(date: date, location: WorksiteLocation.name)
    )
  }

  public func listCounts(token: String) async throws -> [InventoryCountSession] {
    try await api.request(path: "/api/inventory/counts", token: token)
  }

  public func listAdminStockBalances(token: String) async throws -> [InventoryAdminStockBalance] {
    try await api.request(path: "/api/inventory/admin-stock/balances", token: token)
  }

  public func salesImportPreview(token: String, fileName: String, csv: String) async throws -> SalesImportBatch {
    struct Request: Encodable {
      let fileName: String
      let csv: String
    }
    return try await api.request(
      path: "/api/sales/imports/preview",
      method: "POST",
      token: token,
      body: Request(fileName: fileName, csv: csv)
    )
  }

  public func salesImportCommit(
    token: String,
    batchId: String,
    mapping: [String: String]? = nil
  ) async throws -> SalesImportBatch {
    struct Request: Encodable {
      let datasetId: String
      let mapping: [String: String]
      let preserveColumns: [String]
    }
    return try await api.request(
      path: "/api/sales/imports/\(batchId)/commit",
      method: "POST",
      token: token,
      body: Request(
        datasetId: "sales",
        mapping: mapping ?? [:],
        preserveColumns: []
      )
    )
  }
}

public struct InventoryCountSession: Decodable, Sendable, Identifiable {
  public let id: String
  public let countDate: String
  public let countedAt: String
  public let location: String
}

public struct InventoryAdminStockBalance: Decodable, Sendable, Identifiable {
  public var id: String { productKey }
  public let productKey: String
  public let productName: String
  public let location: String
  public let quantity: Double
}
