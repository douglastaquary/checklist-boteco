import XCTest
@testable import Persistence

final class RepositoryTests: XCTestCase {
  func testSeedsAdminUserInEmptyDatabase() throws {
    let db = try AppDatabase.inMemory()
    let repo = ChecklistRepository(dbQueue: db)
    try repo.seedInitialDataIfNeeded()
    let user = try repo.getUserByEmail("admin@checklistboteco.com")
    XCTAssertEqual(user?.permissionLevel, .admin)
  }
}
