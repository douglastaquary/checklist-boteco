import Foundation
import Combine

public struct SessionExpiredEvent: Equatable, Sendable {
  public let reason: String

  public init(reason: String) {
    self.reason = reason
  }
}

@MainActor
public final class SessionExpiredCenter: ObservableObject {
  public static let shared = SessionExpiredCenter()

  @Published public private(set) var latestEvent: SessionExpiredEvent?
  public private(set) var isHandling = false

  private init() {}

  public func notify(reason: String) {
    let message = reason.trimmingCharacters(in: .whitespacesAndNewlines)
    let normalized = message.isEmpty ? "Sua sessão expirou. Entre novamente." : message
    guard !isHandling else { return }
    isHandling = true
    latestEvent = SessionExpiredEvent(reason: normalized)
  }

  public func reset() {
    isHandling = false
    latestEvent = nil
  }
}
