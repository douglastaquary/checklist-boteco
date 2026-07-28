import SwiftUI
import Models
import Persistence
import Env
import DesignSystem
import UserNotifications

public struct ChecklistRootView: View {
  private let user: User
  private let repository: ChecklistRepository
  private let syncController: SyncController
  private let onLogout: () -> Void
  private let embeddedInNavigationStack: Bool
  private let onSelectActivity: ((Int64, Area) -> Void)?

  @Environment(\.colorScheme) private var colorScheme
  @Environment(\.dismiss) private var dismiss
  @Namespace private var chromeNamespace
  @State private var selectedArea: Area
  @State private var items: [ActivityWithCompletion] = []
  @State private var selectedFilter: ChecklistViewFilter = .all
  @State private var cameraCapture: CameraCaptureRequest?
  @State private var alert: ChecklistAlert?
  @State private var now = Date()
  @State private var schedule = ChecklistSchedule()
  @State private var isAreaDrawerOpen = false
  @State private var isChromeCollapsed = false

  private var accessibleAreas: [Area] {
    user.checklistAccessibleAreas
  }

  private var isDark: Bool { colorScheme == .dark }
  private var foreground: Color { isDark ? .white : .black }
  private var muted: Color { isDark ? .white.opacity(0.62) : .secondary }

  private var isOperatingDayToday: Bool {
    guard let weekday = Self.weekdayName(for: now, timezone: schedule.timezone) else { return false }
    return schedule.days[weekday]?.active == true
  }

  private var visibleItems: [ActivityWithCompletion] {
    filteredItems(for: selectedFilter)
  }

  private var scheduledItems: [ActivityWithCompletion] {
    scheduledItems(for: selectedFilter)
  }

  private var pendingItems: [ActivityWithCompletion] {
    scheduledItems.filter { $0.completion == nil }
  }

  private var lateCount: Int {
    pendingItems.filter {
      ActivityTiming.today(
        activity: $0.activity,
        completion: nil,
        now: now,
        schedule: schedule
      ).status == .red
    }.count
  }

  private var remainingMinutes: Int {
    pendingItems.reduce(0) { $0 + $1.activity.estimatedDurationMinutes }
  }

  private var emptyMessage: String {
    if !isOperatingDayToday {
      return "Hoje não é dia de operação. Nenhuma atividade agendada."
    }
    switch selectedFilter {
    case .all:
      if items.isEmpty {
        return "Não há atividades para \(selectedArea.displayName)."
      }
      return "Nenhuma atividade prevista para hoje em \(selectedArea.displayName)."
    case .pending: return "Todas as atividades de hoje foram concluídas."
    case .completed: return "Nenhuma atividade foi concluída hoje."
    }
  }

  private var filterCounts: [ChecklistViewFilter: Int] {
    Dictionary(uniqueKeysWithValues: ChecklistViewFilter.allCases.map { ($0, count(for: $0)) })
  }

  private var statusSubtitle: String {
    "\(pendingItems.count) pendentes · \(selectedArea.displayName)"
  }

  public init(
    user: User,
    repository: ChecklistRepository,
    syncController: SyncController,
    onLogout: @escaping () -> Void,
    embeddedInNavigationStack: Bool = false,
    onSelectActivity: ((Int64, Area) -> Void)? = nil
  ) {
    self.user = user
    self.repository = repository
    self.syncController = syncController
    self.onLogout = onLogout
    self.embeddedInNavigationStack = embeddedInNavigationStack
    self.onSelectActivity = onSelectActivity
    let areas = user.checklistAccessibleAreas
    let initialArea = areas.first ?? user.workSector.checklistAreas.first ?? .atendimento
    _selectedArea = State(initialValue: initialArea)
  }

