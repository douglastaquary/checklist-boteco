import XCTest
@testable import Models

final class WorkSectorChecklistAccessTests: XCTestCase {
  func testKitchenSectorsSeeCozinhaChecklistOnly() {
    for sector in [WorkSector.cozinha, .chefeCozinha, .ajudanteCozinha] {
      XCTAssertTrue(sector.isKitchenSector)
      XCTAssertEqual(sector.checklistAreas, [.cozinha])
      XCTAssertEqual(sector.activityArea, .cozinha)
    }
  }

  func testNonKitchenSectorsSeeAtendimentoChecklist() {
    let sectors: [WorkSector] = [
      .atendimento, .garcom, .cumim, .gerente, .atendente, .barman, .servicosGerais,
    ]
    for sector in sectors {
      XCTAssertFalse(sector.isKitchenSector)
      XCTAssertEqual(sector.checklistAreas, [.atendimento])
    }
  }

  func testUserChecklistAccessibleAreasFollowsSectorRule() {
    let garcom = User(
      id: 1,
      name: "G",
      email: "g@test.com",
      password: "x",
      area: .atendimento,
      workSector: .garcom,
      permissionLevel: .user,
      allowedAreas: [.atendimento],
      createdAt: 0
    )
    XCTAssertEqual(garcom.checklistAccessibleAreas, [.atendimento])
    XCTAssertTrue(garcom.canAccessChecklistArea(.atendimento))
    XCTAssertFalse(garcom.canAccessChecklistArea(.cozinha))

    let ajudante = User(
      id: 2,
      name: "A",
      email: "a@test.com",
      password: "x",
      area: .cozinha,
      workSector: .ajudanteCozinha,
      permissionLevel: .user,
      allowedAreas: [.cozinha],
      createdAt: 0
    )
    XCTAssertEqual(ajudante.checklistAccessibleAreas, [.cozinha])
    XCTAssertFalse(ajudante.canAccessChecklistArea(.atendimento))
  }

  func testAdminSeesAllChecklistAreas() {
    let admin = User(
      id: 1,
      name: "Admin",
      email: "a@test.com",
      password: "x",
      area: .atendimento,
      workSector: .gerente,
      permissionLevel: .admin,
      allowedAreas: Area.allCases,
      createdAt: 0
    )
    XCTAssertEqual(admin.checklistAccessibleAreas, Area.allCases)
  }
}
