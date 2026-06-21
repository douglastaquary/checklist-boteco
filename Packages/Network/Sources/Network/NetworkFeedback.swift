import Foundation
import Combine

@MainActor
public final class NetworkFeedback: ObservableObject {
  public static let shared = NetworkFeedback()

  @Published public private(set) var isLoading = false
  @Published public var errorDialog: String?

  private var activeRequests = 0

  private init() {}

  public func onRequestStarted(path: String) {
    guard !path.hasSuffix("/api/health") else { return }
    activeRequests += 1
    isLoading = activeRequests > 0
  }

  public func onRequestFinished(path: String) {
    guard !path.hasSuffix("/api/health") else { return }
    activeRequests = max(0, activeRequests - 1)
    isLoading = activeRequests > 0
  }

  public func showError(_ message: String) {
    guard !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
    errorDialog = message
  }

  public func dismissError() {
    errorDialog = nil
  }
}
