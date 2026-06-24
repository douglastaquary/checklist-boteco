import Foundation

public enum WorkClockType: String, CaseIterable, Codable, Sendable {
  case entrada = "ENTRADA"
  case almocoInicio = "ALMOCO_INICIO"
  case almocoFim = "ALMOCO_FIM"
  case descansoInicio = "DESCANSO_INICIO"
  case descansoFim = "DESCANSO_FIM"
  case saida = "SAIDA"

  public var displayName: String {
    switch self {
    case .entrada: return "Entrada"
    case .almocoInicio: return "Saída para almoço"
    case .almocoFim: return "Retorno do almoço"
    case .descansoInicio: return "Início do descanso"
    case .descansoFim: return "Fim do descanso"
    case .saida: return "Saída"
    }
  }

  public static func from(_ value: String) -> WorkClockType {
    WorkClockType(rawValue: value.uppercased()) ?? .entrada
  }
}

public struct GeoPoint: Equatable, Codable, Sendable {
  public let latitude: Double
  public let longitude: Double

  public init(latitude: Double, longitude: Double) {
    self.latitude = latitude
    self.longitude = longitude
  }
}

public struct WorkClockEntry: Identifiable, Equatable, Sendable {
  public let id: Int64
  public let userId: Int64
  public let type: WorkClockType
  public let registeredAt: Int64
  public let location: GeoPoint
  public let distanceFromWorkMeters: Double
  public let isLate: Bool
  public var syncStatus: String
  public var remoteId: String?

  public init(
    id: Int64,
    userId: Int64,
    type: WorkClockType,
    registeredAt: Int64,
    location: GeoPoint,
    distanceFromWorkMeters: Double,
    isLate: Bool,
    syncStatus: String = "PENDING",
    remoteId: String? = nil
  ) {
    self.id = id
    self.userId = userId
    self.type = type
    self.registeredAt = registeredAt
    self.location = location
    self.distanceFromWorkMeters = distanceFromWorkMeters
    self.isLate = isLate
    self.syncStatus = syncStatus
    self.remoteId = remoteId
  }
}

public struct WorkClockSummary: Equatable, Sendable {
  public let workedMillis: Int64
  public let lunchMillis: Int64
  public let restMillis: Int64
  public let requiredBreakMillis: Int64
  public let missingBreakMillis: Int64
  public let breakOverageMillis: Int64
  public let missingDailyMillis: Int64
  public let missingWeeklyMillis: Int64
  public let overtimeMillis: Int64
  public let requiresTwoHoursRest: Bool
}

public struct WorksiteInfo: Equatable, Sendable {
  public let name: String
  public let latitude: Double
  public let longitude: Double
  public let radiusMeters: Double

  public init(name: String, latitude: Double, longitude: Double, radiusMeters: Double) {
    self.name = name
    self.latitude = latitude
    self.longitude = longitude
    self.radiusMeters = radiusMeters
  }

  public var point: GeoPoint {
    GeoPoint(latitude: latitude, longitude: longitude)
  }
}

public enum WorksiteLocation {
  private static let lock = NSLock()
  private static var _cached: WorksiteInfo?

  public static let defaultInfo = WorksiteInfo(
    name: "Beco da Praia",
    latitude: -23.85491,
    longitude: -46.13872,
    radiusMeters: 5.0
  )

  public static var current: WorksiteInfo {
    lock.lock()
    defer { lock.unlock() }
    return _cached ?? defaultInfo
  }

  public static func applyCached(_ info: WorksiteInfo) {
    lock.lock()
    _cached = info
    lock.unlock()
  }

  public static var name: String { current.name }
  public static var allowedRadiusMeters: Double { current.radiusMeters }
  public static var point: GeoPoint { current.point }
}

public enum WorkClockCalculator {
  public static let dailyExpectedMillis: Int64 = 8 * 60 * 60 * 1000
  public static let weeklyExpectedMillis: Int64 = 40 * 60 * 60 * 1000
  public static let regularBreakMillis: Int64 = 60 * 60 * 1000
  public static let extendedShiftBreakMillis: Int64 = 2 * 60 * 60 * 1000

