import SwiftUI
import DesignSystem

public struct InventoryAuditDetailView: View {
  private let snapshot: InventoryAuditItemSnapshot

  public init(snapshot: InventoryAuditItemSnapshot) {
    self.snapshot = snapshot
  }

  public var body: some View {
    List {
      Section {
        Text(snapshot.product)
          .font(.title2.bold())
          .themedListRowBackground()
      } header: {
        themedSectionHeader("Produto")
      }

      Section {
        LabeledContent("Data", value: snapshot.auditDate)
          .themedListRowBackground()
        LabeledContent("Local", value: snapshot.location)
          .themedListRowBackground()
        LabeledContent("Status", value: snapshot.status)
          .themedListRowBackground()
      } header: {
        themedSectionHeader("Auditoria")
      }

      Section {
        LabeledContent("Abertura", value: formatNumber(snapshot.openingQuantity))
          .themedListRowBackground()
        LabeledContent("Vendido", value: formatNumber(snapshot.soldQuantity))
          .themedListRowBackground()
        LabeledContent("Saldo teórico", value: formatNumber(snapshot.theoreticalRemaining))
          .themedListRowBackground()
      } header: {
        themedSectionHeader("Quantidades")
      }

      Section {
        LabeledContent("Total abertura", value: formatNumber(snapshot.totalOpening))
          .themedListRowBackground()
        LabeledContent("Total vendido", value: formatNumber(snapshot.totalSold))
          .themedListRowBackground()
        LabeledContent("Total saldo", value: formatNumber(snapshot.totalRemaining))
          .themedListRowBackground()
      } header: {
        themedSectionHeader("Totais do dia")
      }

      if !snapshot.notes.isEmpty {
        Section {
          Text(snapshot.notes)
            .font(.footnote)
            .themedListRowBackground()
        } header: {
          themedSectionHeader("Observações")
        }
      }
    }
    .themedListStyle()
    .navigationTitle("Detalhe")
    .navigationBarTitleDisplayMode(.inline)
    .becoBackButton()
  }

  private func formatNumber(_ value: Double) -> String {
    String(format: "%.1f", value)
  }
}

#if os(iOS) && DEBUG
struct InventoryAuditDetailView_Previews: PreviewProvider {
  static var previews: some View {
    NavigationStack {
      InventoryAuditDetailView(snapshot: InventoryAuditDetailView_Previews.sample)
    }
    .environmentObject(AppTheme.shared)
  }

  fileprivate static var sample: InventoryAuditItemSnapshot {
    InventoryAuditItemSnapshot(
      item: InventoryDailyAuditItem(
        product: "Cerveja Lata",
        status: "OK",
        notes: "Conferido no turno da tarde.",
        openingQuantity: 120,
        soldQuantity: 45,
        theoreticalRemaining: 75
      ),
      audit: InventoryDailyAudit(
        date: "2026-06-21",
        location: "Beco da Praia",
        items: [],
        totalOpening: 500,
        totalSold: 180,
        totalRemaining: 320
      )
    )
  }
}
#endif
