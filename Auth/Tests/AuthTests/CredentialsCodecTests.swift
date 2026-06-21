import XCTest
@testable import Auth

final class CredentialsCodecTests: XCTestCase {
  func testCodecRoundTrip() {
    let packed = CredentialsCodec.pack(username: "admin@checklistboteco.com", password: "admin123")
    let unlocked = CredentialsCodec.unpack(packed)
    XCTAssertEqual(unlocked.username, "admin@checklistboteco.com")
    XCTAssertEqual(unlocked.password, "admin123")
  }
}
