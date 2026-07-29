import SwiftUI
import Models
import Network
import Persistence
import DesignSystem

public struct DashboardAreaDetailView: View {
  private let area: Area
  private let repository: ChecklistRepository

  @State private var activities: [Activity] = []

  public init(area: Area, repository: ChecklistRepository) {
    self.area = area
    self.repository = repository
  }

  public var body: some View {
    List(activities) { activity in
      VStack(alignment: .leading, spacing: 4) {
        Text(activity.name).font(.headline)
        Text(activity.frequency.displayName)
          .font(.caption)
          .foregroundStyle(.secondary)
      }
      .themedListRowBackground()
    }
    .themedListStyle()
    .navigationTitle(area.displayName)
    .becoBackButton()
    .task { activities = (try? repository.allActivities().filter { $0.area == area }) ?? [] }
  }
}

public struct DashboardRootView: View {
  private let repository: ChecklistRepository
  private let dashboardClient: DashboardClient?
  private let authToken: String?
  private let onSelectArea: ((Area) -> Void)?
  private let onLogout: () -> Void

  @Environment(\.colorScheme) private var colorScheme
  @State private var activities: [Activity] = []
  @State private var remoteStats: DashboardStats?
  @State private var salesHeatmap: SalesHeatmapResponse?

  public init(
    repository: ChecklistRepository,
    dashboardClient: DashboardClient? = nil,
    authToken: String? = nil,
    onLogout: @escaping () -> Void = {},
    onSelectArea: ((Area) -> Void)? = nil
  ) {
    self.repository = repository
    self.dashboardClient = dashboardClient
    self.authToken = authToken
    self.onLogout = onLogout
    self.onSelectArea = onSelectArea
  }

  private var isDark: Bool { colorScheme == .dark }
  private var foreground: Color { isDark ? .white : .black }
  private var muted: Color { isDark ? .white.opacity(0.62) : .secondary }
  private var card: Color { isDark ? Color.white.opacity(0.08) : Color(.secondarySystemGroupedBackground) }

  private var heatmapYear: Int {
    salesHeatmap?.year ?? Calendar.current.component(.year, from: Date())
  }

  private var heatmapQuantities: [Date: Double] {
    Self.dayMetrics(from: salesHeatmap?.days ?? []).quantities
  }

  private var heatmapRevenue: [Date: Int64] {
    Self.dayMetrics(from: salesHeatmap?.days ?? []).revenue
  }

  private var displayedActivityCount: Int {
    remoteStats?.totalActivities ?? activities.count
  }

  private var topArea: (Area, Int)? {
    Area.allCases
      .map { ($0, areaCount(for: $0)) }
      .filter { $0.1 > 0 }
      .max { $0.1 < $1.1 }
  }

