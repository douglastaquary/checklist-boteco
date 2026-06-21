import Foundation
import Combine

@MainActor
public final class SyncController: ObservableObject {
  private let engine: SyncEngine

  public init(engine: SyncEngine) {
    self.engine = engine
  }

  public func requestSync() {
    Task { await engine.requestSync() }
  }

  public func syncOnce() async {
    await engine.syncOnce()
  }

  public func retryWorkClockEntries(userId: Int64, remoteUserId: String, token: String, deviceId: String) async {
    await engine.retryWorkClockEntries(
      userId: userId,
      remoteUserId: remoteUserId,
      token: token,
      deviceId: deviceId
    )
  }
}
