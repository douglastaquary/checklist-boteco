import SwiftUI
import Models
import Auth
import ChecklistFeature
import WorkClockFeature
import InventoryFeature
import DashboardFeature
import AdminFeatures
import Env
import Persistence

/// Dependências compartilhadas entre tabs — passadas para `AppTab.makeContentView`.
struct MainTabContext {
  let repository: ChecklistRepository
  let session: AppSession
  let syncController: SyncController
  let inventoryClient: InventoryClient?
  let authToken: String?
  let remoteUserId: String?
  let deviceId: String
  let onLogout: () -> Void
}

extension AppTab {
  @ViewBuilder
  var label: some View {
    Label(title, systemImage: iconName)
  }

  private var iconName: String {
    switch self {
    case .checklist: return "checklist"
    case .workClock: return "clock"
    case .inventory: return "shippingbox"
    case .dashboard: return "chart.bar"
    case .activities: return "slider.horizontal.3"
    case .permissions: return "person.badge.key"
    }
  }

  @ViewBuilder
  func makeContentView(context: MainTabContext, user: User, tabRouter: TabRouter) -> some View {
    switch self {
    case .checklist:
      ChecklistRootView(
        user: user,
        repository: context.repository,
        syncController: context.syncController,
        onLogout: context.onLogout,
        onSelectActivity: { activityId, area in
          Task { @MainActor in
            tabRouter.push(
              .checklistActivityDetail(activityId: activityId, area: area),
              on: .checklist
            )
          }
        }
      )
    case .workClock:
      WorkClockRootView(
        userId: user.id,
        authToken: context.authToken,
        remoteUserId: context.remoteUserId ?? user.remoteId,
        repository: context.repository,
        syncController: context.syncController,
        deviceId: context.deviceId,
        onShowDayEntries: {
          Task { @MainActor in
            tabRouter.push(.workClockDayEntries(userId: user.id), on: .workClock)
          }
        }
      )
    case .inventory:
      InventoryRootView(
        repository: context.repository,
        inventoryClient: context.inventoryClient,
        token: context.authToken,
        canCreate: user.canCreateInventoryCounts(),
        canViewInsights: user.canViewInventoryInsights(),
        canManageAdministrativeStock: user.canManageAdministrativeStock()
      )
    case .dashboard:
      DashboardRootView(repository: context.repository) { area in
        Task { @MainActor in
          tabRouter.push(.dashboardAreaDetail(area: area), on: .dashboard)
        }
      }
    case .activities:
      ActivitiesManagementView(repository: context.repository)
    case .permissions:
      PermissionManagementView(repository: context.repository)
    }
  }
}
