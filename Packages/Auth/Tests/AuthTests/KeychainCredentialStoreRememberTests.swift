import XCTest
@testable import Auth

final class KeychainCredentialStoreRememberTests: XCTestCase {
  private let rememberKey = "remember_login"
  private let usernameKey = "remembered_username"

  override func tearDown() {
    UserDefaults.standard.removeObject(forKey: rememberKey)
    UserDefaults.standard.removeObject(forKey: usernameKey)
    KeychainCredentialStore().clear()
    super.tearDown()
  }

  func testSaveDoesNotClearRememberFlagBeforeKeychainWrite() throws {
    let store = KeychainCredentialStore()
    try store.save(username: "admin@checklistboteco.com", password: "admin123", remember: true)

    XCTAssertTrue(UserDefaults.standard.bool(forKey: rememberKey))
    XCTAssertEqual(UserDefaults.standard.string(forKey: usernameKey), "admin@checklistboteco.com")

    let metadata = store.loadMetadata()
    XCTAssertTrue(metadata.remember)
    #if targetEnvironment(simulator)
    XCTAssertEqual(metadata.username, "admin@checklistboteco.com")
    XCTAssertEqual(metadata.password, "admin123")
    XCTAssertFalse(metadata.requiresBiometricUnlock)
    #endif
  }

  func testClearRememberRemovesStoredUsernameHint() throws {
    let store = KeychainCredentialStore()
    try store.save(username: "admin@checklistboteco.com", password: "admin123", remember: true)
    try store.save(username: "admin@checklistboteco.com", password: "admin123", remember: false)

    XCTAssertFalse(UserDefaults.standard.bool(forKey: rememberKey))
    XCTAssertNil(UserDefaults.standard.string(forKey: usernameKey))
  }
}
