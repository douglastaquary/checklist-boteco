#if os(iOS) && DEBUG
import SwiftUI
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
      .previewDisplayName("Erro de rede")
    }
    .environmentObject(AppTheme.shared)
  }

  private static var previewRepository: ChecklistRepository {
    ChecklistRepository(dbQueue: try! AppDatabase.inMemory())
  }
}
#endif
