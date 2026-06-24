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

public final class DashboardClient: Sendable {
  private let api: APIClient

  public init(api: APIClient) {
    self.api = api
  }

  public func fetchStats(token: String) async throws -> DashboardStats {
    try await api.request(path: "/api/admin/dashboard", token: token)
  }
}
