import Foundation

public struct SyncSession: Codable, Equatable, Sendable {
  public let authToken: String
  public let remoteUserId: String

  public init(authToken: String, remoteUserId: String) {
    self.authToken = authToken
    self.remoteUserId = remoteUserId
  }
}

public enum SyncEntityType: String, Codable, Sendable {
  case activity = "ACTIVITY"
  case completion = "COMPLETION"
}

public enum SyncOperationType: String, Codable, Sendable {
  case activityUpsert = "ACTIVITY_UPSERT"
  case activityDelete = "ACTIVITY_DELETE"
  case completionCreate = "COMPLETION_CREATE"
}

public enum SyncAckStatus: String, Codable, Sendable {
  case applied = "APPLIED"
  case alreadyApplied = "ALREADY_APPLIED"
  case conflict = "CONFLICT"
  case rejected = "REJECTED"
}

public struct PendingSyncOperation: Codable, Equatable, Sendable, Identifiable {
  public var id: String { operationId }
  public let operationId: String
  public let entityType: SyncEntityType
  public let entitySyncId: String
  public let operationType: SyncOperationType
  public let payload: String
  public let createdAt: Int64
  public let attemptCount: Int64
  public let nextAttemptAt: Int64
  public let lastError: String?
  public let status: String

  public init(
    operationId: String,
    entityType: SyncEntityType,
    entitySyncId: String,
    operationType: SyncOperationType,
    payload: String,
    createdAt: Int64,
    attemptCount: Int64,
    nextAttemptAt: Int64,
    lastError: String? = nil,
    status: String = "PENDING"
  ) {
    self.operationId = operationId
    self.entityType = entityType
    self.entitySyncId = entitySyncId
    self.operationType = operationType
    self.payload = payload
    self.createdAt = createdAt
    self.attemptCount = attemptCount
    self.nextAttemptAt = nextAttemptAt
    self.lastError = lastError
    self.status = status
  }
}

public struct RemoteActivityRecord: Decodable, Equatable, Sendable {
  public let syncId: String
  public let name: String
  public let area: String
  public let frequency: String
  public let effort: Int
  public let assigneeIds: [String]
  public let estimatedDurationMinutes: Int
  public let executionPhase: String
  public let activeWeekdays: [String]
  public let recurrenceAnchorDate: String?
  public let serverRevision: Int64
  public let updatedAt: Int64

  private enum CodingKeys: String, CodingKey {
    case syncId, id, name, area, frequency, effort, assigneeIds, estimatedDurationMinutes, executionPhase, activeWeekdays, recurrenceAnchorDate, serverRevision, updatedAt
  }

  public init(
    syncId: String,
    name: String,
    area: String,
    frequency: String,
    effort: Int,
    assigneeIds: [String] = [], estimatedDurationMinutes: Int = 15, executionPhase: String = "BEFORE_LUNCH",
    activeWeekdays: [String] = ["TUESDAY", "FRIDAY", "SATURDAY", "SUNDAY"], recurrenceAnchorDate: String? = nil,
    serverRevision: Int64,
    updatedAt: Int64
  ) {
    self.syncId = syncId
    self.name = name
    self.area = area
    self.frequency = frequency
    self.effort = effort
    self.assigneeIds = assigneeIds; self.estimatedDurationMinutes = estimatedDurationMinutes; self.executionPhase = executionPhase
    self.activeWeekdays = activeWeekdays; self.recurrenceAnchorDate = recurrenceAnchorDate
    self.serverRevision = serverRevision
    self.updatedAt = updatedAt
  }

  public init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    syncId = try container.decodeIfPresent(String.self, forKey: .syncId)
      ?? container.decode(String.self, forKey: .id)
    name = try container.decode(String.self, forKey: .name)
    area = try container.decode(String.self, forKey: .area)
    frequency = try container.decode(String.self, forKey: .frequency)
    effort = try container.decode(Int.self, forKey: .effort)
    assigneeIds = try container.decodeIfPresent([String].self, forKey: .assigneeIds) ?? []
    estimatedDurationMinutes = try container.decodeIfPresent(Int.self, forKey: .estimatedDurationMinutes) ?? 15
    executionPhase = try container.decodeIfPresent(String.self, forKey: .executionPhase) ?? "BEFORE_LUNCH"
    activeWeekdays = try container.decodeIfPresent([String].self, forKey: .activeWeekdays) ?? ["TUESDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
    recurrenceAnchorDate = try container.decodeIfPresent(String.self, forKey: .recurrenceAnchorDate)
    serverRevision = try container.decodeIfPresent(Int64.self, forKey: .serverRevision) ?? 0
    updatedAt = try container.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
  }
}

public struct RemoteCompletionRecord: Decodable, Equatable, Sendable {
  public let syncId: String
  public let activitySyncId: String
  public let userId: String
  public let completedAt: Int64
  public let imagePath: String?
  public let isLate: Bool
  public let serviceDate: String
  public let serverRevision: Int64
  public let updatedAt: Int64