  public var body: some View {
    ZStack {
      LinearGradient(
        colors: isDark
          ? [Color.black, Color(red: 0.03, green: 0.03, blue: 0.04), Color.black]
          : [Color(.systemBackground), Color(.systemGroupedBackground)],
        startPoint: .top,
        endPoint: .bottom
      )
      .ignoresSafeArea()

      ScrollView {
        VStack(alignment: .leading, spacing: 20) {
          HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
              Text("Dashboard")
                .font(.largeTitle.bold())
                .foregroundStyle(foreground)
              Text("Indicadores operacionais do Beco")
                .font(.subheadline)
                .foregroundStyle(muted)
            }
            Spacer(minLength: 8)
            Menu {
              Button(role: .destructive, action: onLogout) {
                Label("Sair", systemImage: "rectangle.portrait.and.arrow.right")
              }
            } label: {
              Image(systemName: "ellipsis")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(foreground)
                .frame(width: 44, height: 44)
                .background(
                  Circle()
                    .fill(.ultraThinMaterial)
                    .overlay(
                      Circle().stroke(
                        isDark ? Color.white.opacity(0.14) : Color.black.opacity(0.08),
                        lineWidth: 1
                      )
                    )
                )
            }
            .accessibilityLabel("Mais opções")
          }
          .padding(.top, 8)

          DashboardStatsGrid(
            users: remoteStats?.totalUsers ?? 0,
            completions: remoteStats?.totalCompletions ?? 0,
            pendingSync: remoteStats?.pendingSyncItems ?? 0,
            activities: displayedActivityCount,
            foreground: foreground,
            muted: muted,
            card: card
          )

          if let topArea {
            DashboardMetricCard(
              value: "\(topArea.1)",
              label: "Mais atividades · \(topArea.0.displayName)",
              foreground: foreground,
              muted: muted,
              card: card
            )
          }

          BecoSalesHeatmap(
            title: "Vendas \(heatmapYear)",
            quantitiesByDay: heatmapQuantities,
            revenueCentsByDay: heatmapRevenue,
            year: heatmapYear,
            isDark: isDark
          )

          DashboardAreasSection(
            areas: Area.allCases,
            countForArea: areaCount(for:),
            foreground: foreground,
            muted: muted,
            card: card,
            onSelectArea: onSelectArea
          )
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 32)
      }
    }
    .navigationTitle("")
    .navigationBarTitleDisplayMode(.inline)
    .toolbar(.hidden, for: .navigationBar)
    .task { await reload() }
  }

  private func areaCount(for area: Area) -> Int {
    if let remoteStats,
       let remoteCount = remoteStats.activitiesByArea[area.rawValue] {
      return remoteCount
    }
    return activities.filter { $0.area == area }.count
  }

  private func reload() async {
    activities = (try? repository.allActivities()) ?? []
    guard let dashboardClient, let authToken, !authToken.isEmpty else { return }
    async let stats = dashboardClient.fetchStats(token: authToken)
    async let heatmap = dashboardClient.fetchSalesHeatmap(
      year: Calendar.current.component(.year, from: Date()),
      token: authToken
    )
    remoteStats = try? await stats
    salesHeatmap = try? await heatmap
  }

  static func dayMetrics(from days: [SalesHeatmapDay]) -> (quantities: [Date: Double], revenue: [Date: Int64]) {
    var calendar = Calendar.current
    calendar.timeZone = TimeZone.current
    let formatter = DateFormatter()
    formatter.calendar = calendar
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone.current
    formatter.dateFormat = "yyyy-MM-dd"

    var quantities: [Date: Double] = [:]
    var revenue: [Date: Int64] = [:]
    for day in days {
      guard let date = formatter.date(from: day.date) else { continue }
      let key = calendar.startOfDay(for: date)
      quantities[key] = day.quantity
      revenue[key] = day.totalInCents
    }
    return (quantities, revenue)
  }
}

// MARK: - Subviews (MV)

private struct DashboardStatsGrid: View {
  let users: Int
  let completions: Int
  let pendingSync: Int
  let activities: Int
  let foreground: Color
  let muted: Color
  let card: Color

  private let columns = [
    GridItem(.flexible(), spacing: 12),
    GridItem(.flexible(), spacing: 12)
  ]

  var body: some View {
    LazyVGrid(columns: columns, spacing: 12) {
      DashboardMetricCard(value: "\(users)", label: "Usuários", foreground: foreground, muted: muted, card: card)
      DashboardMetricCard(value: "\(completions)", label: "Conclusões", foreground: foreground, muted: muted, card: card)
      DashboardMetricCard(value: "\(pendingSync)", label: "Sync pendente", foreground: foreground, muted: muted, card: card)
      DashboardMetricCard(value: "\(activities)", label: "Atividades", foreground: foreground, muted: muted, card: card)
    }
  }
}

private struct DashboardMetricCard: View {
  let value: String
  let label: String
  let foreground: Color
  let muted: Color
  let card: Color

  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      Text(value)
        .font(.title.bold())
        .foregroundStyle(foreground)
        .lineLimit(1)
        .minimumScaleFactor(0.7)
      Text(label)
        .font(.caption)
        .foregroundStyle(muted)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(16)
    .background(card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
  }
}

private struct DashboardAreasSection: View {
  let areas: [Area]
  let countForArea: (Area) -> Int
  let foreground: Color
  let muted: Color
  let card: Color
  let onSelectArea: ((Area) -> Void)?

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      Text("Por área")
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(muted)

      VStack(spacing: 0) {
        ForEach(Array(areas.enumerated()), id: \.element) { index, area in
          let count = countForArea(area)
          Button {
            onSelectArea?(area)
          } label: {
            HStack {
              Text(area.displayName)
                .foregroundStyle(foreground)
              Spacer()
              Text("\(count)")
                .foregroundStyle(muted)
              Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(muted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
          }
          .buttonStyle(.plain)
          .disabled(count == 0 || onSelectArea == nil)

          if index < areas.count - 1 {
            Divider().padding(.leading, 16)
          }
        }
      }
      .background(card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
  }
}
