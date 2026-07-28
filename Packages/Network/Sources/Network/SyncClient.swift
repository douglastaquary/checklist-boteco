import Foundation
import Models
public final class SyncClient: Sendable {
  private let api: APIClient
  private let encoder = JSONEncoder()

  public init(api: APIClient) {
    self.api = api
  }

  public func push(token: String, batchId: String, request: SyncPushRequestDTO) async throws -> SyncPushResponse {
    var urlRequest = URLRequest(url: apiBaseURL().appendingPathComponent("api/sync/push"))
    urlRequest.httpMethod = "POST"
    urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
    urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    urlRequest.setValue(batchId, forHTTPHeaderField: "Idempotency-Key")
    urlRequest.httpBody = try encoder.encode(request)
    return try await perform(urlRequest, path: "/api/sync/push")
  }

  public func pull(token: String, cursor: String?, limit: Int) async throws -> SyncPullResponse {
    var components = URLComponents(url: apiBaseURL().appendingPathComponent("api/sync/pull"), resolvingAgainstBaseURL: false)!
    var items = [URLQueryItem(name: "limit", value: String(limit))]
    if let cursor, !cursor.isEmpty { items.append(URLQueryItem(name: "cursor", value: cursor)) }
    components.queryItems = items
    var request = URLRequest(url: components.url!)
    request.httpMethod = "GET"
    request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    return try await perform(request, path: "/api/sync/pull")
  }

  public func pushWorkClockEntry(
    token: String,
    deviceId: String,
    remoteUserId: String,
    type: WorkClockType,
    registeredAt: Int64,
    latitude: Double,
    longitude: Double,
    distanceFromWorkMeters: Double,
    isLate: Bool
  ) async throws -> String {
    let remoteId = "\(remoteUserId)-\(type.rawValue)-\(registeredAt)"
    let dto = WorkClockPushDTO(
      deviceId: deviceId,
      workClockEntries: [
        WorkClockEntryDTO(
          id: remoteId,
          userId: remoteUserId,
          type: type.rawValue,
          registeredAt: registeredAt,
          latitude: latitude,
          longitude: longitude,
          distanceFromWorkMeters: distanceFromWorkMeters,
          isLate: isLate,
          createdAt: registeredAt,
          updatedAt: registeredAt
        ),
      ]
    )
    let _: EmptyResponse = try await api.request(path: "/api/sync/push", method: "POST", token: token, body: dto)
    return remoteId
  }

  private func apiBaseURL() -> URL {
    api.baseURL
  }

  private func perform<T: Decodable>(_ request: URLRequest, path: String) async throws -> T {
    await NetworkFeedback.shared.onRequestStarted(path: path)
    defer { Task { @MainActor in NetworkFeedback.shared.onRequestFinished(path: path) } }
    let (data, response) = try await URLSession.shared.data(for: request)
    guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
    if http.statusCode >= 400 {
      let body = String(data: data, encoding: .utf8) ?? ""
      throw APIError.http(status: http.statusCode, message: AppErrorMapper.fromHTTP(status: http.statusCode, body: body))
    }
    return try JSONDecoder().decode(T.self, from: data)
  }
}

public struct SyncPushRequestDTO: Encodable {
  public let deviceId: String
  public let operations: [SyncOperationEnvelopeDTO]

  public init(deviceId: String, operations: [SyncOperationEnvelopeDTO]) {
    self.deviceId = deviceId
    self.operations = operations
  }
}

public struct SyncOperationEnvelopeDTO: Encodable {
  public let operationId: String
  public let type: String
  public let entityId: String
  public let baseRevision: Int64
  public let occurredAt: Int64
  public let payload: [String: JSONValue]

  public init(
    operationId: String,
    type: SyncOperationType,
    entityId: String,
    baseRevision: Int64,
    occurredAt: Int64,
    payload: [String: JSONValue]
  ) {
    self.operationId = operationId
    self.type = type.rawValue
    self.entityId = entityId
    self.baseRevision = baseRevision
    self.occurredAt = occurredAt
    self.payload = payload
  }
}

public enum JSONValue: Encodable {
  case string(String)
  case int(Int)
  case int64(Int64)
  case double(Double)
  case bool(Bool)
  case strings([String])
  case null

  public func encode(to encoder: Encoder) throws {
    var container = encoder.singleValueContainer()
    switch self {
    case let .string(value): try container.encode(value)
    case let .int(value): try container.encode(value)
    case let .int64(value): try container.encode(value)
    case let .double(value): try container.encode(value)
    case let .bool(value): try container.encode(value)
    case let .strings(value): try container.encode(value)
    case .null: try container.encodeNil()
    }
  }
}

private struct WorkClockPushDTO: Encodable {
  let deviceId: String
  let workClockEntries: [WorkClockEntryDTO]
}

private struct WorkClockEntryDTO: Encodable {
  let id: String
  let userId: String
  let type: String
  let registeredAt: Int64
  let latitude: Double
  let longitude: Double
  let distanceFromWorkMeters: Double
  let isLate: Bool
  let createdAt: Int64
  let updatedAt: Int64
  let syncStatus: String = "SYNCED"
}

private struct EmptyResponse: Decodable {}