  public var body: some View {
    ZStack {
      ChecklistBackground(isDark: isDark)

      VStack(spacing: 0) {
        if embeddedInNavigationStack {
          embeddedChrome
            .padding(.horizontal, 20)
            .padding(.top, 4)
        } else {
          ChecklistChromeHeader(
            centerTitle: selectedArea.displayName,
            statusSubtitle: statusSubtitle,
            foreground: foreground,
            muted: muted,
            isDark: isDark,
            showMenuButton: accessibleAreas.count > 1,
            onMenuTap: openAreaDrawer,
            onLogout: onLogout
          )
          .padding(.horizontal, 20)
          .padding(.top, 10)
        }

        ScrollView {
          LazyVStack(alignment: .leading, spacing: BecoTokens.Spacing.sm) {
            Text("Checklist")
              .font(.largeTitle.bold())
              .foregroundStyle(foreground)
              .padding(.top, 12)

            ChecklistStatsBar(
              pendingCount: pendingItems.count,
              lateCount: lateCount,
              remainingMinutes: remainingMinutes,
              foreground: foreground,
              muted: muted,
              isDark: isDark
            )
            ChecklistStatusFilters(
              selectedFilter: $selectedFilter,
              filterCounts: filterCounts
            )
            Divider().padding(.vertical, BecoTokens.Spacing.xs)
            Text(selectedArea.displayName)
              .font(.headline)
              .foregroundStyle(foreground)
            ChecklistContent(
              scheduledItems: scheduledItems,
              emptyMessage: emptyMessage,
              now: now,
              schedule: schedule,
              selectedArea: selectedArea,
              muted: muted,
              onSelectActivity: onSelectActivity,
              onComplete: { cameraCapture = CameraCaptureRequest(activityId: $0) }
            )
          }
          .padding(.horizontal, 20)
          .padding(.bottom, BecoTokens.Spacing.xl)
        }
      }

      if isAreaDrawerOpen {
        ChecklistAreaDrawer(
          areas: accessibleAreas,
          selectedArea: selectedArea,
          isDark: isDark,
          foreground: foreground,
          muted: muted,
          onSelect: { area in
            selectedArea = area
            withAnimation(.easeInOut(duration: 0.28)) {
              isAreaDrawerOpen = false
            }
          },
          onClose: {
            withAnimation(.easeInOut(duration: 0.28)) {
              isAreaDrawerOpen = false
            }
          }
        )
        .transition(.move(edge: .leading).combined(with: .opacity))
        .zIndex(2)
      }
    }
    .toolbar(.hidden, for: .navigationBar)
    .navigationBarBackButtonHidden(embeddedInNavigationStack)
    .background {
      if embeddedInNavigationStack {
        BecoInteractivePopGestureEnabler()
      }
    }    .task(id: selectedArea) { await reload() }
    .task {
      while !Task.isCancelled {
        try? await Task.sleep(nanoseconds: 60_000_000_000)
        now = Date()
      }
    }
    .task(id: embeddedInNavigationStack) {
      guard embeddedInNavigationStack else {
        isChromeCollapsed = false
        return
      }
      isChromeCollapsed = false
      try? await Task.sleep(nanoseconds: 40_000_000)
      withAnimation(.spring(response: 0.38, dampingFraction: 0.86)) {
        isChromeCollapsed = true
      }
    }
    .sheet(item: $cameraCapture, onDismiss: { Task { await reload() } }) { request in
      CameraCaptureView { path in
        guard let path else { return }
        Task { await complete(activityId: request.activityId, imagePath: path) }
      }
    }
    .alert(item: $alert) { item in
      Alert(
        title: Text(item.title),
        message: Text(item.message),
        dismissButton: .default(Text("OK"))
      )
    }
  }

  @ViewBuilder
  private var embeddedChrome: some View {
    VStack(alignment: .leading, spacing: isChromeCollapsed ? 0 : 8) {
      HStack(spacing: 8) {
        BecoBackButton { dismiss() }

        if isChromeCollapsed {
          ChecklistChromeControls(
            centerTitle: selectedArea.displayName,
            statusSubtitle: statusSubtitle,
            foreground: foreground,
            muted: muted,
            isDark: isDark,
            showMenuButton: accessibleAreas.count > 1,
            chromeNamespace: chromeNamespace,
            compact: true,
            onMenuTap: openAreaDrawer,
            onLogout: onLogout
          )
        } else {
          Spacer(minLength: 0)
        }
      }

      if !isChromeCollapsed {
        ChecklistChromeControls(
          centerTitle: selectedArea.displayName,
          statusSubtitle: statusSubtitle,
          foreground: foreground,
          muted: muted,
          isDark: isDark,
          showMenuButton: accessibleAreas.count > 1,
          chromeNamespace: chromeNamespace,
          compact: false,
          onMenuTap: openAreaDrawer,
          onLogout: onLogout
        )
        .transition(.opacity)
      }
    }
  }

