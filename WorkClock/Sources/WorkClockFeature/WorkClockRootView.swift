import SwiftUI
import CoreLocation
import Models
import Persistence
import Env
import DesignSystem

public final class LocationTracker: NSObject, ObservableObject, CLLocationManagerDelegate {
  @Published public var location: CLLocation?
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
        accuracy: tracker.location?.horizontalAccuracy
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
          Task { await register(type: nextType, distance: distance ?? 999) }
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(!canRegister)
      }
    }
    .themedFormStyle()
    .navigationTitle("Ponto")
    .onAppear { tracker.start() }
    .task { await reload() }
  }

  private var currentDistance: Double? {
    guard let location = tracker.location else { return nil }
    return WorkClockCalculator.distanceMeters(
      from: WorksiteLocation.point,
      to: GeoPoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude)
    )
  }

  private func canUseClock(distance: Double?) -> Bool {
    guard let distance, let accuracy = tracker.location?.horizontalAccuracy else { return false }
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
    guard let location = tracker.location,
          let token = authToken,
          let remoteUserId
    else { return }
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
      await syncController.retryWorkClockEntries(
        userId: userId,
        remoteUserId: remoteUserId,
        token: token,
        deviceId: deviceId
      )
      feedback = "\(type.displayName) registrada."
      await reload()
    } catch {
      feedback = "Registrada localmente. Sincronização pendente."
    }
  }
}

private struct WorkClockStatusSection: View {
  let nextType: WorkClockType
  let distance: Double?
  let accuracy: Double?

  var body: some View {
    Section("Próxima marcação") {
      Text(nextType.displayName).font(.title2.bold())
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
