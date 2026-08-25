import SwiftUI
import Models
import Network
import Auth
import ChecklistFeature
import WorkClockFeature
import InventoryFeature
import PurchasesFeature
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
  let purchaseClient: PurchaseClient?
  let workClockClient: WorkClockClient?
  let userClient: UserClient?
  let dashboardClient: DashboardClient?
  let aiChatClient: AIChatClient?
  let authToken: String?
  let remoteSessionGeneration: Int
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
    case .purchases: return "cart"
    case .dashboard: return "chart.bar"
    case .activities: return "slider.horizontal.3"
    case .permissions: return "person.badge.key"
    case .aiChat: return "sparkles"
    case .more: return "ellipsis"
    }
  }

  @ViewBuilder
  func makeContentView(
    context: MainTabContext,
    user: User,
    tabRouter: TabRouter,
    hostTab: AppTab,
    embeddedInNavigationStack: Bool = false
  ) -> some View {
    switch self {
    case .checklist:
      SyncRefreshingContainer(syncController: context.syncController) {
        ChecklistRootView(
          user: user,
          repository: context.repository,
          syncController: context.syncController,
          onLogout: context.onLogout,
          embeddedInNavigationStack: embeddedInNavigationStack,
          onSelectActivity: { activityId, area in
            Task { @MainActor in
              tabRouter.push(
                .checklistActivityDetail(activityId: activityId, area: area),
                on: hostTab
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
        embeddedInNavigationStack: embeddedInNavigationStack,
        onShowDayEntries: {
          Task { @MainActor in
            tabRouter.push(.workClockDayEntries(userId: user.id), on: hostTab)
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
        userClient: context.userClient,
        token: context.authToken,
        canCreate: user.canCreateInventoryCounts(),
        canViewInsights: user.canViewInventoryInsights(),
        canManageAdministrativeStock: user.canManageAdministrativeStock(),
        onSelectAuditItem: { snapshot in
          Task { @MainActor in
            tabRouter.push(.inventoryAuditDetail(snapshot), on: hostTab)
          }
        }
      )
    case .purchases:
      PurchasesRootView(purchaseClient: context.purchaseClient, token: context.authToken)
    case .dashboard:
      SyncRefreshingContainer(syncController: context.syncController) {
        DashboardRootView(
          repository: context.repository,
          dashboardClient: context.dashboardClient,
          authToken: context.authToken,
          refreshID: context.remoteSessionGeneration,
          onLogout: context.onLogout
        ) { area in
          Task { @MainActor in
            tabRouter.push(.dashboardAreaDetail(area: area), on: hostTab)
          }
        }
      }
    case .activities:
      SyncRefreshingContainer(syncController: context.syncController) {
        ActivitiesManagementView(
          repository: context.repository,
          embeddedInParentNavigationStack: true
        )
      }
    case .permissions:
      PermissionManagementView(
        repository: context.repository,
        userClient: context.userClient,
        authToken: context.authToken,
        embeddedInParentNavigationStack: true
      )
    case .aiChat:
      AIChatView(
        client: context.aiChatClient,
        token: context.authToken,
        repository: context.repository,
        userClient: context.userClient
      )
    case .more:
      EmptyView()
    }
  }
}

struct MoreHubView: View {
  let modules: [AppTab]
  let onOpen: (AppTab) -> Void

  @Environment(\.colorScheme) private var colorScheme

  private var isDark: Bool { colorScheme == .dark }
  private var foreground: Color { isDark ? .white : .black }
  private var muted: Color { isDark ? .white.opacity(0.62) : .secondary }
  private var card: Color { isDark ? Color.white.opacity(0.08) : Color(.secondarySystemGroupedBackground) }

  var body: some View {
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
          Text("Mais")
            .font(.largeTitle.bold())
            .foregroundStyle(foreground)
            .padding(.top, 8)

          Text("Módulos")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(muted)

          VStack(spacing: 0) {
            ForEach(Array(modules.enumerated()), id: \.element.id) { index, tab in
              Button {
                onOpen(tab)
              } label: {
                HStack(spacing: 14) {
                  Image(systemName: tab.iconName)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(foreground)
                    .frame(width: 28)
                  Text(tab.title)
                    .font(.body)
                    .foregroundStyle(foreground)
                  Spacer()
                  Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(muted)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .contentShape(Rectangle())
              }
              .buttonStyle(.plain)

              if index < modules.count - 1 {
                Divider()
                  .padding(.leading, 58)
                  .opacity(isDark ? 0.35 : 1)
              }
            }
          }
          .background(card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 32)
      }
    }
    .navigationTitle("")
    .navigationBarTitleDisplayMode(.inline)
    .toolbar(.hidden, for: .navigationBar)
  }
}
