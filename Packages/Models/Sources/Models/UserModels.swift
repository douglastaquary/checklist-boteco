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

  public var displayName: String {
    switch self {
    case .atendimento: return "Atendimento"
    case .cozinha: return "Cozinha"
    case .servicosGerais: return "Serviços Gerais"
    case .garcom: return "Garçom"
    case .cumim: return "Cumim"
    case .chefeCozinha: return "Chefe de Cozinha"
    case .gerente: return "Gerente"
    case .ajudanteCozinha: return "Ajudante de Cozinha"
    case .atendente: return "Atendente"
    case .barman: return "Barman"
    }
  }

  /// Área principal de atividades associada ao setor (paridade backend/KMP).
  public var activityArea: Area {
    switch self {
    case .atendimento, .garcom, .cumim, .gerente, .atendente, .barman:
      return .atendimento
    case .cozinha, .chefeCozinha, .ajudanteCozinha:
      return .cozinha
    case .servicosGerais:
      return .limpeza
    }
  }

  public var isKitchenSector: Bool {
    switch self {
    case .cozinha, .chefeCozinha, .ajudanteCozinha:
      return true
    default:
      return false
    }
  }

  /// Áreas visíveis no checklist conforme regra de negócio: cozinha só vê COZINHA; demais setores veem ATENDIMENTO.
  public var checklistAreas: [Area] {
    isKitchenSector ? [.cozinha] : [.atendimento]
  }
}

public struct FeaturePermissions: Codable, Equatable, Sendable {
  public var canRegisterUsers: Bool
  public var canCreateActivities: Bool
  public var canEditUsers: Bool
  public var canCreateInventoryCounts: Bool
  public var canViewInventoryInsights: Bool
  public var canManageAdministrativeStock: Bool
  public var canImportPurchases: Bool

  public init(
    canRegisterUsers: Bool = false,
    canCreateActivities: Bool = false,
    canEditUsers: Bool = false,
    canCreateInventoryCounts: Bool = false,
    canViewInventoryInsights: Bool = false,
    canManageAdministrativeStock: Bool = false,
    canImportPurchases: Bool = false
  ) {
    self.canRegisterUsers = canRegisterUsers
    self.canCreateActivities = canCreateActivities
    self.canEditUsers = canEditUsers
    self.canCreateInventoryCounts = canCreateInventoryCounts
    self.canViewInventoryInsights = canViewInventoryInsights
    self.canManageAdministrativeStock = canManageAdministrativeStock
    self.canImportPurchases = canImportPurchases
  }

  public static let admin = FeaturePermissions(
    canRegisterUsers: true,
    canCreateActivities: true,
    canEditUsers: true,
    canCreateInventoryCounts: true,
    canViewInventoryInsights: true,
    canManageAdministrativeStock: true,
    canImportPurchases: true
  )

  public static let `default` = FeaturePermissions()

  private enum CodingKeys: String, CodingKey {
    case canRegisterUsers
    case canCreateActivities
    case canEditUsers
    case canCreateInventoryCounts
    case canViewInventoryInsights
    case canManageAdministrativeStock
    case canImportPurchases
  }

  public init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    canRegisterUsers = try container.decodeIfPresent(Bool.self, forKey: .canRegisterUsers) ?? false
    canCreateActivities = try container.decodeIfPresent(Bool.self, forKey: .canCreateActivities) ?? false
    canEditUsers = try container.decodeIfPresent(Bool.self, forKey: .canEditUsers) ?? false
    canCreateInventoryCounts = try container.decodeIfPresent(Bool.self, forKey: .canCreateInventoryCounts) ?? false
    canViewInventoryInsights = try container.decodeIfPresent(Bool.self, forKey: .canViewInventoryInsights) ?? false
    canManageAdministrativeStock = try container.decodeIfPresent(Bool.self, forKey: .canManageAdministrativeStock) ?? false
    canImportPurchases = try container.decodeIfPresent(Bool.self, forKey: .canImportPurchases) ?? false
  }
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
  public var mustChangePassword: Bool

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
    featurePermissions: FeaturePermissions = .default,
    mustChangePassword: Bool = false
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
    self.mustChangePassword = mustChangePassword
  }

  public func canAccessArea(_ area: Area) -> Bool {
    permissionLevel == .admin || allowedAreas.contains(area)
  }

  /// Áreas do checklist derivadas do setor (admin vê todas).
  public var checklistAccessibleAreas: [Area] {
    if permissionLevel == .admin { return Area.allCases }
    return workSector.checklistAreas
  }

  public func canAccessChecklistArea(_ area: Area) -> Bool {
    checklistAccessibleAreas.contains(area)
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

  public func canImportPurchases() -> Bool {
    permissionLevel == .admin || featurePermissions.canImportPurchases
  }

  public func canManagePermissions() -> Bool { permissionLevel == .admin }
  public func canUseWorkClock() -> Bool { permissionLevel != .admin }

  public func canUseInventoryModule() -> Bool {
    canCreateInventoryCounts() || canViewInventoryInsights() || canManageAdministrativeStock()
  }

  public func canUsePurchasesModule() -> Bool { canImportPurchases() }

  public func canUseDashboardModule() -> Bool {
    canCreateActivities() || canEditUsers() || canRegisterUsers()
  }

  public func canUseActivitiesModule() -> Bool { canCreateActivities() }
}

