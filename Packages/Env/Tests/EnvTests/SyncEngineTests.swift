import XCTest
@testable import Env
@testable import Persistence

final class SyncEngineTests: XCTestCase {
  func testSyncOnceWithoutRemoteClientDoesNotCrash() async throws {
    let db = try AppDatabase.inMemory()
    let repository = ChecklistRepository(dbQueue: db)
    try repository.seedInitialDataIfNeeded()
    let engine = SyncEngine(repository: repository, syncClient: nil, deviceId: "test-device")
    await engine.syncOnce()
  }
}
