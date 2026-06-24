import Foundation
import Models
import Network

enum SyncPayloadMapper {
  static func envelope(for operation: PendingSyncOperation) -> SyncOperationEnvelopeDTO? {
    guard let data = operation.payload.data(using: .utf8) else { return nil }
    let decoder = JSONDecoder()
    switch operation.operationType {
    case .activityUpsert:
      guard let payload = try? decoder.decode(ActivityPayload.self, from: data) else { return nil }
      return SyncOperationEnvelopeDTO(
        operationId: operation.operationId,
        type: .activityUpsert,
        entityId: operation.entitySyncId,
        baseRevision: payload.baseRevision,
        occurredAt: operation.createdAt,
        payload: [
          "name": .string(payload.name),
          "area": .string(payload.area),
          "frequency": .string(payload.frequency),
          "effort": .int(payload.effort),
        ]
      )
    case .activityDelete:
      guard let payload = try? decoder.decode(ActivityPayload.self, from: data) else { return nil }
      return SyncOperationEnvelopeDTO(
        operationId: operation.operationId,
        type: .activityDelete,
        entityId: operation.entitySyncId,
        baseRevision: payload.baseRevision,
        occurredAt: payload.deletedAt ?? operation.createdAt,
        payload: [
          "deletedAt": .int64(payload.deletedAt ?? operation.createdAt),
        ]
      )
    case .completionCreate:
      guard let payload = try? decoder.decode(CompletionPayload.self, from: data) else { return nil }
      var envelopePayload: [String: JSONValue] = [
        "activitySyncId": .string(payload.activitySyncId),
        "completedAt": .int64(payload.completedAt),
        "isLate": .bool(payload.isLate),
      ]
      if let imagePath = payload.imagePath {
        envelopePayload["imagePath"] = .string(imagePath)
      } else {
        envelopePayload["imagePath"] = .null
      }
      return SyncOperationEnvelopeDTO(
        operationId: operation.operationId,
        type: .completionCreate,
        entityId: operation.entitySyncId,
        baseRevision: payload.baseRevision,
        occurredAt: payload.completedAt,
        payload: envelopePayload
      )
    }
  }
}

private struct ActivityPayload: Decodable {
  let syncId: String
  let name: String
  let area: String
  let frequency: String
  let effort: Int
  let baseRevision: Int64
  let deletedAt: Int64?
}

private struct CompletionPayload: Decodable {
  let syncId: String
  let activitySyncId: String
  let baseRevision: Int64
  let completedAt: Int64
  let imagePath: String?
  let isLate: Bool
}
