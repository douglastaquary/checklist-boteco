import Foundation
import Models
import Network
public struct InventoryDailyAuditItem: Decodable, Sendable, Identifiable {
  public var id: String { product }
  public let product: String
  public let status: String
  public let notes: String
  public let openingQuantity: Double
  public let soldQuantity: Double
  public let theoreticalRemaining: Double
}

public struct InventoryDailyAudit: Decodable, Sendable {
  public let date: String
  public let location: String
  public let items: [InventoryDailyAuditItem]
  public let totalOpening: Double
  public let totalSold: Double
  public let totalRemaining: Double
}

public struct ApplyDailyAuditResponse: Decodable, Sendable {
  public let audit: InventoryDailyAudit?
  public let alreadyApplied: Bool
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
      location: "Beco da Praia",
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
    let _: [String: String] = try await api.request(path: path, method: "POST", token: token, body: body)
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
      body: Request(date: date, location: "Beco da Praia")
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
      body: Request(date: date, location: "Beco da Praia")
    )
  }
}
