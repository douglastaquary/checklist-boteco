import SwiftUI
import UIKit
import Network
import DesignSystem
import Persistence
import AdminFeatures

private enum AIChatAdminDestination: String, Identifiable {
  case activities
  case permissions

  var id: String { rawValue }
}

public struct AIChatView: View {
  @Environment(\.colorScheme) private var colorScheme
  @EnvironmentObject private var tabBarVisibility: TabBarVisibilityController

  private let client: AIChatClient?
  private let token: String?
  private let repository: ChecklistRepository?
  private let userClient: UserClient?

  @StateObject private var voiceController = BecoVoiceCaptureController()
  @State private var messages: [Message] = []
  @State private var text = ""
  @State private var isSending = false
  @State private var errorMessage: String?
  @State private var usage: AIUsageSummaryDTO?
  @State private var composerScrollTick: UInt = 0
  @State private var adminDestination: AIChatAdminDestination?
  @State private var isInputFocused = false

  fileprivate static let suggestionItems: [(value: String, icon: String)] = [
    ("Quanto vendemos este mês?", "chart.line.uptrend.xyaxis"),
    ("Quantas Heinekens vendemos em março?", "magnifyingglass"),
    ("Houve perdas no estoque hoje?", "shippingbox")
  ]

  public init(
    client: AIChatClient?,
    token: String?,
    repository: ChecklistRepository? = nil,
    userClient: UserClient? = nil
  ) {
    self.client = client
    self.token = token
    self.repository = repository
    self.userClient = userClient
  }

