import SwiftUI
import Models
import Network
import Auth
import ChecklistFeature
import WorkClockFeature
import InventoryFeature
import DashboardFeature
import AdminFeatures
import Env
import Persistence
import AIChatFeature

/// Dependências compartilhadas entre tabs — passadas para `AppTab.makeContentView`.
struct MainTabContext {
  let repository: ChecklistRepository
  let syncController: SyncController
  let inventoryClient: InventoryClient?
  let workClockClient: WorkClockClient?
  let userClient: UserClient?
  let dashboardClient: DashboardClient?
  let aiChatClient: AIChatClient?
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

  var iconName: String {
    switch self {
    case .checklist: return "checklist"
    case .workClock: return "clock"
    case .inventory: return "shippingbox"
    case .dashboard: return "chart.bar"
    case .activities: return "slider.horizontal.3"
    case .permissions: return "person.badge.key"
    case .aiChat: return "sparkles"
    }
  }

  @ViewBuilder
  func makeContentView(context: MainTabContext, user: User, tabRouter: TabRouter) -> some View {
    switch self {
    case .checklist:
      SyncRefreshingContainer(syncController: context.syncController) {
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
      }
    case .workClock:
      WorkClockRootView(
        user: user,
        userId: user.id,
        authToken: context.authToken,
        remoteUserId: context.remoteUserId ?? user.remoteId,
        repository: context.repository,
        workClockClient: context.workClockClient,
        syncController: context.syncController,
        deviceId: context.deviceId,
        onShowDayEntries: {
          Task { @MainActor in
            tabRouter.push(.workClockDayEntries(userId: user.id), on: .workClock)
          }
        },
        onLogout: context.onLogout
      )
    case .inventory:
      InventoryRootView(
        user: user,
        onLogout: context.onLogout,
        repository: context.repository,
        inventoryClient: context.inventoryClient,
        token: context.authToken,
        canCreate: user.canCreateInventoryCounts(),
        canViewInsights: user.canViewInventoryInsights(),
        canManageAdministrativeStock: user.canManageAdministrativeStock(),
        onSelectAuditItem: { snapshot in
          Task { @MainActor in
            tabRouter.push(.inventoryAuditDetail(snapshot), on: .inventory)
          }
        }
      )
    case .dashboard:
      SyncRefreshingContainer(syncController: context.syncController) {
        DashboardRootView(
          repository: context.repository,
          dashboardClient: context.dashboardClient,
          authToken: context.authToken
        ) { area in
          Task { @MainActor in
            tabRouter.push(.dashboardAreaDetail(area: area), on: .dashboard)
          }
        }
      }
    case .activities:
      SyncRefreshingContainer(syncController: context.syncController) {
        ActivitiesManagementView(repository: context.repository)
      }
    case .permissions:
      PermissionManagementView(
        repository: context.repository,
        userClient: context.userClient,
        authToken: context.authToken
      )
    case .aiChat:
      AIChatView(client: context.aiChatClient, token: context.authToken)
    }
  }
}