  private func openAreaDrawer() {
    withAnimation(.easeInOut(duration: 0.28)) {
      isAreaDrawerOpen = true
    }
  }

  private func count(for filter: ChecklistViewFilter) -> Int {
    scheduledItems(for: filter).count
  }

  private func filteredItems(for filter: ChecklistViewFilter) -> [ActivityWithCompletion] {
    switch filter {
    case .all: return items
    case .pending: return items.filter { $0.completion == nil }
    case .completed: return items.filter { $0.completion != nil }
    }
  }

  private func scheduledItems(for filter: ChecklistViewFilter) -> [ActivityWithCompletion] {
    guard isOperatingDayToday else { return [] }
    return filteredItems(for: filter).filter {
      ActivityTiming.isDueToday(activity: $0.activity, now: now, schedule: schedule)
    }
  }

  private static func weekdayName(for date: Date, timezone: String) -> String? {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: timezone) ?? .current
    let names = [
      1: "SUNDAY", 2: "MONDAY", 3: "TUESDAY", 4: "WEDNESDAY",
      5: "THURSDAY", 6: "FRIDAY", 7: "SATURDAY"
    ]
    return names[calendar.component(.weekday, from: date)]
  }

  @MainActor
  private func reload() async {
    guard user.canAccessChecklistArea(selectedArea) else {
      items = []
      return
    }
    do {
      items = try repository.activitiesByArea(selectedArea)
      schedule = try repository.checklistSchedule()
      await scheduleLocalNotifications()
    } catch {
      alert = ChecklistAlert(title: "Erro", message: error.localizedDescription)
    }
  }

  @MainActor
  private func scheduleLocalNotifications() async {
    let center = UNUserNotificationCenter.current()
    _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
    let remoteUserId = user.remoteId
    for item in scheduledItems {
      let identifier = "checklist-reminder-\(item.activity.syncId ?? String(item.id))"
      center.removePendingNotificationRequests(withIdentifiers: [identifier])
      let assigned = item.activity.assigneeIds.isEmpty
        || remoteUserId == nil
        || item.activity.assigneeIds.contains(remoteUserId!)
      guard item.completion == nil, assigned else { continue }
      let timing = ActivityTiming.today(activity: item.activity, completion: nil, schedule: schedule)
      guard timing.recommendedStart > Date() else { continue }
      let content = UNMutableNotificationContent()
      content.title = "Hora de iniciar uma atividade"
      content.body = item.activity.name
      content.sound = .default
      let components = Calendar.current.dateComponents(
        [.year, .month, .day, .hour, .minute],
        from: timing.recommendedStart
      )
      try? await center.add(
        UNNotificationRequest(
          identifier: identifier,
          content: content,
          trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        )
      )
    }
  }

  @MainActor
  private func complete(activityId: Int64, imagePath: String?) async {
    do {
      try repository.completeActivity(
        activityId: activityId,
        userId: user.id,
        imagePath: imagePath,
        isLate: false
      )
      syncController.requestSync()
      await reload()
      if imagePath != nil {
        alert = ChecklistAlert(title: "Checklist", message: "Atividade concluída.")
      }
    } catch {
      alert = ChecklistAlert(title: "Erro", message: error.localizedDescription)
    }
  }
}

// MARK: - Subviews (MV)

private struct ChecklistBackground: View {
  let isDark: Bool

  var body: some View {
    ZStack {
      LinearGradient(
        colors: isDark
          ? [Color.black, Color(red: 0.03, green: 0.03, blue: 0.04), Color.black]
          : [Color(.systemBackground), Color(.systemGroupedBackground)],
        startPoint: .top,
        endPoint: .bottom
      )
      Circle()
        .fill(isDark ? Color.white.opacity(0.06) : Color.black.opacity(0.03))
        .frame(width: 220, height: 220)
        .blur(radius: 60)
        .offset(x: -140, y: -220)
    }
    .ignoresSafeArea()
  }
}

private struct ChecklistChromeHeader: View {
  let centerTitle: String
  let statusSubtitle: String
  let foreground: Color
  let muted: Color
  let isDark: Bool
  let showMenuButton: Bool
  let onMenuTap: () -> Void
  let onLogout: () -> Void

