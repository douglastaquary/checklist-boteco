#if DEBUG
import SwiftUI
import Models
import DesignSystem
import Env
import Network
import Persistence

struct MainTabView_Previews: PreviewProvider {
  static var previews: some View {
    Group {
      MainTabView(context: previewContext, user: adminUser)
        .previewDisplayName("Admin — todas as tabs")
      MainTabView(context: previewContext, user: limitedUser)
        .previewDisplayName("Colaborador — tabs reduzidas")
    }
    .environmentObject(AppTheme.shared)
  }

  private static var previewRepository: ChecklistRepository {
    let db = try! AppDatabase.inMemory()
    return ChecklistRepository(dbQueue: db)
  }

  private static var previewSyncController: SyncController {
    SyncController(engine: SyncEngine(repository: previewRepository, syncClient: nil, deviceId: "preview-device"))
  }

  private static var previewContext: MainTabContext {
    MainTabContext(
      repository: previewRepository,
      syncController: previewSyncController,
      inventoryClient: nil,
      authToken: nil,
      remoteUserId: nil,
      deviceId: "preview",
      onLogout: {}
    )
  }

  private static var adminUser: User {
    User(
      id: 1,
      name: "Preview Admin",
      email: "admin@test.com",
      password: "x",
      area: .atendimento,
      permissionLevel: .admin,
      allowedAreas: Area.allCases,
      createdAt: 0,
      featurePermissions: FeaturePermissions(
        canRegisterUsers: true,
        canCreateActivities: true,
        canEditUsers: true,
        canCreateInventoryCounts: true,
        canViewInventoryInsights: true,
        canManageAdministrativeStock: true
      )
    )
  }

  private static var limitedUser: User {
    User(
      id: 2,
      name: "Colaborador",
      email: "user@test.com",
      password: "x",
      area: .atendimento,
      permissionLevel: .user,
      allowedAreas: [.atendimento],
      createdAt: 0
    )
  }
}
#endif
