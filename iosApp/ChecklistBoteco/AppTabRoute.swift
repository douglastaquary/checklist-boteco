import SwiftUI
import Models
import Persistence
import WorkClockFeature
import DashboardFeature
import ChecklistFeature

/// Destinos de navegação programática por tab (iOS 16 — `Hashable` + `NavigationPath`).
enum AppTabRoute: Hashable {
  case workClockDayEntries(userId: Int64)
  case dashboardAreaDetail(area: Area)
  case checklistActivityDetail(activityId: Int64, area: Area)

  @ViewBuilder
  func destination(context: MainTabContext) -> some View {
    switch self {
    case .workClockDayEntries(let userId):
      WorkClockDayEntriesView(userId: userId, repository: context.repository)
    case .dashboardAreaDetail(let area):
      DashboardAreaDetailView(area: area, repository: context.repository)
    case .checklistActivityDetail(let activityId, let area):
      ChecklistActivityDetailView(
        activityId: activityId,
        area: area,
        repository: context.repository
      )
    }
  }
}
