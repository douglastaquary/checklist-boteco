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
    SyncController(engine: SyncEngine(repository: previewRepository, syncClient: nil))
  }
}
#endif