  var body: some View {
    ChecklistChromeControls(
      centerTitle: centerTitle,
      statusSubtitle: statusSubtitle,
      foreground: foreground,
      muted: muted,
      isDark: isDark,
      showMenuButton: showMenuButton,
      chromeNamespace: nil,
      compact: false,
      onMenuTap: onMenuTap,
      onLogout: onLogout
    )
  }
}

private struct ChecklistChromeControls: View {
  let centerTitle: String
  let statusSubtitle: String
  let foreground: Color
  let muted: Color
  let isDark: Bool
  let showMenuButton: Bool
  let chromeNamespace: Namespace.ID?
  let compact: Bool
  let onMenuTap: () -> Void
  let onLogout: () -> Void

  var body: some View {
    HStack(spacing: compact ? 8 : 12) {
      if showMenuButton {
        Button(action: onMenuTap) {
          Image(systemName: "line.3.horizontal")
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(foreground)
            .frame(width: compact ? 40 : 44, height: compact ? 40 : 44)
            .background(glassCircle)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Abrir áreas")
        .matchedChromeGeometry(id: "checklistChrome.menu", namespace: chromeNamespace)
      }

      VStack(spacing: 2) {
        Text(centerTitle)
          .font(.headline.weight(.semibold))
          .foregroundStyle(foreground)
          .lineLimit(1)
        HStack(spacing: 6) {
          Circle()
            .fill(Color.green)
            .frame(width: 7, height: 7)
          Text(statusSubtitle)
            .font(.caption)
            .foregroundStyle(muted)
            .lineLimit(1)
        }
      }
      .frame(maxWidth: .infinity)
      .matchedChromeGeometry(id: "checklistChrome.title", namespace: chromeNamespace)

      Menu {
        Button(role: .destructive, action: onLogout) {
          Label("Sair", systemImage: "rectangle.portrait.and.arrow.right")
        }
      } label: {
        Image(systemName: "ellipsis")
          .font(.system(size: 16, weight: .semibold))
          .foregroundStyle(foreground)
          .frame(width: compact ? 40 : 44, height: compact ? 40 : 44)
          .background(glassCircle)
      }
      .accessibilityLabel("Mais opções")
      .matchedChromeGeometry(id: "checklistChrome.more", namespace: chromeNamespace)
    }
  }

  private var glassCircle: some View {
    Circle()
      .fill(.ultraThinMaterial)
      .overlay(
        Circle().stroke(
          isDark ? Color.white.opacity(0.14) : Color.black.opacity(0.08),
          lineWidth: 1
        )
      )
  }
}

private extension View {
  @ViewBuilder
  func matchedChromeGeometry(id: String, namespace: Namespace.ID?) -> some View {
    if let namespace {
      self.matchedGeometryEffect(id: id, in: namespace)
    } else {
      self
    }
  }
}


private struct ChecklistAreaDrawer: View {
  let areas: [Area]
  let selectedArea: Area
  let isDark: Bool
  let foreground: Color
  let muted: Color
  let onSelect: (Area) -> Void
  let onClose: () -> Void

  var body: some View {
    GeometryReader { proxy in
      let width = min(proxy.size.width * 0.78, 320)
      ZStack(alignment: .leading) {
        Color.black.opacity(isDark ? 0.55 : 0.35)
          .ignoresSafeArea()
          .onTapGesture(perform: onClose)

        VStack(alignment: .leading, spacing: 18) {
          HStack {
            Text("Áreas")
              .font(.title2.bold())
              .foregroundStyle(foreground)
            Spacer()
            Button(action: onClose) {
              Image(systemName: "xmark")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(foreground)
                .frame(width: 36, height: 36)
                .background(
                  Circle().fill(isDark ? Color.white.opacity(0.1) : Color.black.opacity(0.06))
                )
            }
            .buttonStyle(.plain)
          }
          .padding(.top, 12)

          VStack(spacing: 6) {
            ForEach(areas, id: \.self) { area in
              Button {
                onSelect(area)
              } label: {
                HStack(spacing: 12) {
                  Image(systemName: iconName(for: area))
                    .frame(width: 22)
                  Text(area.displayName)
                    .font(.body.weight(area == selectedArea ? .semibold : .regular))
                  Spacer()
                }
                .foregroundStyle(foreground)
                .padding(.horizontal, 12)
                .padding(.vertical, 12)
                .background(
                  RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(area == selectedArea
                      ? (isDark ? Color.white.opacity(0.12) : Color.black.opacity(0.06))
                      : Color.clear)
                )
              }
              .buttonStyle(.plain)
            }
          }

          Spacer()
        }
        .padding(.horizontal, 16)
        .frame(width: width, alignment: .leading)
        .frame(maxHeight: .infinity)
        .background(isDark ? Color.black : Color(.systemBackground))
      }
    }
  }

