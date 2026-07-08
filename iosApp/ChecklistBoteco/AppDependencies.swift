import Foundation
import Models
import Network
import Persistence
import Env
import InventoryFeature

@MainActor
public final class AppDependencies {
  public let repository: ChecklistRepository
  public let session: AppSession
  public let syncController: SyncController
  public let apiClient: APIClient?
  public let authClient: AuthClient?
  public let userClient: UserClient?
  public let syncClient: SyncClient?
  public let workClockClient: WorkClockClient?
  public let dashboardClient: DashboardClient?
  public let inventoryClient: InventoryClient?
  public let aiChatClient: AIChatClient?
  public let deviceId: String

  public init() throws {
    let db = try AppDatabase.open()
    let repository = ChecklistRepository(dbQueue: db)
    let apiConfiguration = APIConfiguration.fromBundle()
    if apiConfiguration == nil {
      try repository.seedInitialDataIfNeeded()
    } else {
      try repository.purgeLocalSeedArtifactsIfNeeded()
    }

    let apiClient = apiConfiguration.map { APIClient(config: $0) }
    let authClient = apiClient.map(AuthClient.init)
    let userClient = apiClient.map(UserClient.init)
    let syncClient = apiClient.map(SyncClient.init)
    let workClockClient = apiClient.map(WorkClockClient.init)
    let dashboardClient = apiClient.map(DashboardClient.init)
    let inventoryClient = apiClient.map(InventoryClient.init)
    let aiChatClient = apiClient.map(AIChatClient.init)
    let deviceId = DeviceIdentity.current
    let session = AppSession(
      repository: repository,
      authClient: authClient,
      workClockClient: workClockClient,
      deviceId: deviceId
    )
    let engine = SyncEngine(repository: repository, syncClient: syncClient, deviceId: deviceId)
    let syncController = SyncController(engine: engine)

    session.onRemoteLoginCompleted = {
      await syncController.syncOnce()
    }

    repository.bindSyncHandler {
      Task { @MainActor in syncController.requestSync() }
    }

    self.repository = repository
    self.session = session
    self.syncController = syncController
    self.apiClient = apiClient
    self.authClient = authClient
    self.userClient = userClient
    self.syncClient = syncClient
    self.workClockClient = workClockClient
    self.dashboardClient = dashboardClient
    self.inventoryClient = inventoryClient
    self.aiChatClient = aiChatClient
    self.deviceId = deviceId
  }
}

enum DeviceIdentity {
  private static let key = "checklist_device_id"

  static var current: String {
    if let existing = UserDefaults.standard.string(forKey: key) { return existing }
    let value = UUID().uuidString
    UserDefaults.standard.set(value, forKey: key)
    return value
  }
}
