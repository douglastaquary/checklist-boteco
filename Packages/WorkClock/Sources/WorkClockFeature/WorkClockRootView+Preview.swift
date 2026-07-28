#if os(iOS) && DEBUG
import SwiftUI
import Models
import Persistence
import Env
import DesignSystem

struct WorkClockRootView_Previews: PreviewProvider {
  static var previews: some View {
    NavigationStack {
      WorkClockRootView(
        user: previewUser,
        userId: 1,
        authToken: nil,
        remoteUserId: nil,
        repository: previewRepository,
        syncController: previewSyncController,
        deviceId: "preview"
      )
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

  private static var previewUser: User {
    User(
      id: 1,
      name: "Daniel Acevedo",
      email: "daniel@beco.local",
      password: "x",
      area: .atendimento,
      workSector: .garcom,
      permissionLevel: .user,
      allowedAreas: [.atendimento]
    )
  }
}
#endif
