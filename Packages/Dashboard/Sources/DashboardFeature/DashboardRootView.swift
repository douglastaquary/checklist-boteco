import SwiftUI
import Models
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
  private let onSelectArea: ((Area) -> Void)?

  @State private var activities: [Activity] = []

  public init(repository: ChecklistRepository, onSelectArea: ((Area) -> Void)? = nil) {
    self.repository = repository
    self.onSelectArea = onSelectArea
  }

  public var body: some View {
    List {
      Section("Atividades cadastradas") {
        Text("\(activities.count) atividades ativas")
          .themedListRowBackground()
      }
      Section("Por área") {
        ForEach(Area.allCases, id: \.self) { area in
          let count = activities.filter { $0.area == area }.count
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
    .navigationTitle("Dashboard")
    .task {
      activities = (try? repository.allActivities()) ?? []
    }
  }
}
