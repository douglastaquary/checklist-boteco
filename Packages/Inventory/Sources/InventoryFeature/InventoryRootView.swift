import SwiftUI
import UIKit
import Models
import Persistence
import Network
import DesignSystem
import AdminFeatures

private enum InventoryAdminDestination: String, Identifiable {
  case activities
  case permissions

  var id: String { rawValue }
}

private enum InventoryDraftSheet: Identifiable {
  case create
  case createPrefill(InventoryCountDraft)
  case edit(InventoryCountDraft)

  var id: String {
    switch self {
    case .create: return "create"
    case .createPrefill: return "create-prefill"
    case .edit(let draft): return "edit-\(draft.id)"
    }
  }

  var formMode: InventoryDraftFormMode {
    switch self {
    case .create: return .create
    case .createPrefill(let draft): return .createPrefill(draft)
    case .edit(let draft): return .edit(draft)
    }
  }
}

private struct InventorySessionEvent: Identifiable {
  enum Kind {
    case system(String)
    case utterance(AttributedString, draftSummary: String?)
  }

  let id: UUID
  let kind: Kind

  init(kind: Kind) {
    self.id = UUID()
    self.kind = kind
  }
}

public struct InventoryRootView: View {
  private let user: User
  private let onLogout: () -> Void
  private let repository: ChecklistRepository
  private let inventoryClient: InventoryClient?
  private let userClient: UserClient?
  private let token: String?
  private let canCreate: Bool
  private let canViewInsights: Bool
  private let canManageAdministrativeStock: Bool

  @Environment(\.colorScheme) private var colorScheme
  @EnvironmentObject private var tabBarVisibility: TabBarVisibilityController
  @StateObject private var voiceController = BecoVoiceCaptureController()
  @State private var drafts: [InventoryCountDraft] = []
  @State private var administrativeMode = false
  @State private var banner: InventoryBanner?
  @State private var draftSheet: InventoryDraftSheet?
  @State private var showSubmitConfirm = false
  @State private var sending = false
  @State private var composerText = ""
  @State private var sessionEvents: [InventorySessionEvent] = []
  @State private var composerScrollTick = 0
  @State private var adminDestination: InventoryAdminDestination?
  @State private var isComposerFocused = false

  @State private var auditSheetStep: AuditSheetStep?
  @State private var showAuditSheet = false
  @State private var showAuditResult = false
  @State private var auditImportBatch: SalesImportBatch?
  @State private var auditImportFileName: String?
  @State private var auditResult: ApplyDailyAuditResponse?

  private var canCreateInMode: Bool {
    administrativeMode ? canManageAdministrativeStock : canCreate
  }

  private var canOpenAudit: Bool {
    canViewInsights || canManageAdministrativeStock
  }

  private var canApplyAudit: Bool {
    canManageAdministrativeStock || canViewInsights
  }

