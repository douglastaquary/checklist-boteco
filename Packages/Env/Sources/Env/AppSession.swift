import Foundation
import Combine
import Models
import Network
import Persistence
@MainActor
public final class AppSession: ObservableObject {
  @Published public private(set) var currentUser: User?
  @Published public private(set) var authToken: String?
  @Published public private(set) var remoteUserId: String?
  public var isLoggedIn: Bool { currentUser != nil }

  public var onRemoteLoginCompleted: (() async -> Void)?

  private let repository: ChecklistRepository
  private let authClient: AuthClient?
  private let workClockClient: WorkClockClient?
  private let deviceId: String
  private var pendingDeviceVerification: PendingDeviceVerification?
  private var didAttemptSessionRestore = false

  public init(
    repository: ChecklistRepository,
    authClient: AuthClient?,
    workClockClient: WorkClockClient? = nil,
    deviceId: String
  ) {
    self.repository = repository
    self.authClient = authClient
    self.workClockClient = workClockClient
    self.deviceId = deviceId
    _ = repository.loadWorksite()
  }

  public func restorePersistedSessionIfPossible() async {
    guard !didAttemptSessionRestore else { return }
    didAttemptSessionRestore = true
    guard let authClient,
          let session = try? repository.getSyncSession(),
          !session.authToken.isEmpty
    else { return }

    do {
      let result = try await authClient.fetchCurrentUser(token: session.authToken)
      guard let remoteUser = result.user, let remoteUserId = result.remoteUserId ?? remoteUser.remoteId else {
        try? repository.clearSyncSession()
        return
      }
      let profile = remoteUser
      let localUser = try repository.getUserByRemoteId(remoteUserId)
        ?? repository.getUserByEmail(profile.email)
        ?? repository.getUserByName(profile.name)
      guard let localUser else {
        try? repository.clearSyncSession()
        return
      }
      let synced = try repository.syncLocalUserFromRemote(localUserId: localUser.id, remoteUser: profile)
      currentUser = synced
      authToken = session.authToken
      self.remoteUserId = remoteUserId
      await refreshWorksite(token: session.authToken)
      await onRemoteLoginCompleted?()
    } catch {
      if isAuthError(error) {
        try? repository.clearSyncSession()
      }
    }
  }

  public func loginOffline(name: String, password: String) throws -> User {
    let user = try repository.getUserByEmail(name) ?? repository.getUserByName(name)
    guard let user, user.password == password else {
      throw NSError(domain: "Auth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Usuário ou senha inválidos"])
    }
    currentUser = user
    authToken = nil
    remoteUserId = user.remoteId
    return user
  }

  public func loginRemote(email: String, password: String) async throws -> RemoteLoginResult {
    guard let authClient else {
      _ = try loginOffline(name: email, password: password)
      return RemoteLoginResult(user: currentUser)
    }
    do {
      let result = try await authClient.login(
        email: email,
        password: password,
        deviceId: deviceId,
        deviceName: deviceName()
      )
      if result.requiresTwoFactor {
        guard let challengeId = result.challengeId?.trimmingCharacters(in: .whitespacesAndNewlines),
              !challengeId.isEmpty
        else {
          throw NSError(
            domain: "Auth",
            code: 3,
            userInfo: [NSLocalizedDescriptionKey: "Resposta de verificação inválida. Tente entrar novamente."]
          )
        }
        pendingDeviceVerification = PendingDeviceVerification(
          challengeId: challengeId,
          deviceId: deviceId,
          developmentCode: result.developmentCode
        )
        return result
      }
      pendingDeviceVerification = nil
      try await completeRemoteLogin(result: result, password: password)
      return result
    } catch {
      if let user = try? loginOffline(name: email, password: password) {
        return RemoteLoginResult(user: user)
      }
      throw error
    }
  }

  public func verifyTwoFactor(
    code: String,
    password: String
  ) async throws -> RemoteLoginResult {
    guard let authClient else { throw APIError.invalidURL }
    guard let pending = pendingDeviceVerification else {
      throw NSError(
        domain: "Auth",
        code: 4,
        userInfo: [NSLocalizedDescriptionKey: "Sessão de verificação expirada. Entre novamente."]
      )
    }
    let normalizedCode = Self.normalizeVerificationCode(code)
    guard normalizedCode.count == 6 else {
      throw NSError(
        domain: "Auth",
        code: 5,
        userInfo: [NSLocalizedDescriptionKey: "Informe o código de 6 dígitos"]
      )
    }
    let result = try await authClient.verifyDevice(
      challengeId: pending.challengeId,
      code: normalizedCode,
      deviceId: pending.deviceId,
      deviceName: deviceName()
    )
    pendingDeviceVerification = nil
    try await completeRemoteLogin(result: result, password: password)
    return result
  }

  public var pendingVerificationDevelopmentCode: String? {
    pendingDeviceVerification?.developmentCode
  }

  public func clearPendingDeviceVerification() {
    pendingDeviceVerification = nil
  }

  private static func normalizeVerificationCode(_ raw: String) -> String {
    String(raw.filter(\.isNumber).prefix(6))
  }

  public func completeRemoteLogin(result: RemoteLoginResult, password: String) async throws {
    guard let authClient, let token = result.token, let remoteUserId = result.remoteUserId ?? result.user?.remoteId else {
      throw NSError(domain: "Auth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Resposta de login inválida"])
    }
    let authoritative = try await authClient.fetchCurrentUser(token: token)
    guard let remoteUser = authoritative.user else {
      throw NSError(domain: "Auth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Resposta de login inválida"])
    }
    let profile = User(
      id: remoteUser.id,
      name: remoteUser.name,
      email: remoteUser.email,
      password: password,
      area: remoteUser.area,
      workSector: remoteUser.workSector,
      permissionLevel: remoteUser.permissionLevel,
      allowedAreas: remoteUser.allowedAreas,
      createdAt: remoteUser.createdAt,
      remoteId: remoteUserId,
      featurePermissions: remoteUser.featurePermissions
    )
    let localUser = try repository.getUserByRemoteId(remoteUserId)
      ?? repository.getUserByEmail(profile.email)
      ?? repository.getUserByName(profile.name)
      ?? repository.insertUser(profile)
    let synced = try repository.syncLocalUserFromRemote(localUserId: localUser.id, remoteUser: profile)
    try repository.saveSyncSession(
      localUserId: synced.id,
      session: SyncSession(authToken: token, remoteUserId: remoteUserId)
    )
    currentUser = synced
    authToken = token
    self.remoteUserId = remoteUserId
    await refreshWorksite(token: token)
    await onRemoteLoginCompleted?()
  }

  public func logout() throws {
    try repository.clearSyncSession()
    currentUser = nil
    authToken = nil
    remoteUserId = nil
    pendingDeviceVerification = nil
  }

  private func refreshWorksite(token: String) async {
    guard let workClockClient else { return }
    do {
      let worksite = try await workClockClient.fetchWorksite(token: token)
      try repository.saveWorksite(worksite)
    } catch {
      _ = repository.loadWorksite()
    }
  }

  private func isAuthError(_ error: Error) -> Bool {
    if case let APIError.http(status, _) = error {
      return status == 401 || status == 403
    }
    return false
  }

  private struct PendingDeviceVerification: Sendable {
    let challengeId: String
    let deviceId: String
    let developmentCode: String?
  }

  private func deviceName() -> String {
    #if os(iOS)
    return UIDevice.current.name
    #else
    return Host.current().localizedName ?? "ChecklistBoteco"
    #endif
  }
}

#if os(iOS)
import UIKit
#else
import Foundation
#endif
