import SwiftUI
import CoreLocation
import Models
import Persistence
import Env
import DesignSystem

public final class LocationTracker: NSObject, ObservableObject, CLLocationManagerDelegate {
  @Published public var location: CLLocation?
  @Published public var authorizationDenied = false
  @Published public var locationError: String?
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
    guard let latest = locations.last, latest.horizontalAccuracy >= 0 else { return }
    location = latest
    authorizationDenied = false
    locationError = nil
  }

  public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    locationError = error.localizedDescription
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
  private let user: User
  private let userId: Int64
  private let authToken: String?
  private let remoteUserId: String?
  private let repository: ChecklistRepository
  private let syncController: SyncController
  private let deviceId: String
  private let onShowDayEntries: (() -> Void)?
  private let onLogout: () -> Void

  @EnvironmentObject private var theme: AppTheme
  @StateObject private var tracker = LocationTracker()
  @State private var entries: [WorkClockEntry] = []
  @State private var feedbackAlert: WorkClockFeedbackAlert?
  @State private var isRegistering = false

  public init(
    user: User,
    userId: Int64,
    authToken: String?,
    remoteUserId: String?,
    repository: ChecklistRepository,
    syncController: SyncController,
    deviceId: String,
    onShowDayEntries: (() -> Void)? = nil,
    onLogout: @escaping () -> Void = {}
  ) {
    self.user = user
    self.userId = userId
    self.authToken = authToken
    self.remoteUserId = remoteUserId
    self.repository = repository
    self.syncController = syncController
    self.deviceId = deviceId
    self.onShowDayEntries = onShowDayEntries
    self.onLogout = onLogout
  }

  public var body: some View {
    let nextType = WorkClockCalculator.nextType(entries: entries)
    let summary = WorkClockCalculator.summarizeDay(entries: entries)
    let distance = currentDistance
    let canRegister = canUseClock(distance: distance)

    ScrollView {
      LazyVStack(alignment: .leading, spacing: BecoTokens.Spacing.lg) {
        VStack(alignment: .leading, spacing: BecoTokens.Spacing.xxs) {
          Text("Ponto").font(.largeTitle.bold())
          Text("Próxima marcação: \(nextType.displayName)")
            .foregroundStyle(BecoTokens.ColorToken.muted)
        }
        WorkClockStatusSection(
          nextType: nextType,
          distance: distance,
          accuracy: effectiveAccuracy,
          locationStatus: locationStatus,
          isWithinRadius: canRegister,
          authorizationDenied: tracker.authorizationDenied
        )
        WorkClockSummarySection(summary: summary)
        if let onShowDayEntries, !entries.isEmpty {
          Button("Ver marcações do dia", action: onShowDayEntries)
            .buttonStyle(.bordered)
        }
      }
      .padding(.horizontal, BecoTokens.Spacing.md)
      .padding(.bottom, BecoTokens.Spacing.xxl)
    }
    .background(BecoTokens.ColorToken.background)
    .toolbar(.hidden, for: .navigationBar)
    .safeAreaInset(edge: .top, spacing: 0) {
      BecoUserHeader(
        name: user.name,
        role: user.workSector.displayName,
        date: Date.now.formatted(date: .abbreviated, time: .omitted),
        onLogout: onLogout
      )
      .background(BecoTokens.ColorToken.background)
    }
    .safeAreaInset(edge: .bottom) {
      VStack(spacing: 12) {
        Button {
          handleRegisterTap(type: nextType, distance: distance ?? 0, canRegister: canRegister)
        } label: {
          Group {
            if isRegistering {
              ProgressView()
                .tint(.white)
            } else {
              Text("Registrar \(nextType.displayName)")
            }
          }
          .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .tint(theme.tint)
        .disabled(isRegistering)
      }
      .padding(.horizontal)
      .padding(.vertical, 12)
      .background(Color(uiColor: .systemBackground))
    }
    .onAppear { tracker.start() }
    .task { await reload() }
    .alert(item: $feedbackAlert) { alert in
      Alert(
        title: Text(alert.title),
        message: Text(alert.message),
        dismissButton: .default(Text("OK"))
      )
    }
  }

  private var effectiveLocation: CLLocation? {
    #if targetEnvironment(simulator)
    return Self.simulatorWorksiteLocation
    #else
    guard let location = tracker.location, location.horizontalAccuracy >= 0 else { return nil }
    return location
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
    if let locationError = tracker.locationError {
      return locationError
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

  private func handleRegisterTap(type: WorkClockType, distance: Double, canRegister: Bool) {
    if canRegister {
      Task { await register(type: type, distance: distance) }
    } else {
      feedbackAlert = WorkClockFeedbackAlert(
        title: "Ponto",
        message: locationStatus
      )
    }
  }

  @MainActor
  private func reload() async {
    let start = Date.startOfDayMillis
    let end = start + 24 * 60 * 60 * 1000
    entries = (try? repository.workClockEntries(userId: userId, dayStart: start, dayEnd: end)) ?? []
  }

  @MainActor
  private func register(type: WorkClockType, distance: Double) async {
    guard !isRegistering else { return }
    guard let location = effectiveLocation else {
      feedbackAlert = WorkClockFeedbackAlert(title: "Ponto", message: locationStatus)
      return
    }
    isRegistering = true
    defer { isRegistering = false }

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
        feedbackAlert = WorkClockFeedbackAlert(
          title: "Ponto",
          message: "\(type.displayName) registrada."
        )
      } else {
        feedbackAlert = WorkClockFeedbackAlert(
          title: "Ponto",
          message: "\(type.displayName) registrada localmente. Sincronização pendente."
        )
      }
      await reload()
    } catch {
      feedbackAlert = WorkClockFeedbackAlert(
        title: "Ponto",
        message: "Não foi possível registrar: \(error.localizedDescription)"
      )
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

private struct WorkClockFeedbackAlert: Identifiable {
  let title: String
  let message: String
  var id: String { "\(title)-\(message)" }
}

private struct WorkClockStatusSection: View {
  let nextType: WorkClockType
  let distance: Double?
  let accuracy: Double?
  let locationStatus: String
  let isWithinRadius: Bool
  let authorizationDenied: Bool

  var body: some View {
    VStack(alignment: .leading, spacing: BecoTokens.Spacing.sm) {
      Text("Localização").font(.headline)
      statusRow
      if let distance {
        Text(String(format: "Distância do local: %.1f m", distance))
      }
      if let accuracy {
        Text(String(format: "Precisão GPS: %.0f m", accuracy))
      }
      if authorizationDenied {
        Button("Abrir Ajustes") {
          guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
          UIApplication.shared.open(url)
        }
      }
    }
    .padding(BecoTokens.Spacing.md)
    .background(BecoTokens.ColorToken.surface, in: RoundedRectangle(cornerRadius: 16))
  }

  @ViewBuilder
  private var statusRow: some View {
    HStack {
      Image(systemName: isWithinRadius ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
        .foregroundColor(isWithinRadius ? .green : .red)
      Text(locationStatus)
        .font(.footnote)
        .foregroundColor(isWithinRadius ? .secondary : .red)
    }
  }
}

private struct WorkClockSummarySection: View {
  let summary: WorkClockSummary

  var body: some View {
    VStack(alignment: .leading, spacing: BecoTokens.Spacing.sm) {
      Text("Resumo do dia").font(.headline)
      BecoValueRow(label: "Trabalhadas", value: WorkClockCalculator.formatDuration(summary.workedMillis))
      Divider()
      BecoValueRow(label: "Extras na semana", value: WorkClockCalculator.formatDuration(summary.overtimeMillis))
    }
    .padding(BecoTokens.Spacing.md)
    .background(BecoTokens.ColorToken.surface, in: RoundedRectangle(cornerRadius: 16))
  }
}

private struct BecoValueRow: View {
  let label: String
  let value: String

  var body: some View {
    HStack {
      Text(label).foregroundStyle(BecoTokens.ColorToken.muted)
      Spacer()
      Text(value).fontWeight(.semibold)
    }
  }
}

private extension Date {
  static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
  static var startOfDayMillis: Int64 {
    Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
  }
}

import UIKit
