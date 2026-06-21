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
  private let onSelectAuditItem: ((InventoryAuditItemSnapshot) -> Void)?

  @State private var drafts: [InventoryCountDraft] = []
  @State private var administrativeMode = false
  @State private var banner: InventoryBanner?
  @State private var name = ""
  @State private var quantity = ""
  @State private var audit: InventoryDailyAudit?
  @State private var loadingAudit = false

  public init(
    repository: ChecklistRepository,
    inventoryClient: InventoryClient?,
    token: String?,
    canCreate: Bool,
    canViewInsights: Bool = false,
    canManageAdministrativeStock: Bool = false,
    onSelectAuditItem: ((InventoryAuditItemSnapshot) -> Void)? = nil
  ) {
    self.repository = repository
    self.inventoryClient = inventoryClient
    self.token = token
    self.canCreate = canCreate
    self.canViewInsights = canViewInsights
    self.canManageAdministrativeStock = canManageAdministrativeStock
    self.onSelectAuditItem = onSelectAuditItem
    _banner = State(initialValue: nil)
  }

  #if DEBUG
  init(
    repository: ChecklistRepository,
    inventoryClient: InventoryClient?,
    token: String?,
    canCreate: Bool,
    canViewInsights: Bool,
    canManageAdministrativeStock: Bool,
    onSelectAuditItem: ((InventoryAuditItemSnapshot) -> Void)?,
    initialBanner: InventoryBanner
  ) {
    self.repository = repository
    self.inventoryClient = inventoryClient
    self.token = token
    self.canCreate = canCreate
    self.canViewInsights = canViewInsights
    self.canManageAdministrativeStock = canManageAdministrativeStock
    self.onSelectAuditItem = onSelectAuditItem
    _banner = State(initialValue: initialBanner)
  }
  #endif

  public var body: some View {
    List {
      if canManageAdministrativeStock {
        Section {
          Toggle("Contagem administrativa", isOn: $administrativeMode)
            .onChange(of: administrativeMode) { _ in reload() }
        }
      }
      if canCreate {
        InventoryAddItemSection(name: $name, quantity: $quantity, onAdd: addDraft)
      }
      InventoryDraftSection(drafts: drafts, onDelete: deleteDrafts)
      if canCreate {
        Section {
          Button("Enviar contagem") {
            Task { await submit() }
          }
          .buttonStyle(PrimaryButtonStyle())
          .disabled(drafts.isEmpty)
        }
      }
      if canViewInsights || canManageAdministrativeStock {
        InventoryAuditSection(
          audit: audit,
          loading: loadingAudit,
          canApply: canManageAdministrativeStock,
          onLoad: { Task { await loadAudit() } },
          onApply: { Task { await applyAudit() } },
          onSelectItem: onSelectAuditItem
        )
      }
      if let banner {
        Section {
          Text(banner.message)
            .foregroundColor(banner.textColor)
        }
      }
    }
    .navigationTitle("Contagem")
    .themedListStyle()
    .task { reload() }
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
      banner = .validation(errors.joined(separator: "\n"))
      return
    }
    try? repository.addInventoryDraft(draft, administrative: administrativeMode)
    name = ""
    quantity = ""
    banner = nil
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
      banner = .info("Faça login novamente")
      return
    }
    let date = InventoryDate.today
    do {
      try await inventoryClient.submitCount(
        token: token,
        date: date,
        items: drafts,
        administrative: administrativeMode
      )
      try repository.clearInventoryDrafts(administrative: administrativeMode)
      banner = .success(
        administrativeMode
          ? "Contagem administrativa enviada e saldo atualizado."
          : "Contagem enviada e bloqueada para edição."
      )
      reload()
    } catch {
      await MainActor.run {
        banner = .networkError(AppErrorMapper.toUserMessage(error))
      }
    }
  }

  private func loadAudit() async {
    guard let inventoryClient, let token else { return }
    loadingAudit = true
    defer { loadingAudit = false }
    do {
      audit = try await inventoryClient.dailyAudit(token: token, date: InventoryDate.today)
      banner = nil
    } catch {
      await MainActor.run {
        banner = .networkError(AppErrorMapper.toUserMessage(error))
      }
    }
  }

  private func applyAudit() async {
    guard let inventoryClient, let token else { return }
    loadingAudit = true
    defer { loadingAudit = false }
    do {
      let response = try await inventoryClient.applyDailyAudit(token: token, date: InventoryDate.today)
      audit = response.audit
      banner = .success(
        response.alreadyApplied
          ? "Auditoria já havia sido aplicada ao estoque administrativo."
          : "Vendas abatidas do estoque administrativo conforme planilha."
      )
    } catch {
      await MainActor.run {
        banner = .networkError(AppErrorMapper.toUserMessage(error))
      }
    }
  }
}

enum InventoryBanner: Equatable {
  case info(String)
  case success(String)
  case validation(String)
  case networkError(String)

  var message: String {
    switch self {
    case .info(let text), .success(let text), .validation(let text), .networkError(let text):
      return text
    }
  }

  var textColor: Color {
    switch self {
    case .success: return .green
    case .validation, .networkError: return .red
    case .info: return .primary
    }
  }
}

private enum InventoryDate {
  static var today: String {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: Date())
  }
}

private struct InventoryAddItemSection: View {
  @Binding var name: String
  @Binding var quantity: String
  let onAdd: () -> Void

  var body: some View {
    Section {
      TextField("Produto", text: $name)
      TextField("Quantidade", text: $quantity).keyboardType(.decimalPad)
      Button("Incluir no rascunho", action: onAdd)
    } header: {
      themedSectionHeader("Adicionar item")
    }
  }
}

private struct InventoryDraftSection: View {
  let drafts: [InventoryCountDraft]
  let onDelete: (IndexSet) -> Void

  var body: some View {
    Section {
      ForEach(drafts) { draft in
        Text("\(draft.name) — \(draft.quantity, format: .number)")
      }
      .onDelete(perform: onDelete)
    } header: {
      themedSectionHeader("Rascunho")
    }
  }
}

private struct InventoryAuditSection: View {
  let audit: InventoryDailyAudit?
  let loading: Bool
  let canApply: Bool
  let onLoad: () -> Void
  let onApply: () -> Void
  let onSelectItem: ((InventoryAuditItemSnapshot) -> Void)?

  var body: some View {
    Section {
      Button(loading ? "Carregando..." : "Consultar auditoria", action: onLoad)
        .disabled(loading)
      if let audit {
        ForEach(audit.items) { item in
          if let onSelectItem {
            Button {
              onSelectItem(InventoryAuditItemSnapshot(item: item, audit: audit))
            } label: {
              InventoryAuditRow(item: item)
            }
            .buttonStyle(.plain)
          } else {
            InventoryAuditRow(item: item)
          }
        }
      }
      if canApply {
        Button("Aplicar vendas ao estoque", action: onApply)
          .buttonStyle(PrimaryButtonStyle())
          .disabled(loading)
      }
    } header: {
      themedSectionHeader("Auditoria diária")
    }
  }
}

private struct InventoryAuditRow: View {
  let item: InventoryDailyAuditItem

  var body: some View {
    VStack(alignment: .leading, spacing: 4) {
      Text(item.product).font(.headline)
      Text("Abertura: \(item.openingQuantity, format: .number) · Vendido: \(item.soldQuantity, format: .number)")
        .font(.caption)
      Text("Saldo teórico: \(item.theoreticalRemaining, format: .number)")
        .font(.caption)
      if !item.notes.isEmpty {
        Text(item.notes).font(.caption2).foregroundColor(.secondary)
      }
    }
  }
}
