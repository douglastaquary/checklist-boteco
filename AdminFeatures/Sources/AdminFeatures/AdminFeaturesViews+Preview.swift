#if os(iOS) && DEBUG
import SwiftUI
import Persistence

struct ActivitiesManagementView_Previews: PreviewProvider {
  static var previews: some View {
    NavigationStack {
      ActivitiesManagementView(repository: previewRepository)
    }
  }

  private static var previewRepository: ChecklistRepository {
    let db = try! AppDatabase.inMemory()
    return ChecklistRepository(dbQueue: db)
  }
}
#endif
