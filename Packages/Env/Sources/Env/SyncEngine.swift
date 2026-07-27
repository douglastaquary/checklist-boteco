import Foundation
import Models
import Network
import Persistence

public actor SyncEngine {
  private let repository: ChecklistRepository
  private let syncClient: SyncClient?
  private let deviceId: String
  private var isSyncing = false

  public init(repository: ChecklistRepository, syncClient: SyncClient?, deviceId: String) {
    self.repository = repository
    self.syncClient = syncClient
    self.deviceId = deviceId
  }

  public func requestSync() {
    Task { await syncOnce() }
  }

  public func syncOnce() async {
    guard let syncClient, let session = try? repository.getSyncSession(), !isSyncing else { return }
    guard !session.authToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
    isSyncing = true
    defer { isSyncing = false }
    try? repository.repairPendingSyncQueue()
    await pushPending(syncClient: syncClient, session: session)
    await pullRemote(syncClient: syncClient, session: session)
  }

  private func pushPending(syncClient: SyncClient, session: SyncSession) async {
    while true {
      guard let pending = try? repository.listPendingSyncOperations(), !pending.isEmpty else { return }
      let envelopes = pending.compactMap(SyncPayloadMapper.envelope)
      guard !envelopes.isEmpty else { return }
      do {
        let response = try await syncClient.push(
          token: session.authToken,
          batchId: UUID().uuidString,
          request: SyncPushRequestDTO(deviceId: deviceId, operations: envelopes)
        )
        for ack in response.acknowledgements {
          if let conflict = ack.conflict {
            let cursor = (try? repository.getSyncCursor()) ?? "0"
            try? repository.applyRemoteSync(
              SyncPullResponse(
                nextCursor: cursor,
                hasMore: false,
                activities: [conflict]
              )
            )
          }
          try? repository.acknowledgeSyncOperation(ack)
        }
      } catch {
        if isUnauthorized(error) {
          try? repository.clearSyncSession()
          await MainActor.run {
            SessionExpiredCenter.shared.notify(reason: AppErrorMapper.toUserMessage(error))
          }
          return
        }
        let now = Date.nowMillis
        for op in pending {
          try? repository.markSyncOperationFailed(
            operationId: op.operationId,
            attemptCount: op.attemptCount + 1,
            nextAttemptAt: now + 15 * 60 * 1000,
            error: error.localizedDescription
          )
        }
        await MainActor.run {
          NetworkFeedback.shared.showError(AppErrorMapper.toUserMessage(error))
        }
        return
      }
    }
  }

  private func pullRemote(syncClient: SyncClient, session: SyncSession) async {
    while true {
      let cursor = try? repository.getSyncCursor()
      do {
        let response = try await syncClient.pull(token: session.authToken, cursor: cursor, limit: 500)
        try repository.applyRemoteSync(response)
        try repository.setSyncCursor(response.nextCursor)
        if !response.hasMore { return }
      } catch {
        if isUnauthorized(error) {
          try? repository.clearSyncSession()
          await MainActor.run {
            SessionExpiredCenter.shared.notify(reason: AppErrorMapper.toUserMessage(error))
          }
          return
        }
        await MainActor.run {
          NetworkFeedback.shared.showError(AppErrorMapper.toUserMessage(error))
        }
        return
      }
    }
  }

  private func isUnauthorized(_ error: Error) -> Bool {
    if case let APIError.http(status, _) = error {
      return status == 401
    }
    return false
  }

  private func isAuthError(_ error: Error) -> Bool {
    isUnauthorized(error)
  }

  public func retryWorkClockEntries(
    userId: Int64,
    remoteUserId: String,
    token: String,
    deviceId: String
  ) async {
    guard let syncClient else { return }
    guard let pending = try? repository.pendingWorkClockEntries(userId: userId) else { return }
    for entry in pending {
      do {
        let remoteId = try await syncClient.pushWorkClockEntry(
          token: token,
          deviceId: deviceId,
          remoteUserId: remoteUserId,
          type: entry.type,
          registeredAt: entry.registeredAt,
          latitude: entry.location.latitude,
          longitude: entry.location.longitude,
          distanceFromWorkMeters: entry.distanceFromWorkMeters,
          isLate: entry.isLate
        )
        try repository.markWorkClockSynced(id: entry.id, remoteId: remoteId)
      } catch {
        continue
      }
    }
  }
}

private extension Date {
  static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
