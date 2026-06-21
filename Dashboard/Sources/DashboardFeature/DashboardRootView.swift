import SwiftUI
import Models
import Persistence
public struct DashboardRootView: View {
  private let repository: ChecklistRepository
  @State private var activities: [Activity] = []

  public init(repository: ChecklistRepository) {
    self.repository = repository
  }

  public var body: some View {
    NavigationStack {
      List {
        Section("Atividades cadastradas") {
          Text("\(activities.count) atividades ativas")
        }
        ForEach(Area.allCases, id: \.self) { area in
          let count = activities.filter { $0.area == area }.count
          LabeledContent(area.displayName, value: "\(count)")
        }
      }
      .navigationTitle("Dashboard")
      .task {
        activities = (try? repository.allActivities()) ?? []
      }
    }
  }
}
