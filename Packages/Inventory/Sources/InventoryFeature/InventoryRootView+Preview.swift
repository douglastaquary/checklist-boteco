#if os(iOS) && DEBUG
import SwiftUI
import Models
import Persistence
import DesignSystem

struct InventoryRootView_Previews: PreviewProvider {
  static var previews: some View {
    Group {
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
      .previewDisplayName("Rascunho vazio")

      NavigationStack {
        InventoryRootView(
          repository: previewRepository,
          inventoryClient: nil,
          token: "preview-token",
          canCreate: true,
          canViewInsights: true,
          canManageAdministrativeStock: true,
          onSelectAuditItem: nil,
          initialBanner: .networkError(
            "Não foi possível enviar a contagem. Verifique sua conexão e tente novamente."
          )
        )
      }
      NavigationStack {
        InventoryDraftFormSheet(
          mode: .edit(
            InventoryCountDraft(
              id: 1,
              name: "Heineken Lata",
              quantity: 24,
              category: .alcoolico,
              volume: 350,
              volumeUnit: "ML",
              salePriceInCents: 1200,
              costPriceInCents: 800,
              storageCondition: .gelado
            )
          ),
          showCostField: true,
          onSave: { _ in },
          onCancel: {}
        )
      }
      .previewDisplayName("Sheet editar")
    }
    .environmentObject(AppTheme.shared)
  }

  private static var previewRepository: ChecklistRepository {
    ChecklistRepository(dbQueue: try! AppDatabase.inMemory())
  }
}
#endif
