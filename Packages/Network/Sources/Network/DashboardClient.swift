import Foundation
import Models

public struct DashboardStats: Decodable, Sendable {
  public let totalUsers: Int
  public let totalActivities: Int
  public let totalCompletions: Int
  public let pendingSyncItems: Int
  public let activitiesByArea: [String: Int]

  public init(
    totalUsers: Int,
    totalActivities: Int,
    totalCompletions: Int,
    pendingSyncItems: Int,
    activitiesByArea: [String: Int]
  ) {
    self.totalUsers = totalUsers
    self.totalActivities = totalActivities
    self.totalCompletions = totalCompletions
    self.pendingSyncItems = pendingSyncItems
    self.activitiesByArea = activitiesByArea
  }
}

public struct SalesHeatmapDay: Decodable, Sendable, Hashable {
  public let date: String
  public let quantity: Double
  public let totalInCents: Int64

  public init(date: String, quantity: Double, totalInCents: Int64 = 0) {
    self.date = date
    self.quantity = quantity
    self.totalInCents = totalInCents
  }

  private enum CodingKeys: String, CodingKey {
    case date
    case quantity
    case totalInCents
  }

  public init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    date = try container.decode(String.self, forKey: .date)
    if let value = try? container.decode(Double.self, forKey: .quantity) {
      quantity = value
    } else if let value = try? container.decode(Int.self, forKey: .quantity) {
      quantity = Double(value)
    } else if let value = try? container.decode(String.self, forKey: .quantity),
              let parsed = Double(value.replacingOccurrences(of: ",", with: ".")) {
      quantity = parsed
    } else {
      quantity = 0
    }
    if let value = try? container.decode(Int64.self, forKey: .totalInCents) {
      totalInCents = value
    } else if let value = try? container.decode(Int.self, forKey: .totalInCents) {
      totalInCents = Int64(value)
    } else {
      totalInCents = 0
    }
  }
}

public struct SalesHeatmapResponse: Decodable, Sendable {
  public let year: Int
  public let datasetId: String?
  public let days: [SalesHeatmapDay]

  public init(year: Int, datasetId: String? = "sales", days: [SalesHeatmapDay]) {
    self.year = year
    self.datasetId = datasetId
    self.days = days
  }
}

public final class DashboardClient: Sendable {
  private let api: APIClient

  public init(api: APIClient) {
    self.api = api
  }

  public func fetchStats(token: String) async throws -> DashboardStats {
    try await api.request(path: "/api/admin/dashboard", token: token)
  }

  public func fetchSalesHeatmap(year: Int, token: String) async throws -> SalesHeatmapResponse {
    try await api.request(
      path: "/api/admin/dashboard/sales-heatmap?year=\(year)",
      token: token
    )
  }
}
