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
          user: atendimentoUser,
          repository: previewRepository,
          syncController: previewSyncController,
          onLogout: {}
        )
      }
      .previewDisplayName("Garçom — Atendimento")

      NavigationStack {
        ChecklistRootView(
          user: cozinhaUser,
          repository: previewRepository,
          syncController: previewSyncController,
          onLogout: {}
        )
      }
      .previewDisplayName("Ajudante cozinha")

      NavigationStack {
        ChecklistRootView(
          user: adminUser,
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
    let repo = ChecklistRepository(dbQueue: db)
    try! repo.seedInitialDataIfNeeded()
    return repo
  }

  private static var previewSyncController: SyncController {
    SyncController(engine: SyncEngine(repository: previewRepository, syncClient: nil, deviceId: "preview-device"))
  }

  private static var atendimentoUser: User {
    User(
      id: 1,
      name: "Garçom",
      email: "garcom@test.com",
      password: "x",
      area: .atendimento,
      workSector: .garcom,
      permissionLevel: .user,
      allowedAreas: [.atendimento],
      createdAt: 0
    )
  }

  private static var cozinhaUser: User {
    User(
      id: 2,
      name: "Ajudante",
      email: "cozinha@test.com",
      password: "x",
      area: .cozinha,
      workSector: .ajudanteCozinha,
      permissionLevel: .user,
      allowedAreas: [.cozinha],
      createdAt: 0
    )
  }

  private static var adminUser: User {
    User(
      id: 3,
      name: "Admin",
      email: "admin@test.com",
      password: "x",
      area: .atendimento,
      workSector: .gerente,
      permissionLevel: .admin,
      allowedAreas: Area.allCases,
      createdAt: 0,
      featurePermissions: .admin
    )
  }
}
#endif
