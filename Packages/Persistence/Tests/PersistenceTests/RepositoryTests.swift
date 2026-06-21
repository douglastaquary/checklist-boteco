import XCTest
import Models
@testable import Persistence

final class RepositoryTests: XCTestCase {
  func testSeedsAdminUserInEmptyDatabase() throws {
    let db = try AppDatabase.inMemory()
    let repo = ChecklistRepository(dbQueue: db)
    try repo.seedInitialDataIfNeeded()
    let user = try repo.getUserByEmail("admin@checklistboteco.com")
    XCTAssertEqual(user?.permissionLevel, .admin)
  }

  func testSeedsCollaboratorForWorkClock() throws {
    let db = try AppDatabase.inMemory()
    let repo = ChecklistRepository(dbQueue: db)
    try repo.seedInitialDataIfNeeded()
    let user = try repo.getUserByEmail("colaborador@checklistboteco.com")
    XCTAssertEqual(user?.permissionLevel, .user)
    XCTAssertTrue(user?.canUseWorkClock() ?? false)
  }

  func testInsertWorkClockEntryWithoutRemoteSession() throws {
    let db = try AppDatabase.inMemory()
    let repo = ChecklistRepository(dbQueue: db)
    try repo.seedInitialDataIfNeeded()
    let user = try XCTUnwrap(try repo.getUserByEmail("colaborador@checklistboteco.com"))
    let entry = WorkClockEntry(
      id: 0,
      userId: user.id,
      type: .entrada,
      registeredAt: Date.nowMillis,
      location: WorksiteLocation.point,
      distanceFromWorkMeters: 0,
      isLate: false
    )
    let id = try repo.insertWorkClockEntry(entry)
    XCTAssertGreaterThan(id, 0)
    let entries = try repo.workClockEntries(
      userId: user.id,
      dayStart: Date.startOfDayMillis,
      dayEnd: Date.startOfDayMillis + 86_400_000
    )
    XCTAssertEqual(entries.count, 1)
    XCTAssertEqual(entries.first?.type, .entrada)
  }
}

private extension Date {
  static var nowMillis: Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
  static var startOfDayMillis: Int64 {
    Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 * 1000)
  }
}
