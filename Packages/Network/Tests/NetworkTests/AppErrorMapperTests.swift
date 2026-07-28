import XCTest
@testable import Network

final class AppErrorMapperTests: XCTestCase {
  func testMapsUnauthorizedToFriendlyMessage() {
    let message = AppErrorMapper.fromHTTP(status: 401, body: "")
    XCTAssertTrue(message.lowercased().contains("login"))
  }

  func testMapsServiceUnavailableWithoutBodyToAiConfigHint() {
    let message = AppErrorMapper.fromHTTP(status: 503, body: "")
    XCTAssertTrue(message.contains("OPENAI_API_KEY"))
  }

  func testPrefersServerMessageBody() {
    let message = AppErrorMapper.fromHTTP(
      status: 503,
      body: #"{"message":"Chat de IA ainda não configurado. Defina OPENAI_API_KEY no backend."}"#
    )
    XCTAssertTrue(message.contains("ainda não configurado"))
  }
}
