import Foundation
import LocalAuthentication
import Models
public protocol CredentialStoreProtocol: Sendable {
  func loadMetadata() -> SavedLoginMetadata
  func save(username: String, password: String, remember: Bool) throws
  func unlock() async throws -> UnlockedLoginCredentials
  func clear()
}

public enum CredentialStoreError: Error, LocalizedError {
  case biometryUnavailable
  case cancelled
  case notFound

  public var errorDescription: String? {
    switch self {
    case .biometryUnavailable: return "Biometria indisponível neste aparelho."
    case .cancelled: return "Confirme a biometria para preencher usuário e senha."
    case .notFound: return "Nenhum login salvo."
    }
  }
}

public final class KeychainCredentialStore: CredentialStoreProtocol, @unchecked Sendable {
  private let service = "com.checklistboteco.login"
  private let account = "saved_login"
  private let rememberKey = "remember_login"
  private let usernameKey = "remembered_username"

  public init() {}

  public func loadMetadata() -> SavedLoginMetadata {
    let remember = UserDefaults.standard.bool(forKey: rememberKey)
    let savedUsername = UserDefaults.standard.string(forKey: usernameKey) ?? ""
    guard remember else {
      return SavedLoginMetadata(remember: false)
    }
    guard hasStoredCredentials else {
      return SavedLoginMetadata(username: savedUsername, remember: true)
    }
    if shouldProtectWithBiometry {
      return SavedLoginMetadata(
        username: savedUsername,
        remember: true,
        requiresBiometricUnlock: true
      )
    }
    if let unlocked = try? loadCredentials(requireBiometry: false) {
      return SavedLoginMetadata(
        username: unlocked.username,
        password: unlocked.password,
        remember: true
      )
    }
    return SavedLoginMetadata(username: savedUsername, remember: true)
  }

  public func save(username: String, password: String, remember: Bool) throws {
    let trimmedUsername = username.trimmingCharacters(in: .whitespacesAndNewlines)
    UserDefaults.standard.set(remember, forKey: rememberKey)
    if remember {
      UserDefaults.standard.set(trimmedUsername, forKey: usernameKey)
    } else {
      UserDefaults.standard.removeObject(forKey: usernameKey)
      clear()
      return
    }
    guard !trimmedUsername.isEmpty, !password.isEmpty else {
      throw NSError(
        domain: "Auth",
        code: 6,
        userInfo: [NSLocalizedDescriptionKey: "Usuário e senha são obrigatórios para salvar"]
      )
    }
    let payload = CredentialsCodec.pack(username: trimmedUsername, password: password)
    try saveSecure(payload: payload, requireBiometry: shouldProtectWithBiometry)
  }

  public func unlock() async throws -> UnlockedLoginCredentials {
    guard hasStoredCredentials else { throw CredentialStoreError.notFound }
    if shouldProtectWithBiometry {
      let context = LAContext()
      var error: NSError?
      guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
        throw CredentialStoreError.biometryUnavailable
      }
      let success = try await context.evaluatePolicy(
        .deviceOwnerAuthenticationWithBiometrics,
        localizedReason: "Confirme sua biometria para preencher usuário e senha"
      )
      guard success else { throw CredentialStoreError.cancelled }
    }
    return try loadCredentials(requireBiometry: false)
  }

  public func clear() {
    deleteStoredCredentials()
    UserDefaults.standard.set(false, forKey: rememberKey)
    UserDefaults.standard.removeObject(forKey: usernameKey)
  }

  private var shouldProtectWithBiometry: Bool {
    #if targetEnvironment(simulator)
    return false
    #else
    #if DEBUG
    return false
    #else
    return canEvaluateBiometry()
    #endif
    #endif
  }

  private var hasStoredCredentials: Bool {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
      kSecReturnData as String: false,
    ]
    return SecItemCopyMatching(query as CFDictionary, nil) == errSecSuccess
  }

  private func canEvaluateBiometry() -> Bool {
    let context = LAContext()
    var error: NSError?
    return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
  }

  private func deleteStoredCredentials() {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
    ]
    SecItemDelete(query as CFDictionary)
  }

  private func saveSecure(payload: String, requireBiometry: Bool) throws {
    deleteStoredCredentials()
    guard let data = payload.data(using: .utf8) else { return }
    var query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
      kSecValueData as String: data,
      kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
    ]
    if requireBiometry {
      var accessError: Unmanaged<CFError>?
      guard let access = SecAccessControlCreateWithFlags(
        nil,
        kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        .biometryCurrentSet,
        &accessError
      ) else {
        throw accessError!.takeRetainedValue() as Error
      }
      query[kSecAttrAccessControl as String] = access
      query.removeValue(forKey: kSecAttrAccessible as String)
    }
    let status = SecItemAdd(query as CFDictionary, nil)
    guard status == errSecSuccess else {
      throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
    }
  }

  private func loadCredentials(requireBiometry: Bool) throws -> UnlockedLoginCredentials {
    var query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
      kSecReturnData as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne,
    ]
    if requireBiometry {
      let context = LAContext()
      query[kSecUseAuthenticationContext as String] = context
    }
    var item: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &item)
    guard status == errSecSuccess,
          let data = item as? Data,
          let payload = String(data: data, encoding: .utf8)
    else {
      if status == errSecUserCanceled || status == errSecAuthFailed {
        throw CredentialStoreError.cancelled
      }
      throw CredentialStoreError.notFound
    }
    return CredentialsCodec.unpack(payload)
  }
}
