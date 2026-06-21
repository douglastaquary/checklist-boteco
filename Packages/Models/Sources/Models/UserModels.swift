import Foundation

public enum PermissionLevel: String, Codable, Sendable {
  case admin = "ADMIN"
  case user = "USER"

  public static func from(_ value: String) -> PermissionLevel {
    PermissionLevel(rawValue: value.uppercased()) ?? .user
  }
}

public enum WorkSector: String, CaseIterable, Codable, Sendable {
  case atendimento = "ATENDIMENTO"
  case cozinha = "COZINHA"
  case servicosGerais = "SERVICOS_GERAIS"
  case garcom = "GARCOM"
  case cumim = "CUMIM"
  case chefeCozinha = "CHEFE_COZINHA"
  case gerente = "GERENTE"
  case ajudanteCozinha = "AJUDANTE_COZINHA"
  case atendente = "ATENDENTE"
  case barman = "BARMAN"

  public static func from(_ value: String) -> WorkSector {
    WorkSector(rawValue: value.uppercased()) ?? .atendimento
  }
}

public struct FeaturePermissions: Codable, Equatable, Sendable {
  public var canRegisterUsers: Bool
  public var canCreateActivities: Bool
  public var canEditUsers: Bool
  public var canCreateInventoryCounts: Bool
  public var canViewInventoryInsights: Bool
  public var canManageAdministrativeStock: Bool

  public init(
    canRegisterUsers: Bool = false,
    canCreateActivities: Bool = false,
    canEditUsers: Bool = false,
    canCreateInventoryCounts: Bool = false,
    canViewInventoryInsights: Bool = false,
    canManageAdministrativeStock: Bool = false
  ) {
    self.canRegisterUsers = canRegisterUsers
    self.canCreateActivities = canCreateActivities
    self.canEditUsers = canEditUsers
    self.canCreateInventoryCounts = canCreateInventoryCounts
    self.canViewInventoryInsights = canViewInventoryInsights
    self.canManageAdministrativeStock = canManageAdministrativeStock
  }

  public static let admin = FeaturePermissions(
    canRegisterUsers: true,
    canCreateActivities: true,
    canEditUsers: true,
    canCreateInventoryCounts: true,
    canViewInventoryInsights: true,
    canManageAdministrativeStock: true
  )

  public static let `default` = FeaturePermissions()
}

public struct User: Identifiable, Equatable, Sendable {
  public let id: Int64
  public var name: String
  public var email: String
  public var password: String
  public var area: Area
  public var workSector: WorkSector
  public var permissionLevel: PermissionLevel
  public var allowedAreas: [Area]
  public var createdAt: Int64
  public var remoteId: String?
  public var featurePermissions: FeaturePermissions

  public init(
    id: Int64,
    name: String,
    email: String = "",
    password: String,
    area: Area,
    workSector: WorkSector = .atendimento,
    permissionLevel: PermissionLevel,
    allowedAreas: [Area],
    createdAt: Int64 = 0,
    remoteId: String? = nil,
    featurePermissions: FeaturePermissions = .default
  ) {
    self.id = id
    self.name = name
    self.email = email
    self.password = password
    self.area = area
    self.workSector = workSector
    self.permissionLevel = permissionLevel
    self.allowedAreas = allowedAreas
    self.createdAt = createdAt
    self.remoteId = remoteId
    self.featurePermissions = featurePermissions
  }

  public func canAccessArea(_ area: Area) -> Bool {
    permissionLevel == .admin || allowedAreas.contains(area)
  }

  public func canRegisterUsers() -> Bool {
    permissionLevel == .admin || featurePermissions.canRegisterUsers
  }

  public func canCreateActivities() -> Bool {
    permissionLevel == .admin || featurePermissions.canCreateActivities
  }

  public func canEditUsers() -> Bool {
    permissionLevel == .admin || featurePermissions.canEditUsers
  }

  public func canCreateInventoryCounts() -> Bool {
    permissionLevel == .admin || featurePermissions.canCreateInventoryCounts
  }

  public func canViewInventoryInsights() -> Bool {
    permissionLevel == .admin || featurePermissions.canViewInventoryInsights
  }

  public func canManageAdministrativeStock() -> Bool {
    permissionLevel == .admin || featurePermissions.canManageAdministrativeStock
  }

  public func canManagePermissions() -> Bool { permissionLevel == .admin }
  public func canUseWorkClock() -> Bool { permissionLevel != .admin }

  public func canUseInventoryModule() -> Bool {
    canCreateInventoryCounts() || canViewInventoryInsights() || canManageAdministrativeStock()
  }

  public func canUseDashboardModule() -> Bool {
    canCreateActivities() || canEditUsers() || canRegisterUsers()
  }

  public func canUseActivitiesModule() -> Bool { canCreateActivities() }
}

public enum AppTab: String, CaseIterable, Identifiable, Sendable {
  case checklist
  case workClock
  case inventory
  case dashboard
  case activities
  case permissions

  public var id: String { rawValue }

  public var title: String {
    switch self {
    case .checklist: return "Checklist"
    case .workClock: return "Ponto"
    case .inventory: return "Contagem"
    case .dashboard: return "Dashboard"
    case .activities: return "Atividades"
    case .permissions: return "Permissões"
    }
  }

  public static func available(for user: User) -> [AppTab] {
    var tabs: [AppTab] = [.checklist]
    if user.canUseWorkClock() { tabs.append(.workClock) }
    if user.canUseInventoryModule() { tabs.append(.inventory) }
    if user.canUseDashboardModule() { tabs.append(.dashboard) }
    if user.canUseActivitiesModule() { tabs.append(.activities) }
    if user.canManagePermissions() { tabs.append(.permissions) }
    return tabs
  }
}
