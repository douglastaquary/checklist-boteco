import Foundation
import Models
public final class AuthClient: Sendable {
  private let api: APIClient

  public init(api: APIClient) {
    self.api = api
  }

  public func login(
    email: String,
    password: String,
    deviceId: String,
    deviceName: String
  ) async throws -> RemoteLoginResult {
    let dto: LoginResponseDTO = try await api.request(
      path: "/api/auth/login",
      method: "POST",
      body: LoginRequestDTO(email: email, password: password, deviceId: deviceId, deviceName: deviceName)
    )
    return dto.toResult()
  }

  public func verifyDevice(
    challengeId: String,
    code: String,
    deviceId: String,
    deviceName: String
  ) async throws -> RemoteLoginResult {
    let dto: LoginResponseDTO = try await api.request(
      path: "/api/auth/verify-device",
      method: "POST",
      body: VerifyDeviceRequestDTO(
        challengeId: challengeId,
        code: code,
        deviceId: deviceId,
        deviceName: deviceName
      )
    )
    return dto.toResult()
  }

  public func fetchCurrentUser(token: String) async throws -> RemoteLoginResult {
    let dto: PublicUserDTO = try await api.request(path: "/api/me", token: token)
    let user = dto.toDomain()
    return RemoteLoginResult(token: token, user: user, remoteUserId: dto.id)
  }
}

private struct LoginRequestDTO: Encodable {
  let email: String
  let password: String
  let deviceId: String
  let deviceName: String
}

private struct VerifyDeviceRequestDTO: Encodable {
  let challengeId: String
  let code: String
  let deviceId: String
  let deviceName: String
}

private struct LoginResponseDTO: Decodable {
  let token: String?
  let user: PublicUserDTO?
  let requiresTwoFactor: Bool
  let challengeId: String?
  let deliveryHint: String?
  let developmentCode: String?

  func toResult() -> RemoteLoginResult {
    RemoteLoginResult(
      token: token,
      user: user?.toDomain(),
      remoteUserId: user?.id,
      requiresTwoFactor: requiresTwoFactor,
      challengeId: challengeId,
      deliveryHint: deliveryHint,
      developmentCode: developmentCode
    )
  }
}

private struct PublicUserDTO: Decodable {
  let id: String
  let name: String
  let email: String
  let area: String
  let workSector: String
  let permissionLevel: String
  let allowedAreas: [String]
  let createdAt: Int64
  let permissions: FeaturePermissionsDTO?

  func toDomain() -> User {
    let level = PermissionLevel.from(permissionLevel)
    let sector = WorkSector.from(workSector)
    let parsedAreas = allowedAreas.map(Area.from).filter { !$0.rawValue.isEmpty }
    let resolvedAreas: [Area]
    if level == .admin {
      resolvedAreas = Area.allCases
    } else if parsedAreas.isEmpty {
      resolvedAreas = [sector.activityArea]
    } else {
      resolvedAreas = parsedAreas
    }
    return User(
      id: 0,
      name: name,
      email: email,
      password: "",
      area: Area.from(area),
      workSector: sector,
      permissionLevel: level,
      allowedAreas: resolvedAreas,
      createdAt: createdAt,
      remoteId: id,
      featurePermissions: permissions?.toDomain() ?? .default
    )
  }
}

private struct FeaturePermissionsDTO: Decodable {
  let canRegisterUsers: Bool
  let canCreateActivities: Bool
  let canEditUsers: Bool
  let canCreateInventoryCounts: Bool
  let canViewInventoryInsights: Bool
  let canManageAdministrativeStock: Bool

  func toDomain() -> FeaturePermissions {
    FeaturePermissions(
      canRegisterUsers: canRegisterUsers,
      canCreateActivities: canCreateActivities,
      canEditUsers: canEditUsers,
      canCreateInventoryCounts: canCreateInventoryCounts,
      canViewInventoryInsights: canViewInventoryInsights,
      canManageAdministrativeStock: canManageAdministrativeStock
    )
  }
}
