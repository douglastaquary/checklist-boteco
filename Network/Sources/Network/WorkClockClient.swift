import Foundation
import Models

public final class WorkClockClient: Sendable {
  private let api: APIClient

  public init(api: APIClient) {
    self.api = api
  }

  public func fetchWorksite(token: String) async throws -> WorksiteInfo {
    let dto: WorksiteInfoDTO = try await api.request(path: "/api/work-clock/worksite", token: token)
    return WorksiteInfo(
      name: dto.name,
      latitude: dto.latitude,
      longitude: dto.longitude,
      radiusMeters: dto.radiusMeters
    )
  }
}

private struct WorksiteInfoDTO: Decodable {
  let latitude: Double
  let longitude: Double
  let radiusMeters: Double
  let name: String
}
