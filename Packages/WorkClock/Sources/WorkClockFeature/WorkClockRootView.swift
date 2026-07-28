import SwiftUI
import CoreLocation
import UIKit
import Models
import Persistence
import Env
import DesignSystem
import Network

public struct WorkClockRootView: View {
  private let user: User
  private let userId: Int64
  private let authToken: String?
  private let remoteUserId: String?
  private let repository: ChecklistRepository
  private let workClockClient: WorkClockClient?
  private let syncController: SyncController
  private let deviceId: String
  private let onShowDayEntries: (() -> Void)?
  private let onLogout: () -> Void
  private let embeddedInNavigationStack: Bool

  @StateObject private var tracker = LocationTracker()
  @State private var entries: [WorkClockEntry] = []
  @State private var weeklyWorkedMillis: Int64 = 0
  @State private var monthlyAbsences: [WorkClockAbsenceDetail] = []
  @State private var monthlyAbsenceStatus = "Buscando faltas do mês…"
  @State private var feedbackAlert: WorkClockFeedbackAlert?
  @State private var isRegistering = false

  public init(
    user: User,
    userId: Int64,
    authToken: String?,
    remoteUserId: String?,
    repository: ChecklistRepository,
    workClockClient: WorkClockClient? = nil,
    syncController: SyncController,
    deviceId: String,
    embeddedInNavigationStack: Bool = false,
    onShowDayEntries: (() -> Void)? = nil,
    onLogout: @escaping () -> Void = {}
  ) {
    self.user = user
    self.userId = userId
    self.authToken = authToken
    self.remoteUserId = remoteUserId
    self.repository = repository
    self.workClockClient = workClockClient
    self.syncController = syncController
    self.deviceId = deviceId
    self.embeddedInNavigationStack = embeddedInNavigationStack
    self.onShowDayEntries = onShowDayEntries
    self.onLogout = onLogout
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

  public var body: some View {
    let nextType = WorkClockCalculator.nextType(entries: entries)
    let summary = WorkClockCalculator.summarizeDay(
      entries: entries,
      weeklyWorkedMillis: weeklyWorkedMillis
    )
    let distance = currentDistance
    let canRegister = canUseClock(distance: distance)

    ScrollView {
      LazyVStack(alignment: .leading, spacing: BecoTokens.Spacing.lg) {
        WorkClockTitleSection(nextType: nextType)
        WorkClockStatusSection(
          distance: distance,
          accuracy: effectiveAccuracy,
          locationStatus: locationStatus,
          isWithinRadius: canRegister,
          authorizationDenied: tracker.authorizationDenied
        )
        WorkClockSummarySection(summary: summary)
        WorkClockAbsenceSection(absences: monthlyAbsences, status: monthlyAbsenceStatus)
        if let onShowDayEntries, !entries.isEmpty {
          WorkClockDayEntriesButton(action: onShowDayEntries)
        }
      }
      .padding(.horizontal, BecoTokens.Spacing.md)
      .padding(.bottom, BecoTokens.Spacing.xxl)
    }
    .background(BecoTokens.ColorToken.background)
    .toolbar(embeddedInNavigationStack ? .automatic : .hidden, for: .navigationBar)
    .safeAreaInset(edge: .top, spacing: 0) {
      if !embeddedInNavigationStack {
        BecoUserHeader(
          name: user.name,
          role: user.workSector.displayName,
          date: Date.now.formatted(date: .abbreviated, time: .omitted),
          onLogout: onLogout
        )
        .background(BecoTokens.ColorToken.background)
      }
    }
    .safeAreaInset(edge: .bottom) {
      WorkClockRegisterBar(
        nextType: nextType,
        isRegistering: isRegistering,
        canRegister: canRegister,
        onRegister: { handleRegisterTap(type: nextType, distance: distance ?? 0, canRegister: canRegister) }
      )
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
    let week = Date.currentWeekMillis
    let weeklyEntries = (try? repository.workClockEntries(
      userId: userId,
      dayStart: week.start,
      dayEnd: week.end
    )) ?? []
    weeklyWorkedMillis = Dictionary(grouping: weeklyEntries) { entry in
      Calendar.current.startOfDay(
        for: Date(timeIntervalSince1970: TimeInterval(entry.registeredAt) / 1000)
      )
    }
    .values
    .reduce(0) { total, dayEntries in
      total + WorkClockCalculator.summarizeDay(entries: dayEntries).workedMillis
    }
    await loadMonthlyAbsences()
  }

  @MainActor
  private func loadMonthlyAbsences() async {
    guard let token = authToken, let workClockClient else {
      monthlyAbsenceStatus = "Faltas indisponíveis offline."
      return
    }
    let range = Calendar.current.dateInterval(of: .month, for: Date()) ?? DateInterval(start: Date(), duration: 0)
    let from = Self.isoDateFormatter.string(from: range.start)
    let endDate = Calendar.current.date(byAdding: DateComponents(day: -1), to: range.end) ?? range.start
    let to = Self.isoDateFormatter.string(from: endDate)
    do {
      let summary = try await workClockClient.fetchMySummary(token: token, from: from, to: to)
      monthlyAbsences = summary.absenceDetails
      monthlyAbsenceStatus = summary.absenceDays == 0 ? "Sem faltas no mês." : "\(summary.absenceDays) falta(s) no mês."
    } catch {
      monthlyAbsenceStatus = "Faltas indisponíveis offline."
    }
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

  private static let isoDateFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter
  }()
}

// MARK: - Subviews (MV)

private struct WorkClockTitleSection: View {
  let nextType: WorkClockType

  var body: some View {
    VStack(alignment: .leading, spacing: BecoTokens.Spacing.xxs) {
      Text("Ponto").font(.largeTitle.bold())
      Text("Próxima marcação: \(nextType.displayName)")
        .foregroundStyle(BecoTokens.ColorToken.muted)
    }
  }
}

private struct WorkClockDayEntriesButton: View {
  let action: () -> Void

  var body: some View {
    Button("Ver marcações do dia", action: action)
      .buttonStyle(.bordered)
  }
}

private struct WorkClockRegisterBar: View {
  let nextType: WorkClockType
  let isRegistering: Bool
  let canRegister: Bool
  let onRegister: () -> Void

  var body: some View {
    VStack(spacing: 12) {
      BecoButton(
        "Registrar \(nextType.displayName)",
        isLoading: isRegistering,
        action: onRegister
      )
      .disabled(isRegistering || !canRegister)
    }
    .padding(.horizontal)
    .padding(.vertical, 12)
    .background(Color(uiColor: .systemBackground))
  }
}

private struct WorkClockFeedbackAlert: Identifiable {
  let title: String
  let message: String
  var id: String { "\(title)-\(message)" }
}

private struct WorkClockStatusSection: View {
  let distance: Double?
  let accuracy: Double?
  let locationStatus: String
  let isWithinRadius: Bool
  let authorizationDenied: Bool

  var body: some View {
    VStack(alignment: .leading, spacing: BecoTokens.Spacing.sm) {
      Text("Localização").font(.headline)
      WorkClockLocationStatusRow(
        locationStatus: locationStatus,
        isWithinRadius: isWithinRadius
      )
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
}

private struct WorkClockLocationStatusRow: View {
  let locationStatus: String
  let isWithinRadius: Bool

  var body: some View {
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
      Text("Resumo").font(.headline)
      BecoValueRow(label: "Trabalhadas hoje", value: WorkClockCalculator.formatDuration(summary.workedMillis))
      Divider()
      BecoValueRow(
        label: "Horas restantes na semana",
        value: WorkClockCalculator.formatDuration(summary.missingWeeklyMillis)
      )
      Divider()
      BecoValueRow(label: "Extras na semana", value: WorkClockCalculator.formatDuration(summary.overtimeMillis))
      Divider()
      BecoValueRow(label: "Descanso devido", value: WorkClockCalculator.formatDuration(summary.missingBreakMillis))
      Divider()
      BecoValueRow(label: "Horas devidas hoje", value: WorkClockCalculator.formatDuration(summary.missingDailyMillis))
    }
    .padding(BecoTokens.Spacing.md)
    .background(BecoTokens.ColorToken.surface, in: RoundedRectangle(cornerRadius: 16))
  }
}

private struct WorkClockAbsenceSection: View {
  let absences: [WorkClockAbsenceDetail]
  let status: String

  var body: some View {
    VStack(alignment: .leading, spacing: BecoTokens.Spacing.sm) {
      Text("Faltas")
        .font(.headline)
      BecoValueRow(label: "Faltas no mês", value: "\(absences.count)")
      if absences.isEmpty {
        Text(status)
          .font(.subheadline)
          .foregroundStyle(BecoTokens.ColorToken.muted)
      } else {
        ForEach(absences.prefix(5), id: \.date) { absence in
          BecoValueRow(label: formatIsoDateBR(absence.date), value: absence.reason)
        }
      }
    }
    .padding()
    .background(BecoTokens.ColorToken.surface)
    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
  }

  private func formatIsoDateBR(_ value: String) -> String {
    let parts = value.split(separator: "-")
    guard parts.count == 3 else { return value }
    return "\(parts[2])/\(parts[1])/\(parts[0])"
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

  static var currentWeekMillis: (start: Int64, end: Int64) {
    let calendar = Calendar.current
    let today = calendar.startOfDay(for: Date())
    let daysSinceMonday = (calendar.component(.weekday, from: today) + 5) % 7
    let monday = calendar.date(byAdding: .day, value: -daysSinceMonday, to: today) ?? today
    let nextMonday = calendar.date(byAdding: .day, value: 7, to: monday) ?? today
    return (
      Int64(monday.timeIntervalSince1970 * 1000),
      Int64(nextMonday.timeIntervalSince1970 * 1000)
    )
  }
}
