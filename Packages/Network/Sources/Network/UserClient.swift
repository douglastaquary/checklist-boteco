import Foundation
import Models

public final class UserClient: Sendable {
  private let api: APIClient

  public init(api: APIClient) {
    self.api = api
  }

  public func listUsers(token: String) async throws -> [User] {
    let users: [PublicUserDTO] = try await api.request(path: "/api/users", token: token)
    return users.map { $0.toDomain() }
  }

  public func createUser(
    token: String,
    name: String,
    email: String,
    password: String,
    workSector: WorkSector,
    permissionLevel: PermissionLevel = .user,
    permissions: FeaturePermissions = .default
  ) async throws -> User {
    let dto: PublicUserDTO = try await api.request(
      path: "/api/users",
      method: "POST",
      token: token,
      body: CreateUserRequestDTO(
        name: name,
        email: email,
        password: password,
        workSector: workSector.rawValue,
        permissionLevel: permissionLevel.rawValue,
        permissions: FeaturePermissionsDTO(from: permissions)
      )
    )
    return dto.toDomain()
  }

  public func updatePermissions(
    token: String,
    userId: String,
    permissions: FeaturePermissions
  ) async throws -> User {
    let dto: PublicUserDTO = try await api.request(
      path: "/api/users/\(userId)/permissions",
      method: "PATCH",
      token: token,
      body: PermissionUpdateRequestDTO(permissions: FeaturePermissionsDTO(from: permissions))
    )
    return dto.toDomain()
  }
}

private struct CreateUserRequestDTO: Encodable {
  let name: String
  let email: String
  let password: String
  let workSector: String
  let permissionLevel: String
  let permissions: FeaturePermissionsDTO
}

private struct PermissionUpdateRequestDTO: Encodable {
  let permissions: FeaturePermissionsDTO
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
  let mustChangePassword: Bool?

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
      featurePermissions: permissions?.toDomain() ?? .default,
      mustChangePassword: mustChangePassword ?? false
    )
  }
}

private struct FeaturePermissionsDTO: Codable {
  let canRegisterUsers: Bool
  let canCreateActivities: Bool
  let canEditUsers: Bool
  let canCreateInventoryCounts: Bool
  let canViewInventoryInsights: Bool
  let canManageAdministrativeStock: Bool
  let canImportPurchases: Bool

  private enum CodingKeys: String, CodingKey {
    case canRegisterUsers
    case canCreateActivities
    case canEditUsers
    case canCreateInventoryCounts
    case canViewInventoryInsights
    case canManageAdministrativeStock
    case canImportPurchases
  }

  init(from permissions: FeaturePermissions) {
    canRegisterUsers = permissions.canRegisterUsers
    canCreateActivities = permissions.canCreateActivities
    canEditUsers = permissions.canEditUsers
    canCreateInventoryCounts = permissions.canCreateInventoryCounts
    canViewInventoryInsights = permissions.canViewInventoryInsights
    canManageAdministrativeStock = permissions.canManageAdministrativeStock
    canImportPurchases = permissions.canImportPurchases
  }

  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    canRegisterUsers = try container.decodeIfPresent(Bool.self, forKey: .canRegisterUsers) ?? false
    canCreateActivities = try container.decodeIfPresent(Bool.self, forKey: .canCreateActivities) ?? false
    canEditUsers = try container.decodeIfPresent(Bool.self, forKey: .canEditUsers) ?? false
    canCreateInventoryCounts = try container.decodeIfPresent(Bool.self, forKey: .canCreateInventoryCounts) ?? false
    canViewInventoryInsights = try container.decodeIfPresent(Bool.self, forKey: .canViewInventoryInsights) ?? false
    canManageAdministrativeStock = try container.decodeIfPresent(Bool.self, forKey: .canManageAdministrativeStock) ?? false
    canImportPurchases = try container.decodeIfPresent(Bool.self, forKey: .canImportPurchases) ?? false
  }

  func toDomain() -> FeaturePermissions {
    FeaturePermissions(
      canRegisterUsers: canRegisterUsers,
      canCreateActivities: canCreateActivities,
      canEditUsers: canEditUsers,
      canCreateInventoryCounts: canCreateInventoryCounts,
      canViewInventoryInsights: canViewInventoryInsights,
      canManageAdministrativeStock: canManageAdministrativeStock,
      canImportPurchases: canImportPurchases
    )
  }
}
