import SwiftUI
import UniformTypeIdentifiers
import DesignSystem

enum AuditSheetStep: Equatable {
  case checkingSales
  case uploadCsv
  case readyToConfirm(InventoryDailyAudit?)
  case processing
  case error(String)
}

struct InventoryAuditSheet: View {
  let step: AuditSheetStep
  let importBatch: SalesImportBatch?
  let importFileName: String?
  let canApplyAudit: Bool
  let onDismiss: () -> Void
  let onConfirmAudit: () -> Void
  let onUploadCsv: (String, String) -> Void

  @State private var showFileImporter = false

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 16) {
          switch step {
          case .checkingSales:
            HStack(spacing: 12) {
              ProgressView()
              Text(importFileName == nil ? "Verificando vendas do dia…" : "Importando vendas…")
            }
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.vertical, 24)

          case .uploadCsv:
            Text("Não há vendas importadas para hoje. Selecione a planilha CSV para continuar.")
              .font(.body)
              .foregroundColor(.secondary)
            if let importFileName {
              Text("Arquivo: \(importFileName)")
                .font(.subheadline.weight(.semibold))
            }
            Button("Selecionar CSV") {
              showFileImporter = true
            }
            .buttonStyle(.bordered)
            if let importBatch {
              Text("Prévia: \(importBatch.totalRows) linhas · \(importBatch.importedRows) importadas")
                .font(.subheadline)
              ForEach(Array(importBatch.sampleRows.prefix(3).enumerated()), id: \.offset) { _, row in
                Text(row.values.joined(separator: " · "))
                  .font(.caption)
                  .foregroundColor(.secondary)
              }
              ForEach(Array(importBatch.errors.prefix(5).enumerated()), id: \.offset) { _, error in
                Text("Linha \(error.row): \(error.message)")
                  .font(.caption)
                  .foregroundColor(.red)
              }
            }

          case .readyToConfirm(let audit):
            Text("Revise os totais antes de confirmar a auditoria.")
              .font(.body)
              .foregroundColor(.secondary)
            if let audit {
              Text("Contado \(audit.totalOpening, format: .number)")
                .font(.headline)
              Text("Vendido \(audit.totalSold, format: .number)")
                .font(.headline)
              Text("Saldo \(audit.totalRemaining, format: .number)")
                .font(.headline)
            }
            if canApplyAudit {
              Button("Confirmar auditoria", action: onConfirmAudit)
                .buttonStyle(PrimaryButtonStyle())
            } else {
              Text("Você tem acesso somente leitura aos insights de auditoria.")
                .font(.footnote)
                .foregroundColor(.secondary)
            }

          case .processing:
            ProgressView("Processando auditoria…")
              .frame(maxWidth: .infinity, alignment: .center)
              .padding(.vertical, 24)

          case .error(let message):
            Text(message)
              .foregroundColor(.red)
            Button("Fechar", action: onDismiss)
              .buttonStyle(.bordered)
          }
        }
        .padding(20)
      }
      .navigationTitle("Auditoria diária")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("Fechar", action: onDismiss)
        }
      }
      .fileImporter(
        isPresented: $showFileImporter,
        allowedContentTypes: [.commaSeparatedText, .plainText, .data, .text],
        allowsMultipleSelection: false
      ) { result in
        switch result {
        case .success(let urls):
          guard let url = urls.first else { return }
          guard let content = readCsvContent(from: url) else { return }
          let fileName = url.lastPathComponent.isEmpty ? "import.csv" : url.lastPathComponent
          onUploadCsv(fileName, content)
        case .failure:
          break
        }
      }
    }
  }

  private func readCsvContent(from url: URL) -> String? {
    let access = url.startAccessingSecurityScopedResource()
    defer {
      if access { url.stopAccessingSecurityScopedResource() }
    }
    return try? String(contentsOf: url, encoding: .utf8)
  }
}

#if os(iOS) && DEBUG
struct InventoryAuditSheet_Previews: PreviewProvider {
  static var previews: some View {
    InventoryAuditSheet(
      step: .readyToConfirm(
        InventoryDailyAudit(
          date: "2026-06-20",
          location: "Beco da Praia",
          items: [],
          totalOpening: 120,
          totalSold: 45,
          totalRemaining: 75
        )
      ),
      importBatch: nil,
      importFileName: nil,
      canApplyAudit: true,
      onDismiss: {},
      onConfirmAudit: {},
      onUploadCsv: { _, _ in }
    )
    .environmentObject(AppTheme.shared)
  }
}
#endif
