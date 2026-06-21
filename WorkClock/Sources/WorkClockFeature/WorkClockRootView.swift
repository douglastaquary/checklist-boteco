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
  @EnvironmentObject private var session: AppSession

  private let repository: ChecklistRepository
  private let syncController: SyncController
  private let deviceId: String

  @StateObject private var tracker = LocationTracker()
  @State private var entries: [WorkClockEntry] = []
  @State private var feedback: String?

  public init(repository: ChecklistRepository, syncController: SyncController, deviceId: String) {
    self.repository = repository
    self.syncController = syncController
    self.deviceId = deviceId
  }

  public var body: some View {
    let nextType = WorkClockCalculator.nextType(entries: entries)
    let summary = WorkClockCalculator.summarizeDay(entries: entries)
    let distance = currentDistance
    let canRegister = canUseClock(distance: distance)

    NavigationStack {
      Form {
        Section("Próxima marcação") {
          Text(nextType.displayName).font(.title2.bold())
          if let distance {
            Text(String(format: "Distância do local: %.1f m", distance))
          }
          if let accuracy = tracker.location?.horizontalAccuracy {
            Text(String(format: "Precisão GPS: %.0f m", accuracy))
          }
        }
        Section("Resumo do dia") {
          LabeledContent("Trabalhadas", value: WorkClockCalculator.formatDuration(summary.workedMillis))
          LabeledContent("Extras semana", value: WorkClockCalculator.formatDuration(summary.overtimeMillis))
        }
        if let feedback {
          Text(feedback).foregroundStyle(.orange)
        }
        Button("Registrar \(nextType.displayName)") {
          Task { await register(type: nextType, distance: distance ?? 999) }
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(!canRegister)
      }
      .navigationTitle("Ponto")
      .onAppear { tracker.start() }
      .task { await reload() }
    }
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

  private func reload() async {
    guard let userId = session.currentUser?.id else { return }
    let start = Date.startOfDayMillis
    let end = start + 24 * 60 * 60 * 1000
    entries = (try? repository.workClockEntries(userId: userId, dayStart: start, dayEnd: end)) ?? []
  }

  private func register(type: WorkClockType, distance: Double) async {
    guard let user = session.currentUser,
          let location = tracker.location,
          let token = session.authToken,
          let remoteUserId = session.remoteUserId
    else { return }
    let now = Date.nowMillis
    let entry = WorkClockEntry(
      id: 0,
      userId: user.id,
      type: type,
      registeredAt: now,
      location: GeoPoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude),
      distanceFromWorkMeters: distance,
      isLate: WorkClockCalculator.isLateEntry()
    )
    do {
      _ = try repository.insertWorkClockEntry(entry)
      await syncController.retryWorkClockEntries(
        userId: user.id,
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

private extension Date {
  static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
  static var startOfDayMillis: Int64 {
    Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
  }
}
