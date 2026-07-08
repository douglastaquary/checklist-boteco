import Foundation

public enum Frequency: String, CaseIterable, Sendable {
  case diario = "DIARIO"
  case quinzenal = "QUINZENAL"
  case mensal = "MENSAL"

  public var displayName: String {
    switch self {
    case .diario: return "Diária"
    case .quinzenal: return "Quinzenal"
    case .mensal: return "Mensal"
    }
  }

  public static func from(_ value: String) -> Frequency {
    switch value.uppercased() {
    case "DIARIO", "DAILY": return .diario
    case "QUINZENAL", "WEEKLY": return .quinzenal
    case "MENSAL", "MONTHLY": return .mensal
    default: return .diario
    }
  }
}

extension Frequency: Codable {
  public init(from decoder: Decoder) throws {
    let raw = try decoder.singleValueContainer().decode(String.self)
    self = Frequency.from(raw)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.singleValueContainer()
    try container.encode(rawValue)
  }
}

public enum SyncState: String, Codable, Sendable {
  case synced = "SYNCED"
  case pending = "PENDING"
}

public enum ExecutionPhase: String, Codable, CaseIterable, Sendable {
  case beforeLunch = "BEFORE_LUNCH"
  case beforeOpening = "BEFORE_OPENING"
  case duringOperation = "DURING_OPERATION"

  public var displayName: String {
    switch self {
    case .beforeLunch: return "Antes do almoço"
    case .beforeOpening: return "Antes da abertura"
    case .duringOperation: return "Durante a operação"
    }
  }
}

public enum ChecklistTimingStatus: String, Codable, Sendable {
  case green = "GREEN", yellow = "YELLOW", red = "RED", completed = "COMPLETED"
}

public struct Activity: Identifiable, Equatable, Sendable {
  public let id: Int64
  public var syncId: String?
  public var name: String
  public var area: Area
  public var frequency: Frequency
  public var effort: Int
  public var assigneeIds: [String]
  public var estimatedDurationMinutes: Int
  public var executionPhase: ExecutionPhase
  public var activeWeekdays: [String]
  public var recurrenceAnchorDate: String?
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
    assigneeIds: [String] = [],
    estimatedDurationMinutes: Int = 15,
    executionPhase: ExecutionPhase = .beforeLunch,
    activeWeekdays: [String] = ["TUESDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
    recurrenceAnchorDate: String? = nil,
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
    self.assigneeIds = assigneeIds
    self.estimatedDurationMinutes = estimatedDurationMinutes
    self.executionPhase = executionPhase
    self.activeWeekdays = activeWeekdays
    self.recurrenceAnchorDate = recurrenceAnchorDate
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
  public var serviceDate: String
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
    serviceDate: String = "",
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
    self.serviceDate = serviceDate
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

public struct ActivityTiming: Equatable, Sendable {
  public let status: ChecklistTimingStatus
  public let deadline: Date
  public let recommendedStart: Date

  public static func today(activity: Activity, completion: ActivityCompletion?, now: Date = Date()) -> ActivityTiming {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "America/Fortaleza") ?? .current
    let weekday = calendar.component(.weekday, from: now)
    let weekend = weekday == 1 || weekday == 7
    let hour: Int
    switch activity.executionPhase {
    case .beforeLunch: hour = weekend ? 11 : 17
    case .beforeOpening: hour = weekend ? 12 : 18
    case .duringOperation: hour = 0
    }
    let base = activity.executionPhase == .duringOperation
      ? calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now)) ?? now
      : calendar.startOfDay(for: now)
    let deadline = calendar.date(bySettingHour: hour, minute: 0, second: 0, of: base) ?? now
    let status: ChecklistTimingStatus
    if completion != nil { status = .completed }
    else if now > deadline { status = .red }
    else if deadline.timeIntervalSince(now) <= 30 * 60 { status = .yellow }
    else { status = .green }
    return ActivityTiming(status: status, deadline: deadline, recommendedStart: deadline.addingTimeInterval(TimeInterval(-activity.estimatedDurationMinutes * 60)))
  }

  public var label: String {
    switch status { case .green: return "Dentro do prazo"; case .yellow: return "Próxima do limite"; case .red: return "Atrasada"; case .completed: return "Concluída" }
  }
}
