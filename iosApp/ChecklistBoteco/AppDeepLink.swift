import Foundation
import Models
import InventoryFeature

/// Deep links: `checklistboteco://tab/inventory` · `checklistboteco://inventory/audit?product=X&date=YYYY-MM-DD`
enum AppDeepLink: Equatable {
  case openTab(AppTab)
  case inventoryAudit(product: String?, date: String?)

  static func parse(_ url: URL) -> AppDeepLink? {
    guard url.scheme?.lowercased() == "checklistboteco" else { return nil }
    let host = url.host?.lowercased() ?? ""
    let path = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
    let query = components?.queryItems ?? []

    switch host {
    case "tab":
      guard let tab = AppTab(rawValue: path) else { return nil }
      return .openTab(tab)
    case "inventory":
      if path == "audit" || path.isEmpty {
        let product = query.first(where: { $0.name == "product" })?.value
        let date = query.first(where: { $0.name == "date" })?.value
        return .inventoryAudit(product: product, date: date)
      }
      return .openTab(.inventory)
    default:
      return nil
    }
  }
}

@MainActor
enum AppDeepLinkHandler {
  static func apply(
    _ link: AppDeepLink,
    user: User,
    selectedTab: inout AppTab,
    tabRouter: TabRouter
  ) {
    switch link {
    case .openTab(let tab):
      guard AppTab.available(for: user).contains(tab) else { return }
      selectedTab = tab
    case .inventoryAudit(let product, let date):
      guard AppTab.available(for: user).contains(.inventory) else { return }
      selectedTab = .inventory
      if let product, !product.isEmpty {
        let snapshot = InventoryAuditItemSnapshot(
          item: InventoryDailyAuditItem(
            product: product,
            status: "—",
            notes: "Aberto via deep link.",
            openingQuantity: 0,
            soldQuantity: 0,
            theoreticalRemaining: 0
          ),
          audit: InventoryDailyAudit(
            date: dateString(from: date),
            location: "Beco da Praia",
            items: [],
            totalOpening: 0,
            totalSold: 0,
            totalRemaining: 0
          )
        )
        tabRouter.reset(.inventory)
        tabRouter.push(.inventoryAuditDetail(snapshot), on: .inventory)
      }
    }
  }

  private static func dateString(from raw: String?) -> String {
    if let raw, !raw.isEmpty { return raw }
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: Date())
  }
}
