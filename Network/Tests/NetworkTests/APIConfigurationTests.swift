import XCTest
@testable import Network

final class APIConfigurationTests: XCTestCase {
  func testBuildsLoginURLWithoutStrippingSchemeSlashes() {
    let config = APIConfiguration(baseURL: URL(string: "http://localhost:8181")!)
    XCTAssertEqual(
      config.url(for: "/api/auth/login").absoluteString,
      "http://localhost:8181/api/auth/login"
    )
  }

  func testBuildsURLWhenBaseHasTrailingSlash() {
    let config = APIConfiguration(baseURL: URL(string: "http://localhost:8181/")!)
    XCTAssertEqual(
      config.url(for: "api/health").absoluteString,
      "http://localhost:8181/api/health"
    )
  }

  func testRejectsMalformedBaseURLWithoutHost() {
    XCTAssertNil(URL(string: "http:"))
  }
}