  private var trimmedText: String {
    text.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  private var canSend: Bool {
    !trimmedText.isEmpty && !isSending && usage?.blocked != true && !voiceController.isActive
  }

  private var palette: BecoChatPalette {
    BecoChatPalette(isDark: colorScheme == .dark)
  }

  private var isComposerEngaged: Bool {
    isInputFocused || voiceController.isActive
  }

  public var body: some View {
    ZStack {
      AIChatBackground(palette: palette)

      VStack(spacing: 0) {
        AIChatHeader(palette: palette)
          .padding(.horizontal, 20)
          .padding(.top, 10)

        if let usage {
          AIChatUsageBar(usage: usage, mutedForegroundColor: palette.mutedForeground)
        }

        AIChatMessageScroll(
          messages: messages,
          isSending: isSending,
          palette: palette,
          scrollTick: composerScrollTick,
          onSuggestionTap: applySuggestion
        )

        if let errorMessage {
          Text(errorMessage)
            .font(.footnote)
            .foregroundStyle(.red)
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
      }
    }
    .navigationTitle("")
    .safeAreaInset(edge: .bottom) {
      bottomChrome
    }
    .task { await loadUsage() }
    .onChange(of: voiceController.errorMessage) { message in
      if let message {
        errorMessage = message
      }
    }
    .onChange(of: voiceController.readyText) { ready in
      guard let ready, !ready.isEmpty else { return }
      text = ready
      _ = voiceController.consumeReadyText()
      isInputFocused = true
    }
    .onChange(of: isInputFocused) { focused in
      syncTabBar(engaged: focused || voiceController.isActive)
    }
    .onChange(of: voiceController.phase) { phase in
      let active = phase == .recording || phase == .transcribing
      syncTabBar(engaged: active || isInputFocused)
    }
    .onDisappear {
      tabBarVisibility.show()
    }
    .sheet(item: $adminDestination) { destination in
      adminCover(destination)
        .becoCodexSheetChrome()
    }
  }

  @ViewBuilder
  private var bottomChrome: some View {
    VStack(spacing: 10) {
      if isSending {
        AIChatProcessingPill(palette: palette)
      }

      if voiceController.isActive {
        BecoVoiceFeedbackBar(
          controller: voiceController,
          onCancel: { dismissComposerEngagement() }
        )
      } else {
        BecoChatComposer(
          text: $text,
          isInputFocused: $isInputFocused,
          placeholder: "Pergunte a AI do Beco",
          canSend: canSend,
          isSending: isSending,
          isBlocked: usage?.blocked == true,
          showsDismissButton: isComposerEngaged,
          palette: palette,
          onSend: { Task { await send() } },
          onDismiss: { dismissComposerEngagement() },
          onInputHeightChange: { _ in
            composerScrollTick &+= 1
          },
          plusContent: { suggestionsMenu },
          micContent: { micButton }
        )
      }
    }
    .padding(.horizontal, 18)
    .padding(.top, 10)
    .padding(.bottom, 8)
    .background(BecoChatBottomFade())
  }

  private var suggestionsMenu: some View {
    Menu {
      ForEach(Self.suggestionItems, id: \.value) { item in
        Button {
          applySuggestion(item.value)
        } label: {
          Label(item.value, systemImage: item.icon)
        }
      }
      if repository != nil {
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
      }
    } label: {
      BecoChatComposerIconButton(
        systemName: "plus",
        accessibilityLabel: "Sugestões e módulos",
        palette: palette
      )
    }
  }

  private var micButton: some View {
    BecoChatComposerIconButton(
      systemName: "mic",
      accessibilityLabel: "Entrada por voz",
      palette: palette,
      action: { voiceController.start() }
    )
    .disabled(isSending || usage?.blocked == true)
    .opacity(isSending || usage?.blocked == true ? 0.45 : 1)
  }

  @ViewBuilder
  private func adminCover(_ destination: AIChatAdminDestination) -> some View {
    if let repository {
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
  }

  private func applySuggestion(_ value: String) {
    text = value
    isInputFocused = true
  }

  private func dismissComposerEngagement() {
    isInputFocused = false
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

  private func syncTabBar(engaged: Bool) {
    if engaged {
      tabBarVisibility.hide()
    } else {
      tabBarVisibility.show()
    }
  }

  @MainActor
  private func send() async {
    guard let client, let token, !token.isEmpty else {
      errorMessage = "Chat indisponível sem conexão com o backend."
      return
    }
    let question = trimmedText
    guard !question.isEmpty, !isSending else { return }
    text = ""
    errorMessage = nil
    messages.append(Message(role: "user", text: question))
    isSending = true
    defer { isSending = false }
    do {
      let context = messages.suffix(4).map { AIChatMessageRequest(role: $0.role, text: $0.text) }
      let response = try await client.send(messages: Array(context), token: token)
      messages.append(
        Message(role: "assistant", text: response.answer, tools: response.consultedTools)
      )
      usage = response.budget
    } catch {
      errorMessage = AppErrorMapper.toUserMessage(error)
    }
  }

  @MainActor
  private func loadUsage() async {
    guard let client, let token else { return }
    usage = try? await client.usage(token: token)
  }
}

// MARK: - Subviews (MV)

private struct AIChatBackground: View {
  let palette: BecoChatPalette

  var body: some View {
    ZStack {
      LinearGradient(
        colors: palette.isDark
          ? [Color.black, Color(red: 0.03, green: 0.03, blue: 0.04), Color.black]
          : [Color(.systemBackground), Color(.systemGroupedBackground), Color(.secondarySystemGroupedBackground)],
        startPoint: .top,
        endPoint: .bottom
      )
      Circle()
        .fill(palette.isDark ? Color.white.opacity(0.08) : Color.black.opacity(0.035))
        .frame(width: 240, height: 240)
        .blur(radius: 70)
        .offset(x: -150, y: -240)
      Circle()
        .fill(palette.isDark ? Color.white.opacity(0.05) : Color.white.opacity(0.75))
        .frame(width: 320, height: 320)
        .blur(radius: 80)
        .offset(x: 150, y: 210)
    }
    .ignoresSafeArea()
  }
}

private struct AIChatHeader: View {
  let palette: BecoChatPalette

  var body: some View {
    HStack(spacing: 12) {
      Image(systemName: "sparkles")
        .font(.system(size: 18, weight: .semibold))
        .foregroundStyle(palette.foreground)
        .frame(width: 44, height: 44)
        .background(BecoChatGlassCapsule(palette: palette))

      VStack(alignment: .leading, spacing: 2) {
        Text("AI do Beco")
          .font(.headline.weight(.semibold))
          .foregroundStyle(palette.foreground)
        Text("Xocoalho · dados do Beco da Praia")
          .font(.caption)
          .foregroundStyle(palette.mutedForeground)
          .lineLimit(1)
      }

      Spacer(minLength: 8)

      HStack(spacing: 14) {
        Image(systemName: "square.and.pencil")
        Image(systemName: "ellipsis")
      }
      .font(.system(size: 17, weight: .semibold))
      .foregroundStyle(palette.foreground)
      .frame(height: 44)
      .padding(.horizontal, 16)
      .background(BecoChatGlassCapsule(palette: palette))
    }
  }
}

private struct AIChatUsageBar: View {
  let usage: AIUsageSummaryDTO
  let mutedForegroundColor: Color

  var body: some View {
    let spent = Double(usage.estimatedCostMicros) / 1_000_000
    let limit = Double(usage.monthlyLimitCents) / 100
    HStack {
      Text(String(format: "US$ %.2f de US$ %.2f", spent, limit))
      Spacer()
      Text("\(usage.requests) consultas")
    }
    .font(.caption)
    .foregroundStyle(usage.blocked ? .red : mutedForegroundColor)
    .padding(.horizontal, 20)
    .padding(.vertical, 8)
  }
}

private struct AIChatMessageScroll: View {
  let messages: [Message]
  let isSending: Bool
  let palette: BecoChatPalette
  let scrollTick: UInt
  let onSuggestionTap: (String) -> Void

  var body: some View {
    ScrollViewReader { proxy in
      ScrollView {
        LazyVStack(spacing: 14) {
          if messages.isEmpty {
            AIChatWelcome(palette: palette, onSuggestionTap: onSuggestionTap)
          }
          ForEach(messages) { message in
            AIChatBubble(message: message, palette: palette)
              .id(message.id)
          }
          if isSending {
            BecoChatSystemCaption("Consultando dados do Beco…")
              .id("processing-caption")
            AIChatProcessingBubble(isSending: isSending, palette: palette)
              .id("processing-bubble")
          }
          Color.clear
            .frame(height: 1)
            .id("chat-bottom")
        }
        .padding(.horizontal, 18)
        .padding(.top, 18)
        .padding(.bottom, 34)
      }
      .scrollDismissesKeyboard(.interactively)
      .onChange(of: messages.count) { _ in
        scrollToBottom(proxy)
      }
      .onChange(of: isSending) { _ in
        scrollToBottom(proxy)
      }
      .onChange(of: scrollTick) { _ in
        scrollToBottom(proxy)
      }
    }
  }

  private func scrollToBottom(_ proxy: ScrollViewProxy) {
    withAnimation(.easeOut(duration: 0.2)) {
      proxy.scrollTo("chat-bottom", anchor: .bottom)
    }
  }
}

private struct AIChatWelcome: View {
  let palette: BecoChatPalette
  let onSuggestionTap: (String) -> Void

  var body: some View {
    VStack(alignment: .leading, spacing: 18) {
      HStack(spacing: 12) {
        Image(systemName: "sparkles")
          .font(.title2.weight(.semibold))
          .foregroundStyle(palette.foreground)
          .frame(width: 42, height: 42)
          .background(Circle().fill(palette.foreground.opacity(palette.isDark ? 0.12 : 0.06)))
        VStack(alignment: .leading, spacing: 3) {
          Text("Como posso ajudar na operação?")
            .font(.headline.weight(.semibold))
            .foregroundStyle(palette.foreground)
          Text("Dados de vendas, compras, estoque e ponto")
            .font(.subheadline)
            .foregroundStyle(palette.mutedForeground)
        }
      }

      VStack(alignment: .leading, spacing: 8) {
        Text("O que você quer entender hoje?")
          .font(.title3.bold())
          .foregroundStyle(palette.foreground)
        Text("Pergunte em linguagem simples. A resposta usa os dados importados e disponíveis no backend do Beco.")
          .font(.body)
          .foregroundStyle(palette.mutedForeground)
      }

      VStack(alignment: .leading, spacing: 10) {
        ForEach(AIChatView.suggestionItems, id: \.value) { item in
          AIChatSuggestionChip(
            value: item.value,
            icon: item.icon,
            palette: palette,
            action: { onSuggestionTap(item.value) }
          )
        }
      }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(20)
    .background(BecoChatGlassRoundedRectangle(radius: 28, palette: palette))
  }
}

private struct AIChatSuggestionChip: View {
  let value: String
  let icon: String
  let palette: BecoChatPalette
  let action: () -> Void

  var body: some View {
    Button(action: action) {
      HStack(spacing: 8) {
        Image(systemName: icon)
          .font(.caption.weight(.semibold))
        Text(value)
          .font(.subheadline.weight(.medium))
          .lineLimit(2)
      }
      .foregroundStyle(palette.foreground)
      .padding(.horizontal, 12)
      .padding(.vertical, 9)
      .background(
        Capsule(style: .continuous)
          .fill(palette.foreground.opacity(palette.isDark ? 0.10 : 0.06))
      )
    }
    .buttonStyle(.plain)
  }
}

private struct AIChatBubble: View {
  let message: Message
  let palette: BecoChatPalette

  private var isUser: Bool { message.role == "user" }

  var body: some View {
    Group {
      if isUser {
        BecoChatUserTextBubble(message.text, palette: palette, cornerRadius: 19)
      } else {
        BecoChatAssistantTextBubble(
          message.text,
          palette: palette,
          sourcesCaption: sourcesCaption
        )
      }
    }
  }

  private var sourcesCaption: String? {
    guard !message.tools.isEmpty else { return nil }
    let labels = message.tools.map(Self.toolLabel)
    let unique = Array(NSOrderedSet(array: labels)) as? [String] ?? labels
    return "Fontes: \(unique.joined(separator: ", "))"
  }

  private static func toolLabel(_ value: String) -> String {
    if value.hasPrefix("sales") { return "vendas" }
    if value.hasPrefix("purchases") { return "compras" }
    if value.hasPrefix("inventory") { return "estoque" }
    if value.hasPrefix("work_clock") { return "ponto" }
    return value
  }
}

private struct AIChatProcessingBubble: View {
  let isSending: Bool
  let palette: BecoChatPalette

  var body: some View {
    BecoChatAssistantBubble(palette: palette, cornerRadius: 18) {
      HStack(spacing: 10) {
        ForEach(0..<3, id: \.self) { index in
          Circle()
            .fill(Color.secondary.opacity(0.55))
            .frame(width: 8, height: 8)
            .scaleEffect(isSending ? 1.0 : 0.7)
            .animation(
              .easeInOut(duration: 0.8).repeatForever().delay(Double(index) * 0.16),
              value: isSending
            )
        }
        Text("Processando dados do Beco…")
          .font(.subheadline.weight(.medium))
          .foregroundStyle(palette.mutedForeground)
      }
    }
    .shimmer(active: true)
  }
}

private struct AIChatProcessingPill: View {
  let palette: BecoChatPalette

  var body: some View {
    HStack(spacing: 8) {
      Image(systemName: "sparkles")
        .font(.caption.weight(.bold))
      Text("Consultando dados do Beco")
      Spacer(minLength: 0)
    }
    .font(.caption.weight(.semibold))
    .foregroundStyle(palette.mutedForeground)
    .padding(.horizontal, 14)
    .padding(.vertical, 7)
    .background(BecoChatGlassCapsule(palette: palette))
    .frame(maxWidth: 260)
    .shimmer(active: true)
  }
}

private struct Message: Identifiable {
  let id = UUID()
  let role: String
  let text: String
  var tools: [String] = []
}

private struct ShimmerModifier: ViewModifier {
  let active: Bool
  @State private var phase: CGFloat = -0.8

  func body(content: Content) -> some View {
    content
      .overlay {
        if active {
          GeometryReader { proxy in
            let width = proxy.size.width
            LinearGradient(
              colors: [.clear, .white.opacity(0.28), .clear],
              startPoint: .top,
              endPoint: .bottom
            )
            .rotationEffect(.degrees(18))
            .offset(x: width * phase)
            .blendMode(.screen)
          }
          .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
          .allowsHitTesting(false)
        }
      }
      .onAppear {
        guard active else { return }
        withAnimation(.linear(duration: 1.25).repeatForever(autoreverses: false)) {
          phase = 0.9
        }
      }
  }
}

private extension View {
  func shimmer(active: Bool) -> some View {
    modifier(ShimmerModifier(active: active))
  }
}
