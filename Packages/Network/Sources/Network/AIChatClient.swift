import Foundation

public struct AIChatMessageRequest: Encodable, Sendable {
  public let role: String
  public let text: String
  public init(role: String, text: String) { self.role = role; self.text = text }
}

public struct AIUsageDTO: Decodable, Sendable {
  public let inputTokens: Int
  public let cachedInputTokens: Int
  public let outputTokens: Int
  public let totalTokens: Int
  public let estimatedCostMicros: Int64
}

public struct AIUsageSummaryDTO: Decodable, Sendable {
  public let month: String
  public let requests: Int64
  public let estimatedCostMicros: Int64
  public let monthlyLimitCents: Int64
  public let inputTokens: Int
  public let cachedInputTokens: Int
  public let outputTokens: Int
  public let blocked: Bool
}

public struct AIChatResponseDTO: Decodable, Sendable {
  public let requestId: String
  public let answer: String
  public let interpretedLocation: String
  public let consultedTools: [String]
  public let usage: AIUsageDTO
  public let budget: AIUsageSummaryDTO
}

private struct AIChatRequestDTO: Encodable {
  let clientRequestId: String
  let messages: [AIChatMessageRequest]
}

public final class AIChatClient: @unchecked Sendable {
  private let api: APIClient
  public init(api: APIClient) { self.api = api }

  @MainActor
  public func send(messages: [AIChatMessageRequest], token: String) async throws -> AIChatResponseDTO {
    try await api.request(
      path: "/api/ai/chat",
      method: "POST",
      token: token,
      body: AIChatRequestDTO(clientRequestId: UUID().uuidString, messages: Array(messages.suffix(4)))
    )
  }

  @MainActor
  public func usage(token: String) async throws -> AIUsageSummaryDTO {
    try await api.request(path: "/api/ai/usage", token: token)
  }
}