  public static func nextType(entries: [WorkClockEntry]) -> WorkClockType {
    switch entries.last?.type {
    case nil, .saida: return .entrada
    case .entrada: return .almocoInicio
    case .almocoInicio: return .almocoFim
    case .almocoFim: return .descansoInicio
    case .descansoInicio: return .descansoFim
    case .descansoFim: return .saida
    }
  }

  public static func isLateEntry() -> Bool { false }

  public static func summarizeDay(
    entries: [WorkClockEntry],
    weeklyWorkedMillis: Int64? = nil
  ) -> WorkClockSummary {
    let worked = workedMillis(entries)
    let weekly = weeklyWorkedMillis ?? worked
    let lunch = durationBetween(entries, start: .almocoInicio, stop: .almocoFim)
    let rest = durationBetween(entries, start: .descansoInicio, stop: .descansoFim)
    let breakMillis = lunch + rest
    let requiredBreak = worked >= 12 * 60 * 60 * 1000 ? extendedShiftBreakMillis : regularBreakMillis
    let overtime = max(0, weekly - weeklyExpectedMillis)
    return WorkClockSummary(
      workedMillis: worked,
      lunchMillis: lunch,
      restMillis: rest,
      requiredBreakMillis: requiredBreak,
      missingBreakMillis: entries.isEmpty ? 0 : max(0, requiredBreak - breakMillis),
      breakOverageMillis: entries.isEmpty ? 0 : max(0, breakMillis - requiredBreak),
      missingDailyMillis: entries.isEmpty ? 0 : max(0, dailyExpectedMillis - worked),
      missingWeeklyMillis: max(0, weeklyExpectedMillis - weekly),
      overtimeMillis: overtime,
      requiresTwoHoursRest: worked >= 12 * 60 * 60 * 1000 && breakMillis < extendedShiftBreakMillis
    )
  }

  public static func distanceMeters(from: GeoPoint, to: GeoPoint) -> Double {
    let earthRadius = 6_371_000.0
    let latDistance = (to.latitude - from.latitude) * .pi / 180
    let lonDistance = (to.longitude - from.longitude) * .pi / 180
    let startLat = from.latitude * .pi / 180
    let endLat = to.latitude * .pi / 180
    let a = sin(latDistance / 2) * sin(latDistance / 2)
      + cos(startLat) * cos(endLat) * sin(lonDistance / 2) * sin(lonDistance / 2)
    let c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
  }

  public static func formatDuration(_ millis: Int64) -> String {
    let totalMinutes = millis / 60_000
    let hours = totalMinutes / 60
    let minutes = totalMinutes % 60
    return "\(hours)h \(String(format: "%02d", minutes))min"
  }

  private static func workedMillis(_ entries: [WorkClockEntry]) -> Int64 {
    durationAcross(
      entries,
      startTypes: [.entrada, .almocoFim, .descansoFim],
      stopTypes: [.almocoInicio, .descansoInicio, .saida]
    )
  }

  private static func durationBetween(
    _ entries: [WorkClockEntry],
    start: WorkClockType,
    stop: WorkClockType
  ) -> Int64 {
    durationAcross(entries, startTypes: [start], stopTypes: [stop])
  }

  private static func durationAcross(
    _ entries: [WorkClockEntry],
    startTypes: Set<WorkClockType>,
    stopTypes: Set<WorkClockType>
  ) -> Int64 {
    var currentStart: Int64?
    var total: Int64 = 0
    for entry in entries.sorted(by: { $0.registeredAt < $1.registeredAt }) {
      if startTypes.contains(entry.type) { currentStart = entry.registeredAt }
      if stopTypes.contains(entry.type), let start = currentStart {
        total += max(0, entry.registeredAt - start)
        currentStart = nil
      }
    }
    return total
  }
}