  private func iconName(for area: Area) -> String {
    switch area {
    case .atendimento: return "person.2"
    case .cozinha: return "fork.knife"
    case .estoque: return "shippingbox"
    case .limpeza: return "sparkles"
    }
  }
}

private struct ChecklistStatsBar: View {
  let pendingCount: Int
  let lateCount: Int
  let remainingMinutes: Int
  let foreground: Color
  let muted: Color
  let isDark: Bool

  var body: some View {
    HStack {
      Label("\(pendingCount) pendentes", systemImage: "checklist")
      Spacer()
      Text("\(lateCount) atrasadas")
      Spacer()
      Text("\(remainingMinutes) min")
    }
    .font(.caption.bold())
    .foregroundStyle(foreground)
    .padding(BecoTokens.Spacing.sm)
    .background(
      (isDark ? Color.white.opacity(0.08) : BecoTokens.ColorToken.subtle),
      in: RoundedRectangle(cornerRadius: 14)
    )
  }
}

private struct ChecklistStatusFilters: View {
  @Binding var selectedFilter: ChecklistViewFilter
  let filterCounts: [ChecklistViewFilter: Int]

  var body: some View {
    BecoSegmentedFilter(
      options: ChecklistViewFilter.allCases.map { filter in
        (filter, filter.label, filterCounts[filter])
      },
      selected: $selectedFilter,
      compact: true
    )
  }
}

private struct ChecklistContent: View {
  let scheduledItems: [ActivityWithCompletion]
  let emptyMessage: String
  let now: Date
  let schedule: ChecklistSchedule
  let selectedArea: Area
  let muted: Color
  let onSelectActivity: ((Int64, Area) -> Void)?
  let onComplete: (Int64) -> Void

  var body: some View {
    if scheduledItems.isEmpty {
      ChecklistEmptyState(message: emptyMessage, muted: muted)
    } else {
      ChecklistActivityList(
        scheduledItems: scheduledItems,
        now: now,
        schedule: schedule,
        selectedArea: selectedArea,
        onSelectActivity: onSelectActivity,
        onComplete: onComplete
      )
    }
  }
}

private struct ChecklistEmptyState: View {
  let message: String
  let muted: Color

  var body: some View {
    VStack(spacing: BecoTokens.Spacing.xs) {
      Text("Nenhuma atividade").font(.headline)
      Text(message)
        .font(.subheadline)
        .foregroundStyle(muted)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, BecoTokens.Spacing.xxl)
  }
}

private struct ChecklistActivityList: View {
  let scheduledItems: [ActivityWithCompletion]
  let now: Date
  let schedule: ChecklistSchedule
  let selectedArea: Area
  let onSelectActivity: ((Int64, Area) -> Void)?
  let onComplete: (Int64) -> Void

  var body: some View {
    ForEach(Array(scheduledItems.enumerated()), id: \.element.id) { index, item in
      let timing = ActivityTiming.today(
        activity: item.activity,
        completion: item.completion,
        now: now,
        schedule: schedule
      )
      BecoTaskRow(
        title: item.activity.name,
        metadata: "\(item.activity.executionPhase.displayName) · \(item.activity.estimatedDurationMinutes) min · \(timing.label)",
        completed: item.completion != nil,
        timingStatus: timing.status,
        onSelect: { onSelectActivity?(item.activity.id, selectedArea) },
        onComplete: { onComplete(item.activity.id) }
      )
      if index < scheduledItems.count - 1 {
        Divider()
      }
    }
  }
}

private enum ChecklistViewFilter: String, CaseIterable, Hashable {
  case all, pending, completed

  var label: String {
    switch self {
    case .all: return "Todas"
    case .pending: return "Pendentes"
    case .completed: return "Concluídas"
    }
  }
}

private struct CameraCaptureRequest: Identifiable {
  let activityId: Int64
  var id: Int64 { activityId }
}

private struct ChecklistAlert: Identifiable {
  let title: String
  let message: String
  var id: String { "\(title)-\(message)" }
}
