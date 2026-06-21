#if os(iOS) && DEBUG
import SwiftUI
import Models
import Persistence
import Env
import DesignSystem

struct ChecklistRootView_Previews: PreviewProvider {
  static var previews: some View {
    Group {
      NavigationStack {
        ChecklistRootView(
          user: singleAreaUser,
          repository: previewRepository,
          syncController: previewSyncController,
          onLogout: {}
        )
      }
      .previewDisplayName("Colaborador — 1 área")

      NavigationStack {
        ChecklistRootView(
          user: multiAreaUser,
          repository: previewRepository,
          syncController: previewSyncController,
          onLogout: {}
        )
      }
      .previewDisplayName("Admin — menu de áreas")
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

  private static var singleAreaUser: User {
    User(
      id: 1,
      name: "Colaborador",
      email: "colab@test.com",
      password: "x",
      area: .atendimento,
      permissionLevel: .user,
      allowedAreas: [.atendimento],
      createdAt: 0
    )
  }

  private static var multiAreaUser: User {
    User(
      id: 2,
      name: "Admin",
      email: "admin@test.com",
      password: "x",
      area: .atendimento,
      permissionLevel: .admin,
      allowedAreas: Area.allCases,
      createdAt: 0,
      featurePermissions: .admin
    )
  }
}
#endif
