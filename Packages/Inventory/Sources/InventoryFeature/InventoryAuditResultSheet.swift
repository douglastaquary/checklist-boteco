import SwiftUI
import DesignSystem

struct InventoryAuditResultSheet: View {
  let result: ApplyDailyAuditResponse
  let onDismiss: () -> Void

  @State private var selectedPage = 0

  private var audit: InventoryDailyAudit? { result.audit }
  private var snapshots: [InventoryAuditItemSnapshot] {
    guard let audit else { return [] }
    return audit.items.map { InventoryAuditItemSnapshot(item: $0, audit: audit) }
  }

  private var pageCount: Int { 1 + snapshots.count }

  var body: some View {
    NavigationStack {
      VStack(spacing: 0) {
        if pageCount > 1 {
          Picker("Página", selection: $selectedPage) {
            Text("Resumo").tag(0)
            ForEach(Array(snapshots.enumerated()), id: \.offset) { index, snapshot in
              Text(snapshot.product.prefix(12)).tag(index + 1)
            }
          }
          .pickerStyle(.segmented)
          .padding(.horizontal)
          .padding(.top, 8)
        }

        TabView(selection: $selectedPage) {
          AuditSummaryPage(result: result, audit: audit)
            .tag(0)
          ForEach(Array(snapshots.enumerated()), id: \.offset) { index, snapshot in
            AuditItemDetailPage(snapshot: snapshot)
              .tag(index + 1)
          }
        }
        .tabViewStyle(.page(indexDisplayMode: pageCount > 1 ? .automatic : .never))

        Text("Página \(selectedPage + 1) de \(pageCount) — deslize para o lado")
          .font(.caption)
          .foregroundColor(.secondary)
          .padding(.vertical, 8)
      }
      .navigationTitle(result.alreadyApplied ? "Auditoria já aplicada" : "Auditoria concluída")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .confirmationAction) {
          Button("Fechar", action: onDismiss)
        }
      }
    }
  }
}

private struct AuditSummaryPage: View {
  let result: ApplyDailyAuditResponse
  let audit: InventoryDailyAudit?

  var body: some View {
    List {
      if result.alreadyApplied {
        Section {
          Text("A auditoria deste dia já havia sido aplicada ao estoque administrativo.")
            .font(.footnote)
            .themedListRowBackground()
        }
      }
      if let audit {
        Section {
          LabeledContent("Data", value: audit.date)
            .themedListRowBackground()
          LabeledContent("Local", value: audit.location)
            .themedListRowBackground()
          LabeledContent("Total abertura", value: formatNumber(audit.totalOpening))
            .themedListRowBackground()
          LabeledContent("Total vendido", value: formatNumber(audit.totalSold))
            .themedListRowBackground()
          LabeledContent("Total saldo", value: formatNumber(audit.totalRemaining))
            .themedListRowBackground()
        } header: {
          themedSectionHeader("Resumo")
        }
        Section {
          ForEach(audit.items.prefix(15)) { item in
            VStack(alignment: .leading, spacing: 4) {
              Text("\(item.status) · \(item.product)")
                .font(.subheadline.weight(.semibold))
              Text("Saldo teórico: \(formatNumber(item.theoreticalRemaining))")
                .font(.caption)
                .foregroundColor(.secondary)
            }
            .themedListRowBackground()
          }
        } header: {
          themedSectionHeader("Itens")
        }
      }
      if !result.balances.isEmpty {
        Section {
          ForEach(result.balances.prefix(15)) { balance in
            LabeledContent(balance.productName, value: formatNumber(balance.quantity))
              .themedListRowBackground()
          }
        } header: {
          themedSectionHeader("Saldo administrativo")
        }
      }
    }
    .themedListStyle()
  }
}

private struct AuditItemDetailPage: View {
  let snapshot: InventoryAuditItemSnapshot

  var body: some View {
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
  }
}

private func formatNumber(_ value: Double) -> String {
  if value.truncatingRemainder(dividingBy: 1) == 0 {
    return String(format: "%.0f", value)
  }
  return String(format: "%.1f", value)
}

#if os(iOS) && DEBUG
struct InventoryAuditResultSheet_Previews: PreviewProvider {
  static var previews: some View {
    InventoryAuditResultSheet(
      result: ApplyDailyAuditResponse(
        audit: InventoryDailyAudit(
          date: "2026-06-20",
          location: "Beco da Praia",
          items: [
            InventoryDailyAuditItem(
              product: "Cerveja Lata",
              status: "OK",
              notes: "Conferido.",
              openingQuantity: 120,
              soldQuantity: 45,
              theoreticalRemaining: 75
            )
          ],
          totalOpening: 500,
          totalSold: 180,
          totalRemaining: 320
        ),
        balances: [],
        alreadyApplied: false
      ),
      onDismiss: {}
    )
    .environmentObject(AppTheme.shared)
  }
}
#endif
