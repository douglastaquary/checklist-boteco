import Foundation
import GRDB
import Models
struct UserRecord: FetchableRecord, Decodable {
  let id: Int64
  let name: String
  let email: String
  let password: String
  let area: String
  let workSector: String
  let permissionLevel: String
  let allowedAreas: String
  let createdAt: Int64
  let remoteId: String?
  let canRegisterUsers: Bool
  let canCreateActivities: Bool
  let canEditUsers: Bool
  let canCreateInventoryCounts: Bool
  let canViewInventoryInsights: Bool
  let canManageAdministrativeStock: Bool

  func toDomain() -> User {
    User(
      id: id,
      name: name,
      email: email,
      password: password,
      area: Area.from(area),
      workSector: WorkSector.from(workSector),
      permissionLevel: PermissionLevel.from(permissionLevel),
      allowedAreas: allowedAreas.split(separator: ",").map { Area.from(String($0)) },
      createdAt: createdAt,
      remoteId: remoteId,
      featurePermissions: FeaturePermissions(
        canRegisterUsers: canRegisterUsers,
        canCreateActivities: canCreateActivities,
        canEditUsers: canEditUsers,
        canCreateInventoryCounts: canCreateInventoryCounts,
        canViewInventoryInsights: canViewInventoryInsights,
        canManageAdministrativeStock: canManageAdministrativeStock
      )
    )
  }
}

struct ActivityRecord: FetchableRecord, Decodable {
  let id: Int64
  let syncId: String?
  let name: String
  let area: String
  let frequency: String
  let effort: Int
  let serverRevision: Int64
  let syncState: String
  let deletedAt: Int64?

  init(row: Row, prefix: String = "") {
    id = row[Column("\(prefix)id")]
    syncId = row[Column("\(prefix)syncId")]
    name = row[Column("\(prefix)name")]
    area = row[Column("\(prefix)area")]
    frequency = row[Column("\(prefix)frequency")]
    effort = row[Column("\(prefix)effort")]
    serverRevision = row[Column("\(prefix)serverRevision")]
    syncState = row[Column("\(prefix)syncState")]
    deletedAt = row[Column("\(prefix)deletedAt")]
  }

  func toDomain() -> Activity {
    Activity(
      id: id,
      syncId: syncId,
      name: name,
      area: Area.from(area),
      frequency: Frequency.from(frequency),
      effort: effort,
      serverRevision: serverRevision,
      syncState: SyncState(rawValue: syncState) ?? .synced,
      deletedAt: deletedAt
    )
  }
}

struct PendingSyncRecord: FetchableRecord, Decodable {
  let operationId: String
  let entityType: String
  let entitySyncId: String
  let operationType: String
  let payload: String
  let createdAt: Int64
  let attemptCount: Int64
  let nextAttemptAt: Int64
  let lastError: String?
  let status: String

  var toDomain: PendingSyncOperation {
    PendingSyncOperation(
      operationId: operationId,
      entityType: SyncEntityType(rawValue: entityType) ?? .activity,
      entitySyncId: entitySyncId,
      operationType: SyncOperationType(rawValue: operationType) ?? .activityUpsert,
      payload: payload,
      createdAt: createdAt,
      attemptCount: attemptCount,
      nextAttemptAt: nextAttemptAt,
      lastError: lastError,
      status: status
    )
  }
}

struct WorkClockRecord: FetchableRecord, Decodable {
  let id: Int64
  let userId: Int64
  let type: String
  let registeredAt: Int64
  let latitude: Double
  let longitude: Double
  let distanceFromWorkMeters: Double
  let isLate: Bool
  let syncStatus: String
  let remoteId: String?

  var toDomain: WorkClockEntry {
    WorkClockEntry(
      id: id,
      userId: userId,
      type: WorkClockType.from(type),
      registeredAt: registeredAt,
      location: GeoPoint(latitude: latitude, longitude: longitude),
      distanceFromWorkMeters: distanceFromWorkMeters,
      isLate: isLate,
      syncStatus: syncStatus,
      remoteId: remoteId
    )
  }
}

struct InventoryDraftRecord: FetchableRecord, Decodable {
  let id: Int64
  let name: String
  let quantity: Double
  let category: String
  let volume: Double
  let volumeUnit: String
  let salePriceInCents: Int64
  let costPriceInCents: Int64?
  let storageCondition: String

  var toDomain: InventoryCountDraft {
    InventoryCountDraft(
      id: id,
      name: name,
      quantity: quantity,
      category: InventoryCategory(rawValue: category) ?? .naoAlcoolico,
      volume: volume,
      volumeUnit: volumeUnit,
      salePriceInCents: salePriceInCents,
      costPriceInCents: costPriceInCents,
      storageCondition: StorageCondition(rawValue: storageCondition) ?? .natural
    )
  }
}
