import SwiftUI
import Models
import Persistence
import Network
import DesignSystem
public struct InventoryRootView: View {
  private let repository: ChecklistRepository
  private let inventoryClient: InventoryClient?
  private let token: String?
  private let canCreate: Bool
  private let canViewInsights: Bool
  private let canManageAdministrativeStock: Bool

  @State private var drafts: [InventoryCountDraft] = []
  @State private var administrativeMode = false
  @State private var message: String?
  @State private var name = ""
  @State private var quantity = ""
  @State private var audit: InventoryDailyAudit?
  @State private var auditAlreadyApplied = false
  @State private var loadingAudit = false

  public init(
    repository: ChecklistRepository,
    inventoryClient: InventoryClient?,
    token: String?,
    canCreate: Bool,
    canViewInsights: Bool = false,
    canManageAdministrativeStock: Bool = false
  ) {
    self.repository = repository
    self.inventoryClient = inventoryClient
    self.token = token
    self.canCreate = canCreate
    self.canViewInsights = canViewInsights
    self.canManageAdministrativeStock = canManageAdministrativeStock
  }

  public var body: some View {
    NavigationStack {
      List {
        if canManageAdministrativeStock {
          Section {
            Toggle("Contagem administrativa", isOn: $administrativeMode)
              .onChange(of: administrativeMode) { _ in reload() }
          }
        }
        if canCreate {
          Section("Adicionar item") {
            TextField("Produto", text: $name)
            TextField("Quantidade", text: $quantity).keyboardType(.decimalPad)
            Button("Incluir no rascunho") { addDraft() }
          }
        }
        Section("Rascunho") {
          ForEach(drafts) { draft in
            Text("\(draft.name) — \(draft.quantity, format: .number)")
          }
          .onDelete(perform: deleteDrafts)
        }
        if canCreate {
          Button("Enviar contagem") {
            Task { await submit() }
          }
          .buttonStyle(PrimaryButtonStyle())
          .disabled(drafts.isEmpty)
        }
        if canViewInsights || canManageAdministrativeStock {
          Section("Auditoria diária") {
            Button(loadingAudit ? "Carregando..." : "Consultar auditoria") {
              Task { await loadAudit() }
            }
            .disabled(loadingAudit)
            if let audit {
              ForEach(audit.items) { item in
                VStack(alignment: .leading, spacing: 4) {
                  Text(item.product).font(.headline)
                  Text("Abertura: \(item.openingQuantity, format: .number) · Vendido: \(item.soldQuantity, format: .number)")
                    .font(.caption)
                  Text("Saldo teórico: \(item.theoreticalRemaining, format: .number)")
                    .font(.caption)
                  if !item.notes.isEmpty {
                    Text(item.notes).font(.caption2).foregroundStyle(.secondary)
                  }
                }
              }
            }
            if canManageAdministrativeStock {
              Button("Aplicar vendas ao estoque") {
                Task { await applyAudit() }
              }
              .buttonStyle(PrimaryButtonStyle())
              .disabled(loadingAudit)
            }
          }
        }
        if let message {
          Section { Text(message) }
        }
      }
      .navigationTitle("Contagem")
      .task { reload() }
    }
  }

  private func reload() {
    drafts = (try? repository.inventoryDrafts(administrative: administrativeMode)) ?? []
  }

  private func addDraft() {
    guard let qty = Double(quantity.replacingOccurrences(of: ",", with: ".")) else { return }
    let draft = InventoryCountDraft(
      name: name,
      quantity: qty,
      category: .naoAlcoolico,
      volume: 350,
      volumeUnit: "ML",
      salePriceInCents: 0,
      storageCondition: .gelado
    )
    let errors = InventoryCountValidator.validate(draft)
    guard errors.isEmpty else {
      message = errors.joined(separator: "\n")
      return
    }
    try? repository.addInventoryDraft(draft, administrative: administrativeMode)
    name = ""
    quantity = ""
    reload()
  }

  private func deleteDrafts(at offsets: IndexSet) {
    for index in offsets {
      try? repository.deleteInventoryDraft(id: drafts[index].id)
    }
    reload()
  }

  private func submit() async {
    guard let inventoryClient, let token else {
      message = "Faça login novamente"
      return
    }
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    let date = formatter.string(from: Date())
    do {
      try await inventoryClient.submitCount(
        token: token,
        date: date,
        items: drafts,
        administrative: administrativeMode
      )
      try repository.clearInventoryDrafts(administrative: administrativeMode)
      message = administrativeMode
        ? "Contagem administrativa enviada e saldo atualizado."
        : "Contagem enviada e bloqueada para edição."
      reload()
    } catch {
      await MainActor.run {
        NetworkFeedback.shared.showError(AppErrorMapper.toUserMessage(error))
      }
    }
  }

  private func loadAudit() async {
    guard let inventoryClient, let token else { return }
    loadingAudit = true
    defer { loadingAudit = false }
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    let date = formatter.string(from: Date())
    do {
      audit = try await inventoryClient.dailyAudit(token: token, date: date)
      message = nil
    } catch {
      await MainActor.run {
        NetworkFeedback.shared.showError(AppErrorMapper.toUserMessage(error))
      }
    }
  }

  private func applyAudit() async {
    guard let inventoryClient, let token else { return }
    loadingAudit = true
    defer { loadingAudit = false }
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    let date = formatter.string(from: Date())
    do {
      let response = try await inventoryClient.applyDailyAudit(token: token, date: date)
      audit = response.audit
      auditAlreadyApplied = response.alreadyApplied
      message = response.alreadyApplied
        ? "Auditoria já havia sido aplicada ao estoque administrativo."
        : "Vendas abatidas do estoque administrativo conforme planilha."
    } catch {
      await MainActor.run {
        NetworkFeedback.shared.showError(AppErrorMapper.toUserMessage(error))
      }
    }
  }
}
