#if os(iOS) && DEBUG
import SwiftUI
import Persistence
import DesignSystem

struct ActivitiesManagementView_Previews: PreviewProvider {
  static var previews: some View {
    NavigationStack {
      ActivitiesManagementView(repository: previewRepository)
    }
    .environmentObject(AppTheme.shared)
  }

  private static var previewRepository: ChecklistRepository {
    let db = try! AppDatabase.inMemory()
    return ChecklistRepository(dbQueue: db)
  }
}

struct PermissionManagementView_Previews: PreviewProvider {
  static var previews: some View {
    NavigationStack {
      PermissionManagementView(repository: previewRepository)
    }
    .environmentObject(AppTheme.shared)
  }

  private static var previewRepository: ChecklistRepository {
    let db = try! AppDatabase.inMemory()
    return ChecklistRepository(dbQueue: db)
  }
}
#endif
