import Foundation

public enum Frequency: String, CaseIterable, Codable, Sendable {
  case daily = "DAILY"
  case weekly = "WEEKLY"
  case monthly = "MONTHLY"

  public var displayName: String {
    switch self {
    case .daily: return "Diária"
    case .weekly: return "Semanal"
    case .monthly: return "Mensal"
    }
  }

  public static func from(_ value: String) -> Frequency {
    Frequency(rawValue: value.uppercased()) ?? .daily
  }
}

public enum SyncState: String, Codable, Sendable {
  case synced = "SYNCED"
  case pending = "PENDING"
}

public struct Activity: Identifiable, Equatable, Sendable {
  public let id: Int64
  public var syncId: String?
  public var name: String
  public var area: Area
  public var frequency: Frequency
  public var effort: Int
  public var serverRevision: Int64
  public var syncState: SyncState
  public var deletedAt: Int64?

  public init(
    id: Int64,
    syncId: String? = nil,
    name: String,
    area: Area,
    frequency: Frequency,
    effort: Int = 1,
    serverRevision: Int64 = 0,
    syncState: SyncState = .synced,
    deletedAt: Int64? = nil
  ) {
    self.id = id
    self.syncId = syncId
    self.name = name
    self.area = area
    self.frequency = frequency
    self.effort = effort
    self.serverRevision = serverRevision
    self.syncState = syncState
    self.deletedAt = deletedAt
  }
}

public struct ActivityCompletion: Identifiable, Equatable, Sendable {
  public let id: Int64
  public var syncId: String?
  public var activityId: Int64
  public var userId: Int64
  public var completedAt: Int64
  public var imagePath: String?
  public var isLate: Bool
  public var serverRevision: Int64
  public var syncState: SyncState

  public init(
    id: Int64,
    syncId: String? = nil,
    activityId: Int64,
    userId: Int64,
    completedAt: Int64,
    imagePath: String? = nil,
    isLate: Bool = false,
    serverRevision: Int64 = 0,
    syncState: SyncState = .synced
  ) {
    self.id = id
    self.syncId = syncId
    self.activityId = activityId
    self.userId = userId
    self.completedAt = completedAt
    self.imagePath = imagePath
    self.isLate = isLate
    self.serverRevision = serverRevision
    self.syncState = syncState
  }
}

public struct ActivityWithCompletion: Identifiable, Equatable, Sendable {
  public var id: Int64 { activity.id }
  public let activity: Activity
  public let completion: ActivityCompletion?

  public init(activity: Activity, completion: ActivityCompletion?) {
    self.activity = activity
    self.completion = completion
  }
}
