import SwiftUI
import Models
import Persistence
import Network
import DesignSystem

private enum InventoryDraftSheet: Identifiable {
  case create
  case edit(InventoryCountDraft)

  var id: String {
    switch self {
    case .create: return "create"
    case .edit(let draft): return "edit-\(draft.id)"
    }
  }

  var formMode: InventoryDraftFormMode {
    switch self {
    case .create: return .create
    case .edit(let draft): return .edit(draft)
    }
  }
}

public struct InventoryRootView: View {
  private let user: User
  private let onLogout: () -> Void
  private let repository: ChecklistRepository
  private let inventoryClient: InventoryClient?
  private let token: String?
  private let canCreate: Bool
  private let canViewInsights: Bool
  private let canManageAdministrativeStock: Bool

  @State private var drafts: [InventoryCountDraft] = []
  @State private var administrativeMode = false
  @State private var banner: InventoryBanner?
  @State private var draftSheet: InventoryDraftSheet?
  @State private var showSubmitConfirm = false
  @State private var sending = false

  @State private var auditSheetStep: AuditSheetStep?
  @State private var showAuditSheet = false
  @State private var showAuditResult = false
  @State private var auditImportBatch: SalesImportBatch?
  @State private var auditImportFileName: String?
  @State private var auditResult: ApplyDailyAuditResponse?

  public init(
    user: User,
    onLogout: @escaping () -> Void = {},
    repository: ChecklistRepository,
    inventoryClient: InventoryClient?,
    token: String?,
    canCreate: Bool,
    canViewInsights: Bool = false,
    canManageAdministrativeStock: Bool = false,
    onSelectAuditItem: ((InventoryAuditItemSnapshot) -> Void)? = nil
  ) {
    self.user = user
    self.onLogout = onLogout
    self.repository = repository
    self.inventoryClient = inventoryClient
    self.token = token
    self.canCreate = canCreate
    self.canViewInsights = canViewInsights
    self.canManageAdministrativeStock = canManageAdministrativeStock
    _banner = State(initialValue: nil)
    _ = onSelectAuditItem
  }

  #if DEBUG
  init(
    user: User,
    onLogout: @escaping () -> Void = {},
    repository: ChecklistRepository,
    inventoryClient: InventoryClient?,
    token: String?,
    canCreate: Bool,
    canViewInsights: Bool,
    canManageAdministrativeStock: Bool,
    onSelectAuditItem: ((InventoryAuditItemSnapshot) -> Void)?,
    initialBanner: InventoryBanner
  ) {
    self.user = user
    self.onLogout = onLogout
    self.repository = repository
    self.inventoryClient = inventoryClient
    self.token = token
    self.canCreate = canCreate
    self.canViewInsights = canViewInsights
    self.canManageAdministrativeStock = canManageAdministrativeStock
    _banner = State(initialValue: initialBanner)
    _ = onSelectAuditItem
  }
  #endif

  private var canCreateInMode: Bool {
    administrativeMode ? canManageAdministrativeStock : canCreate
  }

  private var canOpenAudit: Bool {
    canViewInsights || canManageAdministrativeStock
  }

  private var canApplyAudit: Bool {
    canManageAdministrativeStock || canViewInsights
  }

  private var navigationTitle: String {
    administrativeMode ? "Estoque admin" : "Contagem"
  }

  private var navigationSubtitle: String {
    if administrativeMode { return "Contagem administrativa" }
    if canCreateInMode { return "Abertura" }
    return "Somente insights"
  }

