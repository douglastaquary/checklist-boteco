#if os(iOS) && DEBUG
import SwiftUI
import Persistence
import DesignSystem

struct InventoryRootView_Previews: PreviewProvider {
  static var previews: some View {
    NavigationStack {
      InventoryRootView(
        repository: previewRepository,
        inventoryClient: nil,
        token: nil,
        canCreate: true,
        canViewInsights: true,
        canManageAdministrativeStock: false
      )
    }
    .environmentObject(AppTheme.shared)
    .previewDisplayName("Rascunho vazio")
  }

  private static var previewRepository: ChecklistRepository {
    ChecklistRepository(dbQueue: try! AppDatabase.inMemory())
  }
}
#endif
