import SwiftUI
import Models
import Persistence

public struct WorkClockDayEntriesView: View {
  private let userId: Int64
  private let repository: ChecklistRepository

  @State private var entries: [WorkClockEntry] = []

  public init(userId: Int64, repository: ChecklistRepository) {
    self.userId = userId
    self.repository = repository
  }

  public var body: some View {
    List(entries) { entry in
      VStack(alignment: .leading, spacing: 4) {
        Text(entry.type.displayName).font(.headline)
        Text(formattedTime(entry.registeredAt))
          .font(.caption)
          .foregroundStyle(.secondary)
        if entry.distanceFromWorkMeters > 0 {
          Text(String(format: "Distância: %.1f m", entry.distanceFromWorkMeters))
            .font(.caption2)
            .foregroundStyle(.secondary)
        }
      }
    }
    .navigationTitle("Marcações do dia")
    .task { await reload() }
  }

  @MainActor
  private func reload() async {
    let start = Date.startOfDayMillis
    let end = start + 24 * 60 * 60 * 1000
    entries = (try? repository.workClockEntries(userId: userId, dayStart: start, dayEnd: end)) ?? []
  }

  private func formattedTime(_ millis: Int64) -> String {
    let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
    return date.formatted(date: .omitted, time: .shortened)
  }
}

private extension Date {
  static var startOfDayMillis: Int64 {
    Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
  }
}
