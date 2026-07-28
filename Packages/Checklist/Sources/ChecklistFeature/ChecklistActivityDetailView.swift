import SwiftUI
import Models
import Persistence
import DesignSystem

public struct ChecklistActivityDetailView: View {
  private let activityId: Int64
  private let area: Area
  private let repository: ChecklistRepository

  @State private var item: ActivityWithCompletion?

  public init(activityId: Int64, area: Area, repository: ChecklistRepository) {
    self.activityId = activityId
    self.area = area
    self.repository = repository
  }

  public var body: some View {
    Group {
      if let item {
        List {
          Section("Atividade") {
            LabeledContent("Nome", value: item.activity.name)
              .themedListRowBackground()
            LabeledContent("Frequência", value: item.activity.frequency.displayName)
              .themedListRowBackground()
            LabeledContent("Área", value: area.displayName)
              .themedListRowBackground()
          }
          Section("Status") {
            if item.completion != nil {
              LabeledContent("Concluída", value: "Sim")
                .themedListRowBackground()
              if item.completion?.imagePath != nil {
                LabeledContent("Foto", value: "Anexada")
                  .themedListRowBackground()
              }
            } else {
              Text("Pendente")
                .foregroundStyle(.orange)
                .themedListRowBackground()
            }
          }
        }
        .themedListStyle()
      } else {
        ProgressView()
      }
    }
    .navigationTitle("Detalhe")
    .becoBackButton()
    .task { await load() }
  }

  @MainActor
  private func load() async {
    item = try? repository.activitiesByArea(area).first { $0.activity.id == activityId }
  }
}
