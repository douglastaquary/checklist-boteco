import Foundation
import Models
public struct APIConfiguration: Sendable {
  public let baseURL: URL

  public init(baseURL: URL) {
    self.baseURL = baseURL
  }

  func url(for path: String) -> URL {
    var base = baseURL.absoluteString
    while base.hasSuffix("/") { base.removeLast() }
    let suffix = path.trimmingCharacters(in: .whitespacesAndNewlines)
    let normalized = suffix.hasPrefix("/") ? suffix : "/\(suffix)"
    return URL(string: base + normalized) ?? baseURL
  }

  public static func fromBundle() -> APIConfiguration? {
    guard let raw = Bundle.main.object(forInfoDictionaryKey: "CHECKLIST_API_BASE_URL") as? String else {
      return nil
    }
    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty,
          let url = URL(string: trimmed),
          let scheme = url.scheme?.lowercased(),
          scheme == "http" || scheme == "https",
          url.host != nil
    else { return nil }
    return APIConfiguration(baseURL: url)
  }
}

public final class APIClient: @unchecked Sendable {
  private let config: APIConfiguration
  private let session: URLSession
  private let encoder = JSONEncoder()
  private let decoder = JSONDecoder()

  public init(config: APIConfiguration, session: URLSession = .shared) {
    self.config = config
    self.session = session
  }

  public var baseURL: URL { config.baseURL }

  @MainActor
  public func request<T: Decodable>(
    path: String,
    method: String = "GET",
    token: String? = nil,
    body: Encodable? = nil
  ) async throws -> T {
    let url = config.url(for: path)
    var request = URLRequest(url: url)
    request.httpMethod = method
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
    if let body {
      request.httpBody = try encoder.encode(AnyEncodable(body))
    }

    await NetworkFeedback.shared.onRequestStarted(path: path)
    defer { Task { @MainActor in NetworkFeedback.shared.onRequestFinished(path: path) } }

    do {
      let (data, response) = try await session.data(for: request)
      guard let http = response as? HTTPURLResponse else { throw APIError.invalidResponse }
      if http.statusCode >= 400 {
        let bodyText = String(data: data, encoding: .utf8) ?? ""
        let message = AppErrorMapper.fromHTTP(status: http.statusCode, body: bodyText)
        if http.statusCode == 401, token != nil {
          SessionExpiredCenter.shared.notify(reason: message)
        }
        throw APIError.http(status: http.statusCode, message: message)
      }
      do {
        return try decoder.decode(T.self, from: data)
      } catch {
        throw APIError.decoding(error)
      }
    } catch let error as APIError {
      throw error
    } catch {
      throw APIError.transport(error)
    }
  }

  public func health() async -> Bool {
    do {
      let response: [String: String] = try await request(path: "/api/health")
      return response["status"] == "ok"
    } catch {
      return false
    }
  }
}

private struct AnyEncodable: Encodable {
  private let encodeFunc: (Encoder) throws -> Void

  init(_ wrapped: Encodable) {
    encodeFunc = wrapped.encode
  }

  func encode(to encoder: Encoder) throws {
    try encodeFunc(encoder)
  }
}
