#if os(iOS) && DEBUG
import SwiftUI
import Models
import Persistence
import Env
import DesignSystem

struct ChecklistRootView_Previews: PreviewProvider {
  static var previews: some View {
    NavigationStack {
      ChecklistRootView(
        user: previewUser,
        repository: previewRepository,
        syncController: previewSyncController,
        onLogout: {}
      )
    }
    .environmentObject(AppTheme.shared)
  }

  private static var previewRepository: ChecklistRepository {
    let db = try! AppDatabase.inMemory()
    return ChecklistRepository(dbQueue: db)
  }

  private static var previewSyncController: SyncController {
    SyncController(engine: SyncEngine(repository: previewRepository, syncClient: nil))
  }

  private static var previewUser: User {
    User(
      id: 1,
      name: "Preview",
      email: "preview@test.com",
      password: "x",
      area: .atendimento,
      permissionLevel: .user,
      allowedAreas: Area.allCases,
      createdAt: 0
    )
  }
}
#endif
