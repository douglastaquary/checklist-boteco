import XCTest
@testable import Network

final class AppErrorMapperTests: XCTestCase {
  func testMapsUnauthorizedToFriendlyMessage() {
    let message = AppErrorMapper.fromHTTP(status: 401, body: "")
    XCTAssertTrue(message.lowercased().contains("login"))
  }
}
