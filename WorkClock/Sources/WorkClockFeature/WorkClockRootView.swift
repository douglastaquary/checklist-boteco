import SwiftUI
import CoreLocation
import Models
import Persistence
import Env
import DesignSystem

public final class LocationTracker: NSObject, ObservableObject, CLLocationManagerDelegate {
  @Published public var location: CLLocation?
  @Published public var authorizationDenied = false
  private let manager = CLLocationManager()

  public override init() {
    super.init()
    manager.delegate = self
    manager.desiredAccuracy = kCLLocationAccuracyBest
  }

  public func start() {
    manager.requestWhenInUseAuthorization()
    manager.startUpdatingLocation()
  }

  public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    location = locations.last
    authorizationDenied = false
  }

  public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    switch manager.authorizationStatus {
    case .denied, .restricted:
      authorizationDenied = true
    case .authorizedAlways, .authorizedWhenInUse:
      authorizationDenied = false
      manager.startUpdatingLocation()
    default:
      break
    }
  }
}

public struct WorkClockRootView: View {
  private let userId: Int64
  private let authToken: String?
  private let remoteUserId: String?
  private let repository: ChecklistRepository
  private let syncController: SyncController
  private let deviceId: String
  private let onShowDayEntries: (() -> Void)?

  @StateObject private var tracker = LocationTracker()
  @State private var entries: [WorkClockEntry] = []
  @State private var feedback: String?

  public init(
    userId: Int64,
    authToken: String?,
    remoteUserId: String?,
    repository: ChecklistRepository,
    syncController: SyncController,
    deviceId: String,
    onShowDayEntries: (() -> Void)? = nil
  ) {
    self.userId = userId
    self.authToken = authToken
    self.remoteUserId = remoteUserId
    self.repository = repository
    self.syncController = syncController
    self.deviceId = deviceId
    self.onShowDayEntries = onShowDayEntries
  }

  public var body: some View {
    let nextType = WorkClockCalculator.nextType(entries: entries)
    let summary = WorkClockCalculator.summarizeDay(entries: entries)
    let distance = currentDistance
    let canRegister = canUseClock(distance: distance)

    Form {
      WorkClockStatusSection(
        nextType: nextType,
        distance: distance,
        accuracy: effectiveAccuracy,
        locationStatus: locationStatus
      )
      WorkClockSummarySection(summary: summary)
      if let feedback {
        Section {
          Text(feedback).foregroundStyle(.orange)
        }
      }
      if let onShowDayEntries, !entries.isEmpty {
        Section {
          Button("Ver marcações do dia", action: onShowDayEntries)
        }
      }
      Section {
        Button("Registrar \(nextType.displayName)") {
          if canRegister {
            Task { await register(type: nextType, distance: distance ?? 0) }
          } else {
            feedback = locationStatus
          }
        }
        .buttonStyle(PrimaryButtonStyle())
      }
    }
    .themedFormStyle()
    .navigationTitle("Ponto")
    .onAppear { tracker.start() }
    .task { await reload() }
  }

  private var effectiveLocation: CLLocation? {
    if let location = tracker.location, location.horizontalAccuracy >= 0 {
      return location
    }
    #if targetEnvironment(simulator)
    return Self.simulatorWorksiteLocation
    #else
    return nil
    #endif
  }

  private var effectiveAccuracy: Double? {
    effectiveLocation?.horizontalAccuracy
  }

  private var currentDistance: Double? {
    guard let location = effectiveLocation else { return nil }
    return WorkClockCalculator.distanceMeters(
      from: WorksiteLocation.point,
      to: GeoPoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude)
    )
  }

  private var locationStatus: String {
    if tracker.authorizationDenied {
      return "Permita o acesso ao GPS para registrar ponto."
    }
    guard effectiveLocation != nil else {
      return "Aguardando sinal GPS…"
    }
    if let accuracy = effectiveAccuracy, accuracy > 20 {
      return "Aguardando precisão do GPS (≤ 20 m)…"
    }
    if let distance = currentDistance, distance > WorksiteLocation.allowedRadiusMeters {
      return "Fora do raio de \(Int(WorksiteLocation.allowedRadiusMeters)) m (\(Int(distance)) m)."
    }
    return "Dentro do raio permitido."
  }

  private func canUseClock(distance: Double?) -> Bool {
    guard !tracker.authorizationDenied else { return false }
    guard let distance, let accuracy = effectiveAccuracy else { return false }
    return accuracy <= 20 && distance <= WorksiteLocation.allowedRadiusMeters
  }

  @MainActor
  private func reload() async {
    let start = Date.startOfDayMillis
    let end = start + 24 * 60 * 60 * 1000
    entries = (try? repository.workClockEntries(userId: userId, dayStart: start, dayEnd: end)) ?? []
  }

  @MainActor
  private func register(type: WorkClockType, distance: Double) async {
    guard let location = effectiveLocation else {
      feedback = locationStatus
      return
    }
    let entry = WorkClockEntry(
      id: 0,
      userId: userId,
      type: type,
      registeredAt: Date.nowMillis,
      location: GeoPoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude),
      distanceFromWorkMeters: distance,
      isLate: WorkClockCalculator.isLateEntry()
    )
    do {
      _ = try repository.insertWorkClockEntry(entry)
      if let token = authToken, let remoteUserId {
        await syncController.retryWorkClockEntries(
          userId: userId,
          remoteUserId: remoteUserId,
          token: token,
          deviceId: deviceId
        )
        feedback = "\(type.displayName) registrada."
      } else {
        feedback = "\(type.displayName) registrada localmente. Sincronização pendente."
      }
      await reload()
    } catch {
      feedback = "Não foi possível registrar: \(error.localizedDescription)"
    }
  }

  #if targetEnvironment(simulator)
  private static let simulatorWorksiteLocation = CLLocation(
    coordinate: CLLocationCoordinate2D(
      latitude: WorksiteLocation.point.latitude,
      longitude: WorksiteLocation.point.longitude
    ),
    altitude: 0,
    horizontalAccuracy: 5,
    verticalAccuracy: 5,
    timestamp: Date()
  )
  #endif
}

private struct WorkClockStatusSection: View {
  let nextType: WorkClockType
  let distance: Double?
  let accuracy: Double?
  let locationStatus: String

  var body: some View {
    Section("Próxima marcação") {
      Text(nextType.displayName).font(.title2.bold())
      Text(locationStatus)
        .font(.footnote)
        .foregroundStyle(.secondary)
      if let distance {
        Text(String(format: "Distância do local: %.1f m", distance))
      }
      if let accuracy {
        Text(String(format: "Precisão GPS: %.0f m", accuracy))
      }
    }
  }
}

private struct WorkClockSummarySection: View {
  let summary: WorkClockSummary

  var body: some View {
    Section("Resumo do dia") {
      LabeledContent("Trabalhadas", value: WorkClockCalculator.formatDuration(summary.workedMillis))
      LabeledContent("Extras semana", value: WorkClockCalculator.formatDuration(summary.overtimeMillis))
    }
  }
}

private extension Date {
  static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
  static var startOfDayMillis: Int64 {
    Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
  }
}
