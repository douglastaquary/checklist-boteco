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

  public func fetchMySummary(token: String, from: String, to: String) async throws -> RemoteWorkClockSummary {
    let dto: WorkClockSummaryDTO = try await api.request(path: "/api/work-clock/me/summary?from=\(from)&to=\(to)", token: token)
    return RemoteWorkClockSummary(
      absenceDays: dto.absenceDays,
      absenceDates: dto.absenceDates,
      absenceDetails: dto.absenceDetails.map { WorkClockAbsenceDetail(date: $0.date, reason: $0.reason) }
    )
  }
}

private struct WorksiteInfoDTO: Decodable {
  let latitude: Double
  let longitude: Double
  let radiusMeters: Double
  let name: String
}

private struct WorkClockSummaryDTO: Decodable {
  let absenceDays: Int
  let absenceDates: [String]
  let absenceDetails: [WorkClockAbsenceDetailDTO]
}

private struct WorkClockAbsenceDetailDTO: Decodable {
  let date: String
  let reason: String
}