  public var body: some View {
    List {
      Section {
        BecoUserHeader(
          name: user.name,
          role: user.workSector.displayName,
          date: Date.now.formatted(date: .abbreviated, time: .omitted),
          onLogout: onLogout
        )
        .listRowInsets(EdgeInsets())
        .listRowSeparator(.hidden)
        .themedListRowBackground()
      }

      if canManageAdministrativeStock && canCreate {
        Section {
          BecoSegmentedFilter(
            options: [(false, "Abertura", nil), (true, "Estoque admin", nil)],
            selected: $administrativeMode
          )
          .onChange(of: administrativeMode) { _ in reload() }
          .themedListRowBackground()
        }
      }

      Section {
        Text(navigationSubtitle)
          .font(.subheadline.weight(.semibold))
          .themedListRowBackground()
        Text(helperText)
          .font(.footnote)
          .foregroundColor(.secondary)
          .themedListRowBackground()
      }

      if canCreateInMode {
        InventoryDraftSection(
          drafts: drafts,
          onEdit: { draftSheet = .edit($0) },
          onDelete: deleteDrafts
        )
      }

      if let banner {
        Section {
          Text(banner.message)
            .foregroundColor(banner.textColor)
            .themedListRowBackground()
        }
      }
    }
    .themedListStyle()
    .navigationTitle("")
    .navigationBarTitleDisplayMode(.inline)
    .toolbar {
      if canOpenAudit {
        ToolbarItem(placement: .primaryAction) {
          Menu {
            Button("Gerar auditoria") {
              openAuditSheet()
            }
          } label: {
            Image(systemName: "ellipsis.circle")
          }
        }
      }
    }
    .safeAreaInset(edge: .bottom, spacing: 0) {
      if canCreateInMode {
        bottomBar
      }
    }
    .overlay(alignment: .bottomTrailing) {
      if canCreateInMode {
        Button {
          draftSheet = .create
        } label: {
          Label("Adicionar", systemImage: "plus")
        }
        .buttonStyle(.borderedProminent)
        .padding(.trailing, 16)
        .padding(.bottom, canCreateInMode ? 88 : 16)
      }
    }
    .task { reload() }
    .onAppear {
      if canManageAdministrativeStock && !canCreate {
        administrativeMode = true
        reload()
      }
    }
    .sheet(item: $draftSheet) { sheet in
      InventoryDraftFormSheet(
        mode: sheet.formMode,
        showCostField: canManageAdministrativeStock,
        onSave: { saveDraft($0, isEdit: sheet.formMode != .create) },
        onCancel: { draftSheet = nil }
      )
      .environmentObject(AppTheme.shared)
    }
    .sheet(isPresented: $showAuditSheet) {
      if let auditSheetStep {
        InventoryAuditSheet(
          step: auditSheetStep,
          importBatch: auditImportBatch,
          importFileName: auditImportFileName,
          canApplyAudit: canApplyAudit,
          onDismiss: closeAuditSheet,
          onConfirmAudit: { Task { await confirmAudit() } },
          onUploadCsv: { fileName, content in
            Task { await uploadSalesCsv(fileName: fileName, content: content) }
          }
        )
        .environmentObject(AppTheme.shared)
        .interactiveDismissDisabled(auditSheetStep == .processing)
      }
    }
    .sheet(isPresented: $showAuditResult) {
      if let auditResult {
        InventoryAuditResultSheet(result: auditResult, onDismiss: closeAuditResult)
          .environmentObject(AppTheme.shared)
      }
    }
    .confirmationDialog(
      administrativeMode ? "Confirmar estoque" : "Confirmar contagem",
      isPresented: $showSubmitConfirm,
      titleVisibility: .visible
    ) {
      Button("Sim, enviar") {
        Task { await submit() }
      }
      Button("Revisar", role: .cancel) {}
    } message: {
      Text(
        administrativeMode
          ? "Confirma o envio da contagem administrativa? O saldo acumulado será atualizado."
          : "Os valores estão corretos? Após o envio, a contagem não poderá ser editada."
      )
    }
  }

  private var helperText: String {
    if canCreateInMode && administrativeMode {
      return "Soma ao saldo acumulado de estoque. Após a auditoria diária, as vendas são abatidas."
    }
    if canCreateInMode {
      return "Itens ficam no aparelho até o envio em lote."
    }
    return "Acesso somente aos insights e à auditoria."
  }

  private var bottomBar: some View {
    VStack(spacing: 0) {
      Divider()
      Button(sending ? "Enviando…" : "Revisar e enviar") {
        showSubmitConfirm = true
      }
      .buttonStyle(PrimaryButtonStyle())
      .disabled(drafts.isEmpty || sending)
      .padding(.horizontal, 16)
      .padding(.vertical, 12)
      .background(.bar)
    }
  }

  private func reload() {
    drafts = (try? repository.inventoryDrafts(administrative: administrativeMode)) ?? []
  }

  private func saveDraft(_ draft: InventoryCountDraft, isEdit: Bool) {
    do {
      if isEdit {
        try repository.updateInventoryDraft(draft)
      } else {
        try repository.addInventoryDraft(draft, administrative: administrativeMode)
      }
      banner = nil
      draftSheet = nil
      reload()
    } catch {
      banner = .validation(error.localizedDescription)
    }
  }

  private func deleteDrafts(at offsets: IndexSet) {
    for index in offsets {
      try? repository.deleteInventoryDraft(id: drafts[index].id)
    }
    reload()
  }

  @MainActor
  private func requireInventoryRemoteAccess() throws -> (InventoryClient, String) {
    guard let inventoryClient else {
      throw RemoteSessionRequiredError(message: "Faça login novamente")
    }
    let validToken = try AuthSessionGuard.requireRemoteToken(apiConfigured: true, token: token)
    return (inventoryClient, validToken)
  }