public enum AppTab: String, CaseIterable, Identifiable, Sendable {
  case checklist
  case workClock
  case inventory
  case purchases
  case dashboard
  case activities
  case permissions
  case aiChat
  /// Tab hub for overflow modules (avoids UIKit system More, which blanks SwiftUI pushes).
  case more

  public var id: String { rawValue }

  public var title: String {
    switch self {
    case .checklist: return "Checklist"
    case .workClock: return "Ponto"
    case .inventory: return "Contagem"
    case .purchases: return "Compras"
    case .dashboard: return "Dashboard"
    case .activities: return "Atividades"
    case .permissions: return "Permissões"
    case .aiChat: return "Chat IA"
    case .more: return "Mais"
    }
  }

  /// Modules the user can open (never includes `.more`).
  public static func available(for user: User) -> [AppTab] {
    var tabs: [AppTab] = [.checklist]
    if user.canUseWorkClock() { tabs.append(.workClock) }
    if user.canUseInventoryModule() { tabs.append(.inventory) }
    if user.canUsePurchasesModule() { tabs.append(.purchases) }
    if user.canUseDashboardModule() { tabs.append(.dashboard) }
    if user.canUseActivitiesModule() { tabs.append(.activities) }
    if user.canManagePermissions() { tabs.append(.permissions) }
    if user.permissionLevel == .admin { tabs.append(.aiChat) }
    return ordered(tabs, for: user)
  }

  /// Primary tab-bar items (≤4) + overflow shown under custom Mais.
  public static func layout(for user: User) -> AppTabLayout {
    let all = available(for: user)
    if all.count <= 4 {
      return AppTabLayout(primary: all, overflow: [])
    }
    let preferredPrimary: [AppTab]
    if user.permissionLevel == .admin {
      preferredPrimary = [.dashboard, .aiChat, .purchases, .inventory]
    } else {
      preferredPrimary = Array(all.prefix(4))
    }
    let primary = preferredPrimary.filter { all.contains($0) }
    let overflow = all.filter { !primary.contains($0) }
    if primary.isEmpty {
      return AppTabLayout(primary: Array(all.prefix(4)), overflow: Array(all.dropFirst(4)))
    }
    return AppTabLayout(primary: primary, overflow: overflow)
  }

  private static func ordered(_ tabs: [AppTab], for user: User) -> [AppTab] {
    guard user.permissionLevel == .admin else { return tabs }
    let priority: [AppTab] = [
      .dashboard, .aiChat, .purchases, .inventory,
      .checklist, .workClock, .activities, .permissions
    ]
    return priority.filter { tabs.contains($0) } + tabs.filter { !priority.contains($0) }
  }
}

public struct AppTabLayout: Sendable {
  public let primary: [AppTab]
  public let overflow: [AppTab]

  public init(primary: [AppTab], overflow: [AppTab]) {
    self.primary = primary
    self.overflow = overflow
  }

  public var tabBarItems: [AppTab] {
    overflow.isEmpty ? primary : primary + [.more]
  }

  public var startTab: AppTab {
    primary.first ?? .checklist
  }

  public func hosts(_ tab: AppTab) -> AppTab {
    if primary.contains(tab) { return tab }
    if overflow.contains(tab) { return .more }
    return tab
  }
}