  private enum CodingKeys: String, CodingKey {
    case syncId, id, activitySyncId, activityId, userId, completedAt, imagePath, isLate, serviceDate, serverRevision, updatedAt
  }

  public init(
    syncId: String,
    activitySyncId: String,
    userId: String,
    completedAt: Int64,
    imagePath: String? = nil,
    isLate: Bool = false,
    serviceDate: String = "",
    serverRevision: Int64,
    updatedAt: Int64
  ) {
    self.syncId = syncId
    self.activitySyncId = activitySyncId
    self.userId = userId
    self.completedAt = completedAt
    self.imagePath = imagePath
    self.isLate = isLate
    self.serviceDate = serviceDate
    self.serverRevision = serverRevision
    self.updatedAt = updatedAt
  }

  public init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    syncId = try container.decodeIfPresent(String.self, forKey: .syncId)
      ?? container.decode(String.self, forKey: .id)
    activitySyncId = try container.decodeIfPresent(String.self, forKey: .activitySyncId)
      ?? container.decode(String.self, forKey: .activityId)
    userId = try container.decode(String.self, forKey: .userId)
    completedAt = try container.decode(Int64.self, forKey: .completedAt)
    imagePath = try container.decodeIfPresent(String.self, forKey: .imagePath)
    isLate = try container.decodeIfPresent(Bool.self, forKey: .isLate) ?? false
    serviceDate = try container.decodeIfPresent(String.self, forKey: .serviceDate) ?? ""
    serverRevision = try container.decodeIfPresent(Int64.self, forKey: .serverRevision) ?? 0
    updatedAt = try container.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
  }
}

public struct RemoteTombstone: Codable, Equatable, Sendable {
  public let entityType: SyncEntityType
  public let entityId: String
  public let revision: Int64
  public let deletedAt: Int64
}

public struct SyncAcknowledgement: Decodable, Equatable, Sendable {
  public let operationId: String
  public let status: SyncAckStatus
  public let serverRevision: Int64
  public let conflict: RemoteActivityRecord?
  public let message: String?

  public init(
    operationId: String,
    status: SyncAckStatus,
    serverRevision: Int64 = 0,
    conflict: RemoteActivityRecord? = nil,
    message: String? = nil
  ) {
    self.operationId = operationId
    self.status = status
    self.serverRevision = serverRevision
    self.conflict = conflict
    self.message = message
  }

  public init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    operationId = try container.decode(String.self, forKey: .operationId)
    status = try container.decode(SyncAckStatus.self, forKey: .status)
    serverRevision = try container.decodeIfPresent(Int64.self, forKey: .serverRevision) ?? 0
    conflict = try container.decodeIfPresent(RemoteActivityRecord.self, forKey: .conflict)
    message = try container.decodeIfPresent(String.self, forKey: .message)
  }

  private enum CodingKeys: String, CodingKey {
    case operationId, status, serverRevision, conflict, message
  }
}

public struct SyncPushResponse: Decodable, Sendable {
  public let serverTime: Int64
  public let cursor: String
  public let acknowledgements: [SyncAcknowledgement]
}

public struct SyncPullResponse: Decodable, Sendable {
  public let nextCursor: String
  public let hasMore: Bool
  public let activities: [RemoteActivityRecord]
  public let completions: [RemoteCompletionRecord]
  public let tombstones: [RemoteTombstone]

  public init(
    nextCursor: String,
    hasMore: Bool,
    activities: [RemoteActivityRecord] = [],
    completions: [RemoteCompletionRecord] = [],
    tombstones: [RemoteTombstone] = []
  ) {
    self.nextCursor = nextCursor
    self.hasMore = hasMore
    self.activities = activities
    self.completions = completions
    self.tombstones = tombstones
  }
}

public struct RemoteLoginResult: Sendable {
  public let token: String?
  public let user: User?
  public let remoteUserId: String?
  public let requiresTwoFactor: Bool
  public let challengeId: String?
  public let deliveryHint: String?
  public let developmentCode: String?

  public init(
    token: String? = nil,
    user: User? = nil,
    remoteUserId: String? = nil,
    requiresTwoFactor: Bool = false,
    challengeId: String? = nil,
    deliveryHint: String? = nil,
    developmentCode: String? = nil
  ) {
    self.token = token
    self.user = user
    self.remoteUserId = remoteUserId
    self.requiresTwoFactor = requiresTwoFactor
    self.challengeId = challengeId
    self.deliveryHint = deliveryHint
    self.developmentCode = developmentCode
  }
}

public struct UnlockedLoginCredentials: Equatable, Sendable {
  public let username: String
  public let password: String

  public init(username: String, password: String) {
    self.username = username
    self.password = password
  }
}

public struct SavedLoginMetadata: Equatable, Sendable {
  public let username: String
  public let password: String
  public let remember: Bool
  public let requiresBiometricUnlock: Bool

  public init(
    username: String = "",
    password: String = "",
    remember: Bool = false,
    requiresBiometricUnlock: Bool = false
  ) {
    self.username = username
    self.password = password
    self.remember = remember
    self.requiresBiometricUnlock = requiresBiometricUnlock
  }
}
