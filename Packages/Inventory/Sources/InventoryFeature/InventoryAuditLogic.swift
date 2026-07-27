import Foundation

enum InventoryAuditLogic {
  static func shouldSkipCsvUpload(_ audit: InventoryDailyAudit) -> Bool {
    audit.totalSold > 0 || audit.items.contains { $0.soldQuantity > 0 }
  }
}
