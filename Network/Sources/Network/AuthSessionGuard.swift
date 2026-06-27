import Foundation

public enum AuthSessionGuard {
  @MainActor
  public static func requireRemoteToken(apiConfigured: Bool, token: String?) throws -> String {
    guard apiConfigured else { return token ?? "" }
    guard let token, !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
      let message = "Faça login novamente"
      SessionExpiredCenter.shared.notify(reason: message)
      throw RemoteSessionRequiredError(message: message)
    }
    return token
  }
}

public struct RemoteSessionRequiredError: Error, LocalizedError, Sendable {
  public let message: String

  public init(message: String) {
    self.message = message
  }

  public var errorDescription: String? { message }
}
