import XCTest
@testable import Network

@MainActor
final class SessionExpiredCenterTests: XCTestCase {
  func testNotifyIsIdempotentUntilReset() {
    let center = SessionExpiredCenter.shared
    center.reset()
    center.notify(reason: "Sua sessão expirou")
    XCTAssertTrue(center.isHandling)
    XCTAssertEqual(center.latestEvent?.reason, "Sua sessão expirou")
    center.notify(reason: "Outra mensagem")
    XCTAssertEqual(center.latestEvent?.reason, "Sua sessão expirou")
    center.reset()
    XCTAssertFalse(center.isHandling)
    XCTAssertNil(center.latestEvent)
  }
}
