import XCTest
@testable import Models

final class InventoryCountValidatorTests: XCTestCase {
  func testAcceptsValidBeverage() {
    let draft = InventoryCountDraft(
      name: "Heineken",
      quantity: 24,
      category: .alcoolico,
      volume: 600,
      volumeUnit: "ML",
      salePriceInCents: 1800,
      costPriceInCents: 900,
      storageCondition: .gelado
    )
    XCTAssertTrue(InventoryCountValidator.validate(draft).isEmpty)
  }

  func testRejectsInvalidQuantityVolumeAndUnit() {
    let draft = InventoryCountDraft(
      name: "",
      quantity: -1,
      category: .naoAlcoolico,
      volume: 0,
      volumeUnit: "L",
      salePriceInCents: -1,
      storageCondition: .natural
    )
    XCTAssertEqual(InventoryCountValidator.validate(draft).count, 5)
  }
}
