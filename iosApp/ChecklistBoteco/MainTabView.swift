import SwiftUI
import Models
import Auth
import ChecklistFeature
import WorkClockFeature
import InventoryFeature
import DashboardFeature
import AdminFeatures
import DesignSystem
import Env
import Persistence
struct MainTabView: View {
  let repository: ChecklistRepository
  @ObservedObject var session: AppSession
  @ObservedObject var syncController: SyncController
  let inventoryClient: InventoryClient?
  let authToken: String?
  let deviceId: String
  let onLogout: () -> Void

  @State private var selectedTab: AppTab = .checklist

  var body: some View {
    let user = session.currentUser!
    let tabs = AppTab.available(for: user)

    TabView(selection: $selectedTab) {
      ForEach(tabs) { tab in
        tabContent(tab, user: user)
          .tabItem { Label(tab.title, systemImage: icon(for: tab)) }
          .tag(tab)
      }
    }
    .onAppear {
      if !tabs.contains(selectedTab) { selectedTab = tabs.first ?? .checklist }
    }
  }

  @ViewBuilder
  private func tabContent(_ tab: AppTab, user: User) -> some View {
    switch tab {
    case .checklist:
      ChecklistRootView(
        repository: repository,
        syncController: syncController,
        onLogout: onLogout
      )
      .environmentObject(session)
    case .workClock:
      WorkClockRootView(
        repository: repository,
        syncController: syncController,
        deviceId: deviceId
      )
      .environmentObject(session)
    case .inventory:
      InventoryRootView(
        repository: repository,
        inventoryClient: inventoryClient,
        token: authToken,
        canCreate: user.canCreateInventoryCounts(),
        canViewInsights: user.canViewInventoryInsights(),
        canManageAdministrativeStock: user.canManageAdministrativeStock()
      )
    case .dashboard:
      DashboardRootView(repository: repository)
    case .activities:
      ActivitiesManagementView(repository: repository)
    case .permissions:
      PermissionManagementView(repository: repository)
    }
  }

  private func icon(for tab: AppTab) -> String {
    switch tab {
    case .checklist: return "checklist"
    case .workClock: return "clock"
    case .inventory: return "shippingbox"
    case .dashboard: return "chart.bar"
    case .activities: return "slider.horizontal.3"
    case .permissions: return "person.badge.key"
    }
  }
}
