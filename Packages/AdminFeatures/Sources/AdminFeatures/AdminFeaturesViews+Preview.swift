#if os(iOS) && DEBUG
import SwiftUI
import Persistence
import DesignSystem

struct ActivitiesManagementView_Previews: PreviewProvider {
  static var previews: some View {
    Group {
      NavigationStack {
        ActivitiesManagementView(repository: emptyRepository)
      }
      .previewDisplayName("Lista vazia")

      NavigationStack {
        ActivitiesManagementView(repository: seededRepository)
      }
      .previewDisplayName("Com atividades")
    }
    .environmentObject(AppTheme.shared)
  }

  private static var emptyRepository: ChecklistRepository {
    ChecklistRepository(dbQueue: try! AppDatabase.inMemory())
  }

  private static var seededRepository: ChecklistRepository {
    let repo = ChecklistRepository(dbQueue: try! AppDatabase.inMemory())
    try! repo.seedInitialDataIfNeeded()
    return repo
  }
}

struct PermissionManagementView_Previews: PreviewProvider {
  static var previews: some View {
    NavigationStack {
      PermissionManagementView(repository: seededRepository)
    }
    .environmentObject(AppTheme.shared)
  }

  private static var seededRepository: ChecklistRepository {
    let repo = ChecklistRepository(dbQueue: try! AppDatabase.inMemory())
    try! repo.seedInitialDataIfNeeded()
    return repo
  }
}
#endif