  private func submit() async {
    let inventoryClient: InventoryClient
    let token: String
    do {
      (inventoryClient, token) = try await requireInventoryRemoteAccess()
    } catch is RemoteSessionRequiredError {
      return
    } catch {
      banner = .info("Faça login novamente")
      return
    }
    sending = true
    defer { sending = false }
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

  private func openAuditSheet() {
    auditSheetStep = .checkingSales
    auditImportBatch = nil
    auditImportFileName = nil
    showAuditResult = false
    showAuditSheet = true
    Task { await checkSalesForAudit() }
  }

  private func closeAuditSheet() {
    showAuditSheet = false
    auditSheetStep = nil
    auditImportBatch = nil
    auditImportFileName = nil
  }

  private func closeAuditResult() {
    showAuditResult = false
    auditResult = nil
    auditSheetStep = nil
  }

  private func checkSalesForAudit() async {
    let inventoryClient: InventoryClient
    let token: String
    do {
      (inventoryClient, token) = try await requireInventoryRemoteAccess()
    } catch is RemoteSessionRequiredError {
      return
    } catch {
      await MainActor.run {
        auditSheetStep = .error("Faça login novamente")
      }
      return
    }
    do {
      let audit = try await inventoryClient.dailyAudit(token: token, date: InventoryDate.today)
      await MainActor.run {
        auditSheetStep = InventoryAuditLogic.shouldSkipCsvUpload(audit)
          ? .readyToConfirm(audit)
          : .uploadCsv
        banner = nil
      }
    } catch {
      await MainActor.run {
        auditSheetStep = .error(AppErrorMapper.toUserMessage(error))
        banner = .networkError(AppErrorMapper.toUserMessage(error))
      }
    }
  }

  private func uploadSalesCsv(fileName: String, content: String) async {
    let inventoryClient: InventoryClient
    let token: String
    do {
      (inventoryClient, token) = try await requireInventoryRemoteAccess()
    } catch is RemoteSessionRequiredError {
      return
    } catch {
      await MainActor.run {
        auditSheetStep = .error("Faça login novamente")
      }
      return
    }
    await MainActor.run {
      auditSheetStep = .checkingSales
      auditImportFileName = fileName
      auditImportBatch = nil
    }
    do {
      let preview = try await inventoryClient.salesImportPreview(
        token: token,
        fileName: fileName,
        csv: content
      )
      if let firstError = preview.errors.first {
        throw NSError(
          domain: "InventoryAudit",
          code: 1,
          userInfo: [NSLocalizedDescriptionKey: firstError.message]
        )
      }
      let mapping = preview.suggestedMapping.isEmpty ? preview.mapping : preview.suggestedMapping
      let committed = try await inventoryClient.salesImportCommit(
        token: token,
        batchId: preview.id,
        mapping: mapping
      )
      if let firstError = committed.errors.first {
        throw NSError(
          domain: "InventoryAudit",
          code: 2,
          userInfo: [NSLocalizedDescriptionKey: firstError.message]
        )
      }
      let audit = try await inventoryClient.dailyAudit(token: token, date: InventoryDate.today)
      await MainActor.run {
        auditImportBatch = committed
        auditSheetStep = .readyToConfirm(audit)
      }
    } catch {
      await MainActor.run {
        auditSheetStep = .uploadCsv
        banner = .networkError(AppErrorMapper.toUserMessage(error))
      }
    }
  }

  private func confirmAudit() async {
    let inventoryClient: InventoryClient
    let token: String
    do {
      (inventoryClient, token) = try await requireInventoryRemoteAccess()
    } catch is RemoteSessionRequiredError {
      return
    } catch {
      await MainActor.run {
        auditSheetStep = .error("Faça login novamente")
      }
      return
    }
    await MainActor.run {
      auditSheetStep = .processing
    }
    do {
      let response = try await inventoryClient.applyDailyAudit(token: token, date: InventoryDate.today)
      await MainActor.run {
        auditResult = response
        showAuditSheet = false
        showAuditResult = true
        banner = .success(
          response.alreadyApplied
            ? "Auditoria já havia sido aplicada ao estoque administrativo."
            : "Vendas abatidas do estoque administrativo conforme planilha."
        )
      }
    } catch {
      await MainActor.run {
        auditSheetStep = .error(AppErrorMapper.toUserMessage(error))
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

private struct InventoryDraftSection: View {
  let drafts: [InventoryCountDraft]
  let onEdit: (InventoryCountDraft) -> Void
  let onDelete: (IndexSet) -> Void

  var body: some View {
    Section {
      if drafts.isEmpty {
        Text("Nenhum produto adicionado. Toque em Adicionar para começar.")
          .font(.footnote)
          .foregroundColor(.secondary)
          .themedListRowBackground()
      } else {
        ForEach(drafts) { draft in
          Button {
            onEdit(draft)
          } label: {
            VStack(alignment: .leading, spacing: 4) {
              Text(draft.name)
                .font(.headline)
                .foregroundColor(.primary)
              Text(InventoryDraftFormatting.summary(for: draft))
                .font(.caption)
                .foregroundColor(.secondary)
            }
          }
          .buttonStyle(.plain)
          .themedListRowBackground()
        }
        .onDelete(perform: onDelete)
      }
    } header: {
      themedSectionHeader("Rascunho")
    }
  }
}
