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
    .task { activities = (try? repository.allActivities().filter { $0.area == area }) ?? [] }
  }
}

public struct DashboardRootView: View {
  private let repository: ChecklistRepository
  private let dashboardClient: DashboardClient?
  private let authToken: String?
  private let onSelectArea: ((Area) -> Void)?

  @State private var activities: [Activity] = []
  @State private var remoteStats: DashboardStats?

  public init(
    repository: ChecklistRepository,
    dashboardClient: DashboardClient? = nil,
    authToken: String? = nil,
    onSelectArea: ((Area) -> Void)? = nil
  ) {
    self.repository = repository
    self.dashboardClient = dashboardClient
    self.authToken = authToken
    self.onSelectArea = onSelectArea
  }

  public var body: some View {
    List {
      Section {
        BecoPageHeader(title: "Visão Geral", subtitle: "Indicadores operacionais")
          .listRowInsets(EdgeInsets())
          .listRowSeparator(.hidden)
          .themedListRowBackground()
      }
      if let remoteStats {
        Section("Servidor") {
          Text("Usuários: \(remoteStats.totalUsers)")
            .themedListRowBackground()
          Text("Conclusões: \(remoteStats.totalCompletions)")
            .themedListRowBackground()
          Text("Pendências de sync: \(remoteStats.pendingSyncItems)")
            .themedListRowBackground()
        }
      }
      Section("Atividades cadastradas") {
        Text("\(displayedActivityCount) atividades ativas")
          .themedListRowBackground()
      }
      Section("Por área") {
        ForEach(Area.allCases, id: \.self) { area in
          let count = areaCount(for: area)
          Button {
            onSelectArea?(area)
          } label: {
            HStack {
              Text(area.displayName)
              Spacer()
              Text("\(count)")
                .foregroundStyle(.secondary)
            }
          }
          .buttonStyle(.plain)
          .themedListRowBackground()
          .disabled(count == 0 || onSelectArea == nil)
        }
      }
    }
    .themedListStyle()
    .navigationTitle("")
    .navigationBarTitleDisplayMode(.inline)
    .task { await reload() }
  }

  private var displayedActivityCount: Int {
    remoteStats?.totalActivities ?? activities.count
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
    remoteStats = try? await dashboardClient.fetchStats(token: authToken)
  }
}