  private var navigationSubtitle: String {
    if administrativeMode { return "Contagem administrativa" }
    if canCreateInMode { return "Abertura" }
    return "Somente insights"
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

  private var palette: BecoChatPalette {
    BecoChatPalette(isDark: colorScheme == .dark)
  }

  private var trimmedComposer: String {
    composerText.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  private var canSendComposer: Bool {
    canCreateInMode && !trimmedComposer.isEmpty && !voiceController.isActive
  }

  private var isComposerEngaged: Bool {
    isComposerFocused || voiceController.isActive
  }

  public init(
    user: User,
    onLogout: @escaping () -> Void = {},
    repository: ChecklistRepository,
    inventoryClient: InventoryClient?,
    userClient: UserClient? = nil,
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
    self.userClient = userClient
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
    userClient: UserClient? = nil,
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
    self.userClient = userClient
    self.token = token
    self.canCreate = canCreate
    self.canViewInsights = canViewInsights
    self.canManageAdministrativeStock = canManageAdministrativeStock
    _banner = State(initialValue: initialBanner)
    _ = onSelectAuditItem
  }
  #endif

  public var body: some View {
    ZStack {
      Color(.systemGroupedBackground).ignoresSafeArea()

      VStack(spacing: 0) {
        sessionScroll
      }
    }
    .navigationTitle("")
    .navigationBarTitleDisplayMode(.inline)
    .safeAreaInset(edge: .top, spacing: 0) {
      BecoUserHeader(
        name: user.name,
        role: user.workSector.displayName,
        date: Date.now.formatted(date: .abbreviated, time: .omitted),
        onLogout: onLogout
      )
      .background(BecoTokens.ColorToken.background)
    }
    .toolbar {
      ToolbarItemGroup(placement: .navigationBarTrailing) {
        if canOpenAudit {
          InventoryAuditMenuButton(onGenerateAudit: openAuditSheet)
        }
        if canCreateInMode {
          InventorySubmitToolbarButton(
            isSending: sending,
            isEnabled: !drafts.isEmpty && !sending && !voiceController.isActive,
            action: { showSubmitConfirm = true }
          )
        }
      }
    }
    .safeAreaInset(edge: .bottom, spacing: 0) {
      bottomChrome
    }
    .task { reload() }
    .onAppear {
      if canManageAdministrativeStock && !canCreate {
        administrativeMode = true
        reload()
      }
      if sessionEvents.isEmpty {
        appendSystemEvent("Modo \(navigationSubtitle). Fale ou digite itens da contagem.")
      }
    }
    .onChange(of: voiceController.errorMessage) { message in
      if let message {
        banner = .validation(message)
      }
    }
    .onChange(of: voiceController.readyText) { text in
      guard let text, !text.isEmpty else { return }
      composerText = text
      _ = voiceController.consumeReadyText()
      isComposerFocused = true
    }
    .onChange(of: isComposerFocused) { focused in
      if focused {
        tabBarVisibility.hide()
      } else if !voiceController.isActive {
        tabBarVisibility.show()
      }
    }
    .onChange(of: voiceController.phase) { phase in
      let active = phase == .recording || phase == .transcribing
      if active {
        tabBarVisibility.hide()
      } else if !isComposerFocused {
        tabBarVisibility.show()
      }
    }
    .onDisappear {
      tabBarVisibility.show()
    }
    .sheet(item: $adminDestination) { destination in
      inventoryAdminCover(destination)
        .becoCodexSheetChrome()
    }
    .sheet(item: $draftSheet) { sheet in
      InventoryDraftFormSheet(
        mode: sheet.formMode,
        showCostField: canManageAdministrativeStock,
        onSave: { saveDraft($0, isEdit: {
          if case .edit = sheet.formMode { return true }
          return false
        }()) },
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

  private var sessionScroll: some View {
    ScrollViewReader { proxy in
      ScrollView {
        LazyVStack(alignment: .leading, spacing: 14) {
          InventoryIntroCard(subtitle: navigationSubtitle, helperText: helperText)

          if let banner {
            Text(banner.message)
              .font(.footnote)
              .foregroundStyle(banner.textColor)
              .padding(12)
              .frame(maxWidth: .infinity, alignment: .leading)
              .background(RoundedRectangle(cornerRadius: 14).fill(Color(.secondarySystemGroupedBackground)))
          }

          ForEach(sessionEvents) { event in
            InventorySessionEventRow(event: event, palette: palette)
              .id(event.id)
          }

          if canCreateInMode {
            InventoryDraftCards(
              drafts: drafts,
              onEdit: { draftSheet = .edit($0) },
              onDelete: deleteDraft
            )
          }

          Color.clear
            .frame(height: 1)
            .id("session-bottom")
        }
        .padding(.horizontal, 18)
        .padding(.top, 12)
        .padding(.bottom, 24)
      }
      .scrollDismissesKeyboard(.interactively)
      .onChange(of: sessionEvents.count) { _ in
        scrollSessionToBottom(proxy)
      }
      .onChange(of: composerScrollTick) { _ in
        scrollSessionToBottom(proxy)
      }
      .onChange(of: composerText) { _ in
        scrollSessionToBottom(proxy)
      }
      .onChange(of: isComposerFocused) { focused in
        if focused {
          scrollSessionToBottom(proxy)
        }
      }
    }
  }

  private func scrollSessionToBottom(_ proxy: ScrollViewProxy) {
    withAnimation(.easeOut(duration: 0.2)) {
      proxy.scrollTo("session-bottom", anchor: .bottom)
    }
  }

  @ViewBuilder
  private var bottomChrome: some View {
    if canCreateInMode {
      VStack(spacing: 0) {
        if voiceController.isActive {
          BecoVoiceFeedbackBar(
            controller: voiceController,
            onCancel: { dismissComposerEngagement() }
          )
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 10)
        } else {
          BecoChatComposer(
            text: $composerText,
            isInputFocused: $isComposerFocused,
            placeholder: "Adicionar item à contagem",
            canSend: canSendComposer,
            isSending: false,
            showsDismissButton: isComposerEngaged,
            palette: palette,
            onSend: { submitComposerText() },
            onDismiss: { dismissComposerEngagement() },
            onInputHeightChange: { _ in
              composerScrollTick &+= 1
            },
            plusContent: { plusMenu },
            micContent: { micButton }
          )
          .padding(.horizontal, 16)
          .padding(.top, 8)
          .padding(.bottom, 10)
        }
      }
      .background(BecoChatBottomFade())
    }
  }

  private var plusMenu: some View {
    Menu {
      if canCreate {
        Button {
          setMode(administrative: false)
        } label: {
          Label("Abertura", systemImage: administrativeMode ? "circle" : "checkmark.circle.fill")
        }
      }
      if canManageAdministrativeStock {
        Button {
          setMode(administrative: true)
        } label: {
          Label("Estoque admin", systemImage: administrativeMode ? "checkmark.circle.fill" : "circle")
        }
      }
      Divider()
      Button {
        draftSheet = .create
      } label: {
        Label("Adicionar item…", systemImage: "plus.circle")
      }
      Divider()
      Button {
        adminDestination = .activities
      } label: {
        Label("Atividades", systemImage: "checklist")
      }
      Button {
        adminDestination = .permissions
      } label: {
        Label("Permissão", systemImage: "person.badge.key")
      }
    } label: {
      Image(systemName: "plus")
        .font(.system(size: 22, weight: .regular))
        .frame(width: 34, height: 34)
        .foregroundStyle(palette.foreground)
        .contentShape(Rectangle())
    }
    .accessibilityLabel("Opções da contagem")
  }

  private var micButton: some View {
    Button {
      voiceController.start()
    } label: {
      Image(systemName: "mic")
        .font(.system(size: 22, weight: .regular))
        .frame(width: 34, height: 34)
        .foregroundStyle(palette.foreground)
        .contentShape(Rectangle())
    }
    .buttonStyle(.plain)
    .accessibilityLabel("Gravar por voz")
  }

  @ViewBuilder
  private func inventoryAdminCover(_ destination: InventoryAdminDestination) -> some View {
    switch destination {
    case .activities:
      ActivitiesManagementView(
        repository: repository,
        embeddedInCodexSheet: true,
        onDismissSheet: { adminDestination = nil }
      )
    case .permissions:
      PermissionManagementView(
        repository: repository,
        userClient: userClient,
        authToken: token,
        embeddedInCodexSheet: true,
        onDismissSheet: { adminDestination = nil }
      )
    }
  }

  private func dismissComposerEngagement() {
    isComposerFocused = false
    UIApplication.shared.sendAction(
      #selector(UIResponder.resignFirstResponder),
      to: nil,
      from: nil,
      for: nil
    )
    if voiceController.isActive {
      voiceController.cancel()
    }
    tabBarVisibility.show()
  }

  private func setMode(administrative: Bool) {
    guard administrativeMode != administrative else { return }
    administrativeMode = administrative
    reload()
    appendSystemEvent("Modo \(navigationSubtitle)")
  }

  private func appendSystemEvent(_ text: String) {
    sessionEvents.append(InventorySessionEvent(kind: .system(text)))
  }

  private func submitComposerText() {
    let text = trimmedComposer
    guard !text.isEmpty else { return }
    composerText = ""
    isComposerFocused = false
    ingestUtterance(text)
  }

  private func ingestUtterance(_ text: String) {
    let parsed = InventoryCountUtteranceParser.parse(text)
    let highlighted = InventoryCountUtteranceParser.highlightedText(
      original: text,
      tokens: parsed.highlightTokens
    )
    if parsed.isCompleteEnough {
      saveDraft(parsed.draft, isEdit: false)
      sessionEvents.append(
        InventorySessionEvent(
          kind: .utterance(highlighted, draftSummary: InventoryDraftFormatting.summary(for: parsed.draft))
        )
      )
    } else {
      sessionEvents.append(
        InventorySessionEvent(kind: .utterance(highlighted, draftSummary: nil))
      )
      draftSheet = .createPrefill(parsed.draft)
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

  private func deleteDraft(_ draft: InventoryCountDraft) {
    try? repository.deleteInventoryDraft(id: draft.id)
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
      sessionEvents.append(
        InventorySessionEvent(kind: .system("Contagem enviada. Sessão limpa para novos itens."))
      )
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

// MARK: - Subviews (MV)

private struct InventoryIntroCard: View {
  let subtitle: String
  let helperText: String

  var body: some View {
    VStack(alignment: .leading, spacing: 6) {
      Text(subtitle)
        .font(.headline.weight(.semibold))
      Text(helperText)
        .font(.footnote)
        .foregroundStyle(.secondary)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(16)
    .background(
      RoundedRectangle(cornerRadius: 18, style: .continuous)
        .fill(Color(.secondarySystemGroupedBackground))
    )
  }
}

private struct InventorySessionEventRow: View {
  let event: InventorySessionEvent
  let palette: BecoChatPalette

  var body: some View {
    switch event.kind {
    case .system(let text):
      BecoChatSystemCaption(text)
    case .utterance(let attributed, let summary):
      VStack(alignment: .trailing, spacing: 8) {
        BecoChatUserTextBubble(attributed, palette: palette)

        if let summary {
          HStack(spacing: 8) {
            Image(systemName: "shippingbox")
              .font(.caption.weight(.semibold))
            Text(summary)
              .font(.caption.weight(.medium))
          }
          .foregroundStyle(palette.foreground)
          .padding(.horizontal, 12)
          .padding(.vertical, 10)
          .background(BecoChatGlassRoundedRectangle(radius: 14, palette: palette))
          .frame(maxWidth: .infinity, alignment: .trailing)
          .padding(.leading, 48)
        }
      }
    }
  }
}

private struct InventoryDraftCards: View {
  let drafts: [InventoryCountDraft]
  let onEdit: (InventoryCountDraft) -> Void
  let onDelete: (InventoryCountDraft) -> Void

  var body: some View {
    VStack(alignment: .leading, spacing: 10) {
      Text("Rascunho")
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(.secondary)

      if drafts.isEmpty {
        Text("Nenhum produto ainda. Use + , digite ou segure o microfone.")
          .font(.footnote)
          .foregroundStyle(.secondary)
      } else {
        ForEach(drafts) { draft in
          HStack {
            Button {
              onEdit(draft)
            } label: {
              VStack(alignment: .leading, spacing: 4) {
                Text(draft.name)
                  .font(.headline)
                  .foregroundStyle(.primary)
                Text(InventoryDraftFormatting.summary(for: draft))
                  .font(.caption)
                  .foregroundStyle(.secondary)
              }
              .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)

            Button(role: .destructive) {
              onDelete(draft)
            } label: {
              Image(systemName: "trash")
            }
            .buttonStyle(.borderless)
          }
          .padding(14)
          .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
              .fill(Color(.secondarySystemGroupedBackground))
          )
        }
      }
    }
  }
}

private struct InventorySubmitToolbarButton: View {
  let isSending: Bool
  let isEnabled: Bool
  let action: () -> Void

  var body: some View {
    Button(action: action) {
      if isSending {
        ProgressView()
      } else {
        Image(systemName: "paperplane")
      }
    }
    .disabled(!isEnabled)
    .accessibilityLabel("Revisar e enviar")
  }
}

private struct InventoryAuditMenuButton: View {
  let onGenerateAudit: () -> Void

  var body: some View {
    Menu {
      Button("Gerar auditoria", action: onGenerateAudit)
    } label: {
      Image(systemName: "ellipsis.circle")
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
