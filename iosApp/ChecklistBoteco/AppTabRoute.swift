import SwiftUI
import Models
import Persistence
import WorkClockFeature
import DashboardFeature
import ChecklistFeature
import InventoryFeature
import DesignSystem

/// Destinos de navegação programática por tab (iOS 16 — `Hashable` + `NavigationPath`).
enum AppTabRoute: Hashable {
  case workClockDayEntries(userId: Int64)
  case dashboardAreaDetail(area: Area)
  case checklistActivityDetail(activityId: Int64, area: Area)
  case inventoryAuditDetail(InventoryAuditItemSnapshot)
  case overflowModule(AppTab)

  @ViewBuilder
  func destination(context: MainTabContext, user: User, tabRouter: TabRouter, hostTab: AppTab) -> some View {
    switch self {
    case .workClockDayEntries(let userId):
      WorkClockDayEntriesView(userId: userId, repository: context.repository)
        .becoBackButton()
    case .dashboardAreaDetail(let area):
      DashboardAreaDetailView(area: area, repository: context.repository)
    case .checklistActivityDetail(let activityId, let area):
      ChecklistActivityDetailView(
        activityId: activityId,
        area: area,
        repository: context.repository
      )
    case .inventoryAuditDetail(let snapshot):
      InventoryAuditDetailView(snapshot: snapshot)
        .becoBackButton()
    case .overflowModule(let tab):
      if tab == .checklist {
        // Checklist owns back + chrome animation; skip nav title from MoreModuleHost.
        tab.makeContentView(
          context: context,
          user: user,
          tabRouter: tabRouter,
          hostTab: hostTab,
          embeddedInNavigationStack: true
        )
        .navigationBarBackButtonHidden(true)
      } else {
        MoreModuleHost(title: tab.title) {
          tab.makeContentView(
            context: context,
            user: user,
            tabRouter: tabRouter,
            hostTab: hostTab,
            embeddedInNavigationStack: true
          )
        }
      }
    }
  }
}

/// Host for overflow modules: always exposes a Codex-style back control.
private struct MoreModuleHost<Content: View>: View {
  let title: String
  @ViewBuilder let content: () -> Content

  var body: some View {
    content()
      .navigationTitle(title)
      .navigationBarTitleDisplayMode(.inline)
      .becoBackButton()
  }
}
