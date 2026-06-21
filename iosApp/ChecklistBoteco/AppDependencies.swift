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
  public let syncClient: SyncClient?
  public let inventoryClient: InventoryClient?
  public let deviceId: String

  public init() throws {
    let db = try AppDatabase.open()
    let repository = ChecklistRepository(dbQueue: db)
    let apiConfiguration = APIConfiguration.fromBundle()

    if apiConfiguration == nil {
      try repository.seedInitialDataIfNeeded()
    }

    let apiClient = apiConfiguration.map { APIClient(config: $0) }
    let authClient = apiClient.map(AuthClient.init)
    let syncClient = apiClient.map(SyncClient.init)
    let inventoryClient = apiClient.map(InventoryClient.init)
    let deviceId = DeviceIdentity.current
    let session = AppSession(repository: repository, authClient: authClient, deviceId: deviceId)
    let engine = SyncEngine(repository: repository, syncClient: syncClient)
    let syncController = SyncController(engine: engine)

    repository.bindSyncHandler {
      Task { @MainActor in syncController.requestSync() }
    }

    self.repository = repository
    self.session = session
    self.syncController = syncController
    self.apiClient = apiClient
    self.authClient = authClient
    self.syncClient = syncClient
    self.inventoryClient = inventoryClient
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
